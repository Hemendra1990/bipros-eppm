package com.bipros.cost.application.service;

import com.bipros.cost.domain.entity.ActivityExpense;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ActivityCostCalculatorExpenseActualTest {
  @Test
  void sumsOnlyExpenseActualCost_ignoringResourceAssignments() {
    UUID a = UUID.randomUUID();
    ActivityExpense e1 = new ActivityExpense(); e1.setActualCost(new BigDecimal("100"));
    ActivityExpense e2 = new ActivityExpense(); e2.setActualCost(new BigDecimal("50"));
    ActivityExpense e3 = new ActivityExpense(); e3.setActualCost(null); // null ignored
    BigDecimal result = ActivityCostCalculator.calculateExpenseActualCost(a, Map.of(a, List.of(e1, e2, e3)));
    assertEquals(0, new BigDecimal("150").compareTo(result));
  }

  @Test
  void returnsZeroWhenActivityAbsent() {
    assertEquals(0, BigDecimal.ZERO.compareTo(
        ActivityCostCalculator.calculateExpenseActualCost(UUID.randomUUID(), Map.of())));
  }
}
