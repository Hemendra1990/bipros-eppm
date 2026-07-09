package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.resource.domain.service.ProductivityNormLookupService;
import com.bipros.resource.domain.service.ResolvedNorm;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductivityAnalysisAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000c6");

    @Mock private DailyProgressReportRepository dprRepository;
    @Mock private ProductivityNormLookupService normLookup;

    private ProductivityAnalysisAgent agent() {
        return new ProductivityAnalysisAgent(dprRepository, normLookup, new ObjectMapper());
    }

    private static DailyProgressReport dpr(String activity, String unit, LocalDate date, double qty) {
        return DailyProgressReport.builder()
                .projectId(PROJECT).reportDate(date).supervisorName("Sup")
                .activityName(activity).unit(unit).qtyExecuted(BigDecimal.valueOf(qty))
                .approvalStatus(DprApprovalStatus.APPROVED)
                .build();
    }

    private static ResolvedNorm norm(double outputPerDay, String unit) {
        return new ResolvedNorm(BigDecimal.valueOf(outputPerDay), unit,
                ResolvedNorm.Source.UNSCOPED, UUID.randomUUID(), null, null);
    }

    @Test
    void flagsActivitiesBelowNorm() {
        List<DailyProgressReport> dprs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            dprs.add(dpr("Concrete", "m3", LocalDate.of(2026, 6, 1).plusDays(i), 50)); // 50/day
            dprs.add(dpr("Paving", "m2", LocalDate.of(2026, 6, 1).plusDays(i), 100));  // 100/day
        }
        when(dprRepository.findByProjectIdOrderByReportDateAscIdAsc(PROJECT)).thenReturn(dprs);
        when(normLookup.resolveByName(anyString(), any())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            return "Concrete".equals(name) ? norm(100, "m3")  // actual 50 vs 100 → 50% (below)
                    : norm(90, "m2");                          // actual 100 vs 90 → above
        });

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        AgentFindingDraft f = result.candidates().stream()
                .filter(x -> x.findingType().equals("PRODUCTIVITY_BELOW_NORM")).findFirst().orElseThrow();
        assertThat(f.severity()).isEqualTo(Severity.HIGH);   // worst ratio 0.5 < 0.5? equal → HIGH via share 1/2
        assertThat(f.title()).contains("below their productivity norm");
        assertThat(result.dataSnapshot().get("belowNorm").asInt()).isEqualTo(1);
        assertThat(result.dataSnapshot().get("atOrAboveNorm").asInt()).isEqualTo(1);
    }

    @Test
    void dormantWhenNoNormResolves() {
        List<DailyProgressReport> dprs = new ArrayList<>();
        for (int i = 0; i < 5; i++) dprs.add(dpr("Concrete", "m3", LocalDate.of(2026, 6, 1).plusDays(i), 10));
        when(dprRepository.findByProjectIdOrderByReportDateAscIdAsc(PROJECT)).thenReturn(dprs);
        when(normLookup.resolveByName(anyString(), any()))
                .thenReturn(ResolvedNorm.none(null, null)); // no norm master

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.dataSnapshot().get("activitiesWithNorm").asInt()).isZero();
    }
}
