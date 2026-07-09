package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.gis.application.dto.ProgressVarianceResponse;
import com.bipros.gis.application.service.ConstructionProgressService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * GIS Intelligence agent. Compares each WBS stretch's satellite/AI-derived construction progress
 * against the contractor's self-claimed progress and flags the segments where the two disagree.
 *
 * <p>Data source: {@link ConstructionProgressService#getProgressVariance} — one row per WBS polygon
 * carrying the latest AI-derived percent, the contractor-claimed percent, the signed variance
 * (derived − claimed) and a RAG-band {@code varianceStatus} (ON_TRACK / BEHIND / AHEAD / NO_DATA).
 *
 * <p>Findings:
 * <ul>
 *   <li>{@code STRETCH_BEHIND} — the domain flags the stretch {@code BEHIND} its verification band
 *       (AI-derived progress runs {@literal >}10pp above the contractor claim: under-reported field
 *       progress).</li>
 *   <li>{@code FIELD_PROGRESS_MISMATCH} — any other stretch whose claimed and AI-derived progress
 *       diverge by more than 10 percentage points (the contractor over-claims relative to what the
 *       satellite verifies: a progress-billing risk).</li>
 * </ul>
 * One finding per stretch (RAG band takes precedence). Confidence scales with the size of the gap.
 * Degrades to an empty result when the project has no satellite progress-variance data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GisIntelligenceAgent extends AbstractAgent {

    private static final String KEY = "gis_intelligence";
    private static final Duration TTL = Duration.ofDays(7);
    /** Divergence (percentage points) beyond which a claimed-vs-derived gap is material. */
    private static final double MISMATCH_THRESHOLD = 10.0;

    private final ConstructionProgressService constructionProgressService;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "GIS Intelligence";
    }

    @Override
    public boolean supportsPortfolio() {
        return false;
    }

    @Override
    public GatherResult gather(AgentRunContext ctx) {
        UUID projectId = ctx.projectId();
        List<ProgressVarianceResponse> variances = constructionProgressService.getProgressVariance(projectId);

        // Keep only stretches that carry a comparable variance, sorted for a deterministic snapshot.
        List<ProgressVarianceResponse> rows = new ArrayList<>();
        for (ProgressVarianceResponse v : variances) {
            if (v != null && v.wbsPolygonId() != null
                    && v.variancePercent() != null
                    && v.derivedPercent() != null
                    && v.claimedPercent() != null) {
                rows.add(v);
            }
        }
        rows.sort(Comparator.comparing(v -> v.wbsPolygonId().toString()));

        Instant validUntil = (ctx.now() == null ? Instant.now() : ctx.now()).plus(TTL);
        List<AgentFindingDraft> candidates = new ArrayList<>();
        ArrayNode snapshot = objectMapper.createArrayNode();

        for (ProgressVarianceResponse v : rows) {
            double derived = v.derivedPercent();
            double claimed = v.claimedPercent();
            double variance = v.variancePercent();
            double absVar = Math.abs(variance);
            String status = v.varianceStatus() == null ? "" : v.varianceStatus();

            ObjectNode node = snapshot.addObject();
            node.put("wbsPolygonId", v.wbsPolygonId().toString());
            node.put("wbsCode", v.wbsCode());
            node.put("derived", round(derived));
            node.put("claimed", round(claimed));
            node.put("variance", round(variance));
            node.put("status", status);

            if ("BEHIND".equals(status)) {
                candidates.add(stretchBehind(projectId, v, derived, claimed, variance, validUntil));
            } else if (absVar > MISMATCH_THRESHOLD) {
                candidates.add(mismatch(projectId, v, derived, claimed, variance, validUntil));
            }
        }

        // Most-severe first for a stable, meaningful narration order.
        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    /**
     * Contractor over-claims relative to satellite verification (claimed exceeds derived by more than
     * the threshold, or the two diverge materially outside the BEHIND band): a progress-billing risk.
     */
    private AgentFindingDraft mismatch(UUID projectId, ProgressVarianceResponse v,
                                       double derived, double claimed, double variance, Instant validUntil) {
        double absVar = Math.abs(variance);
        Severity severity = absVar >= 25 ? Severity.CRITICAL : absVar >= 15 ? Severity.HIGH : Severity.MEDIUM;
        boolean overClaim = variance < 0;
        String label = stretchLabel(v);
        return new AgentFindingDraft(
                "FIELD_PROGRESS_MISMATCH",
                "stretch:" + v.wbsPolygonId(),
                severity,
                confidenceForGap(absVar),
                "Gap of " + gap(absVar) + " between satellite-derived and contractor-claimed progress "
                        + "(latest verification snapshot)",
                label + ": claimed progress disagrees with satellite by " + gap(absVar),
                (overClaim
                        ? "The contractor claims " + pct(claimed) + " complete on " + label + " but satellite AI "
                                + "verifies only " + pct(derived) + " — the claim overstates verified progress by "
                                + gap(absVar) + "."
                        : "Satellite AI observes " + pct(derived) + " complete on " + label + " while the contractor "
                                + "claims only " + pct(claimed) + " — the two disagree by " + gap(absVar) + "."),
                (overClaim
                        ? "Self-reported progress has run ahead of the physical work captured on the latest imagery, "
                                + "typically optimistic claiming or work booked before it is verifiable on site."
                        : "Field progress is being under-reported relative to what the imagery captures, usually a "
                                + "lag in claim submission or incomplete self-reporting."),
                (overClaim
                        ? "An over-claim of " + gap(absVar) + " exposes the project to over-payment on "
                                + "progress-based billing and can mask real schedule slippage behind an inflated claim."
                        : "An under-reported claim understates earned progress, distorting billing and EVM and hiding "
                                + "how much of this stretch is actually complete."),
                (overClaim
                        ? "Withhold or adjust the progress payment for " + label + " to the satellite-verified "
                                + pct(derived) + " and request an on-site re-measure before certifying the claim."
                        : "Reconcile the claim for " + label + " with the satellite-verified " + pct(derived)
                                + " and prompt the contractor to update the self-reported progress."),
                evidence(projectId, v, derived, claimed, variance),
                Map.of("SITE_MANAGER", List.of(), "PROJECT_MANAGER", List.of()),
                validUntil);
    }

    /**
     * Domain has flagged the stretch {@code BEHIND} its verification band: satellite-derived progress
     * runs materially above the contractor claim (under-reported field progress).
     */
    private AgentFindingDraft stretchBehind(UUID projectId, ProgressVarianceResponse v,
                                            double derived, double claimed, double variance, Instant validUntil) {
        Severity severity = variance >= 25 ? Severity.HIGH : variance >= 15 ? Severity.MEDIUM : Severity.LOW;
        String label = stretchLabel(v);
        return new AgentFindingDraft(
                "STRETCH_BEHIND",
                "stretch:" + v.wbsPolygonId(),
                severity,
                confidenceForGap(Math.abs(variance)),
                "Stretch flagged BEHIND its progress-verification band; " + gap(Math.abs(variance))
                        + " gap on the latest satellite snapshot",
                label + " is off its verification band (BEHIND)",
                label + " is flagged BEHIND its satellite progress-verification band: AI-derived progress is "
                        + pct(derived) + " against a contractor claim of " + pct(claimed) + " — "
                        + gap(Math.abs(variance)) + " apart.",
                "The claimed and satellite-verified progress for this stretch have drifted out of alignment, so the "
                        + "self-reported figure no longer tracks what the imagery captures on the ground.",
                "A stretch sitting outside its verification band means its reported progress is unreliable for billing "
                        + "and EVM, and the divergence can hide either under-reported work or a data-quality problem.",
                "Reconcile " + label + " against the satellite-verified " + pct(derived) + ", confirm the reading "
                        + "with the site team, and bring the self-reported claim back onto its verification band.",
                evidence(projectId, v, derived, claimed, variance),
                Map.of("SITE_MANAGER", List.of(), "PROJECT_MANAGER", List.of()),
                validUntil);
    }

    private List<EvidenceRef> evidence(UUID projectId, ProgressVarianceResponse v,
                                       double derived, double claimed, double variance) {
        return List.of(
                EvidenceRef.metric("AI-derived progress", pct(derived)),
                EvidenceRef.metric("Contractor-claimed progress", pct(claimed)),
                EvidenceRef.metric("Variance", signed(variance)),
                EvidenceRef.metric("Verification status", v.varianceStatus() == null ? "UNKNOWN" : v.varianceStatus()),
                EvidenceRef.entity("Stretch", stretchLabel(v), "wbs-polygon", v.wbsPolygonId(),
                        "/projects/" + projectId + "/gis?focus=" + v.wbsPolygonId()));
    }

    private static String stretchLabel(ProgressVarianceResponse v) {
        String code = v.wbsCode();
        String name = v.wbsName();
        if (code != null && !code.isBlank() && name != null && !name.isBlank()) {
            return code + " " + name;
        }
        if (code != null && !code.isBlank()) return code;
        if (name != null && !name.isBlank()) return name;
        return "Stretch " + v.wbsPolygonId();
    }

    /** Confidence rises with the size of the gap: 10pp ≈ 0.70, 30pp ≈ 0.90, capped at 0.95. */
    private static double confidenceForGap(double absVar) {
        return Math.min(0.95, 0.6 + absVar / 100.0);
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.1f%%", v);
    }

    private static String gap(double absVar) {
        return String.format(Locale.ROOT, "%.1f pp", absVar);
    }

    private static String signed(double v) {
        return String.format(Locale.ROOT, "%+.1f pp", v);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
