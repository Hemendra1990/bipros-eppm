package com.bipros.reporting.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.reporting.application.dto.PredictionDto;
import com.bipros.reporting.domain.model.Prediction;
import com.bipros.reporting.domain.repository.PredictionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionService {

  private final PredictionRepository predictionRepository;
  private final ObjectMapper objectMapper;

  @PersistenceContext private EntityManager em;

  /**
   * Predict schedule slip based on rule-based factors:
   * - SPI from EVM
   * - Critical path activity count and average float
   * - Count of delayed activities
   */
  @Transactional
  public PredictionDto predictScheduleSlip(UUID projectId) {
    try {
      // Get SPI (Schedule Performance Index)
      Double spi = getSchedulePerformanceIndex(projectId);
      if (spi == null) {
        spi = 1.0;
      }

      // Get critical activity metrics
      Map<String, Object> criticalMetrics = getCriticalActivityMetrics(projectId);
      Integer criticalActivityCount = (Integer) criticalMetrics.getOrDefault("count", 0);
      Double avgFloat = (Double) criticalMetrics.getOrDefault("avgFloat", 0.0);

      // Count delayed activities
      Integer delayedActivityCount = countDelayedActivities(projectId);

      // Rule-based prediction
      double slipRiskScore = calculateSlipRiskScore(spi, criticalActivityCount, avgFloat,
          delayedActivityCount);
      double predictedSlipDays = calculatePredictedSlip(spi);

      // Build factors JSON
      Map<String, Object> factors = new HashMap<>();
      factors.put("spi", spi);
      factors.put("criticalActivityCount", criticalActivityCount);
      factors.put("avgCriticalFloat", avgFloat);
      factors.put("delayedActivityCount", delayedActivityCount);
      factors.put("riskScore", slipRiskScore);

      Prediction prediction = new Prediction();
      prediction.setProjectId(projectId);
      prediction.setPredictionType(Prediction.PredictionType.SCHEDULE_SLIP);
      prediction.setPredictedValue(predictedSlipDays);
      prediction.setConfidenceLevel(0.65);
      prediction.setVariance(Math.abs(predictedSlipDays));
      prediction.setFactors(objectMapper.writeValueAsString(factors));
      prediction.setModelVersion("rule-based-v1");
      prediction.setCalculatedAt(Instant.now());

      Prediction saved = predictionRepository.save(prediction);
      return PredictionDto.from(saved);
    } catch (Exception e) {
      log.error("Error predicting schedule slip for projectId={}", projectId, e);
      throw new RuntimeException("Failed to predict schedule slip", e);
    }
  }

  /**
   * Predict cost overrun based on rule-based factors:
   * - CPI from EVM
   * - Variation order value as % of contract
   */
  @Transactional
  public PredictionDto predictCostOverrun(UUID projectId) {
    try {
      Double cpi = getCostPerformanceIndex(projectId);
      if (cpi == null) {
        cpi = 1.0;
      }

      Double contractValue = getContractValue(projectId);
      Double voValue = getVariationOrderValue(projectId);
      Double voPercentage = contractValue != null && contractValue > 0
          ? (voValue != null ? voValue : 0.0) / contractValue * 100
          : 0.0;

      double overrunRiskScore = calculateOverrunRiskScore(cpi, voPercentage);
      double predictedOverrun = calculatePredictedOverrun(cpi, contractValue);

      Map<String, Object> factors = new HashMap<>();
      factors.put("cpi", cpi);
      factors.put("contractValue", contractValue);
      factors.put("voValue", voValue);
      factors.put("voPercentage", voPercentage);
      factors.put("riskScore", overrunRiskScore);

      Prediction prediction = new Prediction();
      prediction.setProjectId(projectId);
      prediction.setPredictionType(Prediction.PredictionType.COST_OVERRUN);
      prediction.setPredictedValue(predictedOverrun);
      prediction.setConfidenceLevel(0.65);
      prediction.setVariance(Math.abs(predictedOverrun));
      prediction.setFactors(objectMapper.writeValueAsString(factors));
      prediction.setModelVersion("rule-based-v1");
      prediction.setCalculatedAt(Instant.now());

      Prediction saved = predictionRepository.save(prediction);
      return PredictionDto.from(saved);
    } catch (Exception e) {
      log.error("Error predicting cost overrun for projectId={}", projectId, e);
      throw new RuntimeException("Failed to predict cost overrun", e);
    }
  }

  /**
   * Predict project completion date based on SPI
   */
  @Transactional
  public PredictionDto predictCompletionDate(UUID projectId) {
    try {
      Double spi = getSchedulePerformanceIndex(projectId);
      if (spi == null) {
        spi = 1.0;
      }

      Map<String, Double> dateInfo = getProjectDateInfo(projectId);
      Double daysElapsed = dateInfo.getOrDefault("daysElapsed", 0.0);
      Double daysRemaining = dateInfo.getOrDefault("daysRemaining", 0.0);
      Double baselineEndDays = dateInfo.getOrDefault("baselineEndDays", 0.0);

      // Adjusted remaining = remaining / SPI
      double adjustedRemaining = spi > 0 ? daysRemaining / spi : daysRemaining;
      double predictedTotalDays = daysElapsed + adjustedRemaining;
      double variance = predictedTotalDays - baselineEndDays;

      Map<String, Object> factors = new HashMap<>();
      factors.put("spi", spi);
      factors.put("daysElapsed", daysElapsed);
      factors.put("daysRemaining", daysRemaining);
      factors.put("adjustedRemaining", adjustedRemaining);
      factors.put("variance", variance);

      Prediction prediction = new Prediction();
      prediction.setProjectId(projectId);
      prediction.setPredictionType(Prediction.PredictionType.COMPLETION_DATE);
      prediction.setPredictedValue(adjustedRemaining);
      prediction.setConfidenceLevel(0.65);
      prediction.setBaselineValue(daysRemaining);
      prediction.setVariance(variance);
      prediction.setFactors(objectMapper.writeValueAsString(factors));
      prediction.setModelVersion("rule-based-v1");
      prediction.setCalculatedAt(Instant.now());

      Prediction saved = predictionRepository.save(prediction);
      return PredictionDto.from(saved);
    } catch (Exception e) {
      log.error("Error predicting completion date for projectId={}", projectId, e);
      throw new RuntimeException("Failed to predict completion date", e);
    }
  }

  /**
   * Run all predictions for a project
   */
  @Transactional
  public List<PredictionDto> runAllPredictions(UUID projectId) {
    return List.of(
        predictScheduleSlip(projectId),
        predictCostOverrun(projectId),
        predictCompletionDate(projectId)
    );
  }

  /**
   * Get latest predictions for a project
   */
  @Transactional(readOnly = true)
  public List<PredictionDto> getProjectPredictions(UUID projectId) {
    return predictionRepository.findByProjectIdOrderByCalculatedAtDesc(projectId).stream()
        .map(PredictionDto::from)
        .collect(Collectors.toList());
  }

  /**
   * Get prediction by type
   */
  @Transactional(readOnly = true)
  public PredictionDto getLatestPredictionByType(UUID projectId,
      Prediction.PredictionType type) {
    return predictionRepository
        .findByProjectIdAndPredictionTypeOrderByCalculatedAtDesc(projectId, type)
        .stream()
        .findFirst()
        .map(PredictionDto::from)
        .orElseThrow(() -> new ResourceNotFoundException("Prediction", projectId.toString()));
  }

  // Helper methods

  private Double getSchedulePerformanceIndex(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
          "SELECT CAST(COALESCE(pv / ev, 1.0) AS DOUBLE PRECISION) FROM evm.earned_value_summary "
              + "WHERE project_id = ? ORDER BY calculated_at DESC LIMIT 1"
      )
          .setParameter(1, projectId)
          .getSingleResult();
      return result != null ? ((Number) result).doubleValue() : 1.0;
    } catch (Exception e) {
      log.warn("Could not calculate SPI for projectId={}", projectId);
      return 1.0;
    }
  }

  private Double getCostPerformanceIndex(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
          "SELECT CAST(COALESCE(ev / ac, 1.0) AS DOUBLE PRECISION) FROM evm.earned_value_summary "
              + "WHERE project_id = ? ORDER BY calculated_at DESC LIMIT 1"
      )
          .setParameter(1, projectId)
          .getSingleResult();
      return result != null ? ((Number) result).doubleValue() : 1.0;
    } catch (Exception e) {
      log.warn("Could not calculate CPI for projectId={}", projectId);
      return 1.0;
    }
  }

  private Map<String, Object> getCriticalActivityMetrics(UUID projectId) {
    try {
      @SuppressWarnings("unchecked")
      List<Object[]> results = em.createNativeQuery(
          "SELECT COUNT(*), CAST(COALESCE(AVG(total_float), 0) AS DOUBLE PRECISION) "
              + "FROM scheduling.activities WHERE project_id = ? AND is_critical = true"
      )
          .setParameter(1, projectId)
          .getResultList();

      Map<String, Object> metrics = new HashMap<>();
      if (!results.isEmpty()) {
        Object[] row = results.get(0);
        metrics.put("count", ((Number) row[0]).intValue());
        metrics.put("avgFloat", ((Number) row[1]).doubleValue());
      }
      return metrics;
    } catch (Exception e) {
      log.warn("Could not get critical activity metrics for projectId={}", projectId);
      return Map.of("count", 0, "avgFloat", 0.0);
    }
  }

  private Integer countDelayedActivities(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
          "SELECT COUNT(*) FROM scheduling.activities "
              + "WHERE project_id = ? AND status = 'NOT_STARTED' "
              + "AND planned_start < CURRENT_DATE"
      )
          .setParameter(1, projectId)
          .getSingleResult();
      return result != null ? ((Number) result).intValue() : 0;
    } catch (Exception e) {
      log.warn("Could not count delayed activities for projectId={}", projectId);
      return 0;
    }
  }

  private Double getContractValue(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
          "SELECT CAST(COALESCE(SUM(contract_value), 0) AS DOUBLE PRECISION) "
              + "FROM contracts.contracts WHERE project_id = ?"
      )
          .setParameter(1, projectId)
          .getSingleResult();
      return result != null ? ((Number) result).doubleValue() : 0.0;
    } catch (Exception e) {
      log.warn("Could not get contract value for projectId={}", projectId);
      return 0.0;
    }
  }

  private Double getVariationOrderValue(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
          "SELECT CAST(COALESCE(SUM(amount), 0) AS DOUBLE PRECISION) "
              + "FROM contracts.variation_orders WHERE project_id = ?"
      )
          .setParameter(1, projectId)
          .getSingleResult();
      return result != null ? ((Number) result).doubleValue() : 0.0;
    } catch (Exception e) {
      log.warn("Could not get variation order value for projectId={}", projectId);
      return 0.0;
    }
  }

  private Map<String, Double> getProjectDateInfo(UUID projectId) {
    try {
      @SuppressWarnings("unchecked")
      List<Object[]> results = em.createNativeQuery(
          "SELECT start_date, end_date, baseline_end_date FROM project.projects WHERE id = ?"
      )
          .setParameter(1, projectId)
          .getResultList();

      Map<String, Double> info = new HashMap<>();
      if (!results.isEmpty()) {
        Object[] row = results.get(0);
        java.time.LocalDate startDate = (java.time.LocalDate) row[0];
        java.time.LocalDate endDate = (java.time.LocalDate) row[1];
        java.time.LocalDate baselineEndDate = (java.time.LocalDate) row[2];

        java.time.LocalDate today = java.time.LocalDate.now();
        long daysElapsed = java.time.temporal.ChronoUnit.DAYS.between(startDate, today);
        long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(today, endDate);
        long baselineEndDays = java.time.temporal.ChronoUnit.DAYS.between(startDate,
            baselineEndDate);

        info.put("daysElapsed", (double) Math.max(0, daysElapsed));
        info.put("daysRemaining", (double) Math.max(0, daysRemaining));
        info.put("baselineEndDays", (double) baselineEndDays);
      }
      return info;
    } catch (Exception e) {
      log.warn("Could not get project date info for projectId={}", projectId);
      return Map.of("daysElapsed", 0.0, "daysRemaining", 0.0, "baselineEndDays", 0.0);
    }
  }

  private double calculateSlipRiskScore(Double spi, Integer criticalCount, Double avgFloat,
      Integer delayedCount) {
    double score = 0.0;
    if (spi < 0.9) {
      score += 35.0;
    }
    if (criticalCount > 5) {
      score += 25.0;
    }
    if (avgFloat < 3) {
      score += 25.0;
    }
    if (delayedCount > 5) {
      score += 15.0;
    }
    return Math.min(score, 100.0);
  }

  private double calculateOverrunRiskScore(Double cpi, Double voPercentage) {
    double score = 0.0;
    if (cpi < 0.9) {
      score += 40.0;
    }
    if (voPercentage > 8.0) {
      score += 30.0;
    }
    if (voPercentage > 15.0) {
      score += 30.0;
    }
    return Math.min(score, 100.0);
  }

  private double calculatePredictedSlip(Double spi) {
    if (spi >= 1.0) {
      return 0.0;
    }
    // Estimated: slip ratio increases exponentially as SPI drops
    return (1.0 / spi - 1.0) * 10.0;
  }

  private double calculatePredictedOverrun(Double cpi, Double contractValue) {
    if (cpi >= 1.0 || contractValue == null || contractValue == 0) {
      return 0.0;
    }
    return contractValue * (1.0 / cpi - 1.0);
  }
}
