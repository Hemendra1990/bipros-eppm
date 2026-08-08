package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.model.DprEquipment;
import com.bipros.project.domain.model.DprManpower;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.DprEquipmentRepository;
import com.bipros.project.domain.repository.DprManpowerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DprAnomalyAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000da");
    private static final LocalDate BASE = LocalDate.of(2026, 6, 1);

    @Mock private DailyProgressReportRepository dprRepository;
    @Mock private DprManpowerRepository manpowerRepository;
    @Mock private DprEquipmentRepository equipmentRepository;
    @Mock private com.bipros.ai.agent.notify.StakeholderResolver stakeholderResolver;

    private DprAnomalyAgent agent() {
        org.mockito.Mockito.lenient()
                .when(stakeholderResolver.pmPlusManagersOf(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Map.of("PROJECT_MANAGER", java.util.List.of()));
        return new DprAnomalyAgent(dprRepository, manpowerRepository, equipmentRepository,
                new ObjectMapper(), stakeholderResolver);
    }

    private static DailyProgressReport dpr(UUID id, UUID activityId, String activityName, LocalDate date,
                                           UUID supervisor, double qty) {
        DailyProgressReport d = DailyProgressReport.builder()
                .projectId(PROJECT)
                .activityId(activityId).activityName(activityName)
                .reportDate(date).supervisorName("Sup").supervisorUserId(supervisor)
                .unit("m3").qtyExecuted(BigDecimal.valueOf(qty))
                .approvalStatus(DprApprovalStatus.APPROVED)
                .build();
        d.setId(id);
        return d;
    }

    private static DprManpower manpower(UUID dprId, int nos, double hours) {
        return DprManpower.builder().dprId(dprId).nos(nos)
                .workingHours(BigDecimal.valueOf(hours)).otHours(BigDecimal.ZERO).build();
    }

    private static DprEquipment equipment(UUID dprId, double hours) {
        return DprEquipment.builder().dprId(dprId).workingHours(BigDecimal.valueOf(hours)).build();
    }

    @Test
    void detectsHighInputZeroOutputDuplicatesAndInconsistency() {
        UUID actA = UUID.randomUUID(), actB = UUID.randomUUID(), actC = UUID.randomUUID();
        UUID d1 = UUID.randomUUID(), d2 = UUID.randomUUID(), d3 = UUID.randomUUID();
        UUID d4a = UUID.randomUUID(), d4b = UUID.randomUUID();
        UUID dupSup = UUID.randomUUID();

        List<DailyProgressReport> dprs = new ArrayList<>();
        dprs.add(dpr(d1, actA, "Earthwork", BASE, UUID.randomUUID(), 0));            // high labour, 0 output
        dprs.add(dpr(d2, actB, "Paving", BASE.plusDays(1), UUID.randomUUID(), 0));   // high equipment, 0 output
        DailyProgressReport neg = dpr(d3, actB, "Paving", BASE.plusDays(2), UUID.randomUUID(), 0);
        neg.setQtyExecuted(BigDecimal.valueOf(-5));                                  // negative qty
        dprs.add(neg);
        dprs.add(dpr(d4a, actC, "Culvert", BASE.plusDays(3), dupSup, 12));           // duplicate pair
        dprs.add(dpr(d4b, actC, "Culvert", BASE.plusDays(3), dupSup, 12));

        when(dprRepository.findByProjectIdOrderByReportDateAscIdAsc(PROJECT)).thenReturn(dprs);
        when(manpowerRepository.findByDprIdIn(any())).thenReturn(List.of(manpower(d1, 10, 80)));
        when(equipmentRepository.findByDprIdIn(any())).thenReturn(List.of(equipment(d2, 20)));

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));
        List<AgentFindingDraft> c = result.candidates();

        assertThat(c).extracting(AgentFindingDraft::findingType).contains(
                "DPR_NO_PROGRESS_HIGH_LABOUR",
                "DPR_LOW_OUTPUT_HIGH_EQUIPMENT",
                "DPR_DUPLICATE_ENTRY",
                "DPR_DATA_INCONSISTENCY");
        assertThat(result.dataSnapshot().get("duplicateGroups").asInt()).isEqualTo(1);
        assertThat(result.dataSnapshot().get("dataInconsistencies").asInt()).isEqualTo(1);
    }

    @Test
    void detectsProductivityDrop() {
        UUID act = UUID.randomUUID();
        List<DailyProgressReport> dprs = new ArrayList<>();
        List<DprManpower> lines = new ArrayList<>();
        // 5 steady days at 10 units/hr, then a collapse to 1 unit/hr.
        for (int i = 0; i < 5; i++) {
            UUID id = UUID.randomUUID();
            dprs.add(dpr(id, act, "Concrete", BASE.plusDays(i), UUID.randomUUID(), 100));
            lines.add(manpower(id, 5, 10)); // 100/10 = 10
        }
        UUID last = UUID.randomUUID();
        dprs.add(dpr(last, act, "Concrete", BASE.plusDays(5), UUID.randomUUID(), 10));
        lines.add(manpower(last, 5, 10)); // 10/10 = 1  → 90% drop

        when(dprRepository.findByProjectIdOrderByReportDateAscIdAsc(PROJECT)).thenReturn(dprs);
        when(manpowerRepository.findByDprIdIn(any())).thenReturn(lines);
        lenient().when(equipmentRepository.findByDprIdIn(any())).thenReturn(List.of());

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).extracting(AgentFindingDraft::findingType)
                .contains("DPR_PRODUCTIVITY_DROP");
        assertThat(result.dataSnapshot().get("productivityDrops").asInt()).isEqualTo(1);
    }

    @Test
    void cleanDataYieldsNoFindings() {
        UUID act = UUID.randomUUID();
        List<DailyProgressReport> dprs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            dprs.add(dpr(UUID.randomUUID(), act, "Concrete", BASE.plusDays(i), UUID.randomUUID(), 50));
        }
        when(dprRepository.findByProjectIdOrderByReportDateAscIdAsc(PROJECT)).thenReturn(dprs);
        lenient().when(manpowerRepository.findByDprIdIn(any())).thenReturn(List.of());
        lenient().when(equipmentRepository.findByDprIdIn(any())).thenReturn(List.of());

        assertThat(agent().gather(AgentRunContext.manual(PROJECT, null)).candidates()).isEmpty();
    }
}
