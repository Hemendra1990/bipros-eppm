package com.bipros.resource.application.dto;

import com.bipros.resource.domain.service.ResolvedNorm;

import java.math.BigDecimal;
import java.util.UUID;

public record ResolvedNormResponse(
    UUID workActivityId,
    UUID resourceId,
    BigDecimal outputPerDay,
    String unit,
    String source,
    UUID productivityNormId
) {
  public static ResolvedNormResponse from(ResolvedNorm r) {
    return new ResolvedNormResponse(
        r.workActivityId(),
        r.resourceId(),
        r.outputPerDay(),
        r.unit(),
        r.source().name(),
        r.productivityNormId());
  }
}
