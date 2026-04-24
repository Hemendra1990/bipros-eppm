package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.LabTestStatus;
import com.bipros.resource.domain.model.MaterialSource;
import com.bipros.resource.domain.model.MaterialSourceLabTest;
import com.bipros.resource.domain.model.MaterialSourceType;
import com.bipros.resource.domain.model.ResourceUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MaterialSourceResponse(
    UUID id,
    UUID projectId,
    String sourceCode,
    String name,
    MaterialSourceType sourceType,
    String village,
    String taluk,
    String district,
    String state,
    BigDecimal distanceKm,
    BigDecimal approvedQuantity,
    ResourceUnit approvedQuantityUnit,
    String approvalReference,
    String approvalAuthority,
    BigDecimal cbrAveragePercent,
    BigDecimal mddGcc,
    LabTestStatus labTestStatus,
    List<LabTestRow> labTests
) {

    public record LabTestRow(
        UUID id,
        String testName,
        String standardReference,
        BigDecimal resultValue,
        String resultUnit,
        Boolean passed,
        LocalDate testDate,
        String remarks
    ) {
        public static LabTestRow from(MaterialSourceLabTest t) {
            return new LabTestRow(
                t.getId(),
                t.getTestName(),
                t.getStandardReference(),
                t.getResultValue(),
                t.getResultUnit(),
                t.getPassed(),
                t.getTestDate(),
                t.getRemarks()
            );
        }
    }

    public static MaterialSourceResponse from(MaterialSource s, List<MaterialSourceLabTest> tests) {
        return new MaterialSourceResponse(
            s.getId(), s.getProjectId(), s.getSourceCode(), s.getName(),
            s.getSourceType(),
            s.getVillage(), s.getTaluk(), s.getDistrict(), s.getState(),
            s.getDistanceKm(), s.getApprovedQuantity(), s.getApprovedQuantityUnit(),
            s.getApprovalReference(), s.getApprovalAuthority(),
            s.getCbrAveragePercent(), s.getMddGcc(),
            s.getLabTestStatus(),
            tests != null ? tests.stream().map(LabTestRow::from).toList() : List.of()
        );
    }
}
