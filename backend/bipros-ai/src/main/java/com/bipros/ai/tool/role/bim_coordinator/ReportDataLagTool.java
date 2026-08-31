package com.bipros.ai.tool.role.bim_coordinator;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.ProjectScopedTool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * For each DPR submitted in the last 30 days, computes the entry lag — the number of
 * calendar days between the {@code reportDate} (the day the work occurred) and the
 * {@code createdAt} date (when the record was submitted into the system).
 *
 * <p>Results are bucketed into:
 * <ul>
 *   <li>{@code 0d}   — submitted on the same day</li>
 *   <li>{@code 1d}   — submitted one day after</li>
 *   <li>{@code 2d}   — submitted two days after</li>
 *   <li>{@code 3-7d} — submitted 3 to 7 days after</li>
 *   <li>{@code >7d}  — submitted more than 7 days after</li>
 * </ul>
 *
 * <p>Also returns p50/p90 lag percentiles and the worst 10 DPRs by lag descending,
 * so the BIM Data Coordinator can identify systematic entry delays.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportDataLagTool extends ProjectScopedTool {

    private static final int WINDOW_DAYS = 30;
    private static final int WORST_LIMIT = 10;

    private final DailyProgressReportRepository dprRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "report_data_lag";
    }

    @Override
    public String description() {
        return "For each DPR in the last 30 days, computes lag = createdAt::date - reportDate "
                + "in calendar days. Buckets lag into 0d / 1d / 2d / 3-7d / >7d and returns "
                + "counts per bucket, p50 and p90 percentiles, and the worst-10 DPRs by lag "
                + "descending (fields: dpr_id, report_date, created_at, lag_days). "
                + "Use to identify systematic entry delays that risk stale BIM submissions.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", objectMapper.createObjectNode());
        return schema;
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of("BIM_DATA_COORDINATOR", "PROJECT_MANAGER");
    }

    @Override
    protected ToolResult doExecute(JsonNode input, AiContext ctx) {
        if (ctx.projectId() == null) {
            return ToolResult.error("report_data_lag requires a project in scope.");
        }

        UUID projectId = ctx.projectId();
        LocalDate to   = LocalDate.now();
        LocalDate from = to.minusDays(WINDOW_DAYS);

        List<DailyProgressReport> dprs;
        try {
            dprs = dprRepository.findByProjectIdAndReportDateBetweenOrderByReportDateAscIdAsc(
                    projectId, from, to);
        } catch (Exception e) {
            log.warn("report_data_lag: repository query failed for project {}: {}",
                    projectId, e.getMessage());
            return dataUnavailable(
                    "DPR data is not accessible for this project.",
                    "Ensure the project schema is migrated and DPRs have been submitted.");
        }

        if (dprs.isEmpty()) {
            return dataUnavailable(
                    "Project has no DPRs in the last 30 days — entry-lag analysis requires recent submissions.",
                    "Submit at least one Daily Progress Report for the project.");
        }

        // Compute lag for each DPR
        record LaggedDpr(UUID id, LocalDate reportDate, String createdAtIso, long lagDays) {}

        List<LaggedDpr> lagged = new ArrayList<>(dprs.size());
        for (DailyProgressReport dpr : dprs) {
            LocalDate createdDate = dpr.getCreatedAt() == null
                    ? dpr.getReportDate()   // fallback: treat as same-day if audit not populated
                    : dpr.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();

            long lag = Math.max(0, createdDate.toEpochDay() - dpr.getReportDate().toEpochDay());
            String createdAtIso = dpr.getCreatedAt() != null
                    ? dpr.getCreatedAt().toString()
                    : createdDate.toString();

            lagged.add(new LaggedDpr(dpr.getId(), dpr.getReportDate(), createdAtIso, lag));
        }

        // Bucket counts
        long bucket0    = lagged.stream().filter(l -> l.lagDays() == 0).count();
        long bucket1    = lagged.stream().filter(l -> l.lagDays() == 1).count();
        long bucket2    = lagged.stream().filter(l -> l.lagDays() == 2).count();
        long bucket3to7 = lagged.stream().filter(l -> l.lagDays() >= 3 && l.lagDays() <= 7).count();
        long bucketOver7 = lagged.stream().filter(l -> l.lagDays() > 7).count();

        // Percentiles (p50, p90) over sorted lag values
        List<Long> sorted = lagged.stream()
                .map(LaggedDpr::lagDays)
                .sorted()
                .toList();

        long p50 = percentile(sorted, 50);
        long p90 = percentile(sorted, 90);

        // Worst-10 by lag descending
        List<LaggedDpr> worst = lagged.stream()
                .sorted(Comparator.comparingLong(LaggedDpr::lagDays).reversed())
                .limit(WORST_LIMIT)
                .toList();

        // Build payload
        ObjectNode payload = objectMapper.createObjectNode();

        ObjectNode buckets = objectMapper.createObjectNode();
        buckets.put("0d",    bucket0);
        buckets.put("1d",    bucket1);
        buckets.put("2d",    bucket2);
        buckets.put("3-7d",  bucket3to7);
        buckets.put(">7d",   bucketOver7);
        payload.set("buckets", buckets);

        payload.put("p50_lag_days", p50);
        payload.put("p90_lag_days", p90);
        payload.put("total_dprs",   lagged.size());

        ArrayNode worstArr = objectMapper.createArrayNode();
        for (LaggedDpr ld : worst) {
            ObjectNode row = objectMapper.createObjectNode();
            row.put("dpr_id",      ld.id().toString());
            row.put("report_date", ld.reportDate().toString());
            row.put("created_at",  ld.createdAtIso());
            row.put("lag_days",    ld.lagDays());
            worstArr.add(row);
        }
        payload.set("worst", worstArr);

        // Human summary
        String summary = String.format(
                "Entry-lag analysis for project %s (last 30 days): %d DPR(s). "
                        + "Buckets — 0d:%d, 1d:%d, 2d:%d, 3-7d:%d, >7d:%d. "
                        + "p50=%dd, p90=%dd.",
                projectId, lagged.size(),
                bucket0, bucket1, bucket2, bucket3to7, bucketOver7,
                p50, p90);

        return ToolResult.ok(summary, payload);
    }

    // -----------------------------------------------------------------------

    /**
     * Returns the value at the given percentile (0–100) from an already-sorted list.
     */
    private static long percentile(List<Long> sorted, int pct) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }

    private ToolResult dataUnavailable(String reason, String whatNeeded) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("status",               "data_unavailable");
        payload.put("reason",               reason);
        payload.put("what_would_be_needed", whatNeeded);
        return ToolResult.ok("Data not yet captured: " + reason, payload);
    }
}
