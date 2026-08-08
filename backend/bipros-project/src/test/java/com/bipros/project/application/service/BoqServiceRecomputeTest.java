package com.bipros.project.application.service;

import com.bipros.common.util.AuditService;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.project.domain.repository.BoqOperationRepository;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bipros.project.application.dto.BoqSummaryResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BoqService.recomputeExecutedQtyApproved")
class BoqServiceRecomputeTest {

  @Mock BoqItemRepository boqItemRepository;
  @Mock BoqOperationRepository boqOperationRepository;
  @Mock ProjectRepository projectRepository;
  @Mock AuditService auditService;
  @Mock DailyProgressReportRepository dprRepository;
  @Mock EntityManager em;

  BoqService boqService;

  private final UUID projectId = UUID.randomUUID();
  private final UUID boqItemId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    boqService = new BoqService(boqItemRepository, boqOperationRepository,
        new BoqOperationProgressCalculator(), projectRepository, auditService, dprRepository);
    ReflectionTestUtils.setField(boqService, "em", em);
  }

  @Test
  @DisplayName("sets qtyExecutedToDate to the approved sum and saves")
  void setsApprovedQtyAndSaves() {
    BoqItem item = boqItem(null);
    when(boqItemRepository.findByIdForUpdate(boqItemId)).thenReturn(Optional.of(item));
    when(dprRepository.sumQtyExecutedByBoqItemIdApproved(projectId, boqItemId))
        .thenReturn(new BigDecimal("42"));

    boqService.recomputeExecutedQtyApproved(projectId, boqItemId);

    ArgumentCaptor<BoqItem> saved = ArgumentCaptor.forClass(BoqItem.class);
    verify(boqItemRepository).save(saved.capture());
    assertThat(saved.getValue().getQtyExecutedToDate()).isEqualByComparingTo("42");
  }

  @Test
  @DisplayName("BoqCalculator.recompute is called — derived fields are updated")
  void derivedFieldsRecomputed() {
    BoqItem item = boqItem(null);
    item.setBoqQty(new BigDecimal("100"));
    item.setBoqRate(new BigDecimal("50"));
    item.setBudgetedRate(new BigDecimal("40"));
    item.setActualRate(new BigDecimal("45"));
    when(boqItemRepository.findByIdForUpdate(boqItemId)).thenReturn(Optional.of(item));
    when(dprRepository.sumQtyExecutedByBoqItemIdApproved(projectId, boqItemId))
        .thenReturn(new BigDecimal("60"));

    boqService.recomputeExecutedQtyApproved(projectId, boqItemId);

    // BoqCalculator sets percentComplete = qtyExecutedToDate / boqQty = 60/100 = 0.6
    assertThat(item.getPercentComplete()).isEqualByComparingTo("0.600000");
    // actualAmount = qtyExecutedToDate * actualRate = 60 * 45 = 2700
    assertThat(item.getActualAmount()).isEqualByComparingTo("2700.00");
  }

  @Test
  @DisplayName("treats null approved-sum (should not happen with COALESCE) as zero")
  void nullApprovedSumTreatedAsZero() {
    BoqItem item = boqItem(null);
    when(boqItemRepository.findByIdForUpdate(boqItemId)).thenReturn(Optional.of(item));
    when(dprRepository.sumQtyExecutedByBoqItemIdApproved(projectId, boqItemId)).thenReturn(null);

    boqService.recomputeExecutedQtyApproved(projectId, boqItemId);

    ArgumentCaptor<BoqItem> saved = ArgumentCaptor.forClass(BoqItem.class);
    verify(boqItemRepository).save(saved.capture());
    assertThat(saved.getValue().getQtyExecutedToDate()).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("no-op when boqItemId does not exist")
  void noOpWhenItemNotFound() {
    when(boqItemRepository.findByIdForUpdate(boqItemId)).thenReturn(Optional.empty());

    boqService.recomputeExecutedQtyApproved(projectId, boqItemId);

    verify(boqItemRepository, never()).save(any());
    verify(dprRepository, never()).sumQtyExecutedByBoqItemIdApproved(any(), any());
  }

  @Test
  @DisplayName("no-op when item belongs to a different project (project mismatch)")
  void noOpWhenProjectMismatch() {
    UUID otherProject = UUID.randomUUID();
    BoqItem item = boqItem(null);
    item.setProjectId(otherProject);  // different project
    when(boqItemRepository.findByIdForUpdate(boqItemId)).thenReturn(Optional.of(item));

    boqService.recomputeExecutedQtyApproved(projectId, boqItemId);

    verify(boqItemRepository, never()).save(any());
  }

  @Test
  @DisplayName("manualOverride=TRUE item is still recomputed (existing qty path does not skip manual-override)")
  void manualOverrideItemIsNotSkipped() {
    BoqItem item = boqItem(Boolean.TRUE);
    when(boqItemRepository.findByIdForUpdate(boqItemId)).thenReturn(Optional.of(item));
    when(dprRepository.sumQtyExecutedByBoqItemIdApproved(projectId, boqItemId))
        .thenReturn(new BigDecimal("10"));

    boqService.recomputeExecutedQtyApproved(projectId, boqItemId);

    verify(boqItemRepository).save(item);
    assertThat(item.getQtyExecutedToDate()).isEqualByComparingTo("10");
  }

  @Test
  @DisplayName("grand total caps overall % AND the variance basis (Gate A) — footer = Σ rows")
  void grand_total_caps_overall_percent_and_variance_basis() {
    BoqItem item = BoqItem.builder()
        .projectId(projectId)
        .boqQty(new BigDecimal("100"))
        .boqRate(new BigDecimal("10"))
        .budgetedRate(new BigDecimal("10"))
        .qtyExecutedToDate(new BigDecimal("250"))
        .actualRate(new BigDecimal("11"))
        .build();
    BoqCalculator.recompute(item);
    when(projectRepository.existsById(projectId)).thenReturn(true);
    when(boqItemRepository.findByProjectIdOrderByItemNoAsc(projectId)).thenReturn(List.of(item));

    BoqSummaryResponse r = boqService.getProjectBoqSummary(projectId);

    // overall % = capped earned (min(250,100)×10 = 1000) ÷ budgeted (100×10 = 1000) = 1.0
    assertThat(r.overallPercentComplete()).isEqualByComparingTo("1.000000");
    // Gate A (04 Aug 2026): grand variance basis = the same capped earnedBudget the row uses,
    // so the footer equals the row: 2750 − min(250,100)×10 = 1750 (pre-Gate-A: 250)
    assertThat(r.grandCostVariance()).isEqualByComparingTo("1750.00");
    assertThat(r.grandCostVariance()).isEqualByComparingTo(item.getCostVariance());
  }

  @Test
  @DisplayName("overall % stays capped (≤ 1) when a null-boqQty line has executed qty")
  void overall_percent_stays_capped_with_null_boq_qty_line() {
    // itemA: over-executed normal line: boqQty=100, budgetedRate=10, qtyExecuted=250
    //        budgetedAmount=1000, cappedEarned=min(250,100)×10=1000
    BoqItem itemA = BoqItem.builder()
        .projectId(projectId)
        .boqQty(new BigDecimal("100"))
        .boqRate(new BigDecimal("10"))
        .budgetedRate(new BigDecimal("10"))
        .qtyExecutedToDate(new BigDecimal("250"))
        .actualRate(new BigDecimal("11"))
        .build();
    BoqCalculator.recompute(itemA);

    // itemB: null-boqQty line WITH executed qty — the defect case.
    //        budgetedAmount=0 (zero-BAC), cappedEarned must be 0 (not 50×10=500)
    BoqItem itemB = BoqItem.builder()
        .projectId(projectId)
        .budgetedRate(new BigDecimal("10"))
        .qtyExecutedToDate(new BigDecimal("50"))
        .actualRate(new BigDecimal("10"))
        .build();
    BoqCalculator.recompute(itemB);

    when(projectRepository.existsById(projectId)).thenReturn(true);
    when(boqItemRepository.findByProjectIdOrderByItemNoAsc(projectId)).thenReturn(List.of(itemA, itemB));

    BoqSummaryResponse r = boqService.getProjectBoqSummary(projectId);

    // overall % = cappedEarned(1000 + 0) ÷ budgeted(1000 + 0) = 1.0
    // WITHOUT the fix: cappedEarned for B = 500, overallPct = 1500/1000 = 1.5
    assertThat(r.overallPercentComplete()).isEqualByComparingTo("1.000000");
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private BoqItem boqItem(Boolean manualOverride) {
    BoqItem item = new BoqItem();
    item.setId(boqItemId);
    item.setProjectId(projectId);
    item.setItemNo("1.2.3");
    item.setDescription("Test item");
    item.setUnit("m3");
    item.setBoqQty(new BigDecimal("200"));
    item.setBoqRate(new BigDecimal("100"));
    item.setBudgetedRate(new BigDecimal("90"));
    item.setActualRate(new BigDecimal("95"));
    item.setManualOverride(manualOverride);
    return item;
  }
}
