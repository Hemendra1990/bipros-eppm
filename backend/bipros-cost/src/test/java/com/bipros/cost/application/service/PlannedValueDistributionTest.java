package com.bipros.cost.application.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlannedValueDistributionTest {

    private static BigDecimal sum(List<BigDecimal> xs) {
        return xs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void distributesAcrossPlannedWindowAndSumsToTotal() {
        // planned 2026-01-01..2026-04-30, fully elapsed (today past finish). Monthly buckets Jan..Jun.
        LocalDate today = LocalDate.of(2026, 6, 27);
        List<PeriodBucket> buckets = PeriodBuckets.generate(LocalDate.of(2026, 1, 1), today, "M");
        List<BigDecimal> pv = PlannedValueDistribution.distribute(
            buckets, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30), today,
            new BigDecimal("1000"), 2);

        assertThat(sum(pv)).isEqualByComparingTo("1000");          // exact reconciliation
        assertThat(pv.get(4)).isEqualByComparingTo("0");           // May = after planned finish
        assertThat(pv.get(5)).isEqualByComparingTo("0");           // Jun = after planned finish
        assertThat(pv.get(0).signum()).isPositive();               // Jan has planned overlap
    }

    @Test
    void zeroWhenNoPlannedDatesOrZeroTotal() {
        LocalDate today = LocalDate.of(2026, 6, 27);
        List<PeriodBucket> buckets = PeriodBuckets.generate(LocalDate.of(2026, 1, 1), today, "M");
        assertThat(sum(PlannedValueDistribution.distribute(
            buckets, null, null, today, new BigDecimal("1000"), 2))).isEqualByComparingTo("0");
        assertThat(sum(PlannedValueDistribution.distribute(
            buckets, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30), today,
            BigDecimal.ZERO, 2))).isEqualByComparingTo("0");
    }

    @Test
    void overlapDaysIsInclusiveAndZeroWhenDisjoint() {
        assertThat(PlannedValueDistribution.overlapDays(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
            LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 20))).isEqualTo(11);
        assertThat(PlannedValueDistribution.overlapDays(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5),
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 5))).isEqualTo(0);
    }
}
