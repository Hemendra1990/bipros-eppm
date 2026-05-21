package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.master.SubContractorMaster;

import java.time.Instant;
import java.util.UUID;

public record SubContractorMasterResponse(
    UUID id,
    String code,
    String name,
    String location,
    String primaryContactName,
    String primaryContactNumber,
    Boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static SubContractorMasterResponse from(SubContractorMaster m) {
    return new SubContractorMasterResponse(
        m.getId(),
        m.getCode(),
        m.getName(),
        m.getLocation(),
        m.getPrimaryContactName(),
        m.getPrimaryContactNumber(),
        m.getActive(),
        m.getCreatedAt(),
        m.getUpdatedAt());
  }
}
