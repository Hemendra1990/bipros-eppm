package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ResourceCurve;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record ResourceCurveResponse(
    UUID id,
    String name,
    String description,
    Boolean isDefault,
    List<Double> curveData,
    Instant createdAt,
    Instant updatedAt,
    String createdBy,
    String updatedBy) {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static ResourceCurveResponse from(ResourceCurve curve) throws JsonProcessingException {
    List<Double> data = Arrays.asList(MAPPER.readValue(curve.getCurveData(), Double[].class));
    return new ResourceCurveResponse(
        curve.getId(),
        curve.getName(),
        curve.getDescription(),
        curve.getIsDefault(),
        data,
        curve.getCreatedAt(),
        curve.getUpdatedAt(),
        curve.getCreatedBy(),
        curve.getUpdatedBy());
  }
}
