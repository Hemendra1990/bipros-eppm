package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.support.CapacityUtilizationProvider;
import com.bipros.ai.agent.support.CapacityUtilizationProvider.ActivityEfficiency;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductivityAnalysisAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000c6");
    private static final UUID WA_BELOW = UUID.fromString("00000000-0000-0000-0000-00000000b101");
    private static final UUID WA_OK = UUID.fromString("00000000-0000-0000-0000-00000000b102");

    @Mock private CapacityUtilizationProvider capacityProvider;

    private ProductivityAnalysisAgent agent() {
        return new ProductivityAnalysisAgent(Optional.of(capacityProvider), new ObjectMapper());
    }

    @Test
    void flagsActivitiesBelowNorm() {
        when(capacityProvider.cumulativeByActivity(PROJECT)).thenReturn(List.of(
                new ActivityEfficiency(WA_BELOW, "Concrete", "MANPOWER", 25.0, 50.0, 50.0),  // 50% → below
                new ActivityEfficiency(WA_OK, "Paving", "MANPOWER", 47.5, 50.0, 95.0)));      // 95% → above

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        AgentFindingDraft f = result.candidates().stream()
                .filter(x -> x.findingType().equals("PRODUCTIVITY_BELOW_NORM")).findFirst().orElseThrow();
        assertThat(f.title()).contains("below their productivity norm");
        assertThat(result.dataSnapshot().get("belowNorm").asInt()).isEqualTo(1);
        assertThat(result.dataSnapshot().get("atOrAboveNorm").asInt()).isEqualTo(1);
    }

    @Test
    void ignoresSidesWithTooFewTrackedDays() {
        when(capacityProvider.cumulativeByActivity(PROJECT)).thenReturn(List.of(
                new ActivityEfficiency(WA_BELOW, "Concrete", "MANPOWER", 1.0, 2.0, 40.0))); // 40% but only 2 days

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.dataSnapshot().get("activitiesWithNorm").asInt()).isZero();
    }
}
