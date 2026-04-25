package com.bipros.contract.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.contract.application.dto.ContractMilestoneRequest;
import com.bipros.contract.application.dto.ContractMilestoneResponse;
import com.bipros.contract.domain.model.AttachmentEntityType;
import com.bipros.contract.domain.model.ContractMilestone;
import com.bipros.contract.domain.model.MilestoneStatus;
import com.bipros.contract.domain.repository.ContractAttachmentRepository;
import com.bipros.contract.domain.repository.ContractMilestoneRepository;
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
public class ContractMilestoneService {

    private final ContractMilestoneRepository milestoneRepository;
    private final ContractAttachmentRepository attachmentRepository;
    private final ContractAttachmentService attachmentService;
    private final AuditService auditService;

    public ContractMilestoneResponse create(ContractMilestoneRequest request) {
        log.info("Creating milestone for contract: {}", request.contractId());

        ContractMilestone milestone = new ContractMilestone();
        milestone.setContractId(request.contractId());
        milestone.setMilestoneCode(request.milestoneCode());
        milestone.setMilestoneName(request.milestoneName());
        milestone.setTargetDate(request.targetDate());
        milestone.setActualDate(request.actualDate());
        milestone.setPaymentPercentage(request.paymentPercentage());
        milestone.setAmount(request.amount());

        if (request.actualDate() != null) {
            if (request.targetDate() != null && request.actualDate().isAfter(request.targetDate())) {
                milestone.setStatus(MilestoneStatus.DELAYED);
            } else {
                milestone.setStatus(MilestoneStatus.ACHIEVED);
            }
        } else {
            milestone.setStatus(MilestoneStatus.PENDING);
        }

        ContractMilestone saved = milestoneRepository.save(milestone);
        log.info("Milestone created with ID: {}", saved.getId());
        auditService.logCreate("ContractMilestone", saved.getId(), toResponse(saved, 0L));

        return toResponse(saved, 0L);
    }

    @Transactional(readOnly = true)
    public ContractMilestoneResponse getById(UUID id) {
        ContractMilestone milestone = milestoneRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ContractMilestone", id));
        long count = attachmentRepository.countByContractIdAndEntityTypeAndEntityId(
            milestone.getContractId(), AttachmentEntityType.MILESTONE, id);
        return toResponse(milestone, count);
    }

    @Transactional(readOnly = true)
    public List<ContractMilestoneResponse> listByContract(UUID contractId) {
        List<ContractMilestone> rows = milestoneRepository.findByContractId(contractId);
        if (rows.isEmpty()) return List.of();
        Map<UUID, Long> counts = attachmentService.countsByEntities(
            contractId, AttachmentEntityType.MILESTONE,
            rows.stream().map(ContractMilestone::getId).toList());
        return rows.stream()
            .map(m -> toResponse(m, counts.getOrDefault(m.getId(), 0L)))
            .toList();
    }

    public ContractMilestoneResponse update(UUID id, ContractMilestoneRequest request) {
        log.info("Updating milestone with ID: {}", id);

        ContractMilestone milestone = milestoneRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ContractMilestone", id));

        milestone.setMilestoneName(request.milestoneName());
        milestone.setTargetDate(request.targetDate());
        milestone.setActualDate(request.actualDate());
        milestone.setPaymentPercentage(request.paymentPercentage());
        milestone.setAmount(request.amount());

        if (request.actualDate() != null) {
            if (request.targetDate() != null && request.actualDate().isAfter(request.targetDate())) {
                milestone.setStatus(MilestoneStatus.DELAYED);
            } else {
                milestone.setStatus(MilestoneStatus.ACHIEVED);
            }
        }

        ContractMilestone updated = milestoneRepository.save(milestone);
        long count = attachmentRepository.countByContractIdAndEntityTypeAndEntityId(
            updated.getContractId(), AttachmentEntityType.MILESTONE, id);
        ContractMilestoneResponse response = toResponse(updated, count);
        auditService.logUpdate("ContractMilestone", id, "milestone", null, response);
        return response;
    }

    public void delete(UUID id) {
        log.info("Deleting milestone with ID: {}", id);
        ContractMilestone milestone = milestoneRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ContractMilestone", id));
        attachmentService.deleteAllForEntity(
            milestone.getContractId(), AttachmentEntityType.MILESTONE, id);
        milestoneRepository.deleteById(id);
        auditService.logDelete("ContractMilestone", id);
    }

    private ContractMilestoneResponse toResponse(ContractMilestone milestone, long attachmentCount) {
        return new ContractMilestoneResponse(
            milestone.getId(),
            milestone.getContractId(),
            milestone.getMilestoneCode(),
            milestone.getMilestoneName(),
            milestone.getTargetDate(),
            milestone.getActualDate(),
            milestone.getPaymentPercentage(),
            milestone.getAmount(),
            milestone.getStatus(),
            attachmentCount,
            milestone.getCreatedAt(),
            milestone.getUpdatedAt()
        );
    }
}
