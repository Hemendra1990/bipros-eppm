package com.bipros.ai.agent.memory;

import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FindingFingerprintTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private static AgentFindingDraft draft(double confidence, Severity sev, String evValue, String narrative) {
        return new AgentFindingDraft("CRITICAL_PATH_SLIP", "activity:42", sev, confidence,
                "healthScore trend", narrative, narrative, narrative, narrative, narrative,
                List.of(EvidenceRef.metric("slip", evValue)), Map.of(), null);
    }

    @Test
    void fingerprintIsStableForSameIdentity() {
        String a = FindingFingerprint.of("planning_intelligence", PROJECT, "CRITICAL_PATH_SLIP", "activity:42");
        String b = FindingFingerprint.of("planning_intelligence", PROJECT, "CRITICAL_PATH_SLIP", "activity:42");
        assertThat(a).isEqualTo(b).hasSize(64);
    }

    @Test
    void fingerprintDiffersBySubjectAndProject() {
        String base = FindingFingerprint.of("planning_intelligence", PROJECT, "CRITICAL_PATH_SLIP", "activity:42");
        assertThat(FindingFingerprint.of("planning_intelligence", PROJECT, "CRITICAL_PATH_SLIP", "activity:43"))
                .isNotEqualTo(base);
        assertThat(FindingFingerprint.of("planning_intelligence", null, "CRITICAL_PATH_SLIP", "activity:42"))
                .isNotEqualTo(base);
        assertThat(FindingFingerprint.of("capacity_utilisation", PROJECT, "CRITICAL_PATH_SLIP", "activity:42"))
                .isNotEqualTo(base);
    }

    @Test
    void contentHashIgnoresNarrativeButTracksNumbers() {
        AgentFindingDraft original = draft(0.82, Severity.HIGH, "12 days", "The critical path slipped.");
        AgentFindingDraft reworded = draft(0.82, Severity.HIGH, "12 days", "Critical path has eroded by nearly two weeks.");
        AgentFindingDraft numberChanged = draft(0.82, Severity.HIGH, "18 days", "The critical path slipped.");
        AgentFindingDraft severityChanged = draft(0.82, Severity.CRITICAL, "12 days", "The critical path slipped.");

        // Pure prose rewrite → same content hash (a repeat, not re-notified).
        assertThat(FindingFingerprint.content(reworded)).isEqualTo(FindingFingerprint.content(original));
        // A changed evidence number or severity → different content hash (supersede + re-notify).
        assertThat(FindingFingerprint.content(numberChanged)).isNotEqualTo(FindingFingerprint.content(original));
        assertThat(FindingFingerprint.content(severityChanged)).isNotEqualTo(FindingFingerprint.content(original));
    }
}
