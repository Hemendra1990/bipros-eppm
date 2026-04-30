package com.bipros.analytics.read;

import com.bipros.analytics.store.ClickHouseTemplate;
import com.bipros.common.dto.ApiResponse;
import com.bipros.common.security.ProjectAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsReadController {

    private final ClickHouseTemplate clickHouse;
    private final ProjectAccessGuard projectAccess;

    @GetMapping("/kpi/{projectId}")
    @PreAuthorize("@projectAccess.canRead(#projectId)")
    public ResponseEntity<ApiResponse<ProjectKpiResponse>> getProjectKpi(
            @PathVariable UUID projectId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {

        if (from == null) from = LocalDate.now().minusMonths(1);
        if (to == null) to = LocalDate.now();

        String sql = """
            SELECT sum(total_actual) as ac,
                   sum(total_planned) as pv,
                   sum(total_earned) as ev,
                   count() as row_count
            FROM bipros_analytics.fact_cost_daily
            WHERE project_id = :projectId
              AND date BETWEEN :from AND :to
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("from", from);
        params.put("to", to);

        List<Map<String, Object>> rows = clickHouse.queryForList(sql, params);
        Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);

        ProjectKpiResponse kpi = new ProjectKpiResponse(
                projectId, from, to,
                toBigDecimal(row.get("ac")),
                toBigDecimal(row.get("pv")),
                toBigDecimal(row.get("ev")),
                row.get("row_count") != null ? ((Number) row.get("row_count")).longValue() : 0L
        );

        return ResponseEntity.ok(ApiResponse.ok(kpi));
    }

    @GetMapping("/evm/{projectId}")
    @PreAuthorize("@projectAccess.canRead(#projectId)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getEvmSeries(
            @PathVariable UUID projectId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {

        if (from == null) from = LocalDate.now().minusMonths(3);
        if (to == null) to = LocalDate.now();

        String sql = """
            SELECT date, sum(pv) as pv, sum(ev) as ev, sum(ac) as ac,
                   sum(cv) as cv, sum(sv) as sv,
                   avg(cpi) as cpi, avg(spi) as spi
            FROM bipros_analytics.fact_evm_daily
            WHERE project_id = :projectId
              AND date BETWEEN :from AND :to
            GROUP BY date
            ORDER BY date
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("from", from);
        params.put("to", to);

        return ResponseEntity.ok(ApiResponse.ok(clickHouse.queryForList(sql, params)));
    }

    @GetMapping("/scurve/{projectId}")
    @PreAuthorize("@projectAccess.canRead(#projectId)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getScurve(
            @PathVariable UUID projectId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {

        if (from == null) from = LocalDate.now().minusMonths(6);
        if (to == null) to = LocalDate.now();

        String sql = """
            SELECT date, sum(pv) as pv, sum(ev) as ev, sum(ac) as ac
            FROM bipros_analytics.fact_evm_daily
            WHERE project_id = :projectId
              AND date BETWEEN :from AND :to
            GROUP BY date
            ORDER BY date
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("from", from);
        params.put("to", to);

        return ResponseEntity.ok(ApiResponse.ok(clickHouse.queryForList(sql, params)));
    }

    @GetMapping("/risk-heatmap/{projectId}")
    @PreAuthorize("@projectAccess.canRead(#projectId)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRiskHeatmap(
            @PathVariable UUID projectId) {

        String sql = """
            SELECT risk_id, date, probability, impact_cost, impact_days, rag, status,
                   monte_carlo_p50, monte_carlo_p80, monte_carlo_p95
            FROM bipros_analytics.fact_risk_snapshot_daily
            WHERE project_id = :projectId
              AND date = (SELECT max(date) FROM bipros_analytics.fact_risk_snapshot_daily WHERE project_id = :projectId)
            ORDER BY impact_cost DESC
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);

        return ResponseEntity.ok(ApiResponse.ok(clickHouse.queryForList(sql, params)));
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(val.toString());
    }

    public record ProjectKpiResponse(UUID projectId, LocalDate from, LocalDate to,
                                     BigDecimal totalActual, BigDecimal totalPlanned,
                                     BigDecimal totalEarned, Long rowCount) {
    }
}
