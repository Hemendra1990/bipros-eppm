package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.risk.application.service.RiskService;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskStatus;
import com.bipros.risk.domain.model.RiskTrend;
import com.bipros.risk.domain.repository.RiskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Risk Intelligence agent. Deterministic {@link #gather} over the project's risk register that
 * surfaces three finding types the risk team should act on:
 *
 * <ul>
 *   <li>{@code RISK_EXPOSURE_SPIKE} — project-level. Total expected monetary value (EMV) is positive
 *       and being pushed up by worsening-trend risks.</li>
 *   <li>{@code EMERGING_RISK} — a newly assessed risk that already sits in a high (RED/CRIMSON) band.</li>
 *   <li>{@code STALE_RISK_REVIEW} — an open risk whose record has not been touched in N days.</li>
 * </ul>
 *
 * <p>Data sources: {@link RiskService#calculateRiskExposure(UUID)} (the authoritative open-risk EMV)
 * and {@link RiskRepository#findByProjectId(UUID)} for the individual risks (status, score, trend,
 * exposure, and the created/updated timestamps used as identification/last-review proxies). Neither
 * call performs an access check, so this runs safely in the background pipeline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskIntelligenceAgent extends AbstractAgent {

    private static final String KEY = "risk_intelligence";
    private static final Duration TTL = Duration.ofDays(7);

    /** Assessed within this many days = "newly assessed". */
    private static final int EMERGING_WINDOW_DAYS = 14;
    /** RED band per {@code Risk.deriveRag}. */
    private static final double HIGH_SCORE = 12.0;
    /** CRIMSON band per {@code Risk.deriveRag}. */
    private static final double CRITICAL_SCORE = 20.0;
    /** Open risk untouched for at least this many days is stale. */
    private static final long STALE_DAYS = 30;
    /** Escalated staleness threshold. */
    private static final long STALE_CRITICAL_DAYS = 90;

    /** Terminal statuses excluded from the "open" set (no review / emerging finding needed). */
    private static final EnumSet<RiskStatus> CLOSED_STATES = EnumSet.of(
            RiskStatus.CLOSED, RiskStatus.RESOLVED, RiskStatus.REJECTED,
            RiskStatus.REALISED, RiskStatus.REALISED_PARTIALLY);

    /** Statuses {@code RiskService.calculateRiskExposure} excludes from EMV — CLOSED + RESOLVED only. The
     *  EMV-narrative count must use THIS population (not the wider CLOSED_STATES) to reconcile with the Risks tab. */
    private static final EnumSet<RiskStatus> EMV_OPEN_EXCLUDES = EnumSet.of(
            RiskStatus.CLOSED, RiskStatus.RESOLVED);

    private final RiskService riskService;
    private final RiskRepository riskRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Risk Intelligence";
    }

    @Override
    public boolean supportsPortfolio() {
        return false;
    }

    @Override
    public GatherResult gather(AgentRunContext ctx) {
        UUID projectId = ctx.projectId();
        Instant now = ctx.now() == null ? Instant.now() : ctx.now();
        Instant validUntil = now.plus(TTL);

        List<Risk> all = riskRepository.findByProjectId(projectId);
        List<Risk> open = all.stream()
                .filter(r -> !CLOSED_STATES.contains(r.getStatus()))
                .sorted(Comparator.comparing(Risk::getCode, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        // Authoritative total open EMV from the risk service (currency-neutral raw number).
        BigDecimal emv = riskService.calculateRiskExposure(projectId);
        double emvValue = emv == null ? 0.0 : emv.doubleValue();

        // EMV is computed over RiskService's exposure population (excludes CLOSED + RESOLVED only). Count
        // worsening/exposure over the SAME population so the "N active risks" narrated with EMV reconciles with
        // the Risks tab; the wider CLOSED_STATES set drives only the per-risk review findings below.
        List<Risk> exposureOpen = all.stream()
                .filter(r -> !EMV_OPEN_EXCLUDES.contains(r.getStatus()))
                .toList();
        int worseningCount = 0;
        double worseningEmv = 0.0;
        int openWithExposure = 0;
        for (Risk r : exposureOpen) {
            BigDecimal exp = r.getPreResponseExposureCost();
            if (exp != null) openWithExposure++;
            if (r.getTrend() == RiskTrend.WORSENING) {
                worseningCount++;
                worseningEmv += exp == null ? 0.0 : exp.doubleValue();
            }
        }

        List<AgentFindingDraft> candidates = new ArrayList<>();
        ObjectNode snapshot = objectMapper.createObjectNode();
        ArrayNode riskArr = snapshot.putArray("risks");

        for (Risk r : open) {
            double score = r.getRiskScore() == null ? 0.0 : r.getRiskScore();
            BigDecimal exp = r.getPreResponseExposureCost();
            double expVal = exp == null ? 0.0 : exp.doubleValue();

            long ageDays = r.getCreatedAt() == null ? -1
                    : Math.max(0, Duration.between(r.getCreatedAt(), now).toDays());
            long staleDays = r.getUpdatedAt() == null ? -1
                    : Math.max(0, Duration.between(r.getUpdatedAt(), now).toDays());

            ObjectNode row = riskArr.addObject();
            row.put("code", r.getCode());
            row.put("score", round(score));
            row.put("rag", r.getRag() == null ? null : r.getRag().name());
            row.put("trend", r.getTrend() == null ? null : r.getTrend().name());
            row.put("status", r.getStatus() == null ? null : r.getStatus().name());
            row.put("exposure", round(expVal));
            row.put("ageDays", ageDays);
            row.put("staleDays", staleDays);

            if (r.getCreatedAt() != null && ageDays <= EMERGING_WINDOW_DAYS && score >= HIGH_SCORE) {
                candidates.add(emergingRisk(projectId, r, score, expVal, ageDays, validUntil));
            } else if (r.getUpdatedAt() != null && staleDays >= STALE_DAYS) {
                candidates.add(staleReview(projectId, r, score, staleDays, validUntil));
            }
        }

        snapshot.put("emv", round(emvValue));
        snapshot.put("openRiskCount", exposureOpen.size());
        snapshot.put("reviewRiskCount", open.size());
        snapshot.put("worseningCount", worseningCount);
        snapshot.put("worseningEmv", round(worseningEmv));

        if (emvValue > 0 && worseningCount >= 1) {
            candidates.add(exposureSpike(projectId, emvValue, worseningCount, worseningEmv,
                    exposureOpen.size(), openWithExposure, validUntil));
        }

        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    // ── Finding builders ──────────────────────────────────────────────────

    private AgentFindingDraft exposureSpike(UUID projectId, double emvValue, int worseningCount,
                                            double worseningEmv, int openCount, int openWithExposure,
                                            Instant validUntil) {
        double share = emvValue > 0 ? Math.min(1.0, worseningEmv / emvValue) : 0.0;
        Severity severity = (worseningCount >= 3 && share >= 0.5) ? Severity.CRITICAL
                : (worseningCount >= 2 || share >= 0.3) ? Severity.HIGH
                : Severity.MEDIUM;
        double confidence = openCount == 0 ? 0.5
                : Math.min(0.95, Math.max(0.5, (double) openWithExposure / openCount));
        String emvStr = num(emvValue);
        String worseningEmvStr = num(worseningEmv);
        String sharePct = pct(share);
        return new AgentFindingDraft(
                "RISK_EXPOSURE_SPIKE",
                "PROJECT",
                severity,
                confidence,
                openWithExposure + " of " + openCount + " open risks carry a computed exposure cost",
                "Project risk exposure (EMV) is " + emvStr + " and rising",
                "Open-risk exposure totals " + emvStr + " across " + openCount + " active risks; "
                        + worseningCount + " risk(s) with a worsening trend contribute " + worseningEmvStr
                        + " (" + sharePct + " of exposure).",
                "Worsening-trend risks are pushing expected monetary value upward faster than mitigations "
                        + "are retiring it.",
                "A rising EMV erodes contingency and threatens the cost baseline; if left unmanaged these risks "
                        + "convert into actual overruns.",
                "Review the worsening risks and accelerate their response plans; confirm contingency still covers "
                        + "the " + emvStr + " exposure.",
                List.of(
                        EvidenceRef.metric("Total risk exposure (EMV)", emvStr),
                        EvidenceRef.metric("Worsening risks", String.valueOf(worseningCount)),
                        EvidenceRef.metric("Exposure from worsening risks", worseningEmvStr + " (" + sharePct + ")"),
                        EvidenceRef.entity("Risk register", openCount + " open risks", "project", projectId,
                                "/projects/" + projectId + "/risks")),
                Map.of("PROJECT_MANAGER", List.of(), "PORTFOLIO_MANAGER", List.of()),
                validUntil);
    }

    private AgentFindingDraft emergingRisk(UUID projectId, Risk r, double score, double exposure,
                                           long ageDays, Instant validUntil) {
        Severity severity = score >= CRITICAL_SCORE ? Severity.CRITICAL : Severity.HIGH;
        double confidence = Math.min(0.95, 0.6 + score / 50.0);
        String ragStr = r.getRag() == null ? "unbanded" : r.getRag().name();
        String scoreStr = intStr(score);
        String expStr = num(exposure);
        return new AgentFindingDraft(
                "EMERGING_RISK",
                "risk:" + r.getId(),
                severity,
                confidence,
                "Project scoring-matrix composite score = " + scoreStr + " of 25",
                r.getCode() + " newly assessed as a high risk (score " + scoreStr + ", " + ragStr + ")",
                r.getCode() + " \"" + r.getTitle() + "\" was assessed " + ageDays + " day(s) ago with a risk score of "
                        + scoreStr + " (" + ragStr + " band)"
                        + (exposure > 0 ? " and an exposure of " + expStr : "") + ".",
                "A high probability/impact combination on the project scoring matrix places this risk in the "
                        + ragStr + " band immediately on identification.",
                "New high-band risks that are not managed from day one are the most likely to breach the schedule "
                        + "or cost baseline; early response is materially cheaper than late recovery.",
                "Assign an owner and a response strategy for " + r.getCode() + " now, and table it at the next "
                        + "risk review.",
                List.of(
                        EvidenceRef.metric("Risk score", scoreStr + " (" + ragStr + ")"),
                        EvidenceRef.metric("Assessed", ageDays + " day(s) ago"),
                        EvidenceRef.metric("Exposure (EMV)", exposure > 0 ? expStr : "not yet computed"),
                        EvidenceRef.entity("Risk", r.getCode(), "risk", r.getId(),
                                "/projects/" + projectId + "/risks/" + r.getId())),
                Map.of("PROJECT_MANAGER", List.of()),
                validUntil);
    }

    private AgentFindingDraft staleReview(UUID projectId, Risk r, double score, long staleDays,
                                          Instant validUntil) {
        boolean severe = score >= HIGH_SCORE;
        Severity severity = staleDays >= STALE_CRITICAL_DAYS
                ? (severe ? Severity.CRITICAL : Severity.MEDIUM)
                : (severe ? Severity.HIGH : Severity.LOW);
        double confidence = Math.min(0.95, 0.6 + staleDays / 300.0);
        String ragStr = r.getRag() == null ? "unbanded" : r.getRag().name();
        String scoreStr = intStr(score);
        String statusStr = r.getStatus() == null ? "open" : r.getStatus().name();
        return new AgentFindingDraft(
                "STALE_RISK_REVIEW",
                "risk:" + r.getId(),
                severity,
                confidence,
                staleDays + " days since the risk was last updated (last-review proxy)",
                r.getCode() + " has not been reviewed in " + staleDays + " days",
                r.getCode() + " \"" + r.getTitle() + "\" is still " + statusStr + " (score " + scoreStr + ", "
                        + ragStr + " band) but its record has not been updated in " + staleDays + " days.",
                "The risk has stayed open without a review touch, so its probability, impact and response status "
                        + "may no longer reflect conditions on the ground.",
                "A stale " + ragStr + "-band risk hides real exposure: the register understates the threat when a "
                        + "worsening risk goes un-reviewed.",
                "Re-review " + r.getCode() + " — reconfirm probability and impact, update the trend, and record the "
                        + "current response status.",
                List.of(
                        EvidenceRef.metric("Days since review", String.valueOf(staleDays)),
                        EvidenceRef.metric("Risk score", scoreStr + " (" + ragStr + ")"),
                        EvidenceRef.metric("Status", statusStr),
                        EvidenceRef.entity("Risk", r.getCode(), "risk", r.getId(),
                                "/projects/" + projectId + "/risks/" + r.getId())),
                Map.of("PROJECT_MANAGER", List.of()),
                validUntil);
    }

    // ── Formatting helpers ────────────────────────────────────────────────

    /** Currency-neutral grouped number, no symbol (relabelling happens on the frontend). */
    private static String num(double v) {
        return String.format(Locale.ROOT, "%,.0f", v);
    }

    private static String intStr(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }

    private static String pct(double ratio) {
        return String.format(Locale.ROOT, "%.0f%%", ratio * 100.0);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
