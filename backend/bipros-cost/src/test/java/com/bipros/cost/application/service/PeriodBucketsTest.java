package com.bipros.cost.application.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodBucketsTest {

    @Test
    void monthlyClipsFirstAndLastToWindow() {
        List<PeriodBucket> b = PeriodBuckets.generate(
            LocalDate.of(2026, 1, 15), LocalDate.of(2026, 3, 10), "M");
        assertThat(b).hasSize(3);
        assertThat(b.get(0).start()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(b.get(0).end()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(b.get(0).name()).isEqualTo("Jan 2026");
        assertThat(b.get(2).start()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(b.get(2).end()).isEqualTo(LocalDate.of(2026, 3, 10)); // clamped to today
    }

    @Test
    void dailyIsOneBucketPerDay() {
        List<PeriodBucket> b = PeriodBuckets.generate(
            LocalDate.of(2026, 6, 25), LocalDate.of(2026, 6, 27), "D");
        assertThat(b).hasSize(3);
        assertThat(b.get(0).start()).isEqualTo(b.get(0).end());
        assertThat(b.get(0).name()).isEqualTo("25 Jun 2026");
    }

    @Test
    void weeklyEndsOnSundayClampedToToday() {
        // Mon 2026-06-22 .. Sat 2026-06-27; ISO week Sunday is 2026-06-28 (after today) -> clamp
        List<PeriodBucket> b = PeriodBuckets.generate(
            LocalDate.of(2026, 6, 22), LocalDate.of(2026, 6, 27), "W");
        assertThat(b).hasSize(1);
        assertThat(b.get(0).start()).isEqualTo(LocalDate.of(2026, 6, 22));
        assertThat(b.get(0).end()).isEqualTo(LocalDate.of(2026, 6, 27));
    }

    @Test
    void windowStartAfterTodayIsEmpty() {
        assertThat(PeriodBuckets.generate(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 6, 27), "M")).isEmpty();
    }

    @Test
    void normalizeDefaultsToMonthly() {
        assertThat(PeriodBuckets.normalize(null)).isEqualTo("M");
        assertThat(PeriodBuckets.normalize("d")).isEqualTo("D");
        assertThat(PeriodBuckets.normalize("WEEKLY")).isEqualTo("W");
        assertThat(PeriodBuckets.normalize("x")).isEqualTo("M");
    }
}
