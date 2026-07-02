package com.bipros.project.application.service;

import com.bipros.project.domain.repository.DailyProgressReportRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DprEarnedValueLookupTest {
  private final DailyProgressReportRepository repo = mock(DailyProgressReportRepository.class);
  private final DprEarnedValueLookup lookup = new DprEarnedValueLookup(repo);
  private static final UUID P = UUID.randomUUID();
  private static final UUID ITEM = UUID.randomUUID();
  private static final LocalDate D1 = LocalDate.of(2026, 3, 30);
  private static final LocalDate D2 = LocalDate.of(2026, 3, 31);

  @Test
  void period_earned_is_cumulatively_capped_at_boq_qty() {
    // rows: [boqItemId, reportDate, qtyOnDate, boqQty, budgetedRate]
    when(repo.sumQtyByBoqItemAndDate(P)).thenReturn(List.of(
        new Object[]{ITEM, D1, new BigDecimal("60"), new BigDecimal("100"), new BigDecimal("10")},
        new Object[]{ITEM, D2, new BigDecimal("60"), new BigDecimal("100"), new BigDecimal("10")}));

    Map<LocalDate, BigDecimal> out = lookup.sumByProjectGroupedByDate(P);

    // day1: cap(60)−cap(0) = 60 × 10 = 600
    assertThat(out.get(D1)).isEqualByComparingTo("600.00");
    // day2: cap(120)−cap(60) = (100−60) × 10 = 400  (NOT 600)
    assertThat(out.get(D2)).isEqualByComparingTo("400.00");
    // total = 1000 = capped project EV (100 × 10)
    assertThat(out.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("1000.00");
  }

  @Test
  void nullProjectIdReturnsEmpty() {
    assertThat(lookup.sumByProjectGroupedByDate(null)).isEmpty();
  }
}
