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
class RoleBriefingsAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000b9");

    @Mock private AgentMemoryService memory;

    private RoleBriefingsAgent agent() throws Exception {
        RoleBriefingsAgent a = new RoleBriefingsAgent(new ObjectMapper());
        Field f = AbstractAgent.class.getDeclaredField("runtime");
        f.setAccessible(true);
        f.set(a, new AgentRuntime(null, memory, null, null, null, null, new ObjectMapper()));
        return a;
    }

    private static AgentFinding finding(String agentKey, String type, Severity sev, String title, String action) {
        AgentFinding f = new AgentFinding();
        f.setId(UUID.randomUUID());
        f.setAgentKey(agentKey);
        f.setFindingType(type);
        f.setSeverity(sev);
        f.setConfidence(0.85);
        f.setTitle(title);
        f.setRecommendedAction(action);
        return f;
    }

    @Test
    void producesRoleTailoredBriefs() throws Exception {
        when(memory.activeFindings(eq(PROJECT), isNull(), eq(Severity.LOW))).thenReturn(List.of(
                finding("risk_intelligence", "RISK_EXPOSURE_SPIKE", Severity.CRITICAL, "Risk spike", "Escalate risk"),
                finding("progress_variance", "SCHEDULE_PROGRESS_VARIANCE", Severity.HIGH, "Behind schedule", "Recover fronts"),
                finding("dpr_anomaly", "DPR_PRODUCTIVITY_DROP", Severity.HIGH, "Productivity drop", "Check crews"),
                finding("field_utilisation", "HIGH_IDLE_TIME", Severity.MEDIUM, "Equipment idle", "Demobilise plant")));

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).extracting(f -> f.findingType())
                .contains("SUPERVISOR_DAILY_BRIEF", "PLANNING_BRIEF", "PM_ACTION_BRIEF");
        var pm = result.candidates().stream()
                .filter(f -> f.findingType().equals("PM_ACTION_BRIEF")).findFirst().orElseThrow();
        assertThat(pm.severity()).isEqualTo(Severity.CRITICAL); // mirrors worst critical/high item
        assertThat(pm.recommendedAction()).contains("1)");       // numbered priority list
    }

    @Test
    void dormantWhenNoActiveFindings() throws Exception {
        when(memory.activeFindings(eq(PROJECT), isNull(), eq(Severity.LOW))).thenReturn(List.of());
        assertThat(agent().gather(AgentRunContext.manual(PROJECT, null)).candidates()).isEmpty();
    }
}
