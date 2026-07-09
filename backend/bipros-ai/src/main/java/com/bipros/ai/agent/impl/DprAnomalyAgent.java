package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.model.DprEquipment;
import com.bipros.project.domain.model.DprManpower;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.DprEquipmentRepository;
import com.bipros.project.domain.repository.DprManpowerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * DPR Anomaly agent. The productivity / data-integrity counterpart to {@link DprIntelligenceAgent}
 * (which covers reporting-workflow gaps and deliberately skips anomalies). A deterministic
 * {@link #gather} over a project's recent Daily Progress Reports — joined to their manpower and
 * equipment child lines — that surfaces the five "unusual pattern" classes the field team cares about:
 *
 * <ul>
 *   <li>{@code DPR_NO_PROGRESS_HIGH_LABOUR} — a large crew reported with zero quantity executed;</li>
 *   <li>{@code DPR_LOW_OUTPUT_HIGH_EQUIPMENT} — heavy machine-hours reported with zero output;</li>
 *   <li>{@code DPR_PRODUCTIVITY_DROP} — an activity's output-per-manhour collapsing versus its own
 *       trailing average;</li>
 *   <li>{@code DPR_DUPLICATE_ENTRY} — near-identical rows for the same (activity, date, supervisor,
 *       chainage, quantity) — a double-counting risk;</li>
 *   <li>{@code DPR_DATA_INCONSISTENCY} — impossible values (negative quantity, reversed chainage,
 *       future-dated report).</li>
 * </ul>
 *
 * <p>Every signal is a direct fact from stored numbers, so the factual anomalies carry high
 * confidence; the productivity-drop rule is a trailing-average heuristic and is scored lower.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DprAnomalyAgent extends AbstractAgent {

    private static final String KEY = "dpr_anomaly";
    private static final Duration TTL = Duration.ofDays(7);

    /** Only scan the recent window (up to the latest reported date) to keep findings current. */
    private static final int WINDOW_DAYS = 90;
    /** A crew this large (headcount) or this many person-hours with zero output is anomalous. */
    private static final int HIGH_LABOUR_NOS = 8;
    private static final double HIGH_LABOUR_HOURS = 40.0;
    /** This many machine-hours with zero output is anomalous (≈2 machines for a full day). */
    private static final double HIGH_EQUIP_HOURS = 16.0;
    /** Output-per-manhour below this fraction of the activity's trailing average is a drop. */
    private static final double PROD_DROP_RATIO = 0.40;
    /** Trailing history required before a productivity drop is trustworthy. */
    private static final int PROD_MIN_HISTORY = 5;
    /** Cap on offending examples listed as evidence per finding. */
    private static final int MAX_EXAMPLES = 5;

    private final DailyProgressReportRepository dprRepository;
    private final DprManpowerRepository manpowerRepository;
    private final DprEquipmentRepository equipmentRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "DPR Anomaly Detection";
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
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Instant validUntil = now.plus(TTL);

        List<DailyProgressReport> all = dprRepository.findByProjectIdOrderByReportDateAscIdAsc(projectId);
        List<DailyProgressReport> reported = new ArrayList<>();
        LocalDate latest = null;
        for (DailyProgressReport d : all) {
            DprApprovalStatus st = d.getApprovalStatus();
            if (st != DprApprovalStatus.SUBMITTED && st != DprApprovalStatus.APPROVED) continue;
            reported.add(d);
            if (d.getReportDate() != null && (latest == null || d.getReportDate().isAfter(latest))) {
                latest = d.getReportDate();
            }
        }
        if (reported.isEmpty() || latest == null) {
            return new GatherResult(snapshot, candidates);
        }

        LocalDate windowStart = latest.minusDays(WINDOW_DAYS - 1L);
        List<DailyProgressReport> dprs = reported.stream()
                .filter(d -> d.getReportDate() != null && !d.getReportDate().isBefore(windowStart))
                .toList();

        // Bulk-load manpower + equipment lines for the window, aggregated per DPR.
        List<UUID> ids = dprs.stream().map(DailyProgressReport::getId).toList();
        Map<UUID, double[]> labourByDpr = new HashMap<>();   // [nos, hours]
        Map<UUID, Double> equipHoursByDpr = new HashMap<>();
        if (!ids.isEmpty()) {
            for (DprManpower m : manpowerRepository.findByDprIdIn(ids)) {
                double[] agg = labourByDpr.computeIfAbsent(m.getDprId(), k -> new double[2]);
                agg[0] += m.getNos() == null ? 0 : m.getNos();
                agg[1] += dbl(m.getWorkingHours()) + dbl(m.getOtHours());
            }
            for (DprEquipment e : equipmentRepository.findByDprIdIn(ids)) {
                equipHoursByDpr.merge(e.getDprId(), dbl(e.getWorkingHours()), Double::sum);
            }
        }

        // --- Rules 1 & 2: high input, zero output ---
        List<DailyProgressReport> noProgressLabour = new ArrayList<>();
        List<DailyProgressReport> lowOutputEquip = new ArrayList<>();
        // --- Rule 5: data inconsistency (per-DPR sanity) ---
        List<String> inconsistencies = new ArrayList<>();
        // --- Rule 3 input: per (activity, date) productivity series ---
        Map<String, List<double[]>> prodSeries = new LinkedHashMap<>(); // key -> list of [epochDay, qty, hours]
        Map<String, String> activityLabel = new HashMap<>();
        // --- Rule 4 input: exact-duplicate grouping ---
        Map<String, List<DailyProgressReport>> dupGroups = new LinkedHashMap<>();

        for (DailyProgressReport d : dprs) {
            double[] lab = labourByDpr.getOrDefault(d.getId(), new double[2]);
            double manNos = lab[0], manHours = lab[1];
            double eqHours = equipHoursByDpr.getOrDefault(d.getId(), 0.0);
            BigDecimal qty = d.getQtyExecuted() == null ? BigDecimal.ZERO : d.getQtyExecuted();

            if (qty.signum() == 0 && (manNos >= HIGH_LABOUR_NOS || manHours >= HIGH_LABOUR_HOURS)) {
                noProgressLabour.add(d);
            }
            if (qty.signum() == 0 && eqHours >= HIGH_EQUIP_HOURS) {
                lowOutputEquip.add(d);
            }

            if (qty.signum() < 0) {
                inconsistencies.add(desc(d) + " — negative quantity " + qty.toPlainString());
            }
            if (d.getChainageFromM() != null && d.getChainageToM() != null
                    && d.getChainageFromM() > d.getChainageToM()) {
                inconsistencies.add(desc(d) + " — chainage runs backwards ("
                        + d.getChainageFromM() + "→" + d.getChainageToM() + ")");
            }
            if (d.getReportDate() != null && d.getReportDate().isAfter(today)) {
                inconsistencies.add(desc(d) + " — report dated in the future");
            }

            String actKey = d.getActivityId() != null ? d.getActivityId().toString()
                    : "name:" + (d.getActivityName() == null ? "" : d.getActivityName().toLowerCase(Locale.ROOT));
            activityLabel.putIfAbsent(actKey, d.getActivityName() == null ? "activity" : d.getActivityName());
            if (manHours > 0 && qty.signum() > 0 && d.getReportDate() != null) {
                prodSeries.computeIfAbsent(actKey, k -> new ArrayList<>())
                        .add(new double[]{d.getReportDate().toEpochDay(), qty.doubleValue(), manHours});
            }

            String dupKey = actKey + "|" + d.getReportDate() + "|" + d.getSupervisorUserId() + "|"
                    + d.getChainageFromM() + "|" + d.getChainageToM() + "|"
                    + qty.stripTrailingZeros().toPlainString();
            dupGroups.computeIfAbsent(dupKey, k -> new ArrayList<>()).add(d);
        }

        // --- Rule 3: productivity drop (latest point vs trailing mean, per activity) ---
        List<String> prodDrops = new ArrayList<>();
        for (var e : prodSeries.entrySet()) {
            List<double[]> pts = e.getValue();
            if (pts.size() < PROD_MIN_HISTORY + 1) continue;
            pts.sort(Comparator.comparingDouble(p -> p[0]));
            double[] last = pts.get(pts.size() - 1);
            double lastProd = last[1] / last[2];
            double sum = 0;
            for (int i = 0; i < pts.size() - 1; i++) sum += pts.get(i)[1] / pts.get(i)[2];
            double trailingMean = sum / (pts.size() - 1);
            if (trailingMean > 0 && lastProd < PROD_DROP_RATIO * trailingMean) {
                prodDrops.add(String.format(Locale.ROOT,
                        "%s — output/manhour fell to %.2f (trailing avg %.2f, %d%% drop) on %s",
                        activityLabel.get(e.getKey()), lastProd, trailingMean,
                        Math.round((1 - lastProd / trailingMean) * 100),
                        LocalDate.ofEpochDay((long) last[0])));
            }
        }

        List<List<DailyProgressReport>> duplicates = dupGroups.values().stream()
                .filter(g -> g.size() >= 2).toList();

        snapshot.put("windowStart", windowStart.toString());
        snapshot.put("windowEnd", latest.toString());
        snapshot.put("dprsScanned", dprs.size());
        snapshot.put("noProgressHighLabour", noProgressLabour.size());
        snapshot.put("lowOutputHighEquipment", lowOutputEquip.size());
        snapshot.put("productivityDrops", prodDrops.size());
        snapshot.put("duplicateGroups", duplicates.size());
        snapshot.put("dataInconsistencies", inconsistencies.size());

        // --- Emit one aggregated finding per anomaly class present ---
        if (!noProgressLabour.isEmpty()) {
            candidates.add(noProgressFinding(projectId, noProgressLabour, labourByDpr, validUntil));
        }
        if (!lowOutputEquip.isEmpty()) {
            candidates.add(lowOutputEquipFinding(projectId, lowOutputEquip, equipHoursByDpr, validUntil));
        }
        if (!prodDrops.isEmpty()) {
            candidates.add(productivityDropFinding(projectId, prodDrops, validUntil));
        }
        if (!duplicates.isEmpty()) {
            candidates.add(duplicateFinding(projectId, duplicates, validUntil));
        }
        if (!inconsistencies.isEmpty()) {
            candidates.add(inconsistencyFinding(projectId, inconsistencies, validUntil));
        }

        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    private AgentFindingDraft noProgressFinding(UUID projectId, List<DailyProgressReport> hits,
                                                Map<UUID, double[]> labourByDpr, Instant validUntil) {
        int n = hits.size();
        Severity sev = n >= 5 ? Severity.HIGH : n >= 2 ? Severity.MEDIUM : Severity.LOW;
        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("DPRs affected", String.valueOf(n)));
        for (DailyProgressReport d : hits.subList(0, Math.min(MAX_EXAMPLES, n))) {
            double[] lab = labourByDpr.getOrDefault(d.getId(), new double[2]);
            ev.add(EvidenceRef.entity(desc(d), (int) lab[0] + " workers · " + (int) lab[1] + "h · 0 output",
                    "dpr", d.getId(), "/projects/" + projectId + "/dpr"));
        }
        return new AgentFindingDraft(
                "DPR_NO_PROGRESS_HIGH_LABOUR", "PROJECT", sev, 0.9,
                "DPRs where reported crew ≥ " + HIGH_LABOUR_NOS + " or ≥ " + (int) HIGH_LABOUR_HOURS
                        + " person-hours but quantity executed is zero",
                n + " DPR" + (n == 1 ? "" : "s") + " report a full crew with zero output",
                n + " daily report" + (n == 1 ? "" : "s") + " logged a large manpower deployment "
                        + "(≥" + HIGH_LABOUR_NOS + " workers or ≥" + (int) HIGH_LABOUR_HOURS
                        + " person-hours) yet recorded zero quantity executed.",
                "Either the crew was paid but idle/blocked (no work front, materials, access) or the output "
                        + "quantity was simply not entered — both are data the numbers alone can't disambiguate.",
                "Paid labour with no recorded progress is direct cost leakage and corrupts productivity and "
                        + "earned-value rollups; if it is an entry gap, EVM understates progress until it is fixed.",
                "Check each flagged DPR: if work genuinely stalled, log the delay reason and address the blocker; "
                        + "if output was missed, complete the quantity so cost and progress reconcile.",
                ev, Map.of("SITE_MANAGER", List.of(), "PROJECT_MANAGER", List.of()), validUntil);
    }

    private AgentFindingDraft lowOutputEquipFinding(UUID projectId, List<DailyProgressReport> hits,
                                                    Map<UUID, Double> equipHoursByDpr, Instant validUntil) {
        int n = hits.size();
        Severity sev = n >= 5 ? Severity.HIGH : n >= 2 ? Severity.MEDIUM : Severity.LOW;
        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("DPRs affected", String.valueOf(n)));
        for (DailyProgressReport d : hits.subList(0, Math.min(MAX_EXAMPLES, n))) {
            double h = equipHoursByDpr.getOrDefault(d.getId(), 0.0);
            ev.add(EvidenceRef.entity(desc(d), (int) h + " machine-h · 0 output",
                    "dpr", d.getId(), "/projects/" + projectId + "/dpr"));
        }
        return new AgentFindingDraft(
                "DPR_LOW_OUTPUT_HIGH_EQUIPMENT", "PROJECT", sev, 0.9,
                "DPRs where reported equipment ≥ " + (int) HIGH_EQUIP_HOURS
                        + " machine-hours but quantity executed is zero",
                n + " DPR" + (n == 1 ? "" : "s") + " report heavy equipment use with zero output",
                n + " daily report" + (n == 1 ? "" : "s") + " logged ≥" + (int) HIGH_EQUIP_HOURS
                        + " machine-hours of equipment yet recorded zero quantity executed.",
                "Equipment ran (or was booked) without producing measurable output — standing on a blocked face, "
                        + "breakdown, or an un-entered quantity.",
                "Idle-but-charged plant is expensive dead time and it skews equipment-productivity and unit-cost "
                        + "analysis; unrecorded output understates progress against the same machine cost.",
                "Reconcile each flagged DPR: capture the breakdown/standby reason, or enter the missing output so "
                        + "equipment cost maps to real production.",
                ev, Map.of("SITE_MANAGER", List.of(), "PROJECT_MANAGER", List.of()), validUntil);
    }

    private AgentFindingDraft productivityDropFinding(UUID projectId, List<String> drops, Instant validUntil) {
        int n = drops.size();
        Severity sev = n >= 3 ? Severity.HIGH : Severity.MEDIUM;
        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Activities affected", String.valueOf(n)));
        for (String s : drops.subList(0, Math.min(MAX_EXAMPLES, n))) {
            ev.add(EvidenceRef.metric("Drop", s));
        }
        ev.add(EvidenceRef.entity("DPR log", "Open", "project", projectId, "/projects/" + projectId + "/dpr"));
        return new AgentFindingDraft(
                "DPR_PRODUCTIVITY_DROP", "PROJECT", sev, 0.75,
                "Latest output-per-manhour below " + (int) (PROD_DROP_RATIO * 100)
                        + "% of the activity's trailing average (≥" + PROD_MIN_HISTORY + " prior days)",
                n + " activit" + (n == 1 ? "y shows" : "ies show") + " a sharp productivity drop",
                "On " + n + " activit" + (n == 1 ? "y" : "ies") + " the most recent output-per-manhour fell "
                        + "below " + (int) (PROD_DROP_RATIO * 100) + "% of its own trailing average.",
                "A sudden per-manhour collapse usually means a new constraint — harder ground, rework, a weaker "
                        + "crew, tool/material shortage, or a mis-entered quantity — not normal variation.",
                "Falling productivity on active fronts quietly extends durations and inflates unit cost; catching "
                        + "it the day it drops is the difference between a one-day dip and a trend.",
                "Review the flagged activities with the supervisor for the drop cause (ground, rework, crew, "
                        + "materials) and confirm the day's quantity was entered correctly.",
                ev, Map.of("PLANNING_ENGINEER", List.of(), "PROJECT_MANAGER", List.of()), validUntil);
    }

    private AgentFindingDraft duplicateFinding(UUID projectId, List<List<DailyProgressReport>> groups,
                                               Instant validUntil) {
        int n = groups.size();
        int extraRows = groups.stream().mapToInt(g -> g.size() - 1).sum();
        Severity sev = n >= 5 ? Severity.HIGH : n >= 2 ? Severity.MEDIUM : Severity.LOW;
        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Duplicate groups", String.valueOf(n)));
        ev.add(EvidenceRef.metric("Redundant rows", String.valueOf(extraRows)));
        for (List<DailyProgressReport> g : groups.subList(0, Math.min(MAX_EXAMPLES, n))) {
            DailyProgressReport d = g.get(0);
            ev.add(EvidenceRef.entity(desc(d), g.size() + " identical rows",
                    "dpr", d.getId(), "/projects/" + projectId + "/dpr"));
        }
        return new AgentFindingDraft(
                "DPR_DUPLICATE_ENTRY", "PROJECT", sev, 0.9,
                "Groups of ≥2 DPRs identical on activity, date, supervisor, chainage and quantity",
                n + " duplicate DPR group" + (n == 1 ? "" : "s") + " (" + extraRows + " redundant rows)",
                n + " set" + (n == 1 ? "" : "s") + " of daily reports are identical on activity, date, "
                        + "supervisor, chainage and quantity — " + extraRows + " redundant row"
                        + (extraRows == 1 ? "" : "s") + " in total.",
                "The same day's work was entered more than once — a double-submit or a copy-paste during data "
                        + "entry, not two genuine work fronts.",
                "Duplicate rows double-count executed quantity, inflating progress, earned value and DPR-based "
                        + "cost — and can over-pay quantity-linked billing until they are removed.",
                "Review each duplicate group and delete the redundant rows, keeping one per genuine work entry; "
                        + "confirm cumulative quantity and BOQ progress correct themselves afterwards.",
                ev, Map.of("PROJECT_MANAGER", List.of(), "SITE_MANAGER", List.of()), validUntil);
    }

    private AgentFindingDraft inconsistencyFinding(UUID projectId, List<String> issues, Instant validUntil) {
        int n = issues.size();
        Severity sev = n >= 5 ? Severity.HIGH : Severity.MEDIUM;
        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Inconsistent rows", String.valueOf(n)));
        for (String s : issues.subList(0, Math.min(MAX_EXAMPLES, n))) {
            ev.add(EvidenceRef.metric("Issue", s));
        }
        ev.add(EvidenceRef.entity("DPR log", "Open", "project", projectId, "/projects/" + projectId + "/dpr"));
        return new AgentFindingDraft(
                "DPR_DATA_INCONSISTENCY", "PROJECT", sev, 0.95,
                "Per-DPR sanity checks: negative quantity, reversed chainage, future-dated report",
                n + " DPR" + (n == 1 ? "" : "s") + " contain impossible values",
                n + " daily report" + (n == 1 ? "" : "s") + " hold values that cannot be physically true — "
                        + "negative quantity, chainage running backwards, or a future report date.",
                "These are data-entry or import errors, not real site conditions.",
                "Impossible values corrupt every downstream rollup that trusts them (progress, EVM, quantity "
                        + "billing) and undermine confidence in the reports as a control record.",
                "Correct each flagged row at source: fix the sign, swap the chainage endpoints, or set the right "
                        + "report date, then re-submit.",
                ev, Map.of("PROJECT_MANAGER", List.of(), "SITE_MANAGER", List.of()), validUntil);
    }

    private static String desc(DailyProgressReport d) {
        String act = d.getActivityName() == null ? "activity" : d.getActivityName();
        if (act.length() > 40) act = act.substring(0, 39) + "…";
        return "DPR " + d.getReportDate() + " · " + act
                + (d.getSupervisorName() == null ? "" : " · " + d.getSupervisorName());
    }

    private static double dbl(BigDecimal v) {
        return v == null ? 0.0 : v.doubleValue();
    }
}
