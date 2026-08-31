package com.bipros.ai.agent.memory;

import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.EvidenceRef;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;

/**
 * Identity and change-detection hashes for findings.
 *
 * <ul>
 *   <li><b>Fingerprint</b> — SHA-256 of {@code agentKey|projectId|findingType|subjectRef}. Stable
 *       identity of "the same concern about the same subject" across runs; drives dedup/supersession.</li>
 *   <li><b>Content hash</b> — SHA-256 over the deterministic, business-meaningful fields (severity,
 *       confidence, evidence values). Narrative rewording by the LLM does NOT change it, so a
 *       re-run that only reworded prose bumps {@code lastSeenAt} without re-notifying. A changed
 *       number/severity DOES change it → supersede + re-notify.</li>
 * </ul>
 */
public final class FindingFingerprint {

    private FindingFingerprint() {
    }

    public static String of(String agentKey, UUID projectId, String findingType, String subjectRef) {
        String canonical = nz(agentKey) + "|" + (projectId == null ? "GLOBAL" : projectId) + "|"
                + nz(findingType) + "|" + nz(subjectRef);
        return sha256(canonical);
    }

    public static String content(AgentFindingDraft draft) {
        StringBuilder sb = new StringBuilder();
        sb.append(draft.severity() == null ? "" : draft.severity().name()).append('|');
        sb.append(String.format(Locale.ROOT, "%.3f", draft.confidence())).append('|');
        // Evidence values are the deterministic numbers; narrative fields are intentionally excluded.
        for (EvidenceRef e : draft.evidence()) {
            sb.append(nz(e.type())).append(':').append(nz(e.label())).append('=').append(nz(e.value())).append(';');
        }
        return sha256(sb.toString());
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
