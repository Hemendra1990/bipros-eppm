package com.bipros.ai.agent.core;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A candidate finding produced deterministically by an agent's {@code gather()} pass, before
 * (and after) LLM narration.
 *
 * <p>Contract with the narrator: the LLM may reword the four narrative fields
 * ({@code title}, {@code whatHappened}, {@code whyItHappened}, {@code businessImpact},
 * {@code recommendedAction}), drop a candidate, or reorder — it may NOT change
 * {@code severity}, {@code confidence}, {@code confidenceBasis}, {@code evidence}, or any number.
 * Those are deterministic. See {@link #withNarrative}.
 *
 * @param findingType       stable machine type, e.g. "CRITICAL_PATH_SLIP"
 * @param subjectRef        the subject the finding is about (activity id, resource key, "PROJECT", …); part of the fingerprint
 * @param severity          deterministic severity from rule thresholds
 * @param confidence        deterministic statistic in [0,1]
 * @param confidenceBasis   plain-English source of the confidence number, e.g. "P80 of 10k Monte Carlo iterations"
 * @param title             short headline
 * @param whatHappened      the observed fact
 * @param whyItHappened     the cause / driver
 * @param businessImpact    the "so what" in business terms
 * @param recommendedAction the next step
 * @param evidence          deterministic evidence refs
 * @param stakeholders      map of role-key → user ids (or role-key → empty, resolved later by StakeholderResolver)
 * @param validUntil        TTL boundary (nullable = agent default applies)
 */
public record AgentFindingDraft(
        String findingType,
        String subjectRef,
        Severity severity,
        double confidence,
        String confidenceBasis,
        String title,
        String whatHappened,
        String whyItHappened,
        String businessImpact,
        String recommendedAction,
        List<EvidenceRef> evidence,
        Map<String, List<UUID>> stakeholders,
        Instant validUntil) {

    public AgentFindingDraft {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        stakeholders = stakeholders == null ? Map.of() : Map.copyOf(stakeholders);
    }

    /**
     * Returns a copy with the LLM-rewritten narrative text merged in. Any null argument keeps
     * this draft's original text (so a narrator that drops a field cannot blank it out).
     * Numbers, severity, confidence, evidence and stakeholders are always preserved.
     */
    public AgentFindingDraft withNarrative(String newTitle, String newWhat, String newWhy,
                                           String newImpact, String newAction) {
        return new AgentFindingDraft(
                findingType, subjectRef, severity, confidence, confidenceBasis,
                blankToOld(newTitle, title),
                blankToOld(newWhat, whatHappened),
                blankToOld(newWhy, whyItHappened),
                blankToOld(newImpact, businessImpact),
                blankToOld(newAction, recommendedAction),
                evidence, stakeholders, validUntil);
    }

    private static String blankToOld(String candidate, String old) {
        return (candidate == null || candidate.isBlank()) ? old : candidate;
    }
}
