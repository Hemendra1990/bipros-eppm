package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.gis.application.dto.ProgressVarianceResponse;
import com.bipros.gis.application.service.ConstructionProgressService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GisIntelligenceAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID OVER_CLAIM = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID BEHIND = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final UUID ON_TRACK = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    private static final UUID NO_DATA = UUID.fromString("00000000-0000-0000-0000-0000000000c4");

    @Mock
    private ConstructionProgressService constructionProgressService;

    private GisIntelligenceAgent agent() {
        return new GisIntelligenceAgent(constructionProgressService, new ObjectMapper());
    }

    private static List<ProgressVarianceResponse> variances() {
        return List.of(
                // Over-claim: contractor claims 80% but satellite sees 45% (variance -35) -> MISMATCH / CRITICAL.
                new ProgressVarianceResponse(OVER_CLAIM, "S-01", "Northbound carriageway",
                        45.0, 80.0, -35.0, "AHEAD"),
                // Under-report: satellite sees 90% but contractor claims 60% (variance +30) -> STRETCH_BEHIND / HIGH.
                new ProgressVarianceResponse(BEHIND, "S-02", "Southbound carriageway",
                        90.0, 60.0, 30.0, "BEHIND"),
                // On track: 2pp gap -> no finding, but still snapshotted.
                new ProgressVarianceResponse(ON_TRACK, "S-03", "Bridge deck",
                        52.0, 50.0, 2.0, "ON_TRACK"),
                // No data: null progress -> skipped entirely.
                new ProgressVarianceResponse(NO_DATA, "S-04", "Culvert", null, null, null, "NO_DATA"));
    }

    @Test
    void emitsMismatchAndStretchBehindFindings() {
        when(constructionProgressService.getProgressVariance(PROJECT)).thenReturn(variances());

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        // Only the three rows with a real variance are snapshotted (NO_DATA excluded).
        assertThat(result.dataSnapshot().size()).isEqualTo(3);

        List<AgentFindingDraft> c = result.candidates();
        assertThat(c).hasSize(2);

        // Most-severe first: over-claim (35pp) is CRITICAL, behind (30pp) is HIGH.
        assertThat(c.get(0).findingType()).isEqualTo("FIELD_PROGRESS_MISMATCH");
        assertThat(c.get(0).severity()).isEqualTo(Severity.CRITICAL);
        assertThat(c.get(0).subjectRef()).isEqualTo("stretch:" + OVER_CLAIM);
        assertThat(c.get(0).confidence()).isBetween(0.0, 1.0);
        assertThat(c.get(0).evidence())
                .anySatisfy(e -> assertThat(e.label()).isEqualTo("AI-derived progress"));
        assertThat(c.get(0).evidence())
                .anySatisfy(e -> assertThat(e.entityId()).isEqualTo(OVER_CLAIM));

        assertThat(c.get(1).findingType()).isEqualTo("STRETCH_BEHIND");
        assertThat(c.get(1).severity()).isEqualTo(Severity.HIGH);
        assertThat(c.get(1).subjectRef()).isEqualTo("stretch:" + BEHIND);
    }

    @Test
    void noVarianceDataYieldsNoFindings() {
        when(constructionProgressService.getProgressVariance(PROJECT)).thenReturn(List.of());

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.dataSnapshot().size()).isZero();
    }

    @Test
    void skipsRowsWithNoComparableProgress() {
        when(constructionProgressService.getProgressVariance(PROJECT)).thenReturn(List.of(
                new ProgressVarianceResponse(NO_DATA, "S-04", "Culvert", null, null, null, "NO_DATA")));

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.dataSnapshot().size()).isZero();
    }
}
