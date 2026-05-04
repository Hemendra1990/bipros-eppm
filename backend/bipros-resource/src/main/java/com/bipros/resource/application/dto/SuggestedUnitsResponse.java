package com.bipros.resource.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Suggestion for {@code plannedUnits} on a resource assignment, derived from the
 * applicable {@link com.bipros.resource.domain.model.ProductivityNorm} for the
 * given activity + resource. Returned as advisory only — the user is free to override
 * the value when creating the assignment.
 *
 * <p>{@code suggestedPlannedUnits} is null when no norm could be resolved or when the
 * resolved norm has no daily output (so a suggestion would be undefined).
 */
public record SuggestedUnitsResponse(
    UUID activityId,
    UUID resourceId,
    UUID workActivityId,
    BigDecimal quantity,
    String quantityUnit,
    BigDecimal outputPerDay,
    BigDecimal suggestedPlannedUnits,
    String basis,
    String source
) {
}
