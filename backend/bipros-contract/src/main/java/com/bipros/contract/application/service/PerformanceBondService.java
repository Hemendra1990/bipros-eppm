package com.bipros.contract.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.contract.application.dto.PerformanceBondRequest;
import com.bipros.contract.application.dto.PerformanceBondResponse;
import com.bipros.contract.domain.model.PerformanceBond;
import com.bipros.contract.domain.model.BondStatus;
import com.bipros.contract.domain.repository.PerformanceBondRepository;
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
public class PerformanceBondService {

    private final PerformanceBondRepository bondRepository;
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
        auditService.logCreate("PerformanceBond", saved.getId(), toResponse(saved));

        return toResponse(saved);
    }

    public PerformanceBondResponse getById(UUID id) {
        log.info("Fetching performance bond with ID: {}", id);

        PerformanceBond bond = bondRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PerformanceBond", id));

        return toResponse(bond);
    }

    public List<PerformanceBondResponse> listByContract(UUID contractId) {
        log.info("Listing performance bonds for contract: {}", contractId);

        return bondRepository.findByContractId(contractId).stream()
            .map(this::toResponse)
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
        auditService.logUpdate("PerformanceBond", id, "bond", null, toResponse(updated));
        return toResponse(updated);
    }

    public void delete(UUID id) {
        log.info("Deleting performance bond with ID: {}", id);
        bondRepository.deleteById(id);
        auditService.logDelete("PerformanceBond", id);
    }

    private PerformanceBondResponse toResponse(PerformanceBond bond) {
        return new PerformanceBondResponse(
            bond.getId(),
            bond.getContractId(),
            bond.getBondType(),
            bond.getBondValue(),
            bond.getBankName(),
            bond.getIssueDate(),
            bond.getExpiryDate(),
            bond.getStatus(),
            bond.getCreatedAt(),
            bond.getUpdatedAt()
        );
    }
}
