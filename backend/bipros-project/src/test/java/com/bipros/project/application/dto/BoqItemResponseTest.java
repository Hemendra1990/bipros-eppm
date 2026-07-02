package com.bipros.project.application.dto;

import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.model.BoqStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link BoqItemResponse}. Verifies that {@link BoqItemResponse#from(BoqItem)}
 * caps percentComplete at 1.0 (100%) without capping other variance/cost fields.
 */
@DisplayName("BoqItemResponse — percentComplete capping at 100%")
class BoqItemResponseTest {

  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID BOQ_ITEM_ID = UUID.randomUUID();
  private static final UUID WBS_NODE_ID = UUID.randomUUID();

  @Test
  @DisplayName("percentComplete > 1.0 should be capped to 1.0")
  void testPercentCompleteAboveOneIsCapped() {
    // Arrange: Create a BoqItem with percentComplete = 1.538462 (153.8462%)
    BoqItem boqItem = BoqItem.builder()
        .projectId(PROJECT_ID)
        .itemNo("001")
        .description("Test Item")
        .unit("m")
        .wbsNodeId(WBS_NODE_ID)
        .boqQty(new BigDecimal("100.000"))
        .boqRate(new BigDecimal("1000.0000"))
        .boqAmount(new BigDecimal("100000.00"))
        .budgetedRate(new BigDecimal("1000.0000"))
        .budgetedAmount(new BigDecimal("100000.00"))
        .qtyExecutedToDate(new BigDecimal("153.846200"))
        .actualRate(new BigDecimal("1050.0000"))
        .actualAmount(new BigDecimal("161536.00"))
        .percentComplete(new BigDecimal("1.538462"))
        .costVariance(new BigDecimal("11536.00"))
        .costVariancePercent(new BigDecimal("11.536000"))
        .chapter("Chapter 1")
        .status(BoqStatus.ACTIVE)
        .manualOverride(false)
        .build();

    // Act
    BoqItemResponse response = BoqItemResponse.from(boqItem);

    // Assert: percentComplete should be capped to 1.0, not 1.538462
    assertThat(response.percentComplete())
        .isNotNull()
        .isEqualByComparingTo(BigDecimal.ONE);

    // Assert: costVariancePercent should NOT be capped (should stay 11.536%)
    assertThat(response.costVariancePercent())
        .isNotNull()
        .isEqualByComparingTo(new BigDecimal("11.536000"));

    // Assert: costVariance should NOT be capped
    assertThat(response.costVariance())
        .isNotNull()
        .isEqualByComparingTo(new BigDecimal("11536.00"));
  }

  @Test
  @DisplayName("percentComplete < 1.0 should remain unchanged")
  void testPercentCompleteBelowOneIsUnchanged() {
    // Arrange: Create a BoqItem with percentComplete = 0.5 (50%)
    BoqItem boqItem = BoqItem.builder()
        .projectId(PROJECT_ID)
        .itemNo("002")
        .description("Test Item 2")
        .unit("m")
        .wbsNodeId(WBS_NODE_ID)
        .boqQty(new BigDecimal("100.000"))
        .boqRate(new BigDecimal("1000.0000"))
        .boqAmount(new BigDecimal("100000.00"))
        .budgetedRate(new BigDecimal("1000.0000"))
        .budgetedAmount(new BigDecimal("100000.00"))
        .qtyExecutedToDate(new BigDecimal("50.000"))
        .actualRate(new BigDecimal("1000.0000"))
        .actualAmount(new BigDecimal("50000.00"))
        .percentComplete(new BigDecimal("0.500000"))
        .costVariance(new BigDecimal("0.00"))
        .costVariancePercent(new BigDecimal("0.000000"))
        .chapter("Chapter 1")
        .status(BoqStatus.ACTIVE)
        .manualOverride(false)
        .build();

    // Act
    BoqItemResponse response = BoqItemResponse.from(boqItem);

    // Assert: percentComplete should remain 0.5
    assertThat(response.percentComplete())
        .isNotNull()
        .isEqualByComparingTo(new BigDecimal("0.5"));
  }

  @Test
  @DisplayName("null percentComplete should remain null")
  void testNullPercentCompleteRemainsNull() {
    // Arrange: Create a BoqItem with null percentComplete
    BoqItem boqItem = BoqItem.builder()
        .projectId(PROJECT_ID)
        .itemNo("003")
        .description("Test Item 3")
        .unit("m")
        .wbsNodeId(WBS_NODE_ID)
        .boqQty(new BigDecimal("100.000"))
        .boqRate(new BigDecimal("1000.0000"))
        .boqAmount(new BigDecimal("100000.00"))
        .budgetedRate(new BigDecimal("1000.0000"))
        .budgetedAmount(new BigDecimal("100000.00"))
        .qtyExecutedToDate(null)
        .actualRate(null)
        .actualAmount(null)
        .percentComplete(null)
        .costVariance(null)
        .costVariancePercent(null)
        .chapter("Chapter 1")
        .status(BoqStatus.PENDING)
        .manualOverride(false)
        .build();

    // Act
    BoqItemResponse response = BoqItemResponse.from(boqItem);

    // Assert: percentComplete should remain null
    assertThat(response.percentComplete()).isNull();
  }

  @Test
  @DisplayName("percentComplete exactly 1.0 should remain 1.0")
  void testPercentCompleteExactlyOneIsUnchanged() {
    // Arrange: Create a BoqItem with percentComplete = 1.0 (100%)
    BoqItem boqItem = BoqItem.builder()
        .projectId(PROJECT_ID)
        .itemNo("004")
        .description("Test Item 4")
        .unit("m")
        .wbsNodeId(WBS_NODE_ID)
        .boqQty(new BigDecimal("100.000"))
        .boqRate(new BigDecimal("1000.0000"))
        .boqAmount(new BigDecimal("100000.00"))
        .budgetedRate(new BigDecimal("1000.0000"))
        .budgetedAmount(new BigDecimal("100000.00"))
        .qtyExecutedToDate(new BigDecimal("100.000"))
        .actualRate(new BigDecimal("1000.0000"))
        .actualAmount(new BigDecimal("100000.00"))
        .percentComplete(BigDecimal.ONE)
        .costVariance(new BigDecimal("0.00"))
        .costVariancePercent(new BigDecimal("0.000000"))
        .chapter("Chapter 1")
        .status(BoqStatus.COMPLETED)
        .manualOverride(false)
        .build();

    // Act
    BoqItemResponse response = BoqItemResponse.from(boqItem);

    // Assert: percentComplete should remain 1.0
    assertThat(response.percentComplete())
        .isNotNull()
        .isEqualByComparingTo(BigDecimal.ONE);
  }
}
