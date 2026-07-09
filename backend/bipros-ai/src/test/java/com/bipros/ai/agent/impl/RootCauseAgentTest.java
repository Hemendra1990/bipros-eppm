package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RootCauseAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000c5");

    @Mock private DailyProgressReportRepository dprRepository;

    private RootCauseAgent agent() {
        return new RootCauseAgent(dprRepository, new ObjectMapper());
    }

    private static DailyProgressReport dpr(LocalDate date, String delayReason) {
        return DailyProgressReport.builder()
                .projectId(PROJECT).reportDate(date).supervisorName("Sup")
                .activityName("Earthwork").unit("m3").qtyExecuted(BigDecimal.ZERO)
                .delayReason(delayReason).approvalStatus(DprApprovalStatus.APPROVED)
                .build();
    }

    @Test
    void categorisesDelayReasonsAndFlagsRecurring() {
        List<DailyProgressReport> dprs = new ArrayList<>();
        for (int i = 0; i < 6; i++) dprs.add(dpr(LocalDate.of(2026, 6, 1).plusDays(i), "cement shortage on site"));
        dprs.add(dpr(LocalDate.of(2026, 6, 8), "excavator breakdown"));
        dprs.add(dpr(LocalDate.of(2026, 6, 9), "plant repair delayed work"));
        dprs.add(dpr(LocalDate.of(2026, 6, 10), "heavy rain stopped concreting"));
        dprs.add(dpr(LocalDate.of(2026, 6, 11), "all good, no issue"));  // uncategorisable
        when(dprRepository.findByProjectIdOrderByReportDateAscIdAsc(PROJECT)).thenReturn(dprs);

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));
        List<AgentFindingDraft> c = result.candidates();

        assertThat(c).extracting(AgentFindingDraft::findingType)
                .contains("DELAY_ROOT_CAUSE", "RECURRING_DELAY_CAUSE");
        // Material shortage (6) is the dominant cause and recurs across 6 days.
        AgentFindingDraft rec = c.stream()
                .filter(f -> f.findingType().equals("RECURRING_DELAY_CAUSE")).findFirst().orElseThrow();
        assertThat(rec.title()).contains("Material shortage");
        assertThat(result.dataSnapshot().get("distinctCauses").asInt()).isEqualTo(3); // material, equipment, weather
    }

    @Test
    void dormantWhenNoDelayReasons() {
        List<DailyProgressReport> dprs = List.of(
                dpr(LocalDate.of(2026, 6, 1), null),
                dpr(LocalDate.of(2026, 6, 2), "   "));
        when(dprRepository.findByProjectIdOrderByReportDateAscIdAsc(PROJECT)).thenReturn(dprs);

        assertThat(agent().gather(AgentRunContext.manual(PROJECT, null)).candidates()).isEmpty();
    }
}
