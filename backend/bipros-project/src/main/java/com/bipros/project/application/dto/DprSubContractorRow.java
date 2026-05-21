package com.bipros.project.application.dto;

import com.bipros.project.domain.model.DprSubContractor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record DprSubContractorRow(
    UUID id,

    @NotNull(message = "subContractorMasterId is required")
    UUID subContractorMasterId,

    String subContractorName,
    String subContractorCode,

    @NotNull(message = "unitsExecuted is required")
    @PositiveOrZero(message = "unitsExecuted must be >= 0")
    BigDecimal unitsExecuted,

    @Size(max = 500, message = "remarks must be at most 500 characters")
    String remarks
) {
  public static DprSubContractorRow from(DprSubContractor e) {
    return new DprSubContractorRow(
        e.getId(),
        e.getSubContractorMasterId(),
        e.getSubContractorName(),
        e.getSubContractorCode(),
        e.getUnitsExecuted(),
        e.getRemarks());
  }

  public DprSubContractor toEntity(UUID dprId) {
    return DprSubContractor.builder()
        .dprId(dprId)
        .subContractorMasterId(subContractorMasterId)
        .subContractorName(subContractorName)
        .subContractorCode(subContractorCode)
        .unitsExecuted(unitsExecuted)
        .remarks(remarks)
        .build();
  }
}
