package com.bipros.reporting.application.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Executed Commodity Summary (AI Agent sheet, DPR row: "Executed Commodity (cumulative basis
 * for the month / till date) summary to be generated against the respective BOQ level and
 * activity level"). Quantities only — approved DPRs, grouped three ways:
 *
 * <ul>
 *   <li>BOQ level — month executed (approved DPR sum for the calendar month) vs the STORED
 *       till-date / % complete columns (the BOQ tab's billing basis);</li>
 *   <li>Activity level — month + all-time executed per activity name;</li>
 *   <li>Per supervisor — month + all-time executed per supervisor (id-else-name identity),
 *       the "including supervisor performance" slice of the requirement.</li>
 * </ul>
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class CommoditySummaryReportService {

    public record BoqLine(String itemNo, String description, String unit,
                          BigDecimal contractQty, BigDecimal monthQty,
                          BigDecimal toDateQty, BigDecimal pctComplete) {}

    public record ActivityLine(String activityName, String unit,
                               BigDecimal monthQty, BigDecimal toDateQty) {}

    public record SupervisorLine(String supervisorName, BigDecimal monthQty,
                                 BigDecimal toDateQty, long dprsMonth, long dprsToDate) {}

    public record CommoditySummary(YearMonth month, List<BoqLine> boqLines,
                                   List<ActivityLine> activityLines,
                                   List<SupervisorLine> supervisorLines) {}

    @PersistenceContext private EntityManager em;

    @SuppressWarnings("unchecked")
    public CommoditySummary build(UUID projectId, YearMonth month) {
        LocalDate mFrom = month.atDay(1);
        LocalDate mTo = month.atEndOfMonth();

        List<Object[]> boqRows = em.createNativeQuery(
                "SELECT b.item_no, b.description, b.unit, b.boq_qty, b.qty_executed_to_date, "
                    + "       b.percent_complete, COALESCE(mq.qty, 0) "
                    + "FROM project.boq_items b "
                    + "LEFT JOIN (SELECT d.boq_item_no, SUM(d.qty_executed) AS qty "
                    + "           FROM project.daily_progress_reports d "
                    + "           WHERE d.project_id = :pid AND d.approval_status = 'APPROVED' "
                    + "             AND d.report_date BETWEEN :mfrom AND :mto AND d.boq_item_no IS NOT NULL "
                    + "           GROUP BY d.boq_item_no) mq ON mq.boq_item_no = b.item_no "
                    + "WHERE b.project_id = :pid "
                    + "  AND (COALESCE(b.qty_executed_to_date, 0) > 0 OR mq.qty IS NOT NULL) "
                    + "ORDER BY b.item_no")
                .setParameter("pid", projectId)
                .setParameter("mfrom", mFrom)
                .setParameter("mto", mTo)
                .getResultList();
        List<BoqLine> boqLines = new ArrayList<>(boqRows.size());
        for (Object[] r : boqRows) {
            boqLines.add(new BoqLine((String) r[0], (String) r[1], (String) r[2],
                    dec(r[3]), dec(r[6]), dec(r[4]), dec(r[5])));
        }

        List<Object[]> actRows = em.createNativeQuery(
                "SELECT COALESCE(NULLIF(btrim(d.activity_name), ''), '(unnamed)'), MIN(d.unit), "
                    + "       SUM(d.qty_executed) FILTER (WHERE d.report_date BETWEEN :mfrom AND :mto), "
                    + "       SUM(d.qty_executed) "
                    + "FROM project.daily_progress_reports d "
                    + "WHERE d.project_id = :pid AND d.approval_status = 'APPROVED' "
                    + "GROUP BY 1 ORDER BY 4 DESC NULLS LAST")
                .setParameter("pid", projectId)
                .setParameter("mfrom", mFrom)
                .setParameter("mto", mTo)
                .getResultList();
        List<ActivityLine> activityLines = new ArrayList<>(actRows.size());
        for (Object[] r : actRows) {
            activityLines.add(new ActivityLine((String) r[0], (String) r[1], dec(r[2]), dec(r[3])));
        }

        List<Object[]> supRows = em.createNativeQuery(
                "SELECT COALESCE(NULLIF(btrim(d.supervisor_name), ''), d.supervisor_user_id::text, '(unnamed)'), "
                    + "       SUM(d.qty_executed) FILTER (WHERE d.report_date BETWEEN :mfrom AND :mto), "
                    + "       SUM(d.qty_executed), "
                    + "       COUNT(*) FILTER (WHERE d.report_date BETWEEN :mfrom AND :mto), "
                    + "       COUNT(*) "
                    + "FROM project.daily_progress_reports d "
                    + "WHERE d.project_id = :pid AND d.approval_status = 'APPROVED' "
                    + "GROUP BY 1 ORDER BY 3 DESC NULLS LAST")
                .setParameter("pid", projectId)
                .setParameter("mfrom", mFrom)
                .setParameter("mto", mTo)
                .getResultList();
        List<SupervisorLine> supervisorLines = new ArrayList<>(supRows.size());
        for (Object[] r : supRows) {
            supervisorLines.add(new SupervisorLine((String) r[0], dec(r[1]), dec(r[2]),
                    lng(r[3]), lng(r[4])));
        }

        return new CommoditySummary(month, boqLines, activityLines, supervisorLines);
    }

    private static BigDecimal dec(Object o) {
        if (o == null) return null;
        return o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }

    private static long lng(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
}
