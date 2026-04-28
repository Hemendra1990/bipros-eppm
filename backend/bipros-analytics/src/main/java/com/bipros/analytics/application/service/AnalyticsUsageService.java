package com.bipros.analytics.application.service;

import com.bipros.analytics.application.dto.UsageDailyRow;
import com.bipros.analytics.application.dto.UsageSummaryResponse;
import com.bipros.analytics.application.dto.UsageSummaryResponse.UsageTotals;
import com.bipros.analytics.domain.repository.AnalyticsAuditLogRepository;
import com.bipros.security.application.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Per-user usage rollup for the Usage tab on /settings/llm-providers.
 *
 * <p>Reads from {@code analytics.analytics_audit_log} and aggregates by day +
 * provider for the calling user. The user can only see their own data — we
 * resolve the user ID from the security context, never accept it as a parameter.
 *
 * <p>Date range defaults to the last 30 days; capped at 90 days to keep
 * aggregations cheap.
 */
@Service
@ConditionalOnProperty(name = "bipros.analytics.assistant.enabled", havingValue = "true")
@RequiredArgsConstructor
public class AnalyticsUsageService {

    private static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int MAX_WINDOW_DAYS = 90;

    private final AnalyticsAuditLogRepository repo;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public UsageSummaryResponse summaryForCurrentUser(LocalDate fromDate, LocalDate toDate) {
        UUID userId = currentUserService.getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Authentication required to view usage.");
        }

        Instant to = toDate != null ? toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                                    : Instant.now();
        Instant from = fromDate != null ? fromDate.atStartOfDay(ZoneOffset.UTC).toInstant()
                                        : to.minus(DEFAULT_WINDOW_DAYS, ChronoUnit.DAYS);

        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must be on or before to");
        }
        long days = ChronoUnit.DAYS.between(from, to);
        if (days > MAX_WINDOW_DAYS) {
            from = to.minus(MAX_WINDOW_DAYS, ChronoUnit.DAYS);
        }

        List<Object[]> rows = repo.aggregateDailyByProvider(userId, from, to);
        List<UsageDailyRow> daily = new ArrayList<>(rows.size());
        long totalIn = 0, totalOut = 0, totalCost = 0, totalCount = 0;
        for (Object[] r : rows) {
            Instant day = ((Timestamp) r[0]).toInstant();
            String provider = (String) r[1];
            long tIn  = ((Number) r[2]).longValue();
            long tOut = ((Number) r[3]).longValue();
            long cost = ((Number) r[4]).longValue();
            long cnt  = ((Number) r[5]).longValue();
            daily.add(new UsageDailyRow(day, provider, tIn, tOut, cost, cnt));
            totalIn += tIn;
            totalOut += tOut;
            totalCost += cost;
            totalCount += cnt;
        }
        return new UsageSummaryResponse(from, to, daily,
                new UsageTotals(totalIn, totalOut, totalCost, totalCount));
    }
}
