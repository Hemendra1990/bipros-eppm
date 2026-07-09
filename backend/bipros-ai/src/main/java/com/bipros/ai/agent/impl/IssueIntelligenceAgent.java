package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.project.application.dto.DprIssueRow;
import com.bipros.project.application.service.DprIssueService;
import com.bipros.project.domain.model.IssueStatus;
import com.bipros.siteops.application.dto.NcrResponse;
import com.bipros.siteops.application.dto.SafetyRecordResponse;
import com.bipros.siteops.application.dto.SnagResponse;
import com.bipros.siteops.application.service.NcrService;
import com.bipros.siteops.application.service.SafetyService;
import com.bipros.siteops.application.service.SnagService;
import com.bipros.siteops.domain.model.NcrCategory;
import com.bipros.siteops.domain.model.NcrStatus;
import com.bipros.siteops.domain.model.SafetyKind;
import com.bipros.siteops.domain.model.SnagStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Issue Intelligence agent. Deterministic {@link #gather} that reads the project's open-issue
 * backlog across three logs — DPR field issues ({@link DprIssueService}), NCRs
 * ({@link NcrService}) and snags ({@link SnagService}) — plus the safety-event log
 * ({@link SafetyService}), then emits fully templated {@link AgentFindingDraft}s the LLM narrator
 * may only reword.
 *
 * <p>No SLA field exists on any of these entities, so ageing is measured against CONFIGURABLE
 * thresholds held as constants below: an open HSE/safety issue past {@value #HSE_AGE_HOURS}h is
 * CRITICAL, an open NCR past {@value #NCR_AGE_DAYS}d is HIGH, and an open snag past
 * {@value #SNAG_AGE_DAYS}d is MEDIUM.
 *
 * <p>Findings:
 * <ul>
 *   <li>{@code HSE_OPEN_CRITICAL} — any open HSE/safety issue (safety-classified DPR issues, SAFETY
 *       NCRs, and INCIDENT/NEAR_MISS safety records). CRITICAL once the oldest one passes the 24h
 *       ageing threshold (or a fatality is on the log), otherwise HIGH.</li>
 *   <li>{@code ISSUE_AGEING} — open NCRs past 7 days (HIGH) and open snags past 14 days (MEDIUM),
 *       one aggregate finding per backlog.</li>
 *   <li>{@code RECURRING_ISSUE_PATTERN} — open issues clustered by source/category; a cluster of
 *       &ge;3 of the same type is flagged. Confidence is a heuristic CAPPED at 0.6.</li>
 * </ul>
 * All calls degrade to an empty result on sparse/absent data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IssueIntelligenceAgent extends AbstractAgent {

    private static final String KEY = "issue_intelligence";
    private static final Duration TTL = Duration.ofDays(7);

    /** Configurable ageing thresholds (no SLA field exists on the source entities). */
    private static final long HSE_AGE_HOURS = 24;
    private static final long NCR_AGE_DAYS = 7;
    private static final long SNAG_AGE_DAYS = 14;

    /** A cluster of this many same-type open issues counts as a recurring pattern. */
    private static final int PATTERN_MIN = 3;
    /** Recurring-pattern confidence is a heuristic and is capped here. */
    private static final double PATTERN_CONFIDENCE_CAP = 0.6;

    private final DprIssueService dprIssueService;
    private final NcrService ncrService;
    private final SnagService snagService;
    private final SafetyService safetyService;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Issue Intelligence";
    }

    @Override
    public boolean supportsPortfolio() {
        return false;
    }

    @Override
    public GatherResult gather(AgentRunContext ctx) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        List<AgentFindingDraft> candidates = new ArrayList<>();
        UUID projectId = ctx.projectId();
        if (projectId == null) {
            return new GatherResult(snapshot, candidates);
        }

        Instant now = ctx.now() == null ? Instant.now() : ctx.now();
        Instant validUntil = now.plus(TTL);

        List<DprIssueRow> dprIssues = safeDprIssues(projectId);
        List<NcrResponse> ncrs = safeNcrs(projectId);
        List<SnagResponse> snags = safeSnags(projectId);
        List<SafetyRecordResponse> safety = safeSafety(projectId);

        // ---- Open subsets ----
        List<DprIssueRow> openDpr = dprIssues.stream().filter(r -> isOpenDpr(r.status())).toList();
        List<NcrResponse> openNcr = ncrs.stream().filter(n -> isOpenNcr(n.status())).toList();
        List<SnagResponse> openSnag = snags.stream().filter(s -> isOpenSnag(s.status())).toList();
        List<SafetyRecordResponse> openSafety = safety.stream().filter(r -> isOpenSafety(r.kind())).toList();

        snapshot.put("dprOpen", openDpr.size());
        snapshot.put("ncrOpen", openNcr.size());
        snapshot.put("snagOpen", openSnag.size());
        snapshot.put("safetyOpen", openSafety.size());

        // ---- HSE_OPEN_CRITICAL ----
        List<HseItem> hse = collectHse(openDpr, openNcr, openSafety);
        snapshot.put("hseOpen", hse.size());
        if (!hse.isEmpty()) {
            candidates.add(hseOpenCritical(projectId, hse, now, validUntil));
        }

        // ---- ISSUE_AGEING: NCRs (non-safety, since SAFETY NCRs are covered by HSE_OPEN_CRITICAL) ----
        List<NcrResponse> agedNcr = openNcr.stream()
                .filter(n -> n.category() != NcrCategory.SAFETY)
                .filter(n -> ageDays(ncrOpenTs(n), now) > NCR_AGE_DAYS)
                .sorted((a, b) -> Long.compare(ageDays(ncrOpenTs(b), now), ageDays(ncrOpenTs(a), now)))
                .toList();
        snapshot.put("agedNcr", agedNcr.size());
        if (!agedNcr.isEmpty()) {
            candidates.add(ncrAgeing(projectId, agedNcr, now, validUntil));
        }

        // ---- ISSUE_AGEING: snags ----
        List<SnagResponse> agedSnag = openSnag.stream()
                .filter(s -> ageDays(snagOpenTs(s), now) > SNAG_AGE_DAYS)
                .sorted((a, b) -> Long.compare(ageDays(snagOpenTs(b), now), ageDays(snagOpenTs(a), now)))
                .toList();
        snapshot.put("agedSnag", agedSnag.size());
        if (!agedSnag.isEmpty()) {
            candidates.add(snagAgeing(projectId, agedSnag, now, validUntil));
        }

        // ---- RECURRING_ISSUE_PATTERN ----
        Map<String, Integer> clusters = new TreeMap<>();
        for (DprIssueRow r : openDpr) {
            clusters.merge("DPR/" + safeName(r.category()), 1, Integer::sum);
        }
        for (NcrResponse n : openNcr) {
            clusters.merge("NCR/" + safeName(n.category()), 1, Integer::sum);
        }
        if (!openSnag.isEmpty()) {
            clusters.merge("SNAG", openSnag.size(), Integer::sum);
        }
        for (SafetyRecordResponse r : openSafety) {
            clusters.merge("SAFETY/" + safeName(r.kind()), 1, Integer::sum);
        }
        ArrayNode patternsSnap = snapshot.putArray("patterns");
        for (Map.Entry<String, Integer> e : clusters.entrySet()) {
            if (e.getValue() >= PATTERN_MIN) {
                ObjectNode p = patternsSnap.addObject();
                p.put("cluster", e.getKey());
                p.put("count", e.getValue());
                candidates.add(recurringPattern(projectId, e.getKey(), e.getValue(), validUntil));
            }
        }

        // Most-severe first for a stable, meaningful narration order.
        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    // ---------------------------------------------------------------- findings

    private AgentFindingDraft hseOpenCritical(UUID projectId, List<HseItem> hse, Instant now, Instant validUntil) {
        long oldestHours = 0;
        HseItem oldest = null;
        boolean fatality = false;
        for (HseItem h : hse) {
            long hours = ageHours(h.openTs(), now);
            if (hours >= oldestHours) {
                oldestHours = hours;
                oldest = h;
            }
            if (h.fatality()) fatality = true;
        }
        boolean critical = oldestHours > HSE_AGE_HOURS || fatality;
        Severity severity = critical ? Severity.CRITICAL : Severity.HIGH;

        long dprCount = hse.stream().filter(h -> "DPR".equals(h.source())).count();
        long ncrCount = hse.stream().filter(h -> "NCR".equals(h.source())).count();
        long safetyCount = hse.stream().filter(h -> "SAFETY".equals(h.source())).count();
        String oldestLabel = oldest != null ? oldest.label() : "an HSE issue";
        String ageText = humanAge(oldestHours);

        List<EvidenceRef> evidence = new ArrayList<>();
        evidence.add(EvidenceRef.metric("Open HSE/safety issues", String.valueOf(hse.size())));
        evidence.add(EvidenceRef.metric("Oldest open for", ageText));
        evidence.add(EvidenceRef.metric("By log",
                dprCount + " DPR · " + ncrCount + " NCR · " + safetyCount + " safety"));
        if (fatality) {
            evidence.add(EvidenceRef.metric("Fatality on log", "Yes"));
        }
        for (HseItem h : hse.stream().limit(4).toList()) {
            evidence.add(EvidenceRef.entity(h.source() + " issue", h.label(), h.entityType(), h.id(),
                    h.linkUrl(projectId)));
        }

        String impact = "An unresolved HSE/safety issue is an immediate people-safety and compliance exposure; "
                + "left open it risks injury, statutory penalties, and a stop-work order."
                + (fatality ? " A fatality is already recorded on the log." : "");
        return new AgentFindingDraft(
                "HSE_OPEN_CRITICAL",
                "PROJECT",
                severity,
                0.9,
                "Direct count of " + hse.size() + " open HSE/safety records (no statistical estimation)",
                hse.size() + " open HSE/safety issue" + (hse.size() == 1 ? "" : "s") + " require immediate attention",
                hse.size() + " HSE/safety issue" + (hse.size() == 1 ? " is" : "s are") + " still open across the DPR, "
                        + "NCR and safety logs; the oldest (" + oldestLabel + ") has been open for " + ageText
                        + ", past the " + HSE_AGE_HOURS + "-hour resolution threshold"
                        + (critical ? "" : " (still within the threshold)") + ".",
                "Safety-classified issues have not been closed out — corrective action or sign-off is outstanding on "
                        + "the responsible parties.",
                impact,
                "Close out every open HSE/safety issue today: assign an owner, complete the corrective action, and "
                        + "record sign-off; escalate " + oldestLabel + " to the HSE lead now.",
                evidence,
                Map.of("HSE_MANAGER", List.of(), "SITE_MANAGER", List.of(), "PROJECT_MANAGER", List.of()),
                validUntil);
    }

    private AgentFindingDraft ncrAgeing(UUID projectId, List<NcrResponse> aged, Instant now, Instant validUntil) {
        long oldestDays = ageDays(ncrOpenTs(aged.get(0)), now);
        String oldestNo = aged.get(0).ncrNo() != null ? aged.get(0).ncrNo() : "NCR";
        List<EvidenceRef> evidence = new ArrayList<>();
        evidence.add(EvidenceRef.metric("NCRs open past " + NCR_AGE_DAYS + " days", String.valueOf(aged.size())));
        evidence.add(EvidenceRef.metric("Oldest open for", oldestDays + " days"));
        for (NcrResponse n : aged.stream().limit(4).toList()) {
            evidence.add(EvidenceRef.entity("NCR", ncrLabel(n) + " · " + ageDays(ncrOpenTs(n), now) + "d",
                    "ncr", n.id(), "/projects/" + projectId + "/quality/ncr?focus=" + n.id()));
        }
        return new AgentFindingDraft(
                "ISSUE_AGEING",
                "ncr",
                Severity.HIGH,
                0.85,
                "Age computed directly from the raised timestamp of " + aged.size() + " open NCRs",
                aged.size() + " NCR" + (aged.size() == 1 ? " has" : "s have") + " been open beyond the "
                        + NCR_AGE_DAYS + "-day threshold",
                aged.size() + " non-conformance report" + (aged.size() == 1 ? " has" : "s have") + " stayed open for "
                        + "more than " + NCR_AGE_DAYS + " days; " + oldestNo + " has been open for " + oldestDays
                        + " days.",
                "Corrective actions on these NCRs have stalled — root cause or closure sign-off is outstanding.",
                "Ageing NCRs mean unresolved quality defects sit in the works, compounding rework cost and risking "
                        + "downstream activities being built over defective work.",
                "Work the ageing NCR queue oldest-first: confirm root cause, complete corrective action, and approve "
                        + "closure on " + oldestNo + " and the others past " + NCR_AGE_DAYS + " days.",
                evidence,
                Map.of("QUALITY_MANAGER", List.of(), "SITE_MANAGER", List.of()),
                validUntil);
    }

    private AgentFindingDraft snagAgeing(UUID projectId, List<SnagResponse> aged, Instant now, Instant validUntil) {
        long oldestDays = ageDays(snagOpenTs(aged.get(0)), now);
        List<EvidenceRef> evidence = new ArrayList<>();
        evidence.add(EvidenceRef.metric("Snags open past " + SNAG_AGE_DAYS + " days", String.valueOf(aged.size())));
        evidence.add(EvidenceRef.metric("Oldest open for", oldestDays + " days"));
        for (SnagResponse s : aged.stream().limit(4).toList()) {
            evidence.add(EvidenceRef.entity("Snag", snagLabel(s) + " · " + ageDays(snagOpenTs(s), now) + "d",
                    "snag", s.id(), "/projects/" + projectId + "/quality/snags?focus=" + s.id()));
        }
        return new AgentFindingDraft(
                "ISSUE_AGEING",
                "snag",
                Severity.MEDIUM,
                0.85,
                "Age computed directly from the raised timestamp of " + aged.size() + " open snags",
                aged.size() + " snag" + (aged.size() == 1 ? " has" : "s have") + " been open beyond the "
                        + SNAG_AGE_DAYS + "-day threshold",
                aged.size() + " snag" + (aged.size() == 1 ? " has" : "s have") + " stayed open for more than "
                        + SNAG_AGE_DAYS + " days; the oldest has been open for " + oldestDays + " days.",
                "Punch-list items are not being cleared — closure is lagging behind the rate at which snags are raised.",
                "An ageing snag backlog delays handover and inflates the closeout burden at project completion.",
                "Schedule a snag-clearing push on the items past " + SNAG_AGE_DAYS + " days and confirm closure with "
                        + "the raising engineer.",
                evidence,
                Map.of("SITE_MANAGER", List.of()),
                validUntil);
    }

    private AgentFindingDraft recurringPattern(UUID projectId, String cluster, int count, Instant validUntil) {
        Severity severity = count >= 6 ? Severity.HIGH : Severity.MEDIUM;
        double confidence = Math.min(PATTERN_CONFIDENCE_CAP, 0.35 + 0.05 * count);
        String label = clusterLabel(cluster);
        return new AgentFindingDraft(
                "RECURRING_ISSUE_PATTERN",
                "pattern:" + cluster,
                severity,
                confidence,
                "Frequency of " + count + " open issues sharing type '" + label
                        + "' (heuristic clustering, capped at " + PATTERN_CONFIDENCE_CAP + ")",
                count + " open issues share the same type: " + label,
                count + " currently-open issues are all of the same type (" + label + "), well above the "
                        + PATTERN_MIN + "-issue clustering threshold.",
                "A repeated issue type points to a systemic driver — a recurring defect mode, a problem "
                        + "sub-contractor, or a gap in method or supervision — rather than isolated one-offs.",
                "Left unaddressed the same issue will keep recurring, so the true cost is the repeat rate, not any "
                        + "single occurrence.",
                "Run a root-cause review on the '" + label + "' cluster and address the systemic driver so the "
                        + "recurrence stops, not just the individual issues.",
                List.of(
                        EvidenceRef.metric("Open issues in cluster", String.valueOf(count)),
                        EvidenceRef.metric("Issue type", label),
                        EvidenceRef.metric("Clustering threshold", PATTERN_MIN + " of a kind")),
                Map.of("SITE_MANAGER", List.of(), "QUALITY_MANAGER", List.of()),
                validUntil);
    }

    // ---------------------------------------------------------------- collection

    private List<HseItem> collectHse(List<DprIssueRow> openDpr, List<NcrResponse> openNcr,
                                     List<SafetyRecordResponse> openSafety) {
        List<HseItem> out = new ArrayList<>();
        for (DprIssueRow r : openDpr) {
            if (r.hseIncidentType() != null) {
                boolean fatal = "FATALITY".equals(r.hseIncidentType().name());
                String title = r.title() != null ? r.title() : "HSE issue";
                out.add(new HseItem("DPR", title + " (" + r.hseIncidentType().name() + ")", r.id(),
                        r.openedAt(), "dpr_issue", "/issues?focus=", fatal));
            }
        }
        for (NcrResponse n : openNcr) {
            if (n.category() == NcrCategory.SAFETY) {
                out.add(new HseItem("NCR", ncrLabel(n), n.id(), ncrOpenTs(n),
                        "ncr", "/quality/ncr?focus=", false));
            }
        }
        for (SafetyRecordResponse r : openSafety) {
            out.add(new HseItem("SAFETY", safetyLabel(r), r.id(), r.occurredAt(),
                    "safety_record", "/hse/safety?focus=", false));
        }
        return out;
    }

    // ---------------------------------------------------------------- "open" predicates

    private static boolean isOpenDpr(IssueStatus s) {
        return s == IssueStatus.OPEN || s == IssueStatus.IN_PROGRESS || s == IssueStatus.BLOCKED;
    }

    private static boolean isOpenNcr(NcrStatus s) {
        return s == NcrStatus.OPEN || s == NcrStatus.IN_REVIEW;
    }

    private static boolean isOpenSnag(SnagStatus s) {
        return s == SnagStatus.OPEN || s == SnagStatus.IN_PROGRESS;
    }

    /** Safety records carry no status; INCIDENT/NEAR_MISS events are treated as open HSE concerns. */
    private static boolean isOpenSafety(SafetyKind k) {
        return k == SafetyKind.INCIDENT || k == SafetyKind.NEAR_MISS;
    }

    // ---------------------------------------------------------------- timestamps / age

    private static Instant ncrOpenTs(NcrResponse n) {
        return n.raisedAt() != null ? n.raisedAt() : n.createdAt();
    }

    private static Instant snagOpenTs(SnagResponse s) {
        return s.raisedAt() != null ? s.raisedAt() : s.createdAt();
    }

    private static long ageDays(Instant openTs, Instant now) {
        if (openTs == null) return 0;
        return Math.max(0, Duration.between(openTs, now).toDays());
    }

    private static long ageHours(Instant openTs, Instant now) {
        if (openTs == null) return 0;
        return Math.max(0, Duration.between(openTs, now).toHours());
    }

    private static String humanAge(long hours) {
        if (hours < 48) return hours + " hour" + (hours == 1 ? "" : "s");
        return (hours / 24) + " days";
    }

    // ---------------------------------------------------------------- labels

    private static String ncrLabel(NcrResponse n) {
        if (n.ncrNo() != null && !n.ncrNo().isBlank()) return n.ncrNo();
        return n.title() != null ? n.title() : "NCR";
    }

    private static String snagLabel(SnagResponse s) {
        String base = s.description() != null && !s.description().isBlank() ? s.description() : "Snag";
        if (base.length() > 60) base = base.substring(0, 57) + "…";
        return base;
    }

    private static String safetyLabel(SafetyRecordResponse r) {
        String kind = r.kind() != null ? r.kind().name() : "SAFETY";
        String desc = r.description() != null && !r.description().isBlank() ? r.description() : kind;
        if (desc.length() > 60) desc = desc.substring(0, 57) + "…";
        return kind + ": " + desc;
    }

    private static String clusterLabel(String cluster) {
        return cluster.replace('/', ' ').replace('_', ' ');
    }

    private static String safeName(Enum<?> e) {
        return e == null ? "OTHER" : e.name();
    }

    // ---------------------------------------------------------------- safe reads

    private List<DprIssueRow> safeDprIssues(UUID projectId) {
        try {
            return dprIssueService.list(projectId, null, null, null, null, null, null, null, null);
        } catch (Exception e) {
            log.debug("DPR issues unavailable for project {}: {}", projectId, e.getMessage());
            return List.of();
        }
    }

    private List<NcrResponse> safeNcrs(UUID projectId) {
        try {
            return ncrService.list(projectId, null);
        } catch (Exception e) {
            log.debug("NCRs unavailable for project {}: {}", projectId, e.getMessage());
            return List.of();
        }
    }

    private List<SnagResponse> safeSnags(UUID projectId) {
        try {
            return snagService.listByProject(projectId, null);
        } catch (Exception e) {
            log.debug("Snags unavailable for project {}: {}", projectId, e.getMessage());
            return List.of();
        }
    }

    private List<SafetyRecordResponse> safeSafety(UUID projectId) {
        try {
            return safetyService.list(projectId, null);
        } catch (Exception e) {
            log.debug("Safety records unavailable for project {}: {}", projectId, e.getMessage());
            return List.of();
        }
    }

    /** A single open HSE/safety issue, normalised across the three source logs. */
    private record HseItem(String source, String label, UUID id, Instant openTs,
                           String entityType, String linkSuffix, boolean fatality) {
        String linkUrl(UUID projectId) {
            return "/projects/" + projectId + linkSuffix + id;
        }
    }
}
