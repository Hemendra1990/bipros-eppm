package com.bipros.cost.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.cost.application.dto.WbsEvmRow;
import com.bipros.project.application.service.DprActualCostLookup;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Fix 3 (Baseline Variance Correctness): {@link CostService#getEvmByWbs} must regroup BOQ budget
 * by the EXECUTING ACTIVITY's WBS node (via APPROVED DPR links), not {@code BoqItem.wbsNodeId}
 * (frequently null in real data), so it uses the same key the actual-cost side already groups by
 * — without ever changing the totals (only redistributing them across buckets).
 */
@ExtendWith(MockitoExtension.class)
class CostServiceEvmByWbsRegroupTest {

    @Mock ProjectRepository projectRepository;
    @Mock BoqItemRepository boqItemRepository;
    @Mock WbsNodeRepository wbsNodeRepository;
    @Mock ActivityRepository activityRepository;
    @Mock DprActualCostLookup dprActualCostLookup;
    @Mock DailyProgressReportRepository dailyProgressReportRepository;

    @InjectMocks CostService costService;

    @Test
    void boqItemWithNullWbs_regroupsOntoExecutingActivitysWbs_andPreservesTotals() {
        UUID projectId = UUID.randomUUID();
        UUID nodeXId = UUID.randomUUID();
        UUID act1Id = UUID.randomUUID();   // executes boqA, lives on node X
        UUID act2Id = UUID.randomUUID();   // no BOQ link, no WBS — contributes AC to "(Unmapped)"
        UUID boqAId = UUID.randomUUID();

        Project project = new Project();
        project.setId(projectId);

        WbsNode nodeX = new WbsNode();
        nodeX.setId(nodeXId);
        nodeX.setProjectId(projectId);
        nodeX.setParentId(null);
        nodeX.setCode("X");
        nodeX.setName("Node X");

        Activity act1 = new Activity();
        act1.setId(act1Id);
        act1.setProjectId(projectId);
        act1.setWbsNodeId(nodeXId);

        Activity act2 = new Activity();
        act2.setId(act2Id);
        act2.setProjectId(projectId);
        act2.setWbsNodeId(null);

        // BOQ item has NO wbsNodeId of its own — the only link to a WBS is via the executing
        // activity found through its APPROVED DPRs.
        BoqItem boqA = BoqItem.builder()
                .projectId(projectId)
                .itemNo("1")
                .description("Test item")
                .unit("m3")
                .wbsNodeId(null)
                .boqQty(new BigDecimal("10"))
                .budgetedRate(new BigDecimal("100"))
                .budgetedAmount(new BigDecimal("1000"))
                .qtyExecutedToDate(new BigDecimal("5"))
                .build();
        boqA.setId(boqAId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(boqItemRepository.sumBudgetedAmount(projectId)).thenReturn(new BigDecimal("1000"));
        when(boqItemRepository.findByProjectId(projectId)).thenReturn(List.of(boqA));
        when(wbsNodeRepository.findByProjectId(projectId)).thenReturn(List.of(nodeX));
        when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(act1, act2));
        when(dprActualCostLookup.sumByActivity(projectId))
                .thenReturn(Map.of(act2Id, new BigDecimal("250")));
        when(dailyProgressReportRepository.boqItemExecutingActivities(projectId))
                .thenReturn(List.<Object[]>of(new Object[]{boqAId, act1Id, new BigDecimal("5")}));

        List<WbsEvmRow> rows = costService.getEvmByWbs(projectId);

        WbsEvmRow nodeXRow = rows.stream().filter(r -> "X".equals(r.code())).findFirst()
                .orElseThrow(() -> new AssertionError("expected a row for node X, got: " + rows));
        WbsEvmRow unmappedRow = rows.stream().filter(r -> "(Unmapped)".equals(r.code())).findFirst()
                .orElseThrow(() -> new AssertionError("expected an (Unmapped) row, got: " + rows));

        // (1) The BOQ item's budget lands on node X (via its executing activity), not "(Unmapped)".
        assertThat(nodeXRow.bac()).isEqualByComparingTo("1000");
        assertThat(unmappedRow.bac()).isEqualByComparingTo("0");

        // (2) Totals invariant: Σ over all group rows' budget == Σ BoqItem.budgetedAmount.
        BigDecimal totalBac = rows.stream().map(WbsEvmRow::bac).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalBac).isEqualByComparingTo("1000");
    }
}
