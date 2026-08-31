package com.bipros.cost.application.service;

import com.bipros.cost.application.dto.PeriodPerformanceRollupDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Assembles per-period EVM rows from time-keyed AC/EV/expense maps + a planned-value total.
 *  AC = (DPR cost by date) + (expense actuals by date); EV = DPR earned by date; PV = distributed.
 *  Pure / no I/O — `today` is passed in for deterministic testing. */
public final class PerformancePeriodAssembler {

    private static final int AMOUNT_SCALE = 2;
    private static final int RATIO_SCALE = 4;

    private PerformancePeriodAssembler() {}

    public static List<PeriodPerformanceRollupDto> assemble(
            UUID projectId, String periodType,
            LocalDate plannedStart, LocalDate plannedFinish, LocalDate today,
            Map<LocalDate, BigDecimal> acByDate,
            Map<LocalDate, BigDecimal> evByDate,
            Map<LocalDate, BigDecimal> expenseAcByDate,
            BigDecimal acTotal, BigDecimal evTotal, BigDecimal pvTotal) {

        LocalDate windowStart = earliest(plannedStart,
                earliestKey(acByDate), earliestKey(evByDate), earliestKey(expenseAcByDate));
        if (windowStart == null || today == null || windowStart.isAfter(today)) return List.of();

        String normalized = PeriodBuckets.normalize(periodType);
        String typeLabel = switch (normalized) {
            case "D" -> "DAILY"; case "W" -> "WEEKLY"; default -> "MONTHLY";
        };
        List<PeriodBucket> buckets = PeriodBuckets.generate(windowStart, today, normalized);
        List<BigDecimal> pvByBucket = PlannedValueDistribution.distribute(
                buckets, plannedStart, plannedFinish, today, pvTotal, AMOUNT_SCALE);

        // Raw per-bucket time-profile from dated data, then scale each metric to its authoritative
        // project total (the Costs/EVM card figures) so Σ reconciles by construction. PV already
        // arrives pre-scaled (pvTotal = summary.plannedValue()); AC/EV are scaled here.
        List<BigDecimal> acRaw = new ArrayList<>(buckets.size());
        List<BigDecimal> evRaw = new ArrayList<>(buckets.size());
        for (PeriodBucket b : buckets) {
            acRaw.add(sumInRange(acByDate, b).add(sumInRange(expenseAcByDate, b)));
            evRaw.add(sumInRange(evByDate, b));
        }
        List<BigDecimal> acByBucket = scaleToTotal(acRaw, acTotal, AMOUNT_SCALE);
        List<BigDecimal> evByBucket = scaleToTotal(evRaw, evTotal, AMOUNT_SCALE);

        List<PeriodPerformanceRollupDto> out = new ArrayList<>(buckets.size());
        for (int i = 0; i < buckets.size(); i++) {
            PeriodBucket b = buckets.get(i);
            BigDecimal ac = acByBucket.get(i).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            BigDecimal ev = evByBucket.get(i).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            BigDecimal pv = pvByBucket.get(i).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            BigDecimal cv = ev.subtract(ac);
            BigDecimal sv = ev.subtract(pv);
            BigDecimal cpi = ac.signum() == 0 ? null : ev.divide(ac, RATIO_SCALE, RoundingMode.HALF_UP);
            BigDecimal spi = pv.signum() == 0 ? null : ev.divide(pv, RATIO_SCALE, RoundingMode.HALF_UP);
            UUID periodId = UUID.nameUUIDFromBytes(
                    (projectId + "|" + normalized + "|" + b.start()).getBytes(StandardCharsets.UTF_8));
            out.add(new PeriodPerformanceRollupDto(
                    periodId, b.name(), typeLabel, b.start(), b.end(),
                    ac, pv, ev, cv, sv, cpi, spi));
        }
        return out;
    }

    /** Scale the raw per-bucket weights so their sum equals {@code target} exactly — the last
     *  positive-weight bucket absorbs the rounding remainder, preserving the time-shape. target null
     *  → return the raw values (rounded); raw weights all ≤0 → all zeros (the total cannot be
     *  time-phased from dated data, e.g. earned qty set with no dated DPRs). */
    private static List<BigDecimal> scaleToTotal(List<BigDecimal> raw, BigDecimal target, int scale) {
        List<BigDecimal> out = new ArrayList<>(raw.size());
        if (target == null) {
            for (BigDecimal r : raw) out.add(r.setScale(scale, RoundingMode.HALF_UP));
            return out;
        }
        BigDecimal rawTotal = BigDecimal.ZERO;
        int lastPositive = -1;
        for (int i = 0; i < raw.size(); i++) {
            rawTotal = rawTotal.add(raw.get(i));
            if (raw.get(i).signum() > 0) lastPositive = i;
        }
        if (rawTotal.signum() == 0 || lastPositive < 0) {
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
                share = target.multiply(raw.get(i)).divide(rawTotal, scale, RoundingMode.HALF_UP);
                running = running.add(share);
            }
            out.add(share);
        }
        return out;
    }

    private static BigDecimal sumInRange(Map<LocalDate, BigDecimal> byDate, PeriodBucket b) {
        BigDecimal total = BigDecimal.ZERO;
        for (var e : byDate.entrySet()) {
            LocalDate d = e.getKey();
            if (d == null || e.getValue() == null) continue;
            if (!d.isBefore(b.start()) && !d.isAfter(b.end())) total = total.add(e.getValue());
        }
        return total;
    }

    private static LocalDate earliestKey(Map<LocalDate, BigDecimal> m) {
        LocalDate min = null;
        for (LocalDate d : m.keySet()) if (d != null && (min == null || d.isBefore(min))) min = d;
        return min;
    }

    private static LocalDate earliest(LocalDate... dates) {
        LocalDate min = null;
        for (LocalDate d : dates) if (d != null && (min == null || d.isBefore(min))) min = d;
        return min;
    }
}
