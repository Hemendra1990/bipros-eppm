package com.bipros.reporting.application.service;

import com.bipros.reporting.application.dto.DailyDeploymentMatrix;
import com.bipros.reporting.application.dto.DailyDeploymentMatrix.Row;
import com.bipros.reporting.application.dto.DailyDeploymentMatrix.Section;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the 31-day Daily Deployment matrix from {@code project.daily_resource_deployments}.
 *
 * <p>Native SQL through the shared {@link EntityManager} so this module stays decoupled from
 * {@code bipros-project}. Hours are aggregated per (resource_description, log_date); the
 * resource_description column is the human label entered on the supervisor's daily form (e.g.
 * "Dozer", "Mason") which already matches the row labels in the template workbook.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailyDeploymentReportService {

  @PersistenceContext private EntityManager em;

  @Transactional(readOnly = true)
  public DailyDeploymentMatrix build(UUID projectId, YearMonth month) {
    LocalDate from = month.atDay(1);
    LocalDate to = month.atEndOfMonth();
    int daysInMonth = month.lengthOfMonth();

    @SuppressWarnings("unchecked")
    List<Object[]> rows = em.createNativeQuery(
            "SELECT d.resource_description, d.log_date, "
                + "       COALESCE(SUM(d.hours_worked), 0), "
                + "       COALESCE(SUM(d.nos_planned), 0) "
                + "FROM project.daily_resource_deployments d "
                + "WHERE d.project_id = :projectId "
                + "  AND d.log_date BETWEEN :from AND :to "
                + "GROUP BY d.resource_description, d.log_date "
                + "ORDER BY d.resource_description, d.log_date")
        .setParameter("projectId", projectId)
        .setParameter("from", from)
        .setParameter("to", to)
        .getResultList();

    Map<String, BigDecimal[]> hoursByLabel = new LinkedHashMap<>();
    Map<String, BigDecimal> planByLabel = new LinkedHashMap<>();
    for (Object[] r : rows) {
      String label = (String) r[0];
      LocalDate logDate = ((java.sql.Date) r[1]).toLocalDate();
      BigDecimal hours = toBigDecimal(r[2]);
      BigDecimal nosPlanned = toBigDecimal(r[3]);

      BigDecimal[] perDay = hoursByLabel.computeIfAbsent(label, k -> emptyDayArray(daysInMonth));
      int idx = logDate.getDayOfMonth() - 1;
      perDay[idx] = (perDay[idx] == null ? hours : perDay[idx].add(hours));

      planByLabel.merge(label, nosPlanned, BigDecimal::add);
    }

    List<Row> totalRows = new ArrayList<>(hoursByLabel.size());
    for (Map.Entry<String, BigDecimal[]> e : hoursByLabel.entrySet()) {
      BigDecimal total = BigDecimal.ZERO;
      for (BigDecimal v : e.getValue()) {
        if (v != null) total = total.add(v);
      }
      totalRows.add(new Row(e.getKey(), planByLabel.get(e.getKey()), e.getValue(), total));
    }

    // Day Shift / Night Shift sections are emitted as same-row labels with empty values so the
    // Excel layout mirrors the template; the schema does not currently track shift granularity.
    List<Row> dayRows = scaffoldRows(totalRows, daysInMonth);
    List<Row> nightRows = scaffoldRows(totalRows, daysInMonth);

    return new DailyDeploymentMatrix(
        projectId,
        month,
        daysInMonth,
        List.of(
            new Section("DAY", dayRows),
            new Section("NIGHT", nightRows),
            new Section("TOTAL", totalRows)));
  }

  private static List<Row> scaffoldRows(List<Row> source, int days) {
    List<Row> out = new ArrayList<>(source.size());
    for (Row r : source) {
      out.add(new Row(r.resourceLabel(), null, emptyDayArray(days), null));
    }
    return out;
  }

  private static BigDecimal[] emptyDayArray(int days) {
    BigDecimal[] arr = new BigDecimal[days];
    for (int i = 0; i < days; i++) arr[i] = null;
    return arr;
  }

  private static BigDecimal toBigDecimal(Object o) {
    if (o == null) return BigDecimal.ZERO;
    if (o instanceof BigDecimal bd) return bd;
    if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
    return BigDecimal.ZERO;
  }
}
