package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.risk.application.service.RiskService;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskRag;
import com.bipros.risk.domain.model.RiskStatus;
import com.bipros.risk.domain.model.RiskTrend;
import com.bipros.risk.domain.repository.RiskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskIntelligenceAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID EMERGING = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID STALE = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final UUID MANAGED = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    private static final UUID CLOSED = UUID.fromString("00000000-0000-0000-0000-0000000000c4");

    private static final Instant NOW = Instant.parse("2026-07-09T00:00:00Z");

    @Mock
    private RiskService riskService;
    @Mock
    private RiskRepository riskRepository;

    private RiskIntelligenceAgent agent() {
        return new RiskIntelligenceAgent(riskService, riskRepository, new ObjectMapper());
    }

    private static Risk risk(UUID id, String code, RiskStatus status, double score, RiskRag rag,
                             RiskTrend trend, String exposure, long ageDays, long staleDays) {
        Risk r = new Risk();
        r.setId(id);
        r.setProjectId(PROJECT);
        r.setCode(code);
        r.setTitle("Risk " + code);
        r.setStatus(status);
        r.setRiskScore(score);
        r.setRag(rag);
        r.setTrend(trend);
        r.setPreResponseExposureCost(exposure == null ? null : new BigDecimal(exposure));
        r.setCreatedAt(NOW.minus(Duration.ofDays(ageDays)));
        r.setUpdatedAt(NOW.minus(Duration.ofDays(staleDays)));
        return r;
    }

    @Test
    void emitsEmergingStaleAndExposureSpikeFindings() {
        // Emerging: assessed 3 days ago, RED score 16, worsening, exposure 1,000,000.
        Risk emerging = risk(EMERGING, "RISK-0001", RiskStatus.IDENTIFIED, 16, RiskRag.RED,
                RiskTrend.WORSENING, "1000000", 3, 3);
        // Stale: open 45 days without a review touch, low score 8 (not severe), exposure 200,000.
        Risk stale = risk(STALE, "RISK-0002", RiskStatus.MITIGATING, 8, RiskRag.AMBER,
                RiskTrend.STABLE, "200000", 120, 45);
        // Actively managed worsening risk: contributes to EMV/spike but neither emerging nor stale.
        Risk managed = risk(MANAGED, "RISK-0003", RiskStatus.OPEN_BEING_MANAGED, 14, RiskRag.RED,
                RiskTrend.WORSENING, "500000", 60, 2);
        // Closed risk: excluded entirely.
        Risk closed = risk(CLOSED, "RISK-0004", RiskStatus.CLOSED, 20, RiskRag.CRIMSON,
                RiskTrend.WORSENING, "9000000", 200, 200);

        when(riskRepository.findByProjectId(PROJECT))
                .thenReturn(List.of(emerging, stale, managed, closed));
        // EMV of the open set: 1,000,000 + 200,000 + 500,000 = 1,700,000.
        when(riskService.calculateRiskExposure(PROJECT)).thenReturn(new BigDecimal("1700000"));

        GatherResult result = agent().gather(ctx());

        List<AgentFindingDraft> c = result.candidates();
        assertThat(c).hasSize(3);

        AgentFindingDraft emergingFinding = byType(c, "EMERGING_RISK");
        assertThat(emergingFinding.subjectRef()).isEqualTo("risk:" + EMERGING);
        assertThat(emergingFinding.severity()).isEqualTo(Severity.HIGH); // score 16 → HIGH (>=12, <20)
        assertThat(emergingFinding.confidence()).isBetween(0.0, 1.0);
        assertThat(emergingFinding.evidence())
                .anySatisfy(e -> assertThat(e.label()).isEqualTo("Risk score"));

        AgentFindingDraft staleFinding = byType(c, "STALE_RISK_REVIEW");
        assertThat(staleFinding.subjectRef()).isEqualTo("risk:" + STALE);
        assertThat(staleFinding.severity()).isEqualTo(Severity.LOW); // 45d, not severe → LOW

        AgentFindingDraft spike = byType(c, "RISK_EXPOSURE_SPIKE");
        assertThat(spike.subjectRef()).isEqualTo("PROJECT");
        assertThat(spike.severity()).isEqualTo(Severity.HIGH); // worseningCount 2 → HIGH

        // Snapshot reflects the open (non-closed) set: 3 risks, 2 worsening.
        assertThat(result.dataSnapshot().get("openRiskCount").asInt()).isEqualTo(3);
        assertThat(result.dataSnapshot().get("worseningCount").asInt()).isEqualTo(2);
        assertThat(result.dataSnapshot().get("emv").asDouble()).isEqualTo(1700000.0);

        // Ordered most-severe first (both HIGH findings before the LOW stale one).
        assertThat(c.get(c.size() - 1).findingType()).isEqualTo("STALE_RISK_REVIEW");
    }

    @Test
    void escalatesLongStaleHighScoreRiskToCritical() {
        Risk stale = risk(STALE, "RISK-0009", RiskStatus.OPEN_ESCALATED, 20, RiskRag.CRIMSON,
                RiskTrend.STABLE, "800000", 200, 120); // 120d stale, severe → CRITICAL
        when(riskRepository.findByProjectId(PROJECT)).thenReturn(List.of(stale));
        when(riskService.calculateRiskExposure(PROJECT)).thenReturn(new BigDecimal("800000"));

        GatherResult result = agent().gather(ctx());

        AgentFindingDraft staleFinding = byType(result.candidates(), "STALE_RISK_REVIEW");
        assertThat(staleFinding.severity()).isEqualTo(Severity.CRITICAL);
        // No worsening risk → no exposure-spike finding.
        assertThat(result.candidates()).noneMatch(f -> f.findingType().equals("RISK_EXPOSURE_SPIKE"));
    }

    @Test
    void noRisksYieldsNoFindings() {
        when(riskRepository.findByProjectId(PROJECT)).thenReturn(List.of());
        when(riskService.calculateRiskExposure(PROJECT)).thenReturn(BigDecimal.ZERO);

        GatherResult result = agent().gather(ctx());

        assertThat(result.candidates()).isEmpty();
        assertThat(result.dataSnapshot().get("openRiskCount").asInt()).isZero();
    }

    private static AgentRunContext ctx() {
        return new AgentRunContext(PROJECT, false, "MANUAL", null, true, null, null, NOW);
    }

    private static AgentFindingDraft byType(List<AgentFindingDraft> findings, String type) {
        Optional<AgentFindingDraft> match = findings.stream()
                .filter(f -> f.findingType().equals(type))
                .findFirst();
        assertThat(match).as("finding of type %s", type).isPresent();
        return match.get();
    }
}
