package com.bipros.contract.application.service;

import com.bipros.common.dto.PagedResponse;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.contract.application.dto.BidSubmissionRequest;
import com.bipros.contract.application.dto.BidSubmissionResponse;
import com.bipros.contract.domain.model.BidSubmission;
import com.bipros.contract.domain.model.BidSubmissionStatus;
import com.bipros.contract.domain.repository.BidSubmissionRepository;
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
public class BidSubmissionService {

    private final BidSubmissionRepository bidSubmissionRepository;
    private final AuditService auditService;

    public BidSubmissionResponse create(BidSubmissionRequest request) {
        log.info("Creating bid submission from: {}", request.bidderName());

        BidSubmission bid = new BidSubmission();
        bid.setTenderId(request.tenderId());
        bid.setBidderName(request.bidderName());
        bid.setBidderCode(request.bidderCode());
        bid.setTechnicalScore(request.technicalScore());
        bid.setFinancialBid(request.financialBid());
        bid.setEvaluationRemarks(request.evaluationRemarks());
        bid.setStatus(BidSubmissionStatus.SUBMITTED);

        BidSubmission saved = bidSubmissionRepository.save(bid);
        log.info("Bid submission created with ID: {}", saved.getId());
        auditService.logCreate("BidSubmission", saved.getId(), toResponse(saved));

        return toResponse(saved);
    }

    public BidSubmissionResponse getById(UUID id) {
        log.info("Fetching bid submission with ID: {}", id);

        BidSubmission bid = bidSubmissionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("BidSubmission", id));

        return toResponse(bid);
    }

    public PagedResponse<BidSubmissionResponse> listByTender(UUID tenderId, Pageable pageable) {
        log.info("Listing bid submissions for tender: {}", tenderId);

        Page<BidSubmission> page = bidSubmissionRepository.findByTenderId(tenderId, pageable);

        return PagedResponse.of(
            page.getContent().stream().map(this::toResponse).toList(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize()
        );
    }

    public List<BidSubmissionResponse> listByTenderAll(UUID tenderId) {
        log.info("Listing all bid submissions for tender: {}", tenderId);

        return bidSubmissionRepository.findByTenderId(tenderId).stream()
            .map(this::toResponse)
            .toList();
    }

    public BidSubmissionResponse update(UUID id, BidSubmissionRequest request) {
        log.info("Updating bid submission with ID: {}", id);

        BidSubmission bid = bidSubmissionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("BidSubmission", id));

        bid.setTechnicalScore(request.technicalScore());
        bid.setFinancialBid(request.financialBid());
        bid.setEvaluationRemarks(request.evaluationRemarks());

        BidSubmission updated = bidSubmissionRepository.save(bid);
        auditService.logUpdate("BidSubmission", id, "bid", null, toResponse(updated));
        return toResponse(updated);
    }

    public void delete(UUID id) {
        log.info("Deleting bid submission with ID: {}", id);
        bidSubmissionRepository.deleteById(id);
        auditService.logDelete("BidSubmission", id);
    }

    private BidSubmissionResponse toResponse(BidSubmission bid) {
        return new BidSubmissionResponse(
            bid.getId(),
            bid.getTenderId(),
            bid.getBidderName(),
            bid.getBidderCode(),
            bid.getTechnicalScore(),
            bid.getFinancialBid(),
            bid.getStatus(),
            bid.getEvaluationRemarks(),
            bid.getCreatedAt(),
            bid.getUpdatedAt()
        );
    }
}
