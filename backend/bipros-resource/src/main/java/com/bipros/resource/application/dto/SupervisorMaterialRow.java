package com.bipros.resource.application.dto;

import java.math.BigDecimal;

/**
 * One (supervisor × material) line of the supervisor-wise issued material comparison.
 *
 * <p>Issued comes from store issue slips ({@code material_issue.issued_to_user_id}); reported
 * comes from that supervisor's APPROVED DPR material lines. Both cumulative to the as-of date —
 * a strict monthly window would false-flag material issued late one month and consumed early the
 * next. {@code varianceQty} = issued − reported (positive = issued but not accounted for).
 * {@code varianceValue} = varianceQty × rate (avg DPR unit rate for the material, else latest GRN
 * rate) — informational flag only; DBS costing is deferred until the client answers open
 * question Q20 (tolerance rule + authority).
 */
public record SupervisorMaterialRow(
    String supervisorKey,
    String supervisorName,
    String materialName,
    String unit,
    BigDecimal issuedToDate,
    BigDecimal reportedToDate,
    BigDecimal varianceQty,
    BigDecimal varianceValue,
    BigDecimal wastageQty,
    BigDecimal issuedWindow,
    BigDecimal reportedWindow
) {}
