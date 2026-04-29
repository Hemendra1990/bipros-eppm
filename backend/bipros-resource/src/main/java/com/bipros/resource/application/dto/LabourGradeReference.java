package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.LabourGrade;

public record LabourGradeReference(
    LabourGrade grade,
    String classification,
    String dailyRateRange,
    String description
) {}
