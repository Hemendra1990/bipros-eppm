package com.bipros.project.application.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Arithmetic contract of the BOQ split feature — every case is a worked example from the approved
 * split design (2026-08-03 §4.5 / 2026-08-04 integrated design), owner-reviewed numbers.
 */
class BoqOperationProgressCalculatorTest {

  private final BoqOperationProgressCalculator calc = new BoqOperationProgressCalculator();

  @Test
  void weightedExcavation() {
    // 2.3.6(i): Screening w40 t500 e500 → p=1.0; Compaction w60 t500 e300 MEASURE → p=0.6
    var r = calc.compute("WEIGHTED_OPERATIONS", bd("500"), List.of(
        op("40", "500", "500", false),
        op("60", "500", "300", true)));
    assertEquals(0, bd("0.760000").compareTo(r.earnedFraction()));
    assertEquals(0, bd("300").compareTo(r.measuredQty()));   // measurement op, RAW
  }

  @Test
  void weightedConcreteThreeOpsMixedUnits() {
    // 5.1.7(i)e: Formwork w30 t480 e480; Concreting w55 t120 e108 MEASURE; Painting w15 t300 e60
    var r = calc.compute("WEIGHTED_OPERATIONS", bd("120"), List.of(
        op("30", "480", "480", false),
        op("55", "120", "108", true),
        op("15", "300", "60", false)));
    assertEquals(0, bd("0.825000").compareTo(r.earnedFraction()));
    assertEquals(0, bd("108").compareTo(r.measuredQty()));
  }

  @Test
  void weightsNormalisedBySum() {   // edge 1+2: 33.33×3 → still reaches 1.0 when all done
    var r = calc.compute("WEIGHTED_OPERATIONS", bd("100"), List.of(
        op("33.33", "100", "100", true),
        op("33.33", "100", "100", false),
        op("33.33", "100", "100", false)));
    assertEquals(0, bd("1.000000").compareTo(r.earnedFraction()));
  }

  @Test
  void perOpOverExecutionClampsFractionButMeasuredStaysRaw() {   // edge 9
    var r = calc.compute("WEIGHTED_OPERATIONS", bd("260"), List.of(
        op("100", "260", "400", true)));
    assertEquals(0, bd("1.000000").compareTo(r.earnedFraction()));
    assertEquals(0, bd("400").compareTo(r.measuredQty()));   // OVERRUN signal survives
  }

  @Test
  void milestoneNullTargetIsBinary() {   // edge 10: any executed>0 → p=1, else 0
    var r0 = calc.compute("WEIGHTED_OPERATIONS", bd("100"), List.of(
        op("50", null, "0", false), op("50", "100", "50", true)));
    assertEquals(0, bd("0.250000").compareTo(r0.earnedFraction()));   // (0.5×0 + 0.5×0.5)
    var r1 = calc.compute("WEIGHTED_OPERATIONS", bd("100"), List.of(
        op("50", null, "1", false), op("50", "100", "50", true)));
    assertEquals(0, bd("0.750000").compareTo(r1.earnedFraction()));   // (0.5×1 + 0.5×0.5)
  }

  @Test
  void partitionSumsChildrenIgnoringWeights() {   // D4: Blasting vs Mechanical
    var r = calc.compute("QUANTITY_PARTITION", bd("500"), List.of(
        op("30", null, "200", false),
        op("70", null, "250", true)));
    assertEquals(0, bd("0.900000").compareTo(r.earnedFraction()));   // (200+250)/500
    assertEquals(0, bd("450").compareTo(r.measuredQty()));           // partition: measured = Σ all
  }

  @Test
  void partitionCapsAtOne() {
    var r = calc.compute("QUANTITY_PARTITION", bd("500"), List.of(
        op("50", null, "400", false), op("50", null, "300", true)));
    assertEquals(0, bd("1.000000").compareTo(r.earnedFraction()));
    assertEquals(0, bd("700").compareTo(r.measuredQty()));
  }

  @Test
  void zeroWeightSumFallsBackToZeroFraction() {
    var r = calc.compute("WEIGHTED_OPERATIONS", bd("100"),
        List.of(op("0", "100", "50", true)));
    assertEquals(0, BigDecimal.ZERO.compareTo(r.earnedFraction()));
  }

  @Test
  void legacyOpeningQuantityAddsToMeasured() {   // §7.3: split after history — nothing is lost
    // Pre-split 100 done (legacy w40 t100 e100 → p=1); measure op w60 t160 e50 → p=0.3125
    var r = calc.compute("WEIGHTED_OPERATIONS", bd("260"), List.of(
        legacy("40", "100", "100"),
        op("60", "160", "50", true)));
    assertEquals(0, bd("0.587500").compareTo(r.earnedFraction()));   // 0.4×1 + 0.6×0.3125
    assertEquals(0, bd("150").compareTo(r.measuredQty()));           // 100 opening + 50 measured
  }

  private static BoqOperationProgressCalculator.OpSnapshot op(String w, String t, String e, boolean measure) {
    return new BoqOperationProgressCalculator.OpSnapshot(UUID.randomUUID(),
        t == null ? null : bd(t), bd(w), measure, false, bd(e));
  }

  private static BoqOperationProgressCalculator.OpSnapshot legacy(String w, String t, String e) {
    return new BoqOperationProgressCalculator.OpSnapshot(UUID.randomUUID(),
        bd(t), bd(w), false, true, bd(e));
  }

  private static BigDecimal bd(String s) { return new BigDecimal(s); }
}
