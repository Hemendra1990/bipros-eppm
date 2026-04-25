package com.bipros.contract.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.contract.application.dto.ContractAttachmentResponse;
import com.bipros.contract.application.dto.UploadContractAttachmentRequest;
import com.bipros.contract.domain.model.AttachmentEntityType;
import com.bipros.contract.domain.model.Contract;
import com.bipros.contract.domain.model.ContractAttachment;
import com.bipros.contract.domain.model.ContractMilestone;
import com.bipros.contract.domain.model.PerformanceBond;
import com.bipros.contract.domain.model.VariationOrder;
import com.bipros.contract.domain.repository.ContractAttachmentRepository;
import com.bipros.contract.domain.repository.ContractMilestoneRepository;
import com.bipros.contract.domain.repository.ContractRepository;
import com.bipros.contract.domain.repository.PerformanceBondRepository;
import com.bipros.contract.domain.repository.VariationOrderRepository;
import com.bipros.contract.infrastructure.storage.ContractFileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Polymorphic attachment service. Backs the contract-level Attachments tab
 * and the per-row attachment expand panels on the Milestones / VariationOrders
 * / PerformanceBonds tabs.
 *
 * <p>The {@code entityType} discriminator is validated against the parent
 * repository on every write so a milestone-id can never be paired with
 * {@link AttachmentEntityType#PERFORMANCE_BOND}, etc.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ContractAttachmentService {

    private final ContractAttachmentRepository attachmentRepository;
    private final ContractRepository contractRepository;
    private final ContractMilestoneRepository milestoneRepository;
    private final VariationOrderRepository variationOrderRepository;
    private final PerformanceBondRepository bondRepository;
    private final ContractFileStorageService storageService;
    private final AuditService auditService;

    public ContractAttachmentResponse upload(
            UUID projectId,
            UUID contractId,
            AttachmentEntityType entityType,
            UUID entityId,
            UploadContractAttachmentRequest meta,
            MultipartFile file,
            String uploadedBy) {
        verifyOwnership(projectId, contractId, entityType, entityId);

        ContractAttachment a = new ContractAttachment();
        a.setProjectId(projectId);
        a.setContractId(contractId);
        a.setEntityType(entityType);
        a.setEntityId(entityId);
        a.setAttachmentType(meta.attachmentType());
        a.setDescription(meta.description());
        a.setFileName(file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename());
        a.setFileSize(file.getSize());
        a.setMimeType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        a.setFilePath("pending");
        a.setUploadedBy(uploadedBy != null ? uploadedBy : "SYSTEM");
        a.setUploadedAt(Instant.now());

        ContractAttachment saved = attachmentRepository.save(a);

        ContractFileStorageService.StoredFile stored = storageService.store(
            projectId, contractId, entityType, entityId, saved.getId(), file);

        saved.setFileName(stored.fileName());
        saved.setFileSize(stored.fileSize());
        saved.setMimeType(stored.mimeType());
        saved.setFilePath(stored.relativePath());
        saved = attachmentRepository.save(saved);

        auditService.logCreate("ContractAttachment", saved.getId(), ContractAttachmentResponse.from(saved));
        log.info("[Contract attachment] uploaded {} for contract={} entity={}/{}",
            saved.getFileName(), contractId, entityType, entityId);

        return ContractAttachmentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ContractAttachmentResponse> list(
            UUID projectId,
            UUID contractId,
            AttachmentEntityType entityType,
            UUID entityId) {
        verifyOwnership(projectId, contractId, entityType, entityId);
        return attachmentRepository
            .findByContractIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(contractId, entityType, entityId)
            .stream()
            .map(ContractAttachmentResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ContractAttachmentResponse> listAllForContract(UUID projectId, UUID contractId) {
        verifyContract(projectId, contractId);
        return attachmentRepository
            .findByContractIdOrderByCreatedAtDesc(contractId)
            .stream()
            .map(ContractAttachmentResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public AttachmentDownload download(UUID projectId, UUID contractId, UUID attachmentId) {
        ContractAttachment a = fetchAndVerify(projectId, contractId, attachmentId);
        if (a.getFilePath() == null || a.getFilePath().isBlank()) {
            throw new IllegalStateException("Attachment has no file: " + attachmentId);
        }
        return new AttachmentDownload(
            storageService.load(a.getFilePath()),
            a.getFileName(),
            a.getMimeType(),
            a.getFileSize() == null ? 0 : a.getFileSize());
    }

    public void delete(UUID projectId, UUID contractId, UUID attachmentId) {
        ContractAttachment a = fetchAndVerify(projectId, contractId, attachmentId);
        storageService.deleteQuietly(a.getFilePath());
        attachmentRepository.delete(a);
        auditService.logDelete("ContractAttachment", attachmentId);
    }

    /**
     * Cascade entry point used by parent-entity services on {@code delete()}.
     * Fetches all matching attachment rows, removes their binaries, then deletes the rows.
     */
    public void deleteAllForEntity(UUID contractId, AttachmentEntityType entityType, UUID entityId) {
        List<ContractAttachment> rows = attachmentRepository
            .findByContractIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(contractId, entityType, entityId);
        for (ContractAttachment a : rows) {
            storageService.deleteQuietly(a.getFilePath());
            attachmentRepository.delete(a);
            auditService.logDelete("ContractAttachment", a.getId());
        }
    }

    public void deleteAllForContract(UUID contractId) {
        List<ContractAttachment> rows = attachmentRepository.findByContractIdOrderByCreatedAtDesc(contractId);
        for (ContractAttachment a : rows) {
            storageService.deleteQuietly(a.getFilePath());
            attachmentRepository.delete(a);
            auditService.logDelete("ContractAttachment", a.getId());
        }
    }

    /** Bulk count, keyed by entityId, for badge rendering on parent-entity list rows. */
    @Transactional(readOnly = true)
    public Map<UUID, Long> countsByEntities(
            UUID contractId, AttachmentEntityType entityType, Collection<UUID> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> result = new HashMap<>();
        for (var row : attachmentRepository.countByEntities(contractId, entityType, entityIds)) {
            result.put(row.getEntityId(), row.getCnt());
        }
        for (UUID id : entityIds) {
            result.putIfAbsent(id, 0L);
        }
        return result;
    }

    // ------------------------------------------------------------------ helpers

    private ContractAttachment fetchAndVerify(UUID projectId, UUID contractId, UUID attachmentId) {
        ContractAttachment a = attachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new ResourceNotFoundException("ContractAttachment", attachmentId));
        if (!a.getProjectId().equals(projectId) || !a.getContractId().equals(contractId)) {
            throw new ResourceNotFoundException("ContractAttachment", attachmentId);
        }
        return a;
    }

    private void verifyOwnership(
            UUID projectId, UUID contractId, AttachmentEntityType entityType, UUID entityId) {
        Contract contract = verifyContract(projectId, contractId);

        switch (entityType) {
            case CONTRACT -> {
                if (!entityId.equals(contractId)) {
                    throw new BusinessRuleException("ATTACHMENT_ENTITY_MISMATCH",
                        "Contract-level attachment must use entityId = contractId");
                }
            }
            case MILESTONE -> {
                ContractMilestone m = milestoneRepository.findById(entityId)
                    .orElseThrow(() -> new ResourceNotFoundException("ContractMilestone", entityId));
                if (!m.getContractId().equals(contractId)) {
                    throw new BusinessRuleException("ATTACHMENT_ENTITY_MISMATCH",
                        "Milestone " + entityId + " does not belong to contract " + contractId);
                }
            }
            case VARIATION_ORDER -> {
                VariationOrder vo = variationOrderRepository.findById(entityId)
                    .orElseThrow(() -> new ResourceNotFoundException("VariationOrder", entityId));
                if (!vo.getContractId().equals(contractId)) {
                    throw new BusinessRuleException("ATTACHMENT_ENTITY_MISMATCH",
                        "VariationOrder " + entityId + " does not belong to contract " + contractId);
                }
            }
            case PERFORMANCE_BOND -> {
                PerformanceBond bond = bondRepository.findById(entityId)
                    .orElseThrow(() -> new ResourceNotFoundException("PerformanceBond", entityId));
                if (!bond.getContractId().equals(contractId)) {
                    throw new BusinessRuleException("ATTACHMENT_ENTITY_MISMATCH",
                        "PerformanceBond " + entityId + " does not belong to contract " + contractId);
                }
            }
        }
        // suppress unused warning for `contract`
        if (contract == null) {
            throw new ResourceNotFoundException("Contract", contractId);
        }
    }

    private Contract verifyContract(UUID projectId, UUID contractId) {
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ResourceNotFoundException("Contract", contractId));
        if (!contract.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("Contract", contractId);
        }
        return contract;
    }

    public record AttachmentDownload(
        Resource resource,
        String fileName,
        String mimeType,
        long fileSize
    ) {}
}
