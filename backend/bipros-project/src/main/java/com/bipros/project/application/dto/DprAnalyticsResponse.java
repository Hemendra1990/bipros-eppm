package com.bipros.project.application.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * DPR tab analytics strip. Funnel counts cover every DPR in the window (a NULL
 * approval_status row counts as DRAFT). Rates: {@code rejectionRatePct} is the share of
 * DECIDED DPRs (approved + rejected) that were rejected; {@code avgApprovalHours} averages
 * approved_at − submitted_at over the window's approved DPRs.
 */
public record DprAnalyticsResponse(
        long total,
        long draft,
        long submitted,
        long approved,
        long rejected,
        Double avgApprovalHours,
        Double rejectionRatePct,
        List<DayCount> perDay,
        List<SupervisorCount> supervisors,
        long expectedSupervisors
) {
    /** DPRs submitted (SUBMITTED/APPROVED/REJECTED) per report date. */
    public record DayCount(LocalDate date, long count) {}

    /** Per-supervisor submission tally in the window (id-when-present-else-name identity). */
    public record SupervisorCount(String name, long filed, long approved) {}
}
