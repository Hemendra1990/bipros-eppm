package com.bipros.ai.agent.memory;

import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.ai.agent.domain.AgentFindingRepository;
import com.bipros.ai.agent.domain.FindingStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The shared agent-memory store: dedup/supersession, cross-agent reads, ack/resolve, TTL sweep.
 *
 * <p>Supersession rules (per draft, keyed by fingerprint):
 * <ul>
 *   <li>No ACTIVE row → insert, {@code notifiable=true}.</li>
 *   <li>ACTIVE row, same content hash → bump {@code lastSeenAt}, {@code notifiable=false} (a repeat).</li>
 *   <li>ACTIVE row, changed content → mark old SUPERSEDED, insert new {@code notifiable=true},
 *       {@code supersedesId} = old id.</li>
 * </ul>
 * Only {@code notifiable} findings reach the NotificationAgent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMemoryService {

    private static final TypeReference<List<EvidenceRef>> EVIDENCE_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, List<UUID>>> STAKEHOLDER_TYPE = new TypeReference<>() {
    };

    private final AgentFindingRepository findingRepository;
    private final ObjectMapper objectMapper;

    /**
     * Persist a run's narrated drafts into memory, applying dedup/supersession.
     *
     * @return the persisted (ACTIVE) findings for this run, in draft order
     */
    @Transactional
    public List<AgentFinding> upsertAll(UUID runId, String agentKey, UUID projectId,
                                        List<AgentFindingDraft> drafts, Instant now) {
        List<AgentFinding> result = new ArrayList<>(drafts.size());
        for (AgentFindingDraft draft : drafts) {
            String fingerprint = FindingFingerprint.of(agentKey, projectId, draft.findingType(), draft.subjectRef());
            String contentHash = FindingFingerprint.content(draft);
            AgentFinding existing = findingRepository.findByFingerprintAndStatus(fingerprint, FindingStatus.ACTIVE)
                    .orElse(null);

            if (existing != null && contentHash.equals(existing.getContentHash())) {
                existing.setLastSeenAt(now);
                existing.setNotifiable(false);
                result.add(findingRepository.save(existing));
                continue;
            }

            UUID supersedesId = null;
            if (existing != null) {
                existing.setStatus(FindingStatus.SUPERSEDED);
                findingRepository.save(existing);
                supersedesId = existing.getId();
            }

            AgentFinding fresh = toEntity(runId, agentKey, projectId, draft, fingerprint, contentHash, now);
            fresh.setSupersedesId(supersedesId);
            fresh.setNotifiable(true);
            result.add(findingRepository.save(fresh));
        }
        return result;
    }

    /** Active findings for a project produced by any of {@code agentKeys}, filtered to {@code minSeverity}. */
    @Transactional(readOnly = true)
    public List<AgentFinding> activeFindings(UUID projectId, Set<String> agentKeys, Severity minSeverity) {
        List<AgentFinding> rows = (agentKeys == null || agentKeys.isEmpty())
                ? findingRepository.findByProjectIdAndStatus(projectId, FindingStatus.ACTIVE)
                : findingRepository.findByProjectIdAndAgentKeyInAndStatus(projectId, agentKeys, FindingStatus.ACTIVE);
        return rows.stream().filter(f -> f.getSeverity().atLeast(minSeverity)).toList();
    }

    @Transactional(readOnly = true)
    public List<AgentFinding> activeFindingsForProjects(Collection<UUID> projectIds) {
        return findingRepository.findByProjectIdInAndStatus(projectIds, FindingStatus.ACTIVE);
    }

    @Transactional
    public AgentFinding acknowledge(UUID findingId, UUID userId, Instant now) {
        AgentFinding f = findingRepository.findById(findingId).orElseThrow();
        f.setAcknowledgedBy(userId);
        f.setAcknowledgedAt(now);
        return findingRepository.save(f);
    }

    @Transactional
    public AgentFinding resolve(UUID findingId, UUID userId, Instant now) {
        AgentFinding f = findingRepository.findById(findingId).orElseThrow();
        f.setStatus(FindingStatus.RESOLVED_BY_USER);
        f.setResolvedBy(userId);
        f.setResolvedAt(now);
        return findingRepository.save(f);
    }

    /** Flip ACTIVE findings past their TTL to EXPIRED. Returns the number expired. */
    @Transactional
    public int expireStale(Instant now) {
        List<AgentFinding> stale = findingRepository.findByStatusAndValidUntilBefore(FindingStatus.ACTIVE, now);
        for (AgentFinding f : stale) {
            f.setStatus(FindingStatus.EXPIRED);
        }
        findingRepository.saveAll(stale);
        if (!stale.isEmpty()) {
            log.info("AgentMemoryService expired {} stale findings", stale.size());
        }
        return stale.size();
    }

    public List<EvidenceRef> readEvidence(AgentFinding f) {
        return readJson(f.getEvidenceJson(), EVIDENCE_TYPE, List.of());
    }

    public Map<String, List<UUID>> readStakeholders(AgentFinding f) {
        return readJson(f.getStakeholdersJson(), STAKEHOLDER_TYPE, Map.of());
    }

    private AgentFinding toEntity(UUID runId, String agentKey, UUID projectId, AgentFindingDraft draft,
                                  String fingerprint, String contentHash, Instant now) {
        AgentFinding f = new AgentFinding();
        f.setRunId(runId);
        f.setAgentKey(agentKey);
        f.setProjectId(projectId);
        f.setFindingType(draft.findingType());
        f.setSubjectRef(draft.subjectRef());
        f.setFingerprint(fingerprint);
        f.setContentHash(contentHash);
        f.setSeverity(draft.severity());
        f.setConfidence(draft.confidence());
        f.setConfidenceBasis(draft.confidenceBasis());
        f.setTitle(draft.title());
        f.setWhatHappened(draft.whatHappened());
        f.setWhyItHappened(draft.whyItHappened());
        f.setBusinessImpact(draft.businessImpact());
        f.setRecommendedAction(draft.recommendedAction());
        f.setEvidenceJson(writeJson(draft.evidence()));
        f.setStakeholdersJson(writeJson(draft.stakeholders()));
        f.setValidUntil(draft.validUntil());
        f.setLastSeenAt(now);
        f.setStatus(FindingStatus.ACTIVE);
        return f;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialise finding json: {}", e.getMessage());
            return null;
        }
    }

    private <T> T readJson(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Failed to parse finding json: {}", e.getMessage());
            return fallback;
        }
    }
}
