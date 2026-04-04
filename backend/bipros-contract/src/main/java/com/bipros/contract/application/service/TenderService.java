package com.bipros.contract.application.service;

import com.bipros.common.dto.PagedResponse;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.contract.application.dto.TenderRequest;
import com.bipros.contract.application.dto.TenderResponse;
import com.bipros.contract.domain.model.Tender;
import com.bipros.contract.domain.model.TenderStatus;
import com.bipros.contract.domain.repository.TenderRepository;
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
public class TenderService {

    private final TenderRepository tenderRepository;

    public TenderResponse create(TenderRequest request) {
        log.info("Creating tender with number: {}", request.tenderNumber());

        Tender tender = new Tender();
        tender.setProcurementPlanId(request.procurementPlanId());
        tender.setProjectId(request.projectId());
        tender.setTenderNumber(request.tenderNumber());
        tender.setNitDate(request.nitDate());
        tender.setScope(request.scope());
        tender.setEstimatedValue(request.estimatedValue());
        tender.setEmdAmount(request.emdAmount());
        tender.setCompletionPeriodDays(request.completionPeriodDays());
        tender.setBidDueDate(request.bidDueDate());
        tender.setBidOpenDate(request.bidOpenDate());
        tender.setStatus(TenderStatus.DRAFT);

        Tender saved = tenderRepository.save(tender);
        log.info("Tender created with ID: {}", saved.getId());

        return toResponse(saved);
    }

    public TenderResponse getById(UUID id) {
        log.info("Fetching tender with ID: {}", id);

        Tender tender = tenderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Tender", id));

        return toResponse(tender);
    }

    public PagedResponse<TenderResponse> listByProject(UUID projectId, Pageable pageable) {
        log.info("Listing tenders for project: {}", projectId);

        Page<Tender> page = tenderRepository.findByProjectId(projectId, pageable);

        return PagedResponse.of(
            page.getContent().stream().map(this::toResponse).toList(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize()
        );
    }

    public List<TenderResponse> listByProcurementPlan(UUID procurementPlanId) {
        log.info("Listing tenders for procurement plan: {}", procurementPlanId);

        return tenderRepository.findByProcurementPlanId(procurementPlanId).stream()
            .map(this::toResponse)
            .toList();
    }

    public TenderResponse update(UUID id, TenderRequest request) {
        log.info("Updating tender with ID: {}", id);

        Tender tender = tenderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Tender", id));

        tender.setScope(request.scope());
        tender.setEstimatedValue(request.estimatedValue());
        tender.setEmdAmount(request.emdAmount());
        tender.setCompletionPeriodDays(request.completionPeriodDays());
        tender.setBidDueDate(request.bidDueDate());
        tender.setBidOpenDate(request.bidOpenDate());

        Tender updated = tenderRepository.save(tender);
        return toResponse(updated);
    }

    public void delete(UUID id) {
        log.info("Deleting tender with ID: {}", id);
        tenderRepository.deleteById(id);
    }

    private TenderResponse toResponse(Tender tender) {
        return new TenderResponse(
            tender.getId(),
            tender.getProcurementPlanId(),
            tender.getProjectId(),
            tender.getTenderNumber(),
            tender.getNitDate(),
            tender.getScope(),
            tender.getEstimatedValue(),
            tender.getEmdAmount(),
            tender.getCompletionPeriodDays(),
            tender.getBidDueDate(),
            tender.getBidOpenDate(),
            tender.getStatus(),
            tender.getAwardedContractId(),
            tender.getCreatedAt(),
            tender.getUpdatedAt()
        );
    }
}
