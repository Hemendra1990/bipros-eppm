package com.bipros.permit.application.dto;

import com.bipros.permit.domain.model.PermitStatus;

import java.util.List;
import java.util.Map;

public record DashboardSummaryResponse(
        long activePermits,
        long pendingReview,
        long expiringToday,
        long closedThisMonth,
        Map<PermitStatus, Long> statusBreakdown,
        List<PermitTypeCount> activeByType,
        List<PermitSummary> recentActivity
) {
    public record PermitTypeCount(String code, String name, String colorHex, long count) {}
}
