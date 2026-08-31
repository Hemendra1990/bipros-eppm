package com.bipros.project.application.service;

import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.repository.BoqItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoqRebuildServiceTest {

  @Mock BoqItemRepository boqRepo;
  @Mock BoqService boqService;
  @Mock BoqActualCostQuery boqActualCostQuery;

  BoqRebuildService service;

  @BeforeEach
  void setUp() {
    service = new BoqRebuildService(boqRepo, boqService, boqActualCostQuery);
  }

  @Test
  void rebuildsQtyAndActualRateFromScratch() {
    UUID projectId = UUID.randomUUID();
    UUID boqId = UUID.randomUUID();
    BoqItem item = new BoqItem();
    item.setId(boqId); item.setProjectId(projectId); item.setItemNo("2.3.6(i)");
    item.setBoqQty(new BigDecimal("1000")); item.setBoqRate(new BigDecimal("92"));
    item.setBudgetedRate(new BigDecimal("80")); item.setManualOverride(null);

    when(boqRepo.findByProjectId(projectId)).thenReturn(List.of(item));
    // A10: qty is rebuilt by the canonical split-aware roll-up — simulate its write here.
    doAnswer(inv -> { item.setQtyExecutedToDate(new BigDecimal("200")); return null; })
        .when(boqService).recomputeExecutedQtyApproved(projectId, boqId);
    // shared cost query covers MP + EQ + MAT + SC + MCL (2000 = 1500 MP + 500 EQ + 0 MAT)
    when(boqActualCostQuery.sumActualCost(projectId, boqId)).thenReturn(new BigDecimal("2000"));

    int n = service.rebuildFromDprs(projectId);

    assertThat(n).isEqualTo(1);
    assertThat(item.getQtyExecutedToDate()).isEqualByComparingTo("200");
    assertThat(item.getActualRate()).isEqualByComparingTo("10"); // 2000/200
    assertThat(item.getActualAmount()).isEqualByComparingTo("2000"); // recomputed by BoqCalculator
    verify(boqRepo).save(item);
    verify(boqService).recomputeExecutedQtyApproved(projectId, boqId);
    verify(boqActualCostQuery).sumActualCost(projectId, boqId);
  }

  @Test
  void manualOverrideProtectsRateOnly() {
    // A10 reconciliation: quantities are STILL rebuilt for overridden rows; only the
    // manually-set actualRate is preserved (no rate write, no save from the rate branch).
    UUID projectId = UUID.randomUUID();
    UUID boqId = UUID.randomUUID();
    BoqItem item = new BoqItem();
    item.setId(boqId); item.setProjectId(projectId); item.setManualOverride(Boolean.TRUE);
    item.setActualRate(new BigDecimal("123.4567"));
    when(boqRepo.findByProjectId(projectId)).thenReturn(List.of(item));

    int n = service.rebuildFromDprs(projectId);

    assertThat(n).isEqualTo(1);
    verify(boqService).recomputeExecutedQtyApproved(projectId, boqId);   // qty rebuilt
    verify(boqActualCostQuery, never()).sumActualCost(any(), any());     // rate untouched
    verify(boqRepo, never()).save(any());
    assertThat(item.getActualRate()).isEqualByComparingTo("123.4567");
  }
}
