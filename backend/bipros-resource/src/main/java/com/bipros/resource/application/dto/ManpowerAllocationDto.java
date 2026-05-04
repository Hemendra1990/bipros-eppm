package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.enums.AvailabilityStatus;
import com.bipros.resource.domain.model.manpower.ManpowerAllocation;

import java.math.BigDecimal;
import java.util.UUID;

public record ManpowerAllocationDto(
    AvailabilityStatus availabilityStatus,
    UUID currentProjectId,
    UUID assignedActivityId,
    String siteName,
    String roleInProject,
    UUID crewId,
    BigDecimal utilizationPercentage,
    BigDecimal standardOutputPerHour,
    String outputUnit,
    BigDecimal efficiencyFactor,
    BigDecimal performanceRating,
    String productivityTrend,
    BigDecimal attritionRiskScore,
    String skillGapAnalysis,
    String recommendedTraining,
    String optimalAssignment
) {

  public static ManpowerAllocationDto from(ManpowerAllocation a) {
    if (a == null) return null;
    return new ManpowerAllocationDto(
        a.getAvailabilityStatus(),
        a.getCurrentProjectId(),
        a.getAssignedActivityId(),
        a.getSiteName(),
        a.getRoleInProject(),
        a.getCrewId(),
        a.getUtilizationPercentage(),
        a.getStandardOutputPerHour(),
        a.getOutputUnit(),
        a.getEfficiencyFactor(),
        a.getPerformanceRating(),
        a.getProductivityTrend(),
        a.getAttritionRiskScore(),
        a.getSkillGapAnalysis(),
        a.getRecommendedTraining(),
        a.getOptimalAssignment());
  }
}
