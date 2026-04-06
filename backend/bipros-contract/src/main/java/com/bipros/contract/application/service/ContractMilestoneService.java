package com.bipros.contract.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.contract.application.dto.ContractMilestoneRequest;
import com.bipros.contract.application.dto.ContractMilestoneResponse;
import com.bipros.contract.domain.model.ContractMilestone;
import com.bipros.contract.domain.model.MilestoneStatus;
import com.bipros.contract.domain.repository.ContractMilestoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ContractMilestoneService {

    private final ContractMilestoneRepository milestoneRepository;
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

        // Determine status based on actualDate
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
        auditService.logCreate("ContractMilestone", saved.getId(), toResponse(saved));

        return toResponse(saved);
    }

    public ContractMilestoneResponse getById(UUID id) {
        log.info("Fetching milestone with ID: {}", id);

        ContractMilestone milestone = milestoneRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ContractMilestone", id));

        return toResponse(milestone);
    }

    public List<ContractMilestoneResponse> listByContract(UUID contractId) {
        log.info("Listing milestones for contract: {}", contractId);

        return milestoneRepository.findByContractId(contractId).stream()
            .map(this::toResponse)
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

        // Auto-update status based on actualDate
        if (request.actualDate() != null) {
            if (request.targetDate() != null && request.actualDate().isAfter(request.targetDate())) {
                milestone.setStatus(MilestoneStatus.DELAYED);
            } else {
                milestone.setStatus(MilestoneStatus.ACHIEVED);
            }
        }

        ContractMilestone updated = milestoneRepository.save(milestone);
        auditService.logUpdate("ContractMilestone", id, "milestone", null, toResponse(updated));
        return toResponse(updated);
    }

    public void delete(UUID id) {
        log.info("Deleting milestone with ID: {}", id);
        milestoneRepository.deleteById(id);
        auditService.logDelete("ContractMilestone", id);
    }

    private ContractMilestoneResponse toResponse(ContractMilestone milestone) {
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
            milestone.getCreatedAt(),
            milestone.getUpdatedAt()
        );
    }
}
