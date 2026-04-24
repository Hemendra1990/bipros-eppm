package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.LabTestStatus;
import com.bipros.resource.domain.model.MaterialSourceType;
import com.bipros.resource.domain.model.ResourceUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Payload for POST /v1/projects/{projectId}/material-sources (Screen 08). {@code sourceCode}
 * is optional — the service auto-generates {@code BA-NNN} / {@code QRY-NNN} / {@code BD-NNN} /
 * {@code CEM-NNN} based on {@link #sourceType}.
 */
public record CreateMaterialSourceRequest(
    @Size(max = 30) String sourceCode,
    @Size(max = 200) String name,
    @NotNull MaterialSourceType sourceType,
    @Size(max = 150) String village,
    @Size(max = 100) String taluk,
    @Size(max = 100) String district,
    @Size(max = 80) String state,
    BigDecimal distanceKm,
    BigDecimal approvedQuantity,
    ResourceUnit approvedQuantityUnit,
    @Size(max = 200) String approvalReference,
    @Size(max = 200) String approvalAuthority,
    BigDecimal cbrAveragePercent,
    BigDecimal mddGcc,
    /** When omitted the service derives this from the supplied lab tests. */
    LabTestStatus labTestStatus,
    /** Optional lab-test rows to create alongside the source. */
    @Valid List<LabTestInput> labTests
) {

    public record LabTestInput(
        @Size(max = 120) String testName,
        @Size(max = 120) String standardReference,
        BigDecimal resultValue,
        @Size(max = 20) String resultUnit,
        Boolean passed,
        LocalDate testDate,
        @Size(max = 500) String remarks
    ) {}
}
