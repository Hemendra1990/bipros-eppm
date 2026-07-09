package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DprIntelligenceAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    // Fixed run instant so LocalDate/hour maths are deterministic (UTC). today = 2026-06-01.
    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    @Mock
    private DailyProgressReportRepository dprRepository;

    private DprIntelligenceAgent agent() {
        return new DprIntelligenceAgent(dprRepository, new ObjectMapper());
    }

    private static AgentRunContext ctx() {
        return new AgentRunContext(PROJECT, false, "MANUAL", null, true, null, null, NOW);
    }

    private static DailyProgressReport dpr(String idTail, LocalDate reportDate, DprApprovalStatus status,
                                           Instant submittedAt, String supervisor, String activity) {
        DailyProgressReport d = DailyProgressReport.builder()
                .projectId(PROJECT)
                .reportDate(reportDate)
                .approvalStatus(status)
                .submittedAt(submittedAt)
                .supervisorName(supervisor)
                .activityName(activity)
                .build();
        d.setId(UUID.fromString("00000000-0000-0000-0000-0000000000" + idTail));
        return d;
    }

    @Test
    void emitsMissingAndBottleneckFindings() {
        // Latest reported = 2026-05-26 (gap 6 days -> DPR_MISSING MEDIUM).
        // Two SUBMITTED DPRs submitted before the 24h cutoff (2026-05-31T00:00Z) -> APPROVAL_BOTTLENECK,
        // oldest waited 96h -> HIGH.
        List<DailyProgressReport> dprs = List.of(
                dpr("c1", LocalDate.of(2026, 5, 24), DprApprovalStatus.SUBMITTED,
                        Instant.parse("2026-05-29T00:00:00Z"), "Ahmed", "Earthworks"),   // stuck
                dpr("a1", LocalDate.of(2026, 5, 25), DprApprovalStatus.APPROVED, null, "Bilal", "Piling"),
                dpr("b1", LocalDate.of(2026, 5, 25), DprApprovalStatus.SUBMITTED,
                        Instant.parse("2026-05-28T00:00:00Z"), "Carlos", "Formwork"),     // stuck (oldest)
                dpr("d1", LocalDate.of(2026, 5, 26), DprApprovalStatus.SUBMITTED,
                        Instant.parse("2026-05-31T12:00:00Z"), "Dan", "Rebar"));          // NOT stuck (after cutoff)

        when(dprRepository.findByProjectIdOrderByReportDateAscIdAsc(PROJECT)).thenReturn(dprs);

        GatherResult result = agent().gather(ctx());

        List<AgentFindingDraft> c = result.candidates();
        assertThat(c).hasSize(2);
        // Most-severe first: bottleneck (HIGH) before missing (MEDIUM).
        assertThat(c.get(0).findingType()).isEqualTo("APPROVAL_BOTTLENECK");
        assertThat(c.get(0).severity()).isEqualTo(Severity.HIGH);
        assertThat(c.get(0).subjectRef()).isEqualTo("PROJECT");
        assertThat(c.get(0).confidence()).isEqualTo(0.90);
        assertThat(c.get(0).evidence()).anySatisfy(e -> assertThat(e.label()).isEqualTo("DPRs past SLA"));

        assertThat(c.get(1).findingType()).isEqualTo("DPR_MISSING");
        assertThat(c.get(1).severity()).isEqualTo(Severity.MEDIUM);
        assertThat(c.get(1).subjectRef()).isEqualTo("PROJECT");
        assertThat(c.get(1).confidence()).isEqualTo(0.95);
        assertThat(c.get(1).evidence()).anySatisfy(e -> assertThat(e.label()).isEqualTo("Reporting gap"));

        assertThat(result.dataSnapshot().get("submittedReportCount").asInt()).isEqualTo(4);
        assertThat(result.dataSnapshot().get("latestReportDate").asText()).isEqualTo("2026-05-26");
        assertThat(result.dataSnapshot().get("reportGapDays").asInt()).isEqualTo(6);
        assertThat(result.dataSnapshot().get("stuckAwaitingApproval").asInt()).isEqualTo(2);
        assertThat(result.dataSnapshot().get("oldestStuckHours").asLong()).isEqualTo(96L);
    }

    @Test
    void recentReportingAndNoOverdueApprovalsYieldNoFindings() {
        // Latest reported yesterday (gap 1 <= 2) and no SUBMITTED-past-cutoff rows.
        List<DailyProgressReport> dprs = List.of(
                dpr("a1", LocalDate.of(2026, 5, 31), DprApprovalStatus.APPROVED, null, "Bilal", "Piling"));

        when(dprRepository.findByProjectIdOrderByReportDateAscIdAsc(PROJECT)).thenReturn(dprs);

        GatherResult result = agent().gather(ctx());

        assertThat(result.candidates()).isEmpty();
        assertThat(result.dataSnapshot().get("reportGapDays").asInt()).isEqualTo(1);
        assertThat(result.dataSnapshot().get("stuckAwaitingApproval").asInt()).isZero();
    }

    @Test
    void emptyProjectYieldsNoFindings() {
        when(dprRepository.findByProjectIdOrderByReportDateAscIdAsc(PROJECT)).thenReturn(List.of());

        GatherResult result = agent().gather(ctx());

        assertThat(result.candidates()).isEmpty();
        assertThat(result.dataSnapshot().get("submittedReportCount").asInt()).isZero();
        assertThat(result.dataSnapshot().get("latestReportDate").isNull()).isTrue();
    }
}
