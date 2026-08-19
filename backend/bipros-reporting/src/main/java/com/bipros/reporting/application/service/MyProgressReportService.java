package com.bipros.reporting.application.service;

import com.bipros.reporting.application.dto.MyProgressRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * "My Progress" (client ask, 2026-08-20): per-caller rollup of the activities they
 * supervise — quantity executed today / this week / this month / cumulative from
 * APPROVED DPRs, plus the activity's percent complete.
 *
 * <p>Scope is assignment-based: the activity set is the same union the issue
 * Gate-3 filter uses ({@code activity.activity_supervisors} plus the legacy
 * {@code activities.supervisor_user_id} column). Quantities sum ALL supervisors'
 * approved DPRs on those activities — multi-supervisor activities report their
 * physical progress, not the caller's authorship share.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class MyProgressReportService {

    private final com.bipros.common.security.ScopeResolverPort scopeResolver;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    public List<MyProgressRow> myProgress(UUID projectId) {
        UUID uid = scopeResolver.resolveForProject(projectId).userId();
        if (uid == null) return List.of();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                WITH my_acts AS (
                    SELECT activity_id AS id FROM activity.activity_supervisors WHERE user_id = :uid
                    UNION
                    SELECT id FROM activity.activities WHERE supervisor_user_id = :uid
                )
                SELECT a.id,
                       a.name,
                       COALESCE(MAX(d.boq_item_no), '')                       AS boq_item_no,
                       COALESCE(MAX(d.unit), '')                              AS unit,
                       COALESCE(SUM(d.qty_executed)
                           FILTER (WHERE d.report_date = CURRENT_DATE), 0)    AS today_qty,
                       COALESCE(SUM(d.qty_executed)
                           FILTER (WHERE d.report_date >=
                               date_trunc('week', CURRENT_DATE)::date), 0)    AS week_qty,
                       COALESCE(SUM(d.qty_executed)
                           FILTER (WHERE d.report_date >=
                               date_trunc('month', CURRENT_DATE)::date), 0)   AS month_qty,
                       COALESCE(SUM(d.qty_executed), 0)                       AS cumulative_qty,
                       a.percent_complete
                FROM activity.activities a
                JOIN my_acts m ON m.id = a.id
                LEFT JOIN project.daily_progress_reports d
                    ON d.activity_id = a.id
                   AND d.project_id = :pid
                   AND d.approval_status = 'APPROVED'
                WHERE a.project_id = :pid
                GROUP BY a.id, a.name, a.percent_complete
                ORDER BY cumulative_qty DESC, a.name
                """)
            .setParameter("uid", uid)
            .setParameter("pid", projectId)
            .getResultList();

        List<MyProgressRow> result = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            result.add(new MyProgressRow(
                r[0] instanceof UUID u ? u : UUID.fromString(r[0].toString()),
                (String) r[1],
                (String) r[2],
                (String) r[3],
                toDecimal(r[4]),
                toDecimal(r[5]),
                toDecimal(r[6]),
                toDecimal(r[7]),
                r[8] == null ? null : ((Number) r[8]).doubleValue()));
        }
        return result;
    }

    private static BigDecimal toDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        return new BigDecimal(o.toString());
    }
}
