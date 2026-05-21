package com.bipros.project.application.dto;

import com.bipros.project.domain.model.DprSubContractor;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DprSubContractorRow(
    UUID id,

    @NotNull(message = "subContractorMasterId is required")
    UUID subContractorMasterId,

    String subContractorName,
    String subContractorCode,

    @Size(max = 500, message = "remarks must be at most 500 characters")
    String remarks
) {
  public static DprSubContractorRow from(DprSubContractor e) {
    return new DprSubContractorRow(
        e.getId(),
        e.getSubContractorMasterId(),
        e.getSubContractorName(),
        e.getSubContractorCode(),
        e.getRemarks());
  }

  public DprSubContractor toEntity(UUID dprId) {
    return DprSubContractor.builder()
        .dprId(dprId)
        .subContractorMasterId(subContractorMasterId)
        .subContractorName(subContractorName)
        .subContractorCode(subContractorCode)
        .remarks(remarks)
        .build();
  }
}
