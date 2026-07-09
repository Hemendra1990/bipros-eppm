package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.resource.application.dto.UtilizationProfileEntry;
import com.bipros.resource.application.service.ResourceLevelingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapacityUtilisationAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID OVER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID IDLE = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @Mock
    private ResourceLevelingService resourceLevelingService;

    private CapacityUtilisationAgent agent() {
        return new CapacityUtilisationAgent(resourceLevelingService, new ObjectMapper());
    }

    private static List<UtilizationProfileEntry> profile() {
        List<UtilizationProfileEntry> entries = new ArrayList<>();
        LocalDate d = LocalDate.of(2026, 1, 1);
        // Over-allocated crane: peaks at 1.4, over 100% on 4 of 12 days.
        double[] over = {1.4, 1.3, 1.1, 1.05, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2};
        for (int i = 0; i < over.length; i++) {
            entries.add(new UtilizationProfileEntry(d.plusDays(i), OVER, "Tower Crane", over[i] * 8, 8.0, over[i]));
        }
        // Idle excavator: avg 0.2 over 12 days.
        for (int i = 0; i < 12; i++) {
            entries.add(new UtilizationProfileEntry(d.plusDays(i), IDLE, "Excavator", 0.2 * 8, 8.0, 0.2));
        }
        return entries;
    }

    @Test
    void emitsOverallocationAndIdleFindings() {
        when(resourceLevelingService.getUtilizationProfile(PROJECT)).thenReturn(profile());

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.dataSnapshot().size()).isEqualTo(2);
        List<AgentFindingDraft> c = result.candidates();
        assertThat(c).hasSize(2);
        // Most-severe first.
        assertThat(c.get(0).findingType()).isEqualTo("RESOURCE_OVERALLOCATION");
        assertThat(c.get(0).severity()).isEqualTo(Severity.HIGH);   // peak 1.4 → HIGH (>=1.2, <1.5)
        assertThat(c.get(0).subjectRef()).isEqualTo("resource:" + OVER);
        assertThat(c.get(0).evidence()).anySatisfy(e -> assertThat(e.label()).isEqualTo("Peak utilisation"));

        assertThat(c.get(1).findingType()).isEqualTo("IDLE_CAPACITY");
        assertThat(c.get(1).severity()).isEqualTo(Severity.LOW);
        assertThat(c.get(1).confidence()).isBetween(0.0, 1.0);
    }

    @Test
    void noProfileYieldsNoFindings() {
        when(resourceLevelingService.getUtilizationProfile(PROJECT)).thenReturn(List.of());

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.dataSnapshot().size()).isZero();
    }
}
