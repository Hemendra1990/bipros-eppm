package com.bipros.cost.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Distributes the project's planned-value-to-date across buckets in proportion to each bucket's
 *  day-overlap with the elapsed planned window [plannedStart, min(plannedFinish, today)]. Σ equals
 *  pvTotal exactly (the last weighted bucket absorbs rounding). Buckets outside the window get 0.
 *  Pure / no I/O. */
public final class PlannedValueDistribution {

    private PlannedValueDistribution() {}

    public static List<BigDecimal> distribute(List<PeriodBucket> buckets,
                                              LocalDate plannedStart, LocalDate plannedFinish,
                                              LocalDate today, BigDecimal pvTotal, int scale) {
        List<BigDecimal> out = new ArrayList<>(buckets.size());
        BigDecimal pv = pvTotal == null ? BigDecimal.ZERO : pvTotal;
        boolean noWindow = plannedStart == null || plannedFinish == null
                || !plannedFinish.isAfter(plannedStart) || today == null || pv.signum() == 0;
        if (noWindow) {
            for (int i = 0; i < buckets.size(); i++) out.add(BigDecimal.ZERO);
            return out;
        }
        LocalDate pvEnd = plannedFinish.isAfter(today) ? today : plannedFinish; // min(finish, today)
        long[] w = new long[buckets.size()];
        long totalWeight = 0;
        int lastNonZero = -1;
        for (int i = 0; i < buckets.size(); i++) {
            w[i] = overlapDays(buckets.get(i).start(), buckets.get(i).end(), plannedStart, pvEnd);
            totalWeight += w[i];
            if (w[i] > 0) lastNonZero = i;
        }
        if (totalWeight == 0) {
            for (int i = 0; i < buckets.size(); i++) out.add(BigDecimal.ZERO);
            return out;
        }
        BigDecimal running = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.valueOf(totalWeight);
        for (int i = 0; i < buckets.size(); i++) {
            if (w[i] == 0) { out.add(BigDecimal.ZERO); continue; }
            BigDecimal share;
            if (i == lastNonZero) {
                share = pv.subtract(running); // absorb rounding remainder
            } else {
                share = pv.multiply(BigDecimal.valueOf(w[i]))
                          .divide(weightTotal, scale, RoundingMode.HALF_UP);
                running = running.add(share);
            }
            out.add(share);
        }
        return out;
    }

    /** Inclusive day-count of the overlap between [aStart,aEnd] and [bStart,bEnd]; 0 if disjoint. */
    static long overlapDays(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
        LocalDate s = aStart.isAfter(bStart) ? aStart : bStart;
        LocalDate e = aEnd.isBefore(bEnd) ? aEnd : bEnd;
        if (s.isAfter(e)) return 0;
        return ChronoUnit.DAYS.between(s, e) + 1;
    }
}
