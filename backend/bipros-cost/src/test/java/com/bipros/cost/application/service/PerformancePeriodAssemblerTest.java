package com.bipros.cost.application.service;

import com.bipros.cost.application.dto.PeriodPerformanceRollupDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PerformancePeriodAssemblerTest {

    private static BigDecimal sumAc(List<PeriodPerformanceRollupDto> r) {
        return r.stream().map(PeriodPerformanceRollupDto::actualCost).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private static BigDecimal sumEv(List<PeriodPerformanceRollupDto> r) {
        return r.stream().map(PeriodPerformanceRollupDto::earnedValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private static BigDecimal sumPv(List<PeriodPerformanceRollupDto> r) {
        return r.stream().map(PeriodPerformanceRollupDto::plannedValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void monthlyReconcilesAllThreeTotals() {
        UUID pid = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 6, 27);
        Map<LocalDate, BigDecimal> ac = Map.of(LocalDate.of(2026, 2, 15), new BigDecimal("100"));
        Map<LocalDate, BigDecimal> ev = Map.of(
            LocalDate.of(2026, 2, 15), new BigDecimal("300"),
            LocalDate.of(2026, 3, 10), new BigDecimal("200"));
        Map<LocalDate, BigDecimal> exp = Map.of(LocalDate.of(2026, 2, 20), new BigDecimal("50"));

        List<PeriodPerformanceRollupDto> rows = PerformancePeriodAssembler.assemble(
            pid, "M", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30), today,
            ac, ev, exp,
            new BigDecimal("150"), new BigDecimal("500"), new BigDecimal("1000"));

        assertThat(rows).hasSize(6);                          // Jan..Jun
        assertThat(sumAc(rows)).isEqualByComparingTo("150"); // DPR 100 + expense 50
        assertThat(sumEv(rows)).isEqualByComparingTo("500"); // 300 + 200
        assertThat(sumPv(rows)).isEqualByComparingTo("1000");// distributed over Jan..Apr

        PeriodPerformanceRollupDto feb = rows.get(1);
        assertThat(feb.actualCost()).isEqualByComparingTo("150");
        assertThat(feb.earnedValue()).isEqualByComparingTo("300");
        assertThat(feb.periodType()).isEqualTo("MONTHLY");
    }

    @Test
    void cpiSpiNullWhenDenominatorZero() {
        UUID pid = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 6, 27);
        // No AC, no planned window -> cpi and spi must be null, not a divide error.
        List<PeriodPerformanceRollupDto> rows = PerformancePeriodAssembler.assemble(
            pid, "M", null, null, today,
            Map.of(), Map.of(LocalDate.of(2026, 6, 1), new BigDecimal("10")), Map.of(),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.cpi()).isNull();
            assertThat(r.spi()).isNull();
        });
    }

    @Test
    void emptyWhenNoWindow() {
        assertThat(PerformancePeriodAssembler.assemble(
            UUID.randomUUID(), "M", null, null, LocalDate.of(2026, 6, 27),
            Map.of(), Map.of(), Map.of(),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)).isEmpty();
    }

    @Test
    void scalesAcAndEvToCardTotalsPreservingShape() {
        UUID pid = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 6, 27);
        Map<LocalDate, BigDecimal> ac = Map.of(LocalDate.of(2026, 2, 15), new BigDecimal("100"));
        Map<LocalDate, BigDecimal> ev = Map.of(
            LocalDate.of(2026, 2, 15), new BigDecimal("300"),
            LocalDate.of(2026, 3, 10), new BigDecimal("100"));
        Map<LocalDate, BigDecimal> exp = Map.of(LocalDate.of(2026, 2, 20), new BigDecimal("50"));
        // raw AC total = 150, raw EV total = 400; card totals are DOUBLE -> factor 2.
        List<PeriodPerformanceRollupDto> rows = PerformancePeriodAssembler.assemble(
            pid, "M", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30), today,
            ac, ev, exp,
            new BigDecimal("300"), new BigDecimal("800"), new BigDecimal("1000"));

        assertThat(sumAc(rows)).isEqualByComparingTo("300");   // scaled to card AC exactly
        assertThat(sumEv(rows)).isEqualByComparingTo("800");   // scaled to card EV exactly
        assertThat(sumPv(rows)).isEqualByComparingTo("1000");
        // shape preserved: Feb still carries (100+50)*2 AC and 300*2 EV; Mar carries 100*2 EV
        assertThat(rows.get(1).actualCost()).isEqualByComparingTo("300"); // Feb = 150*2
        assertThat(rows.get(1).earnedValue()).isEqualByComparingTo("600"); // Feb = 300*2
        assertThat(rows.get(2).earnedValue()).isEqualByComparingTo("200"); // Mar = 100*2
    }
}
