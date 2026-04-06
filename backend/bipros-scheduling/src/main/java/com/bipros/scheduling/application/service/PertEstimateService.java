package com.bipros.scheduling.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.scheduling.application.dto.PertEstimateRequest;
import com.bipros.scheduling.application.dto.PertEstimateResponse;
import com.bipros.scheduling.domain.model.PertEstimate;
import com.bipros.scheduling.domain.repository.PertEstimateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class PertEstimateService {

  private final PertEstimateRepository pertEstimateRepository;
  private final AuditService auditService;

  public PertEstimateResponse createOrUpdate(PertEstimateRequest request) {
    log.info("Creating or updating PERT estimate for activity: id={}", request.activityId());

    validateRequest(request);

    // Calculate PERT metrics
    double o = request.optimisticDuration();
    double m = request.mostLikelyDuration();
    double p = request.pessimisticDuration();

    double expectedDuration = (o + 4 * m + p) / 6.0;
    double standardDeviation = (p - o) / 6.0;
    double variance = Math.pow((p - o) / 6.0, 2);

    // Find and update or create new
    PertEstimate estimate = pertEstimateRepository.findByActivityId(request.activityId())
        .orElse(PertEstimate.builder()
            .activityId(request.activityId())
            .build());

    estimate.setOptimisticDuration(o);
    estimate.setMostLikelyDuration(m);
    estimate.setPessimisticDuration(p);
    estimate.setExpectedDuration(expectedDuration);
    estimate.setStandardDeviation(standardDeviation);
    estimate.setVariance(variance);

    PertEstimate saved = pertEstimateRepository.save(estimate);
    log.info("PERT estimate saved: activityId={}, expectedDuration={}", request.activityId(), expectedDuration);
    auditService.logCreate("PertEstimate", saved.getId(), PertEstimateResponse.from(saved));

    return PertEstimateResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public PertEstimateResponse getByActivity(UUID activityId) {
    log.debug("Fetching PERT estimate for activity: id={}", activityId);

    return pertEstimateRepository.findByActivityId(activityId)
        .map(PertEstimateResponse::from)
        .orElseThrow(() -> new ResourceNotFoundException("PertEstimate", activityId));
  }

  @Transactional(readOnly = true)
  public List<PertEstimateResponse> getByActivities(List<UUID> activityIds) {
    log.debug("Fetching PERT estimates for {} activities", activityIds.size());

    return pertEstimateRepository.findByActivityIdIn(activityIds)
        .stream()
        .map(PertEstimateResponse::from)
        .toList();
  }

  private void validateRequest(PertEstimateRequest request) {
    double o = request.optimisticDuration();
    double m = request.mostLikelyDuration();
    double p = request.pessimisticDuration();

    if (o <= 0 || m <= 0 || p <= 0) {
      throw new IllegalArgumentException("All PERT durations must be positive");
    }

    if (o > m || m > p) {
      throw new IllegalArgumentException(
          "Optimistic duration must be <= Most Likely duration must be <= Pessimistic duration"
      );
    }
  }
}
