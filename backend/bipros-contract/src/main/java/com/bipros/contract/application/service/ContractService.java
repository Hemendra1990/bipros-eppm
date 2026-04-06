package com.bipros.contract.application.service;

import com.bipros.common.dto.PagedResponse;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.contract.application.dto.ContractRequest;
import com.bipros.contract.application.dto.ContractResponse;
import com.bipros.contract.domain.model.Contract;
import com.bipros.contract.domain.model.ContractStatus;
import com.bipros.contract.domain.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ContractService {

    private final ContractRepository contractRepository;
    private final AuditService auditService;

    public ContractResponse create(ContractRequest request) {
        log.info("Creating contract with number: {}", request.contractNumber());

        try {
            // Validate required fields
            if (request.projectId() == null) {
                throw new BusinessRuleException("INVALID_PROJECT_ID",
                    "Project ID is required for contract creation");
            }

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

            Contract contract = new Contract();
            contract.setProjectId(request.projectId());
            contract.setTenderId(request.tenderId());
            contract.setContractNumber(request.contractNumber());
            contract.setLoaNumber(request.loaNumber());
            contract.setContractorName(request.contractorName());
            contract.setContractorCode(request.contractorCode());
            contract.setContractValue(request.contractValue());
            contract.setLoaDate(request.loaDate());
            contract.setStartDate(request.startDate());
            contract.setCompletionDate(request.completionDate());
            contract.setDlpMonths(request.dlpMonths() != null ? request.dlpMonths() : 12);
            contract.setLdRate(request.ldRate());
            contract.setStatus(ContractStatus.DRAFT);
            contract.setContractType(request.contractType());

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

        return toResponse(contract);
    }

    public PagedResponse<ContractResponse> listByProject(UUID projectId, Pageable pageable) {
        log.info("Listing contracts for project: {}", projectId);

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

        return contractRepository.findByTenderId(tenderId).stream()
            .map(this::toResponse)
            .toList();
    }

    public ContractResponse update(UUID id, ContractRequest request) {
        log.info("Updating contract with ID: {}", id);

        Contract contract = contractRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Contract", id));

        contract.setLoaNumber(request.loaNumber());
        contract.setContractValue(request.contractValue());
        contract.setLoaDate(request.loaDate());
        contract.setStartDate(request.startDate());
        contract.setCompletionDate(request.completionDate());
        contract.setDlpMonths(request.dlpMonths() != null ? request.dlpMonths() : contract.getDlpMonths());
        contract.setLdRate(request.ldRate());

        Contract updated = contractRepository.save(contract);
        auditService.logUpdate("Contract", id, "contract", null, toResponse(updated));
        return toResponse(updated);
    }

    public void delete(UUID id) {
        log.info("Deleting contract with ID: {}", id);
        contractRepository.deleteById(id);
        auditService.logDelete("Contract", id);
    }

    private ContractResponse toResponse(Contract contract) {
        return new ContractResponse(
            contract.getId(),
            contract.getProjectId(),
            contract.getTenderId(),
            contract.getContractNumber(),
            contract.getLoaNumber(),
            contract.getContractorName(),
            contract.getContractorCode(),
            contract.getContractValue(),
            contract.getLoaDate(),
            contract.getStartDate(),
            contract.getCompletionDate(),
            contract.getDlpMonths(),
            contract.getLdRate(),
            contract.getStatus(),
            contract.getContractType(),
            contract.getCreatedAt(),
            contract.getUpdatedAt()
        );
    }
}
