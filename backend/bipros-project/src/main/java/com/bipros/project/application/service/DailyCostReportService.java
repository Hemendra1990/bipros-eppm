package com.bipros.project.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.project.application.dto.DailyCostReportResponse;
import com.bipros.project.application.dto.DailyCostReportRow;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Generates the Daily Cost Report (Section B of the workbook "Daily Cost Report" sheet) by joining
 * Daily Progress Report rows with the project's BOQ items. The rate lookup tries, in order:
 *
 * <ol>
 *   <li>{@code DPR.boqItemNo} → exact match on {@code BoqItem.itemNo}</li>
 *   <li>{@code DPR.activityName} substring-match against {@code BoqItem.description} (case-insensitive)</li>
 * </ol>
 *
 * <p>When neither produces a hit, the row still appears in the report with {@code null} rates and
 * {@code null} costs — never silently replaced with zeros — so downstream reviewers can spot a
 * broken activity-to-BOQ link rather than thinking the work was free.
 *
 * <p>Formulas match the workbook exactly (Section B):
 * <pre>
 *   budgetedCost    = qtyExecuted × budgetedUnitRate
 *   actualCost      = qtyExecuted × actualUnitRate
 *   variance        = actualCost − budgetedCost     (positive = over budget)
 *   variancePercent = variance / budgetedCost       (null when budgetedCost = 0)
 * </pre>
 */
@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class DailyCostReportService {

  private static final int AMOUNT_SCALE = 2;
  private static final int RATIO_SCALE = 6;

  private final DailyProgressReportRepository dprRepository;
  private final BoqItemRepository boqItemRepository;
  private final ProjectRepository projectRepository;

  public DailyCostReportResponse generate(UUID projectId, LocalDate from, LocalDate to) {
    if (!projectRepository.existsById(projectId)) {
      throw new ResourceNotFoundException("Project", projectId);
    }

    List<DailyProgressReport> dprRows = (from != null && to != null)
        ? dprRepository.findByProjectIdAndReportDateBetweenOrderByReportDateAscIdAsc(projectId, from, to)
        : dprRepository.findByProjectIdOrderByReportDateAscIdAsc(projectId);

    List<BoqItem> boqItems = boqItemRepository.findByProjectIdOrderByItemNoAsc(projectId);
    Map<String, BoqItem> boqByItemNo = new HashMap<>();
    for (BoqItem b : boqItems) {
      if (b.getItemNo() != null) {
        boqByItemNo.put(b.getItemNo(), b);
      }
    }

    List<DailyCostReportRow> rows = new ArrayList<>(dprRows.size());
    BigDecimal periodBudgeted = BigDecimal.ZERO;
    BigDecimal periodActual = BigDecimal.ZERO;

    for (DailyProgressReport d : dprRows) {
      BoqItem match = resolveBoqItem(d, boqByItemNo, boqItems);
      BigDecimal budgetedRate = match != null ? match.getBudgetedRate() : null;
      BigDecimal actualRate = match != null ? match.getActualRate() : null;
      BigDecimal qty = d.getQtyExecuted() != null ? d.getQtyExecuted() : BigDecimal.ZERO;

      BigDecimal budgetedCost = (budgetedRate == null)
          ? null
          : qty.multiply(budgetedRate).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
      BigDecimal actualCost = (actualRate == null)
          ? null
          : qty.multiply(actualRate).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

      BigDecimal variance = null;
      BigDecimal variancePct = null;
      if (budgetedCost != null && actualCost != null) {
        variance = actualCost.subtract(budgetedCost);
        if (budgetedCost.signum() != 0) {
          variancePct = variance.divide(budgetedCost, RATIO_SCALE, RoundingMode.HALF_UP);
        }
        periodBudgeted = periodBudgeted.add(budgetedCost);
        periodActual = periodActual.add(actualCost);
      }

      rows.add(new DailyCostReportRow(
          d.getId(),
          d.getReportDate(),
          d.getActivityName(),
          qty,
          d.getUnit(),
          match != null ? match.getItemNo() : null,
          budgetedRate,
          actualRate,
          budgetedCost,
          actualCost,
          variance,
          variancePct,
          d.getSupervisorName()));
    }

    BigDecimal periodVariance = periodActual.subtract(periodBudgeted).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    BigDecimal periodVariancePct = periodBudgeted.signum() == 0
        ? null
        : periodVariance.divide(periodBudgeted, RATIO_SCALE, RoundingMode.HALF_UP);

    log.info("[DailyCostReport] project={}, rows={}, periodBudgeted={}, periodActual={}, variance={}",
        projectId, rows.size(), periodBudgeted, periodActual, periodVariance);

    return new DailyCostReportResponse(
        from,
        to,
        rows,
        periodBudgeted.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP),
        periodActual.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP),
        periodVariance,
        periodVariancePct);
  }

  /**
   * Resolve a BoqItem for a DPR row: exact itemNo match first, then activity-name substring
   * fallback against the BoqItem.description. The fallback lets DPRs seeded without boqItemNo
   * (e.g. the Apr-16 GSB row) still produce a costed line.
   */
  private BoqItem resolveBoqItem(DailyProgressReport d, Map<String, BoqItem> byItemNo, List<BoqItem> all) {
    if (d.getBoqItemNo() != null && !d.getBoqItemNo().isBlank()) {
      BoqItem byNo = byItemNo.get(d.getBoqItemNo());
      if (byNo != null) return byNo;
    }
    String activity = d.getActivityName();
    if (activity == null || activity.isBlank()) return null;
    String needle = activity.toLowerCase(Locale.ROOT);
    // Pick the first BOQ whose description contains (or is contained in) the activity text.
    for (BoqItem b : all) {
      if (b.getDescription() == null) continue;
      String desc = b.getDescription().toLowerCase(Locale.ROOT);
      if (desc.startsWith(needle) || desc.contains(needle) || needle.contains(desc.split("[(\\-]")[0].trim())) {
        return b;
      }
    }
    return null;
  }
}
