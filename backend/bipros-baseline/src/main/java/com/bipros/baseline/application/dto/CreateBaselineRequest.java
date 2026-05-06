package com.bipros.baseline.application.dto;

import com.bipros.baseline.domain.BaselineType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateBaselineRequest(
    @NotBlank(message = "Name is required") String name,
    @NotNull(message = "Baseline type is required") BaselineType baselineType,
    String description,
    /**
     * Phase 4.3: P6's "convert another existing project as baseline" option. When non-null, the
     * snapshot is taken from this source project's activities/relationships instead of the target
     * project's. The resulting baseline is still attached to the target project. Variance/comparison
     * matches by activity ID; useful when the source is a planning copy of the target.
     * When null (default), the baseline snapshots the target project itself.
     */
    UUID sourceProjectId) {

  /** Convenience for callers that don't need the source-project option. */
  public CreateBaselineRequest(String name, BaselineType baselineType, String description) {
    this(name, baselineType, description, null);
  }
}
