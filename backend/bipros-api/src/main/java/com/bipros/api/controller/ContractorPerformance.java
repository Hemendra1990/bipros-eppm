package com.bipros.api.controller;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Per-EPC-contractor score card surfaced on the Programme Dashboard.
 *
 * <p>Fields derived from live data:
 * <ul>
 *   <li>{@code performanceScore} — RA-bill satellite-gate PASS % (0-100).
 *       {@code null} when no RA bills exist for the contractor.</li>
 *   <li>{@code safetyScore} — always {@code null}. Safety / LTI data is
 *       not ingested yet; frontend renders "n/a".</li>
 *   <li>{@code complianceScore} — % of active EPC contracts whose bank-guarantee
 *       expiry date is still in the future (BG validity %). {@code null} when
 *       no BG expiry dates are recorded for the contractor's contracts.</li>
 * </ul>
 */
public record ContractorPerformance(
    UUID orgId,
    String orgCode,
    String orgName,
    Double performanceScore,
    Double safetyScore,
    Double complianceScore,
    Integer activeContracts,
    BigDecimal totalContractValueCr
) {}
