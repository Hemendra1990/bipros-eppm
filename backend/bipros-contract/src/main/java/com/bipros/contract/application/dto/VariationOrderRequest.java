package com.bipros.contract.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// lineItems carries the structured BOQ mutations for Phase 9. Null/empty preserves legacy
// header-only behaviour. On update, the supplied list replaces the existing line items
// wholesale (omitted lines are deleted). Line items are immutable once the parent VO is
// APPROVED — the service rejects edits at that point.
public record VariationOrderRequest(
    @NotNull UUID contractId,
    @NotBlank String voNumber,
    String description,
    BigDecimal voValue,
    String justification,
    BigDecimal impactOnBudget,
    Integer impactOnScheduleDays,
    String approvedBy,
    List<VoLineItemRequest> lineItems
) {}
