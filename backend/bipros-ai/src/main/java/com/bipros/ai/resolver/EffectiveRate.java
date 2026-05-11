package com.bipros.ai.resolver;

import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Effective rate resolved for a (project, resource) pair.
 *
 * <p>Two-tier chain: {@link Source#OVERRIDE} (ProjectResource.rateOverride) takes precedence
 * over {@link Source#RESOURCE} (Resource.costPerUnit, which is the rate-master snapshot).
 * When no rate is available the source is {@link Source#NONE} and {@code rate} is null.
 *
 * <p>Returned by every AI tool that surfaces cost / rate data so the LLM can disclose
 * whether a project-specific override applied and which unit basis governs the cost formula.
 */
public record EffectiveRate(
    @Nullable BigDecimal rate,
    Source source,
    @Nullable String unit,
    @Nullable String basis,
    boolean overrideApplied,
    @Nullable UUID projectResourceId,
    @Nullable UUID rateMasterId) {

  public enum Source {
    OVERRIDE,
    RESOURCE,
    NONE
  }
}
