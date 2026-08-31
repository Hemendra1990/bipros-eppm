package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.AgentRuntime;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.ai.agent.memory.AgentMemoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutiveInsightsAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Mock
    private AgentMemoryService memory;

    private ExecutiveInsightsAgent agent() throws Exception {
        ExecutiveInsightsAgent a = new ExecutiveInsightsAgent(new ObjectMapper());
        Field f = AbstractAgent.class.getDeclaredField("runtime");
        f.setAccessible(true);
        f.set(a, new AgentRuntime(null, memory, null, null, null, null, new ObjectMapper()));
        return a;
    }

    private static AgentFinding finding(String agentKey, String type, Severity sev, double conf, String title) {
        AgentFinding f = new AgentFinding();
        f.setId(UUID.randomUUID());
        f.setAgentKey(agentKey);
        f.setFindingType(type);
        f.setSeverity(sev);
        f.setConfidence(conf);
        f.setTitle(title);
        return f;
    }

    @Test
    void synthesisesOneBriefFromTopConcerns() throws Exception {
        when(memory.activeFindings(eq(PROJECT), isNull(), eq(Severity.MEDIUM))).thenReturn(List.of(
                finding("risk_intelligence", "RISK_EXPOSURE_SPIKE", Severity.CRITICAL, 0.9, "Risk exposure spiking"),
                finding("forecasting", "COST_AT_COMPLETION", Severity.HIGH, 0.82, "EAC over budget"),
                finding("dpr_intelligence", "APPROVAL_BOTTLENECK", Severity.MEDIUM, 0.7, "DPR approvals lagging"),
                finding("capacity_utilisation", "IDLE_CAPACITY", Severity.LOW, 0.6, "Crane idle")));

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).hasSize(1);
        var brief = result.candidates().get(0);
        assertThat(brief.findingType()).isEqualTo("EXECUTIVE_BRIEF");
        assertThat(brief.subjectRef()).isEqualTo("PROJECT");
        assertThat(brief.severity()).isEqualTo(Severity.CRITICAL);        // anchors on the biggest concern
        assertThat(brief.confidence()).isEqualTo(0.7);                     // min of the top-3 cited
        assertThat(brief.evidence()).hasSize(3);
        assertThat(brief.businessImpact()).contains("Risk exposure spiking");
    }

    @Test
    void noActiveFindingsYieldsNoBrief() throws Exception {
        when(memory.activeFindings(eq(PROJECT), isNull(), eq(Severity.MEDIUM))).thenReturn(List.of());

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void portfolioRunReturnsEmpty() throws Exception {
        GatherResult result = agent().gather(AgentRunContext.manual(null, null));
        assertThat(result.candidates()).isEmpty();
    }
}
