package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.LabourCategory;
import com.bipros.resource.domain.model.LabourGrade;
import com.bipros.resource.domain.model.LabourStatus;
import com.bipros.resource.domain.model.NationalityType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LabourDesignationResponse(
    UUID id,
    String code,
    String designation,
    LabourCategory category,
    String categoryDisplay,
    String codePrefix,
    String trade,
    LabourGrade grade,
    NationalityType nationality,
    Integer experienceYearsMin,
    BigDecimal defaultDailyRate,
    String currency,
    List<String> skills,
    List<String> certifications,
    String keyRoleSummary,
    LabourStatus status,
    Integer sortOrder,
    DeploymentBlock deployment
) {
    public record DeploymentBlock(
        UUID id,
        Integer workerCount,
        BigDecimal actualDailyRate,
        BigDecimal effectiveRate,
        BigDecimal dailyCost,
        String notes
    ) {}
}
