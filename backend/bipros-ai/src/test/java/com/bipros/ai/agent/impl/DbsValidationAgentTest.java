package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.dbs.domain.model.DbsDailyProject;
import com.bipros.dbs.domain.repository.DbsDailyProjectRepository;
import com.bipros.dbs.service.DbsAlertEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DbsValidationAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final LocalDate D0 = LocalDate.of(2026, 3, 1);

    @Mock
    private DbsDailyProjectRepository projectRepo;

    private DbsValidationAgent agent() {
        return new DbsValidationAgent(projectRepo, new DbsAlertEvaluator(), new ObjectMapper());
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    private static DbsDailyProject row(LocalDate date, double income, double expense,
                                       double manpower, double machinery) {
        double contribution = income - expense;
        double pct = income > 0 ? contribution / income : 0.0;
        return DbsDailyProject.builder()
                .projectId(PROJECT)
                .reportDate(date)
                .totalIncome(bd(income))
                .totalExpense(bd(expense))
                .manpowerAmount(bd(manpower))
                .machineryAmount(bd(machinery))
                .contribution(bd(contribution))
                .contributionPct(bd(pct))
                .build();
    }

    @Test
    void emitsNegativeContributionFinding() {
        // income 1000, expense 1050 → contribution -50, margin -5% (> -10% → HIGH)
        when(projectRepo.findByProjectIdAndReportDateBetween(eq(PROJECT), any(), any()))
                .thenReturn(List.of(row(D0, 1000, 1050, 700, 350)));

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        List<AgentFindingDraft> c = result.candidates();
        assertThat(c).hasSize(1);
        assertThat(c.get(0).findingType()).isEqualTo("NEGATIVE_CONTRIBUTION");
        assertThat(c.get(0).severity()).isEqualTo(Severity.HIGH);
        assertThat(c.get(0).subjectRef()).isEqualTo("PROJECT");
        assertThat(c.get(0).evidence()).anySatisfy(e -> assertThat(e.label()).isEqualTo("Contribution"));
        assertThat(result.dataSnapshot().get("alerts").toString())
                .contains(DbsAlertEvaluator.NEGATIVE_CONTRIBUTION);
    }

    @Test
    void emitsDataQualityGapFinding() {
        // income booked but manpower & machinery both zero → MISSING_RATE_DATA
        when(projectRepo.findByProjectIdAndReportDateBetween(eq(PROJECT), any(), any()))
                .thenReturn(List.of(row(D0, 1000, 0, 0, 0)));

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        List<AgentFindingDraft> c = result.candidates();
        assertThat(c).hasSize(1);
        assertThat(c.get(0).findingType()).isEqualTo("DATA_QUALITY_GAP");
        assertThat(c.get(0).severity()).isEqualTo(Severity.MEDIUM);
        assertThat(c.get(0).subjectRef()).isEqualTo("PROJECT");
    }

    @Test
    void emitsMarginDeteriorationFinding() {
        // Four revenue days, margin sliding 20% → 5% (drop 15 pts ≥ 10 → HIGH).
        // manpower/machinery nonzero (no data gap), contribution positive (no negative alert).
        List<DbsDailyProject> rows = List.of(
                row(D0.plusDays(0), 1000, 800, 500, 200),   // margin 20%
                row(D0.plusDays(1), 1000, 850, 500, 200),   // margin 15%
                row(D0.plusDays(2), 1000, 900, 500, 200),   // margin 10%
                row(D0.plusDays(3), 1000, 950, 500, 200));  // margin  5%
        when(projectRepo.findByProjectIdAndReportDateBetween(eq(PROJECT), any(), any()))
                .thenReturn(rows);

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        List<AgentFindingDraft> c = result.candidates();
        assertThat(c).hasSize(1);
        assertThat(c.get(0).findingType()).isEqualTo("MARGIN_DETERIORATION");
        assertThat(c.get(0).severity()).isEqualTo(Severity.HIGH);
        assertThat(c.get(0).confidence()).isBetween(0.0, 1.0);
        assertThat(result.dataSnapshot().get("trendDrop").asDouble()).isEqualTo(0.15);
    }

    @Test
    void noRowsYieldsNoFindings() {
        when(projectRepo.findByProjectIdAndReportDateBetween(eq(PROJECT), any(), any()))
                .thenReturn(List.of());

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.dataSnapshot().size()).isZero();
    }
}
