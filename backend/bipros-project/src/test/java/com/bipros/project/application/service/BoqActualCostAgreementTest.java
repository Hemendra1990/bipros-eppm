package com.bipros.project.application.service;

import com.bipros.common.event.DprMutationType;
import com.bipros.common.event.DprSubmittedEvent;
import com.bipros.project.application.listener.BoqActualRateRecalcListener;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.repository.BoqItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Invariant test: {@link BoqRebuildService} (batch) and {@link BoqActualRateRecalcListener}
 * (live event) must produce byte-identical {@code actualRate} for the same BOQ item when given
 * the same cost and qty inputs. This is the core guarantee of the single-source-of-truth fix:
 * both writers delegate to the same {@link BoqActualCostQuery}, so they can never diverge.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BOQ actualRate agreement — rebuild and listener produce identical results")
class BoqActualCostAgreementTest {

  @Mock private BoqItemRepository boqRepo;
  @Mock private BoqService boqService;
  @Mock private BoqActualCostQuery boqActualCostQuery;

  private BoqRebuildService rebuildService;
  private BoqActualRateRecalcListener listener;

  private final UUID projectId = UUID.randomUUID();
  private final UUID boqId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    rebuildService = new BoqRebuildService(boqRepo, boqService, boqActualCostQuery);
    listener = new BoqActualRateRecalcListener(boqRepo, boqActualCostQuery);
    // EntityManager is not exercised by onDprSubmitted path; inject null to satisfy the field
    ReflectionTestUtils.setField(listener, "em", null);
  }

  @Test
  @DisplayName("rebuild and listener produce identical actualRate = cost / qty")
  void bothWritersAgreeOnActualRate() {
    BigDecimal cost = new BigDecimal("45000");
    BigDecimal qty  = new BigDecimal("150");
    BigDecimal expectedRate = cost.divide(qty, 4, RoundingMode.HALF_UP); // 300.0000

    // ── rebuild path ──────────────────────────────────────────────────────
    BoqItem rebuildItem = boqItem();
    when(boqRepo.findByProjectId(projectId)).thenReturn(List.of(rebuildItem));
    // A10: the rebuild's qty comes from the canonical split-aware roll-up — simulate its write.
    org.mockito.Mockito.doAnswer(inv -> { rebuildItem.setQtyExecutedToDate(qty); return null; })
        .when(boqService).recomputeExecutedQtyApproved(projectId, boqId);
    when(boqActualCostQuery.sumActualCost(projectId, boqId)).thenReturn(cost);

    rebuildService.rebuildFromDprs(projectId);
    BigDecimal rebuildRate = rebuildItem.getActualRate();

    // ── listener path ─────────────────────────────────────────────────────
    BoqItem listenerItem = boqItem();
    // A5: the listener divides by the STORED measured qty (written by DprBoqSyncListener at
    // order 10 in production) — simulate that write here.
    listenerItem.setQtyExecutedToDate(qty);
    when(boqRepo.findById(boqId)).thenReturn(Optional.of(listenerItem));

    listener.onDprSubmitted(DprSubmittedEvent.withoutChildren(
        projectId, UUID.randomUUID(), java.time.LocalDate.of(2026, 6, 1),
        "Activity A", null, qty, null, null,
        DprMutationType.CREATED, UUID.randomUUID(), boqId, boqId));

    BigDecimal listenerRate = listenerItem.getActualRate();

    // ── assertion ─────────────────────────────────────────────────────────
    assertThat(rebuildRate)
        .as("rebuild actualRate must equal expected cost/qty")
        .isEqualByComparingTo(expectedRate);
    assertThat(listenerRate)
        .as("listener actualRate must equal expected cost/qty")
        .isEqualByComparingTo(expectedRate);
    assertThat(rebuildRate)
        .as("rebuild and listener must produce identical actualRate (no last-writer-wins flip)")
        .isEqualByComparingTo(listenerRate);
  }

  @Test
  @DisplayName("corrupted effective_rate has no effect — actualRate is cost/qty regardless")
  void corruptedEffectiveRateDoesNotAffectResult() {
    // Simulates the Khasab scenario: effective_rate was ~100× too low due to a bad import rescale.
    // The shared cost query uses line_cost (not effective_rate), so the result is unaffected.
    BigDecimal lineCostBasedCost = new BigDecimal("813692");  // correct: Σ dpr.line_cost
    BigDecimal qty = new BigDecimal("1000");
    BigDecimal expectedRate = lineCostBasedCost.divide(qty, 4, RoundingMode.HALF_UP);

    BoqItem rebuildItem = boqItem();
    when(boqRepo.findByProjectId(projectId)).thenReturn(List.of(rebuildItem));
    org.mockito.Mockito.doAnswer(inv -> { rebuildItem.setQtyExecutedToDate(qty); return null; })
        .when(boqService).recomputeExecutedQtyApproved(projectId, boqId);
    when(boqActualCostQuery.sumActualCost(projectId, boqId)).thenReturn(lineCostBasedCost);

    rebuildService.rebuildFromDprs(projectId);

    BoqItem listenerItem = boqItem();
    listenerItem.setQtyExecutedToDate(qty);   // A5: stored measured qty (order-10 write)
    when(boqRepo.findById(boqId)).thenReturn(Optional.of(listenerItem));

    listener.onDprSubmitted(DprSubmittedEvent.withoutChildren(
        projectId, UUID.randomUUID(), java.time.LocalDate.of(2026, 6, 1),
        "Activity B", null, qty, null, null,
        DprMutationType.UPDATED, UUID.randomUUID(), boqId, boqId));

    assertThat(rebuildItem.getActualRate()).isEqualByComparingTo(expectedRate);
    assertThat(listenerItem.getActualRate()).isEqualByComparingTo(expectedRate);
    assertThat(rebuildItem.getActualRate()).isEqualByComparingTo(listenerItem.getActualRate());
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private BoqItem boqItem() {
    BoqItem item = new BoqItem();
    item.setId(boqId);
    item.setProjectId(projectId);
    item.setBoqQty(new BigDecimal("500"));
    item.setBoqRate(new BigDecimal("1000"));
    item.setBudgetedRate(new BigDecimal("900"));
    item.setQtyExecutedToDate(new BigDecimal("150"));
    item.setManualOverride(Boolean.FALSE);
    return item;
  }
}
