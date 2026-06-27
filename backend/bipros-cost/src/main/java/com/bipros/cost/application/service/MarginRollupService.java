package com.bipros.cost.application.service;

import com.bipros.cost.application.dto.MarginActivityDto;
import com.bipros.cost.application.dto.MarginItemDto;
import com.bipros.cost.application.dto.MarginPeriodDto;
import com.bipros.cost.application.dto.MarginSummaryDto;
import com.bipros.cost.application.dto.PeriodPerformanceRollupDto;
import com.bipros.cost.application.service.MarginCalculator.MarginResult;
import com.bipros.project.application.dto.DailyCostReportRow;
import com.bipros.project.application.service.DailyCostReportService;
import com.bipros.project.application.service.DprActualCostLookup;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.repository.BoqItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Single rollup behind both P&L scopes (budgeted-rate and BOQ-rate). One ledger for the whole
 * page: revenue = Σ over approved-DPR rows of (qty × the row item's rate); attributable actual
 * cost = per-DPR line_cost (a strict subset of {@code CostService.totalActual}); the headline
 * actual cost = {@code totalActual}. Each breakdown foots to {@code totalActual} — by-item /
 * by-activity append an explicit "Other" row for the non-execution remainder (general expenses +
 * material procurement); by-period reuses {@link PerformanceRollupService} whose per-period AC is
 * already {@code totalActual} distributed (so P&L periods match the Performance tab to the cent).
 */
@Service
@RequiredArgsConstructor
public class MarginRollupService {

    /** Sentinel label for the reconciling row. ASCII so the frontend can match it exactly. */
    public static final String OTHER_ROW_LABEL = "Other (general expenses & unattributed)";
    private static final BigDecimal CENT = new BigDecimal("0.005");

    private final BoqItemRepository boqItemRepository;
    private final DailyCostReportService dailyCostReportService;
    private final DprActualCostLookup dprActualCostLookup;
    private final CostService costService;
    private final PerformanceRollupService performanceRollupService;

    @Transactional(readOnly = true)
    public MarginSummaryDto summary(UUID projectId, Function<BoqItem, BigDecimal> rateFn) {
        Ctx c = load(projectId, rateFn);
        BigDecimal revenue = BigDecimal.ZERO;
        for (DailyCostReportRow r : c.rows) revenue = revenue.add(rowRevenue(r, c.rateById));
        MarginResult m = MarginCalculator.fromRevenueAndCost(revenue, c.totalActual);
        return new MarginSummaryDto(m.revenue(), m.actualCost(), m.margin(), m.marginPct());
    }

    @Transactional(readOnly = true)
    public List<MarginItemDto> items(UUID projectId, Function<BoqItem, BigDecimal> rateFn) {
        Ctx c = load(projectId, rateFn);
        Map<UUID, BigDecimal> revById = new HashMap<>();
        Map<UUID, BigDecimal> costById = new HashMap<>();
        Set<UUID> countedDpr = new HashSet<>();
        for (DailyCostReportRow r : c.rows) {
            if (r.boqItemId() == null) continue;
            revById.merge(r.boqItemId(), rowRevenue(r, c.rateById), BigDecimal::add);
            // Execution cost is a DPR-level total. Count each dprId once even if it ever spans
            // multiple rows, so Σ attributed stays a strict subset of totalActual — the footing
            // holds by construction, not by relying on DailyCostReportService emitting one row/DPR.
            if (r.dprId() != null && countedDpr.add(r.dprId())) {
                costById.merge(r.boqItemId(), c.costByDpr.getOrDefault(r.dprId(), BigDecimal.ZERO), BigDecimal::add);
            }
        }
        List<MarginItemDto> out = new ArrayList<>();
        BigDecimal attributed = BigDecimal.ZERO;
        for (BoqItem item : c.items) {
            BigDecimal rev = revById.getOrDefault(item.getId(), BigDecimal.ZERO);
            BigDecimal cost = costById.getOrDefault(item.getId(), BigDecimal.ZERO);
            attributed = attributed.add(cost);
            MarginResult m = MarginCalculator.fromRevenueAndCost(rev, cost);
            out.add(new MarginItemDto(item.getId(), item.getItemNo(), item.getDescription(),
                    item.getUnit(), item.getQtyExecutedToDate(), rateFn.apply(item),
                    m.revenue(), m.actualCost(), m.margin(), m.marginPct()));
        }
        appendOtherItem(out, c.totalActual.subtract(attributed));
        return out;
    }

    @Transactional(readOnly = true)
    public List<MarginActivityDto> activities(UUID projectId, Function<BoqItem, BigDecimal> rateFn) {
        Ctx c = load(projectId, rateFn);
        Map<String, BigDecimal> revByAct = new LinkedHashMap<>();
        Map<String, BigDecimal> costByAct = new LinkedHashMap<>();
        Set<UUID> countedDpr = new HashSet<>();
        for (DailyCostReportRow r : c.rows) {
            String key = r.activity() == null ? "—" : r.activity();
            revByAct.merge(key, rowRevenue(r, c.rateById), BigDecimal::add);
            if (r.dprId() != null && countedDpr.add(r.dprId())) {
                costByAct.merge(key, c.costByDpr.getOrDefault(r.dprId(), BigDecimal.ZERO), BigDecimal::add);
            }
        }
        List<MarginActivityDto> out = new ArrayList<>();
        BigDecimal attributed = BigDecimal.ZERO;
        for (String key : revByAct.keySet()) {
            BigDecimal cost = costByAct.getOrDefault(key, BigDecimal.ZERO);
            attributed = attributed.add(cost);
            MarginResult m = MarginCalculator.fromRevenueAndCost(revByAct.get(key), cost);
            out.add(new MarginActivityDto(key, m.revenue(), m.actualCost(), m.margin(), m.marginPct()));
        }
        appendOtherActivity(out, c.totalActual.subtract(attributed));
        return out;
    }

    @Transactional(readOnly = true)
    public List<MarginPeriodDto> periods(UUID projectId, String periodType, Function<BoqItem, BigDecimal> rateFn) {
        List<PeriodPerformanceRollupDto> perf = performanceRollupService.rollup(projectId, periodType);
        if (perf.isEmpty()) return List.of();
        Ctx c = load(projectId, rateFn);

        // Total revenue across ALL rows — the same figure summary() shows. Period revenue must sum
        // to it, just as period AC sums to totalActual. Compute raw dated revenue per bucket, then
        // scale to this total so revenue carried by rows dated outside the bucket window (e.g.
        // future-dated or null-date DPRs) is not silently dropped — mirroring how AC is
        // total-preserving via PerformancePeriodAssembler.
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (DailyCostReportRow r : c.rows) totalRevenue = totalRevenue.add(rowRevenue(r, c.rateById));

        List<BigDecimal> rawRev = new ArrayList<>(perf.size());
        for (PeriodPerformanceRollupDto p : perf) {
            BigDecimal rev = BigDecimal.ZERO;
            for (DailyCostReportRow r : c.rows) {
                LocalDate d = r.date();
                if (d == null || d.isBefore(p.startDate()) || d.isAfter(p.endDate())) continue;
                rev = rev.add(rowRevenue(r, c.rateById));
            }
            rawRev.add(rev);
        }
        List<BigDecimal> revByBucket = scaleToTotal(rawRev, totalRevenue);

        List<MarginPeriodDto> out = new ArrayList<>(perf.size());
        for (int i = 0; i < perf.size(); i++) {
            PeriodPerformanceRollupDto p = perf.get(i);
            BigDecimal ac = p.actualCost() == null ? BigDecimal.ZERO : p.actualCost();
            MarginResult m = MarginCalculator.fromRevenueAndCost(revByBucket.get(i), ac);
            out.add(new MarginPeriodDto(p.periodId(), p.periodName(), p.periodType(),
                    p.startDate(), p.endDate(), m.revenue(), m.actualCost(), m.margin(), m.marginPct()));
        }
        return out;
    }

    private void appendOtherItem(List<MarginItemDto> out, BigDecimal other) {
        if (other.abs().compareTo(CENT) < 0) return;
        MarginResult m = MarginCalculator.fromRevenueAndCost(BigDecimal.ZERO, other);
        out.add(new MarginItemDto(null, "", OTHER_ROW_LABEL, null, null, null,
                m.revenue(), m.actualCost(), m.margin(), m.marginPct()));
    }

    private void appendOtherActivity(List<MarginActivityDto> out, BigDecimal other) {
        if (other.abs().compareTo(CENT) < 0) return;
        MarginResult m = MarginCalculator.fromRevenueAndCost(BigDecimal.ZERO, other);
        out.add(new MarginActivityDto(OTHER_ROW_LABEL, m.revenue(), m.actualCost(), m.margin(), m.marginPct()));
    }

    private record Ctx(List<BoqItem> items, List<DailyCostReportRow> rows,
                       Map<UUID, BigDecimal> costByDpr, Map<UUID, BigDecimal> rateById,
                       BigDecimal totalActual) {}

    private Ctx load(UUID projectId, Function<BoqItem, BigDecimal> rateFn) {
        List<BoqItem> items = boqItemRepository.findByProjectIdOrderByItemNoAsc(projectId);
        List<DailyCostReportRow> rows = dailyCostReportService.generate(projectId, null, null).rows();
        Map<UUID, BigDecimal> costByDpr = dprActualCostLookup.sumByDpr(projectId);
        Map<UUID, BigDecimal> rateById = new HashMap<>();
        for (BoqItem i : items) rateById.put(i.getId(), nz(rateFn.apply(i)));
        BigDecimal totalActual = nz(costService.getCostSummary(projectId).totalActual());
        return new Ctx(items, rows, costByDpr, rateById, totalActual);
    }

    private static BigDecimal rowRevenue(DailyCostReportRow r, Map<UUID, BigDecimal> rateById) {
        BigDecimal rate = r.boqItemId() == null ? BigDecimal.ZERO
                : rateById.getOrDefault(r.boqItemId(), BigDecimal.ZERO);
        return nz(r.qtyExecuted()).multiply(rate);
    }

    /** Scale raw per-bucket weights so their sum equals {@code target} exactly — the last
     *  positive-weight bucket absorbs the rounding remainder, preserving the time-shape. Mirrors
     *  PerformancePeriodAssembler.scaleToTotal so period revenue is total-preserving like AC.
     *  target null, rawTotal ≤ 0, or no positive bucket → all zeros. */
    private static List<BigDecimal> scaleToTotal(List<BigDecimal> raw, BigDecimal target) {
        List<BigDecimal> out = new ArrayList<>(raw.size());
        BigDecimal rawTotal = BigDecimal.ZERO;
        int lastPositive = -1;
        for (int i = 0; i < raw.size(); i++) {
            rawTotal = rawTotal.add(raw.get(i));
            if (raw.get(i).signum() > 0) lastPositive = i;
        }
        if (target == null || rawTotal.signum() == 0 || lastPositive < 0) {
            for (int i = 0; i < raw.size(); i++) out.add(BigDecimal.ZERO);
            return out;
        }
        BigDecimal running = BigDecimal.ZERO;
        for (int i = 0; i < raw.size(); i++) {
            if (raw.get(i).signum() <= 0) { out.add(BigDecimal.ZERO); continue; }
            BigDecimal share;
            if (i == lastPositive) {
                share = target.subtract(running);
            } else {
                share = target.multiply(raw.get(i)).divide(rawTotal, 2, java.math.RoundingMode.HALF_UP);
                running = running.add(share);
            }
            out.add(share);
        }
        return out;
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
