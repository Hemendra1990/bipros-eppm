package com.bipros.contract.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.contract.application.dto.PerformanceBondRequest;
import com.bipros.contract.application.dto.PerformanceBondResponse;
import com.bipros.contract.domain.model.AttachmentEntityType;
import com.bipros.contract.domain.model.BondStatus;
import com.bipros.contract.domain.model.PerformanceBond;
import com.bipros.contract.domain.repository.ContractAttachmentRepository;
import com.bipros.contract.domain.repository.PerformanceBondRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PerformanceBondService {

    private final PerformanceBondRepository bondRepository;
    private final ContractAttachmentRepository attachmentRepository;
    private final ContractAttachmentService attachmentService;
    private final AuditService auditService;

    public PerformanceBondResponse create(PerformanceBondRequest request) {
        log.info("Creating performance bond for contract: {}", request.contractId());

        PerformanceBond bond = new PerformanceBond();
        bond.setContractId(request.contractId());
        bond.setBondType(request.bondType());
        bond.setBondValue(request.bondValue());
        bond.setBankName(request.bankName());
        bond.setIssueDate(request.issueDate());
        bond.setExpiryDate(request.expiryDate());
        bond.setStatus(BondStatus.ACTIVE);

        PerformanceBond saved = bondRepository.save(bond);
        log.info("Performance bond created with ID: {}", saved.getId());
        auditService.logCreate("PerformanceBond", saved.getId(), toResponse(saved, 0L));

        return toResponse(saved, 0L);
    }

    @Transactional(readOnly = true)
    public PerformanceBondResponse getById(UUID id) {
        PerformanceBond bond = bondRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PerformanceBond", id));
        long count = attachmentRepository.countByContractIdAndEntityTypeAndEntityId(
            bond.getContractId(), AttachmentEntityType.PERFORMANCE_BOND, id);
        return toResponse(bond, count);
    }

    @Transactional(readOnly = true)
    public List<PerformanceBondResponse> listByContract(UUID contractId) {
        List<PerformanceBond> rows = bondRepository.findByContractId(contractId);
        if (rows.isEmpty()) return List.of();
        Map<UUID, Long> counts = attachmentService.countsByEntities(
            contractId, AttachmentEntityType.PERFORMANCE_BOND,
            rows.stream().map(PerformanceBond::getId).toList());
        return rows.stream()
            .map(b -> toResponse(b, counts.getOrDefault(b.getId(), 0L)))
            .toList();
    }

    public PerformanceBondResponse update(UUID id, PerformanceBondRequest request) {
        log.info("Updating performance bond with ID: {}", id);

        PerformanceBond bond = bondRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PerformanceBond", id));

        bond.setBondValue(request.bondValue());
        bond.setBankName(request.bankName());
        bond.setIssueDate(request.issueDate());
        bond.setExpiryDate(request.expiryDate());

        PerformanceBond updated = bondRepository.save(bond);
        long count = attachmentRepository.countByContractIdAndEntityTypeAndEntityId(
            updated.getContractId(), AttachmentEntityType.PERFORMANCE_BOND, id);
        PerformanceBondResponse response = toResponse(updated, count);
        auditService.logUpdate("PerformanceBond", id, "bond", null, response);
        return response;
    }

    public void delete(UUID id) {
        log.info("Deleting performance bond with ID: {}", id);
        PerformanceBond bond = bondRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PerformanceBond", id));
        attachmentService.deleteAllForEntity(
            bond.getContractId(), AttachmentEntityType.PERFORMANCE_BOND, id);
        bondRepository.deleteById(id);
        auditService.logDelete("PerformanceBond", id);
    }

    private PerformanceBondResponse toResponse(PerformanceBond bond, long attachmentCount) {
        return new PerformanceBondResponse(
            bond.getId(),
            bond.getContractId(),
            bond.getBondType(),
            bond.getBondValue(),
            bond.getBankName(),
            bond.getIssueDate(),
            bond.getExpiryDate(),
            bond.getStatus(),
            attachmentCount,
            bond.getCreatedAt(),
            bond.getUpdatedAt()
        );
    }
}
