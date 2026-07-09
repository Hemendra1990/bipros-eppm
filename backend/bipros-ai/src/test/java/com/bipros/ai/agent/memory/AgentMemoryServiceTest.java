package com.bipros.ai.agent.memory;

import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.ai.agent.domain.AgentFindingRepository;
import com.bipros.ai.agent.domain.FindingStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentMemoryServiceTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final Instant NOW = Instant.parse("2026-07-09T02:45:00Z");

    @Mock
    private AgentFindingRepository repo;

    private AgentMemoryService service;

    @BeforeEach
    void setUp() {
        service = new AgentMemoryService(repo, new ObjectMapper());
        lenient().when(repo.save(any(AgentFinding.class))).thenAnswer(inv -> {
            AgentFinding f = inv.getArgument(0);
            if (f.getId() == null) f.setId(UUID.randomUUID());
            return f;
        });
    }

    private static AgentFindingDraft draft(Severity sev, String evValue, String narrative) {
        return new AgentFindingDraft("CRITICAL_PATH_SLIP", "activity:42", sev, 0.82,
                "healthScore trend", narrative, narrative, narrative, narrative, narrative,
                List.of(EvidenceRef.metric("slip", evValue)), Map.of(), null);
    }

    @Test
    void newFindingIsInsertedNotifiable() {
        when(repo.findByFingerprintAndStatus(any(), any())).thenReturn(Optional.empty());

        List<AgentFinding> result = service.upsertAll(RUN, "planning_intelligence", PROJECT,
                List.of(draft(Severity.HIGH, "12 days", "slip")), NOW);

        assertThat(result).hasSize(1);
        AgentFinding saved = result.get(0);
        assertThat(saved.getStatus()).isEqualTo(FindingStatus.ACTIVE);
        assertThat(saved.isNotifiable()).isTrue();
        assertThat(saved.getSupersedesId()).isNull();
        assertThat(saved.getLastSeenAt()).isEqualTo(NOW);
    }

    @Test
    void repeatWithSameContentBumpsLastSeenNotNotifiable() {
        AgentFindingDraft d = draft(Severity.HIGH, "12 days", "slip");
        AgentFinding existing = new AgentFinding();
        existing.setId(UUID.randomUUID());
        existing.setStatus(FindingStatus.ACTIVE);
        existing.setContentHash(FindingFingerprint.content(d));
        existing.setNotifiable(true);
        when(repo.findByFingerprintAndStatus(any(), any())).thenReturn(Optional.of(existing));

        List<AgentFinding> result = service.upsertAll(RUN, "planning_intelligence", PROJECT, List.of(d), NOW);

        assertThat(result).containsExactly(existing);
        assertThat(existing.isNotifiable()).isFalse();
        assertThat(existing.getLastSeenAt()).isEqualTo(NOW);
        assertThat(existing.getStatus()).isEqualTo(FindingStatus.ACTIVE);
        verify(repo, times(1)).save(any());  // only the bump, no new insert
    }

    @Test
    void changedContentSupersedesOldAndInsertsNew() {
        AgentFindingDraft old = draft(Severity.HIGH, "12 days", "slip");
        AgentFindingDraft changed = draft(Severity.CRITICAL, "20 days", "worse slip");
        AgentFinding existing = new AgentFinding();
        UUID oldId = UUID.randomUUID();
        existing.setId(oldId);
        existing.setStatus(FindingStatus.ACTIVE);
        existing.setContentHash(FindingFingerprint.content(old));
        when(repo.findByFingerprintAndStatus(any(), any())).thenReturn(Optional.of(existing));

        List<AgentFinding> result = service.upsertAll(RUN, "planning_intelligence", PROJECT, List.of(changed), NOW);

        assertThat(existing.getStatus()).isEqualTo(FindingStatus.SUPERSEDED);
        AgentFinding fresh = result.get(0);
        assertThat(fresh.getStatus()).isEqualTo(FindingStatus.ACTIVE);
        assertThat(fresh.isNotifiable()).isTrue();
        assertThat(fresh.getSupersedesId()).isEqualTo(oldId);
        assertThat(fresh.getSeverity()).isEqualTo(Severity.CRITICAL);

        ArgumentCaptor<AgentFinding> captor = ArgumentCaptor.forClass(AgentFinding.class);
        verify(repo, times(2)).save(captor.capture());  // supersede old + insert new
    }

    @Test
    void expireStaleFlipsActiveToExpired() {
        AgentFinding stale = new AgentFinding();
        stale.setStatus(FindingStatus.ACTIVE);
        when(repo.findByStatusAndValidUntilBefore(FindingStatus.ACTIVE, NOW)).thenReturn(List.of(stale));

        int expired = service.expireStale(NOW);

        assertThat(expired).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(FindingStatus.EXPIRED);
        verify(repo).saveAll(any());
    }
}
