package com.bipros.contract.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.contract.application.dto.ContractorScorecardRequest;
import com.bipros.contract.application.dto.ContractorScorecardResponse;
import com.bipros.contract.domain.model.ContractorScorecard;
import com.bipros.contract.domain.repository.ContractorScorecardRepository;
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
public class ContractorScorecardService {

    private final ContractorScorecardRepository scorecardRepository;

    public ContractorScorecardResponse create(ContractorScorecardRequest request) {
        log.info("Creating scorecard for contract: {} period: {}", request.contractId(), request.period());

        ContractorScorecard scorecard = new ContractorScorecard();
        scorecard.setContractId(request.contractId());
        scorecard.setPeriod(request.period());
        scorecard.setQualityScore(request.qualityScore());
        scorecard.setSafetyScore(request.safetyScore());
        scorecard.setProgressScore(request.progressScore());
        scorecard.setPaymentComplianceScore(request.paymentComplianceScore());
        scorecard.setOverallScore(request.overallScore());
        scorecard.setRemarks(request.remarks());

        ContractorScorecard saved = scorecardRepository.save(scorecard);
        log.info("Scorecard created with ID: {}", saved.getId());

        return toResponse(saved);
    }

    public ContractorScorecardResponse getById(UUID id) {
        log.info("Fetching scorecard with ID: {}", id);

        ContractorScorecard scorecard = scorecardRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ContractorScorecard", id));

        return toResponse(scorecard);
    }

    public List<ContractorScorecardResponse> listByContract(UUID contractId) {
        log.info("Listing scorecards for contract: {}", contractId);

        return scorecardRepository.findByContractId(contractId).stream()
            .map(this::toResponse)
            .toList();
    }

    public ContractorScorecardResponse getByContractAndPeriod(UUID contractId, String period) {
        log.info("Fetching scorecard for contract: {} period: {}", contractId, period);

        ContractorScorecard scorecard = scorecardRepository.findByContractIdAndPeriod(contractId, period)
            .orElseThrow(() -> new ResourceNotFoundException("ContractorScorecard", contractId));

        return toResponse(scorecard);
    }

    public ContractorScorecardResponse update(UUID id, ContractorScorecardRequest request) {
        log.info("Updating scorecard with ID: {}", id);

        ContractorScorecard scorecard = scorecardRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ContractorScorecard", id));

        scorecard.setQualityScore(request.qualityScore());
        scorecard.setSafetyScore(request.safetyScore());
        scorecard.setProgressScore(request.progressScore());
        scorecard.setPaymentComplianceScore(request.paymentComplianceScore());
        scorecard.setOverallScore(request.overallScore());
        scorecard.setRemarks(request.remarks());

        ContractorScorecard updated = scorecardRepository.save(scorecard);
        return toResponse(updated);
    }

    public void delete(UUID id) {
        log.info("Deleting scorecard with ID: {}", id);
        scorecardRepository.deleteById(id);
    }

    private ContractorScorecardResponse toResponse(ContractorScorecard scorecard) {
        return new ContractorScorecardResponse(
            scorecard.getId(),
            scorecard.getContractId(),
            scorecard.getPeriod(),
            scorecard.getQualityScore(),
            scorecard.getSafetyScore(),
            scorecard.getProgressScore(),
            scorecard.getPaymentComplianceScore(),
            scorecard.getOverallScore(),
            scorecard.getRemarks(),
            scorecard.getCreatedAt(),
            scorecard.getUpdatedAt()
        );
    }
}
