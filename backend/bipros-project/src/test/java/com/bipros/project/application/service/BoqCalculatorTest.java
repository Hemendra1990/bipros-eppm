package com.bipros.project.application.service;

import com.bipros.project.domain.model.BoqItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the six BOQ derived-field formulas. Values are hand-computed from the
 * workbook's "BOQ & Budget" sheet so any future refactor that breaks the math will fail here
 * rather than silently diverging from the source-of-truth spreadsheet.
 */
class BoqCalculatorTest {

  @Test
  void dbm_row_matches_workbook_3_1_exactly() {
    // Row 3.1 DBM 50 mm: BOQ 5800 MT @ 4400 = 25,520,000; budget rate 4500 ⇒ 26,100,000;
    // executed 960 MT @ 4620 = 4,435,200; earned budget = 960 × 4500 = 4,320,000;
    // variance = 4,435,200 − 4,320,000 = 115,200; variance % ≈ 2.6667 %.
    BoqItem item = BoqItem.builder()
        .boqQty(bd("5800"))
        .boqRate(bd("4400"))
        .budgetedRate(bd("4500"))
        .qtyExecutedToDate(bd("960"))
        .actualRate(bd("4620"))
        .build();

    BoqCalculator.recompute(item);

    assertThat(item.getBoqAmount()).isEqualByComparingTo("25520000.00");
    assertThat(item.getBudgetedAmount()).isEqualByComparingTo("26100000.00");
    assertThat(item.getActualAmount()).isEqualByComparingTo("4435200.00");
    assertThat(item.getPercentComplete()).isEqualByComparingTo("0.165517"); // 960/5800
    assertThat(item.getCostVariance()).isEqualByComparingTo("115200.00");
    assertThat(item.getCostVariancePercent()).isEqualByComparingTo("0.026667"); // 115200/4320000
  }

  @Test
  void bc_row_matches_workbook_3_2() {
    // Row 3.2 BC 40 mm: BOQ 4200 MT @ 5100; budget 5200; executed 540 @ 5320
    BoqItem item = BoqItem.builder()
        .boqQty(bd("4200"))
        .boqRate(bd("5100"))
        .budgetedRate(bd("5200"))
        .qtyExecutedToDate(bd("540"))
        .actualRate(bd("5320"))
        .build();

    BoqCalculator.recompute(item);

    assertThat(item.getBoqAmount()).isEqualByComparingTo("21420000.00");
    assertThat(item.getBudgetedAmount()).isEqualByComparingTo("21840000.00");
    assertThat(item.getActualAmount()).isEqualByComparingTo("2872800.00");
    // variance = 540×5320 − 540×5200 = 540×120 = 64,800
    assertThat(item.getCostVariance()).isEqualByComparingTo("64800.00");
  }

  @Test
  void gsb_row_matches_workbook_2_1() {
    BoqItem item = BoqItem.builder()
        .boqQty(bd("9500"))
        .boqRate(bd("780"))
        .budgetedRate(bd("800"))
        .qtyExecutedToDate(bd("1380"))
        .actualRate(bd("810"))
        .build();

    BoqCalculator.recompute(item);

    assertThat(item.getBoqAmount()).isEqualByComparingTo("7410000.00");
    assertThat(item.getBudgetedAmount()).isEqualByComparingTo("7600000.00");
    assertThat(item.getActualAmount()).isEqualByComparingTo("1117800.00");
    // variance = 1380×810 − 1380×800 = 1380×10 = 13,800
    assertThat(item.getCostVariance()).isEqualByComparingTo("13800.00");
  }

  @Test
  void zero_boq_qty_leaves_percent_complete_null() {
    BoqItem item = BoqItem.builder()
        .boqQty(BigDecimal.ZERO)
        .boqRate(bd("100"))
        .budgetedRate(bd("100"))
        .qtyExecutedToDate(BigDecimal.ZERO)
        .actualRate(BigDecimal.ZERO)
        .build();

    BoqCalculator.recompute(item);

    assertThat(item.getPercentComplete()).isNull();
    assertThat(item.getCostVariancePercent()).isNull();
    assertThat(item.getBoqAmount()).isEqualByComparingTo("0.00");
  }

  @Test
  void no_execution_yet_leaves_variance_percent_null_but_amount_zero() {
    BoqItem item = BoqItem.builder()
        .boqQty(bd("1000"))
        .boqRate(bd("500"))
        .budgetedRate(bd("520"))
        .qtyExecutedToDate(BigDecimal.ZERO)
        .actualRate(bd("0"))
        .build();

    BoqCalculator.recompute(item);

    assertThat(item.getPercentComplete()).isEqualByComparingTo("0.000000");
    assertThat(item.getCostVariance()).isEqualByComparingTo("0.00");
    // earned budget = 0 × 520 = 0 ⇒ variance % undefined
    assertThat(item.getCostVariancePercent()).isNull();
  }

  @Test
  void nulls_treated_as_zero() {
    BoqItem item = new BoqItem();
    BoqCalculator.recompute(item);
    assertThat(item.getBoqAmount()).isEqualByComparingTo("0.00");
    assertThat(item.getBudgetedAmount()).isEqualByComparingTo("0.00");
    assertThat(item.getActualAmount()).isEqualByComparingTo("0.00");
    assertThat(item.getCostVariance()).isEqualByComparingTo("0.00");
    assertThat(item.getPercentComplete()).isNull();
    assertThat(item.getCostVariancePercent()).isNull();
  }

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }
}
