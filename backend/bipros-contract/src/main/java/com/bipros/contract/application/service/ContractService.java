package com.bipros.contract.application.service;

import com.bipros.common.dto.PagedResponse;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.common.util.AuditService;
import com.bipros.contract.application.dto.ContractRequest;
import com.bipros.contract.application.dto.ContractResponse;
import com.bipros.contract.domain.model.Contract;
import com.bipros.contract.domain.model.ContractStatus;
import com.bipros.contract.domain.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ContractService {

    private final ContractRepository contractRepository;
    private final AuditService auditService;
    private final ProjectAccessGuard projectAccess;

    /**
     * Lazily injected to break the cycle: ContractAttachmentService needs
     * ContractRepository (for ownership checks) which lives in the same module.
     */
    @Autowired(required = false)
    @Lazy
    private ContractAttachmentService contractAttachmentService;

    public ContractResponse create(ContractRequest request) {
        log.info("Creating contract with number: {}", request.contractNumber());

        try {
            if (request.projectId() == null) {
                throw new BusinessRuleException("INVALID_PROJECT_ID",
                    "Project ID is required for contract creation");
            }

            projectAccess.requireEdit(request.projectId());

            if (request.contractNumber() == null || request.contractNumber().isBlank()) {
                throw new BusinessRuleException("INVALID_CONTRACT_NUMBER",
                    "Contract number is required");
            }

            if (request.contractorName() == null || request.contractorName().isBlank()) {
                throw new BusinessRuleException("INVALID_CONTRACTOR_NAME",
                    "Contractor name is required");
            }

            if (request.contractType() == null) {
                throw new BusinessRuleException("INVALID_CONTRACT_TYPE",
                    "Contract type is required");
            }

            validateValueAndDateInvariants(request);

            Contract contract = new Contract();
            contract.setProjectId(request.projectId());
            contract.setTenderId(request.tenderId());
            contract.setContractNumber(request.contractNumber());
            contract.setLoaNumber(request.loaNumber());
            contract.setContractorName(request.contractorName());
            contract.setContractorCode(request.contractorCode());
            contract.setContractValue(request.contractValue());
            contract.setRevisedValue(request.revisedValue());
            contract.setLoaDate(request.loaDate());
            contract.setStartDate(request.startDate());
            contract.setCompletionDate(request.completionDate());
            contract.setRevisedCompletionDate(request.revisedCompletionDate());
            contract.setDlpMonths(request.dlpMonths() != null ? request.dlpMonths() : 12);
            contract.setLdRate(request.ldRate());
            contract.setStatus(ContractStatus.DRAFT);
            contract.setContractType(request.contractType());
            contract.setDescription(request.description());
            contract.setCurrency(request.currency() != null && !request.currency().isBlank()
                ? request.currency() : "INR");
            contract.setNtpDate(request.ntpDate());
            contract.setMobilisationAdvancePct(request.mobilisationAdvancePct());
            contract.setRetentionPct(request.retentionPct());
            contract.setPerformanceBgPct(request.performanceBgPct());
            contract.setPaymentTermsDays(request.paymentTermsDays());
            contract.setBillingCycle(request.billingCycle());

            Contract saved = contractRepository.save(contract);
            log.info("Contract created with ID: {}", saved.getId());
            auditService.logCreate("Contract", saved.getId(), toResponse(saved));

            return toResponse(saved);
        } catch (BusinessRuleException e) {
            log.error("Validation error creating contract: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error creating contract with number: {}", request.contractNumber(), e);
            throw new BusinessRuleException("CONTRACT_CREATION_ERROR",
                "Failed to create contract: " + e.getMessage());
        }
    }

    public ContractResponse getById(UUID id) {
        log.info("Fetching contract with ID: {}", id);

        Contract contract = contractRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Contract", id));

        projectAccess.requireRead(contract.getProjectId());

        return toResponse(contract);
    }

    public PagedResponse<ContractResponse> listByProject(UUID projectId, Pageable pageable) {
        log.info("Listing contracts for project: {}", projectId);

        projectAccess.requireRead(projectId);

        Page<Contract> page = contractRepository.findByProjectId(projectId, pageable);

        return PagedResponse.of(
            page.getContent().stream().map(this::toResponse).toList(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize()
        );
    }

    public List<ContractResponse> listByTender(UUID tenderId) {
        log.info("Listing contracts for tender: {}", tenderId);

        Set<UUID> allowed = projectAccess.getAccessibleProjectIdsForCurrentUser();
        return contractRepository.findByTenderId(tenderId).stream()
            .filter(c -> allowed == null || allowed.contains(c.getProjectId()))
            .map(this::toResponse)
            .toList();
    }

    public ContractResponse update(UUID id, ContractRequest request) {
        log.info("Updating contract with ID: {}", id);

        Contract contract = contractRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Contract", id));

        projectAccess.requireEdit(contract.getProjectId());

        validateValueAndDateInvariants(request);

        if (request.contractNumber() != null && !request.contractNumber().isBlank()
                && !request.contractNumber().equals(contract.getContractNumber())) {
            contractRepository.findByContractNumber(request.contractNumber()).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new BusinessRuleException("DUPLICATE_CONTRACT_NUMBER",
                        "Another contract already uses contract number " + request.contractNumber());
                }
            });
            contract.setContractNumber(request.contractNumber());
        }

        if (request.contractorName() != null && !request.contractorName().isBlank()) {
            contract.setContractorName(request.contractorName());
        }
        if (request.contractType() != null) {
            contract.setContractType(request.contractType());
        }

        contract.setTenderId(request.tenderId());
        contract.setLoaNumber(request.loaNumber());
        contract.setContractorCode(request.contractorCode());
        contract.setContractValue(request.contractValue());
        contract.setRevisedValue(request.revisedValue());
        contract.setLoaDate(request.loaDate());
        contract.setStartDate(request.startDate());
        contract.setCompletionDate(request.completionDate());
        contract.setRevisedCompletionDate(request.revisedCompletionDate());
        contract.setDlpMonths(request.dlpMonths() != null ? request.dlpMonths() : contract.getDlpMonths());
        contract.setLdRate(request.ldRate());
        contract.setDescription(request.description());
        if (request.currency() != null && !request.currency().isBlank()) {
            contract.setCurrency(request.currency());
        }
        contract.setNtpDate(request.ntpDate());
        contract.setMobilisationAdvancePct(request.mobilisationAdvancePct());
        contract.setRetentionPct(request.retentionPct());
        contract.setPerformanceBgPct(request.performanceBgPct());
        contract.setPaymentTermsDays(request.paymentTermsDays());
        contract.setBillingCycle(request.billingCycle());

        Contract updated = contractRepository.save(contract);
        auditService.logUpdate("Contract", id, "contract", null, toResponse(updated));
        return toResponse(updated);
    }

    public void delete(UUID id) {
        log.info("Deleting contract with ID: {}", id);
        Contract contract = contractRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Contract", id));
        projectAccess.requireDelete(contract.getProjectId());

        if (contractAttachmentService != null) {
            contractAttachmentService.deleteAllForContract(id);
        }
        contractRepository.deleteById(id);
        auditService.logDelete("Contract", id);
    }

    private void validateValueAndDateInvariants(ContractRequest request) {
        if (request.revisedValue() != null && request.contractValue() != null
                && request.revisedValue().compareTo(request.contractValue()) < 0) {
            throw new BusinessRuleException("INVALID_REVISED_VALUE",
                "Revised value cannot be lower than the original contract value");
        }
        if (request.revisedCompletionDate() != null && request.completionDate() != null
                && request.revisedCompletionDate().isBefore(request.completionDate())) {
            throw new BusinessRuleException("INVALID_REVISED_COMPLETION",
                "Revised completion date cannot be earlier than the original completion date");
        }
    }

    ContractResponse toResponse(Contract contract) {
        return new ContractResponse(
            contract.getId(),
            contract.getProjectId(),
            contract.getTenderId(),
            contract.getContractNumber(),
            contract.getLoaNumber(),
            contract.getContractorName(),
            contract.getContractorCode(),
            contract.getContractValue(),
            contract.getRevisedValue(),
            contract.getLoaDate(),
            contract.getStartDate(),
            contract.getCompletionDate(),
            contract.getRevisedCompletionDate(),
            contract.getDlpMonths(),
            contract.getLdRate(),
            contract.getStatus(),
            contract.getContractType(),
            contract.getDescription(),
            contract.getCurrency(),
            contract.getNtpDate(),
            contract.getMobilisationAdvancePct(),
            contract.getRetentionPct(),
            contract.getPerformanceBgPct(),
            contract.getPaymentTermsDays(),
            contract.getBillingCycle(),
            contract.getWbsPackageCode(),
            contract.getPackageDescription(),
            contract.getActualCompletionDate(),
            contract.getSpi(),
            contract.getCpi(),
            contract.getPhysicalProgressAi(),
            contract.getCumulativeRaBillsCrores(),
            contract.getVoNumbersIssued(),
            contract.getVoValueCrores(),
            contract.getPerformanceScore(),
            contract.getBgExpiry(),
            contract.getKpiRefreshedAt(),
            contract.getCreatedAt(),
            contract.getUpdatedAt()
        );
    }
}
