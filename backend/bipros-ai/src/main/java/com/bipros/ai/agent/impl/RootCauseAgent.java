package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Root Cause agent. A deterministic {@link #gather} that reads the free-text delay reasons recorded
 * on the project's Daily Progress Reports and buckets them into the standard construction
 * delay-cause categories, then reports which causes dominate and which recur.
 *
 * <p>Data source: {@link DailyProgressReportRepository} — every reported DPR's {@code delayReason}
 * (and, as a fallback, {@code remarks}) is keyword-matched to a category (material, equipment,
 * weather, manpower, design, approval, site-access). Findings:
 *
 * <ul>
 *   <li>{@code DELAY_ROOT_CAUSE} — the ranked cause breakdown with the dominant category;</li>
 *   <li>{@code RECURRING_DELAY_CAUSE} — a single cause recurring across many days (a systemic issue,
 *       not a one-off).</li>
 * </ul>
 *
 * <p>Dormant on projects that do not capture delay reasons (nothing to categorise). Categorisation
 * is keyword-based, so confidence is high but not certain; the evidence quotes the source reasons.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RootCauseAgent extends AbstractAgent {

    private static final String KEY = "root_cause";
    private static final Duration TTL = Duration.ofDays(7);

    /** A single cause appearing on at least this many distinct days is "recurring" / systemic. */
    private static final int RECURRING_DAYS = 5;
    private static final int MAX_EXAMPLES = 6;

    /** Ordered cause categories with their keyword signatures (first match wins). */
    private static final Map<String, String[]> CATEGORIES = new LinkedHashMap<>();
    static {
        CATEGORIES.put("Material shortage",
                new String[]{"material", "cement", "steel", "aggregate", "concrete supply", "shortage of material",
                        "supply", "stock", "rebar", "bitumen", "sand"});
        CATEGORIES.put("Equipment breakdown",
                new String[]{"breakdown", "equipment", "machine", "excavator", "repair", "spare", "plant",
                        "mechanical", "pump fail"});
        CATEGORIES.put("Weather",
                new String[]{"weather", "rain", "wind", "storm", "heat", "flood", "wet", "monsoon", "temperature"});
        CATEGORIES.put("Manpower shortage",
                new String[]{"manpower", "labour", "labor", "crew short", "worker", "absent", "shortage of labour",
                        "no workers", "workforce"});
        CATEGORIES.put("Design / drawing",
                new String[]{"design", "drawing", "revision", "gfc", "ifc", "rfi", "detail pending", "spec change"});
        CATEGORIES.put("Approval / permit",
                new String[]{"approval", "permit", "sign-off", "signoff", "clearance", "noc", "pending approval",
                        "sanction", "authorisation", "authorization"});
        CATEGORIES.put("Site access",
                new String[]{"access", "right of way", "row ", "land", "encroach", "obstruction", "utility",
                        "diversion pending", "clash"});
    }

    private final DailyProgressReportRepository dprRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Root Cause Analysis";
    }

    @Override
    public boolean supportsPortfolio() {
        return false;
    }

    @Override
    public GatherResult gather(AgentRunContext ctx) {
        UUID projectId = ctx.projectId();
        ObjectNode snapshot = objectMapper.createObjectNode();
        List<AgentFindingDraft> candidates = new ArrayList<>();
        if (projectId == null) {
            return new GatherResult(snapshot, candidates);
        }

        Instant now = ctx.now() == null ? Instant.now() : ctx.now();
        Instant validUntil = now.plus(TTL);

        Map<String, Cause> byCause = new LinkedHashMap<>();
        int reasonsSeen = 0;
        for (DailyProgressReport d : dprRepository.findByProjectIdOrderByReportDateAscIdAsc(projectId)) {
            DprApprovalStatus st = d.getApprovalStatus();
            if (st != DprApprovalStatus.SUBMITTED && st != DprApprovalStatus.APPROVED) continue;
            String text = d.getDelayReason();
            if (isBlank(text)) text = d.getRemarks();
            if (isBlank(text)) continue;
            String category = categorise(text);
            if (category == null) continue; // uncategorisable free text — not a recognised cause
            reasonsSeen++;
            Cause c = byCause.computeIfAbsent(category, k -> new Cause());
            c.count++;
            if (d.getReportDate() != null) c.days.add(d.getReportDate());
            if (c.examples.size() < MAX_EXAMPLES) {
                c.examples.add("DPR " + d.getReportDate() + " · " + trim(text, 90));
            }
        }

        snapshot.put("delayReasonsCategorised", reasonsSeen);
        snapshot.put("distinctCauses", byCause.size());
        if (byCause.isEmpty()) {
            return new GatherResult(snapshot, candidates); // dormant — no delay reasons captured
        }

        List<Map.Entry<String, Cause>> ranked = new ArrayList<>(byCause.entrySet());
        ranked.sort(Comparator.comparingInt((Map.Entry<String, Cause> e) -> e.getValue().count).reversed());

        var topRail = snapshot.putArray("causes");
        for (var e : ranked) {
            ObjectNode n = topRail.addObject();
            n.put("cause", e.getKey());
            n.put("count", e.getValue().count);
            n.put("days", e.getValue().days.size());
        }

        candidates.add(breakdown(projectId, ranked, reasonsSeen, validUntil));

        // Recurring / systemic: any cause spanning many distinct days.
        for (var e : ranked) {
            if (e.getValue().days.size() >= RECURRING_DAYS) {
                candidates.add(recurring(projectId, e.getKey(), e.getValue(), validUntil));
            }
        }

        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    private AgentFindingDraft breakdown(UUID projectId, List<Map.Entry<String, Cause>> ranked,
                                        int total, Instant validUntil) {
        Map.Entry<String, Cause> top = ranked.get(0);
        int topCount = top.getValue().count;
        double topShare = (double) topCount / total;
        Severity severity = topShare >= 0.5 || topCount >= 10 ? Severity.HIGH
                : topShare >= 0.3 || topCount >= 5 ? Severity.MEDIUM : Severity.LOW;

        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Delay reasons analysed", String.valueOf(total)));
        for (var e : ranked) {
            ev.add(EvidenceRef.metric(e.getKey(),
                    e.getValue().count + "× over " + e.getValue().days.size() + " days"));
        }
        ev.add(EvidenceRef.entity("DPR delay log", "Open", "project", projectId,
                "/projects/" + projectId + "/dpr"));

        String causeList = ranked.stream().limit(3)
                .map(e -> e.getKey().toLowerCase(Locale.ROOT) + " (" + e.getValue().count + ")")
                .reduce((a, b) -> a + ", " + b).orElse("");

        return new AgentFindingDraft(
                "DELAY_ROOT_CAUSE", "PROJECT", severity, 0.85,
                "DPR delay reasons keyword-categorised into standard delay-cause buckets",
                top.getKey() + " is the leading cause of reported delays (" + topCount + " of " + total + ")",
                "Across " + total + " reported delay reasons, the dominant cause is " + top.getKey().toLowerCase(Locale.ROOT)
                        + " (" + topCount + " occurrences, " + pct(topShare * 100) + "); the full split is " + causeList + ".",
                "Delays cluster on a small number of root causes rather than being random — the leading cause is a "
                        + "systemic constraint the project keeps hitting.",
                "Attacking the top one or two causes removes most of the recurring lost time; leaving them unaddressed "
                        + "means the same days keep being lost across activities.",
                "Own the top cause (" + top.getKey().toLowerCase(Locale.ROOT) + ") with a targeted action — expedite "
                        + "procurement, pre-position spares, resequence weather-sensitive work, or escalate the pending "
                        + "approval — and track whether its frequency falls.",
                ev, Map.of("PROJECT_MANAGER", List.of(), "PLANNING_ENGINEER", List.of()), validUntil);
    }

    private AgentFindingDraft recurring(UUID projectId, String cause, Cause c, Instant validUntil) {
        Severity severity = c.days.size() >= 12 ? Severity.HIGH : Severity.MEDIUM;
        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Cause", cause));
        ev.add(EvidenceRef.metric("Occurrences", c.count + " over " + c.days.size() + " days"));
        for (String ex : c.examples) ev.add(EvidenceRef.metric("Reported", ex));
        ev.add(EvidenceRef.entity("DPR delay log", "Open", "project", projectId, "/projects/" + projectId + "/dpr"));
        return new AgentFindingDraft(
                "RECURRING_DELAY_CAUSE", "cause:" + cause, severity, 0.85,
                "A single delay cause recurring on ≥" + RECURRING_DAYS + " distinct days",
                cause + " is recurring — " + c.days.size() + " days lost to the same cause",
                cause + " has been recorded as a delay cause on " + c.days.size() + " distinct days ("
                        + c.count + " DPR entries) — a repeating, not one-off, constraint.",
                "A cause that recurs this often is a standing gap in the delivery system (supply chain, plant "
                        + "reliability, approvals pipeline, or access), not day-to-day noise.",
                "Every recurrence is repeat lost time that compounds into schedule slip; a structural fix pays back "
                        + "across all remaining activities exposed to the same cause.",
                "Raise a standing mitigation for " + cause.toLowerCase(Locale.ROOT) + " (buffer stock, standby plant, "
                        + "an approvals fast-track, or an access plan) rather than reacting day by day.",
                ev, Map.of("PROJECT_MANAGER", List.of(), "SITE_MANAGER", List.of()), validUntil);
    }

    /** First category whose keywords appear in the reason text; null if none match. */
    private static String categorise(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (var e : CATEGORIES.entrySet()) {
            for (String kw : e.getValue()) {
                if (lower.contains(kw)) return e.getKey();
            }
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s, int max) {
        s = s.trim();
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.0f%%", v);
    }

    private static final class Cause {
        int count;
        final java.util.Set<LocalDate> days = new java.util.HashSet<>();
        final List<String> examples = new ArrayList<>();
    }
}
