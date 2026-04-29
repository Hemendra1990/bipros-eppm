package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.LabourCategory;
import com.bipros.resource.domain.model.LabourGrade;
import com.bipros.resource.domain.model.LabourStatus;
import com.bipros.resource.domain.model.NationalityType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record LabourDesignationRequest(
    @NotBlank @Pattern(regexp = "^(SM|PO|SL|SS|GL)-\\d{3}$",
                       message = "code must match (SM|PO|SL|SS|GL)-NNN")
    String code,

    @NotBlank @Size(max = 100) String designation,
    @NotNull LabourCategory category,
    @NotBlank @Size(max = 80) String trade,
    @NotNull LabourGrade grade,
    @NotNull NationalityType nationality,
    @NotNull @Min(0) Integer experienceYearsMin,
    @NotNull @PositiveOrZero BigDecimal defaultDailyRate,
    @Size(max = 3) String currency,
    List<String> skills,
    List<String> certifications,
    @Size(max = 500) String keyRoleSummary,
    LabourStatus status,
    Integer sortOrder
) {}
