package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.AgentRuntime;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.ai.agent.domain.AgentFindingRepository;
import com.bipros.ai.agent.domain.FindingStatus;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalLearningAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000ba");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Mock private AgentMemoryService memory;
    @Mock private AgentFindingRepository findingRepository;

    private HistoricalLearningAgent agent() throws Exception {
        HistoricalLearningAgent a = new HistoricalLearningAgent(findingRepository, new ObjectMapper());
        Field f = AbstractAgent.class.getDeclaredField("runtime");
        f.setAccessible(true);
        f.set(a, new AgentRuntime(null, memory, null, null, null, null, new ObjectMapper()));
        return a;
    }

    private static AgentFinding current(String type) {
        AgentFinding f = new AgentFinding();
        f.setId(UUID.randomUUID());
        f.setAgentKey("risk_intelligence");
        f.setFindingType(type);
        f.setSeverity(Severity.HIGH);
        f.setConfidence(0.9);
        f.setTitle(type);
        return f;
    }

    private static AgentFinding resolvedOnOther(String type, String action) {
        AgentFinding f = new AgentFinding();
        f.setId(UUID.randomUUID());
        f.setProjectId(OTHER);
        f.setFindingType(type);
        f.setStatus(FindingStatus.RESOLVED_BY_USER);
        f.setConfidence(0.88);
        f.setRecommendedAction(action);
        return f;
    }

    @Test
    void surfacesCrossProjectPrecedent() throws Exception {
        when(memory.activeFindings(eq(PROJECT), isNull(), eq(Severity.HIGH)))
                .thenReturn(List.of(current("RISK_EXPOSURE_SPIKE")));
        when(findingRepository.findByFindingTypeAndStatusAndProjectIdNot(
                eq("RISK_EXPOSURE_SPIKE"), eq(FindingStatus.RESOLVED_BY_USER), eq(PROJECT)))
                .thenReturn(List.of(resolvedOnOther("RISK_EXPOSURE_SPIKE", "Rebalanced the risk register and added mitigations.")));

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).extracting(f -> f.findingType()).contains("HISTORICAL_PRECEDENT");
        assertThat(result.dataSnapshot().get("typesWithPrecedent").asInt()).isEqualTo(1);
    }

    @Test
    void dormantWhenNoPrecedent() throws Exception {
        when(memory.activeFindings(eq(PROJECT), isNull(), eq(Severity.HIGH)))
                .thenReturn(List.of(current("WEATHER_HEAT_RISK")));
        when(findingRepository.findByFindingTypeAndStatusAndProjectIdNot(any(), any(), any()))
                .thenReturn(List.of());

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.dataSnapshot().get("typesWithPrecedent").asInt()).isZero();
    }
}
