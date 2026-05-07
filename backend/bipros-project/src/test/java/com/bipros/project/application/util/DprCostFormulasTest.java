package com.bipros.project.application.util;

import com.bipros.project.domain.model.DprEquipment;
import com.bipros.project.domain.model.DprManpower;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit/cost formula coverage for {@link DprCostFormulas}. Pins the semantics relied on by
 * the ledger rollup — DAY basis returns nos (one day-equivalent per worker/unit), HOUR basis
 * returns nos × hours.
 */
@DisplayName("DprCostFormulas")
class DprCostFormulasTest {

  @Nested
  @DisplayName("manpowerUnits")
  class ManpowerUnits {

    @Test
    @DisplayName("DAY basis: nos=2, hours=8 → 2 (one day per person, hours are informational)")
    void dayBasisReturnsNos() {
      DprManpower row = DprManpower.builder()
          .nos(2).workingHours(new BigDecimal("8")).otHours(BigDecimal.ZERO).build();
      assertThat(DprCostFormulas.manpowerUnits(row, "DAY"))
          .isEqualByComparingTo(new BigDecimal("2"));
    }

    @Test
    @DisplayName("HOUR basis: nos=2, hours=8, ot=2 → 20 (nos × (hrs + ot), OT not multiplied)")
    void hourBasisReturnsNosTimesEffectiveHours() {
      DprManpower row = DprManpower.builder()
          .nos(2).workingHours(new BigDecimal("8")).otHours(new BigDecimal("2")).build();
      assertThat(DprCostFormulas.manpowerUnits(row, "HOUR"))
          .isEqualByComparingTo(new BigDecimal("20"));
    }

    @Test
    @DisplayName("EACH basis (unknown unit): falls through to nos")
    void eachBasisReturnsNos() {
      DprManpower row = DprManpower.builder()
          .nos(3).workingHours(new BigDecimal("8")).build();
      assertThat(DprCostFormulas.manpowerUnits(row, "EACH"))
          .isEqualByComparingTo(new BigDecimal("3"));
    }
  }

  @Nested
  @DisplayName("manpowerLineCost")
  class ManpowerCost {

    @Test
    @DisplayName("DAY basis: rate × nos (hours informational)")
    void dayBasisCost() {
      DprManpower row = DprManpower.builder()
          .nos(2).workingHours(new BigDecimal("8")).build();
      assertThat(DprCostFormulas.manpowerLineCost(row, new BigDecimal("8"), "DAY"))
          .isEqualByComparingTo(new BigDecimal("16.00"));
    }

    @Test
    @DisplayName("HOUR basis: rate × nos × (hrs + ot × 1.5)")
    void hourBasisCost() {
      DprManpower row = DprManpower.builder()
          .nos(2).workingHours(new BigDecimal("8")).otHours(new BigDecimal("2")).build();
      // 100 × 2 × (8 + 2×1.5) = 100 × 2 × 11 = 2200
      assertThat(DprCostFormulas.manpowerLineCost(row, new BigDecimal("100"), "HOUR"))
          .isEqualByComparingTo(new BigDecimal("2200.00"));
    }
  }

  @Nested
  @DisplayName("equipmentUnits")
  class EquipmentUnits {

    @Test
    @DisplayName("HOUR basis: nos=2, hours=8 → 16 (regression: Crane case)")
    void hourBasisReturnsNosTimesHours() {
      DprEquipment row = DprEquipment.builder()
          .nos(2).workingHours(new BigDecimal("8")).build();
      assertThat(DprCostFormulas.equipmentUnits(row, "HOUR"))
          .isEqualByComparingTo(new BigDecimal("16"));
    }

    @Test
    @DisplayName("DAY basis: nos=2, hours=8 → 2 (one day-equivalent per unit)")
    void dayBasisReturnsNos() {
      DprEquipment row = DprEquipment.builder()
          .nos(2).workingHours(new BigDecimal("8")).build();
      assertThat(DprCostFormulas.equipmentUnits(row, "DAY"))
          .isEqualByComparingTo(new BigDecimal("2"));
    }
  }

  @Nested
  @DisplayName("equipmentLineCost")
  class EquipmentCost {

    @Test
    @DisplayName("HOUR basis: rate × nos × hours (idle/breakdown excluded)")
    void hourBasisCost() {
      DprEquipment row = DprEquipment.builder()
          .nos(2).workingHours(new BigDecimal("8"))
          .idleHours(new BigDecimal("2")).breakdownHours(new BigDecimal("1")).build();
      // 18 × 2 × 8 = 288 — matches user's Crane DPR row
      assertThat(DprCostFormulas.equipmentLineCost(row, new BigDecimal("18"), "HOUR"))
          .isEqualByComparingTo(new BigDecimal("288.00"));
    }

    @Test
    @DisplayName("DAY basis: rate × nos")
    void dayBasisCost() {
      DprEquipment row = DprEquipment.builder()
          .nos(2).workingHours(new BigDecimal("8")).build();
      assertThat(DprCostFormulas.equipmentLineCost(row, new BigDecimal("500"), "DAY"))
          .isEqualByComparingTo(new BigDecimal("1000.00"));
    }
  }
}
