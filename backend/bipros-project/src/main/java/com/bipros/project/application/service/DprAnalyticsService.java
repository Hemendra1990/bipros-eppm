package com.bipros.project.application.service;

import com.bipros.project.application.dto.DprAnalyticsResponse;
import com.bipros.project.application.dto.DprAnalyticsResponse.DayCount;
import com.bipros.project.application.dto.DprAnalyticsResponse.SupervisorCount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read-only DPR-performance aggregates for the DPR tab. Native SQL, window = report_date
 * between from/to. Statuses: a NULL approval_status is a legacy DRAFT (same convention as
 * the DPR list filters).
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class DprAnalyticsService {

    @PersistenceContext private EntityManager em;

    @SuppressWarnings("unchecked")
    public DprAnalyticsResponse analytics(UUID projectId, LocalDate from, LocalDate to) {
        long draft = 0, submitted = 0, approved = 0, rejected = 0;
        List<Object[]> funnel = em.createNativeQuery(
                "SELECT COALESCE(d.approval_status, 'DRAFT'), COUNT(*) "
                    + "FROM project.daily_progress_reports d "
                    + "WHERE d.project_id = :pid AND d.report_date BETWEEN :from AND :to "
                    + "GROUP BY 1")
                .setParameter("pid", projectId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
        for (Object[] r : funnel) {
            long n = toLong(r[1]);
            switch ((String) r[0]) {
                case "SUBMITTED" -> submitted = n;
                case "APPROVED" -> approved = n;
                case "REJECTED" -> rejected = n;
                default -> draft += n; // DRAFT + any legacy value
            }
        }
        long total = draft + submitted + approved + rejected;

        List<Object[]> perDayRows = em.createNativeQuery(
                "SELECT d.report_date, COUNT(*) "
                    + "FROM project.daily_progress_reports d "
                    + "WHERE d.project_id = :pid AND d.report_date BETWEEN :from AND :to "
                    + "  AND d.approval_status IN ('SUBMITTED', 'APPROVED', 'REJECTED') "
                    + "GROUP BY d.report_date ORDER BY d.report_date")
                .setParameter("pid", projectId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
        List<DayCount> perDay = new ArrayList<>(perDayRows.size());
        for (Object[] r : perDayRows) {
            perDay.add(new DayCount(toDate(r[0]), toLong(r[1])));
        }

        Object avgRaw = em.createNativeQuery(
                "SELECT AVG(EXTRACT(EPOCH FROM (d.approved_at - d.submitted_at)) / 3600.0) "
                    + "FROM project.daily_progress_reports d "
                    + "WHERE d.project_id = :pid AND d.report_date BETWEEN :from AND :to "
                    + "  AND d.approved_at IS NOT NULL AND d.submitted_at IS NOT NULL")
                .setParameter("pid", projectId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        Double avgApprovalHours = avgRaw == null ? null : toDouble(avgRaw);

        long decided = approved + rejected;
        Double rejectionRatePct = decided == 0 ? null : rejected * 100.0 / decided;

        List<Object[]> supRows = em.createNativeQuery(
                "SELECT COALESCE(NULLIF(btrim(d.supervisor_name), ''), d.supervisor_user_id::text, '—'), "
                    + "       COUNT(*), "
                    + "       COUNT(*) FILTER (WHERE d.approval_status = 'APPROVED') "
                    + "FROM project.daily_progress_reports d "
                    + "WHERE d.project_id = :pid AND d.report_date BETWEEN :from AND :to "
                    + "  AND d.approval_status IN ('SUBMITTED', 'APPROVED', 'REJECTED') "
                    + "GROUP BY 1 ORDER BY 2 DESC")
                .setParameter("pid", projectId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
        List<SupervisorCount> supervisors = new ArrayList<>(supRows.size());
        for (Object[] r : supRows) {
            supervisors.add(new SupervisorCount((String) r[0], toLong(r[1]), toLong(r[2])));
        }

        Object expectedRaw = em.createNativeQuery(
                "SELECT COUNT(DISTINCT COALESCE(a.supervisor_user_id::text, lower(btrim(a.supervisor_user_name)))) "
                    + "FROM activity.activities a "
                    + "WHERE a.project_id = :pid AND a.status = 'IN_PROGRESS' "
                    + "  AND (a.supervisor_user_id IS NOT NULL "
                    + "       OR (a.supervisor_user_name IS NOT NULL AND btrim(a.supervisor_user_name) <> ''))")
                .setParameter("pid", projectId)
                .getSingleResult();
        long expectedSupervisors = toLong(expectedRaw);

        return new DprAnalyticsResponse(total, draft, submitted, approved, rejected,
                avgApprovalHours, rejectionRatePct, perDay, supervisors, expectedSupervisors);
    }

    private static long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static double toDouble(Object o) {
        return o instanceof BigDecimal b ? b.doubleValue() : ((Number) o).doubleValue();
    }

    private static LocalDate toDate(Object o) {
        return o instanceof LocalDate ld ? ld : ((Date) o).toLocalDate();
    }
}
