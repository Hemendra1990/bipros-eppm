package com.bipros.evm.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Aggregated EVM metrics per cost account for a given project.
 *
 * <p>Null {@code costAccountId} and the sentinel name "Unassigned" represent activities
 * whose resolved cost account is null (no activity-level or WBS-level assignment).
 * The "Unassigned" bucket is always sorted last by the service layer.
 *
 * <p>CPI/SPI are null when the divisor (AC or PV) is zero to avoid division-by-zero.
 * PV/SV/SPI are null for a bucket if any contributing activity has a null PV (the bucket
 * is honest about incomplete planned-value data).
 */
public record CostAccountRollupResponse(
        UUID costAccountId,
        String costAccountCode,
        String costAccountName,
        BigDecimal bac,
        BigDecimal pv,
        BigDecimal ev,
        BigDecimal ac,
        BigDecimal cv,
        BigDecimal sv,
        BigDecimal cpi,
        BigDecimal spi,
        int activityCount
) {}
