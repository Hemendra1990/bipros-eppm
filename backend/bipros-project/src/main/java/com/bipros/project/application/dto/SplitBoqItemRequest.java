package com.bipros.project.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Split a BOQ line into operations (split design §4/§7, D3-D5), or — on the reweight endpoint —
 * update the existing operation set's definitions.
 *
 * @param splitMode           WEIGHTED_OPERATIONS or QUANTITY_PARTITION (D4); ignored on reweight
 * @param legacyWeight        weight of the auto-created LEGACY operation absorbing pre-split
 *                            history (§7.3); required in WEIGHTED mode when the line has executed
 *                            qty, ignored otherwise
 * @param operations          the operation definitions (opCode is the client-side key)
 * @param activityAssignments activityId → opCode for every activity linked to the line (L1 —
 *                            split cannot save while any linked activity is unassigned)
 * @param reason              reweight only: mandatory once weights are frozen (D5)
 */
public record SplitBoqItemRequest(
    String splitMode,
    BigDecimal legacyWeight,
    List<BoqOperationDto> operations,
    Map<UUID, String> activityAssignments,
    String reason
) { }
