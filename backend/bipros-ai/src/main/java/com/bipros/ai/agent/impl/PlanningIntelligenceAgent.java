package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.baseline.application.dto.BaselineVarianceResponse;
import com.bipros.baseline.application.service.BaselineService;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.scheduling.application.dto.ScheduleHealthResponse;
import com.bipros.scheduling.application.service.ScheduleHealthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Planning Intelligence agent. Deterministic {@link #gather} that reads the stored schedule-health
 * index and the active-baseline variance, then emits fully templated {@link AgentFindingDraft}s the
 * LLM narrator may only reword.
 *
 * <p>Data sources:
 * <ul>
 *   <li>{@link ScheduleHealthService#getLatestHealth(UUID)} — the most-recent DCMA-style schedule
 *       health index (deadline slip, missing logic, high-float, criticality). Read-only; chosen over
 *       {@code calculateHealth(scheduleResultId)} because the latter <em>persists</em> a new index on
 *       every call (an unwanted side effect in a read pass) and requires resolving a schedule-result
 *       id. Both return the same {@link ScheduleHealthResponse}.</li>
 *   <li>{@link BaselineService#getVariance(UUID, UUID)} against the project's PRIMARY baseline
 *       ({@link Project#getPrimaryBaselineId()}) — per-activity schedule/cost drift vs the baseline.</li>
 * </ul>
 *
 * <p>Findings: {@code CRITICAL_PATH_SLIP}, {@code FLOAT_EROSION}, {@code LOGIC_QUALITY} (from health)
 * and {@code BASELINE_DRIFT} (from variance). Confidence is a deterministic function of the sample
 * size (scheduled/comparable activities); its basis names the schedule-health score. Where capacity
 * over-allocation is already flagged, those resources are referenced in the business impact.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanningIntelligenceAgent extends AbstractAgent {

    private static final String KEY = "planning_intelligence";
    private static final Duration TTL = Duration.ofDays(7);

    private final ScheduleHealthService scheduleHealthService;
    private final BaselineService baselineService;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Planning Intelligence";
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

        Instant validUntil = (ctx.now() == null ? Instant.now() : ctx.now()).plus(TTL);
        List<String> overAllocated = overAllocatedResourceTitles(projectId);

        // --- Schedule health (read-only latest index) ---
        ScheduleHealthResponse health = scheduleHealthService.getLatestHealth(projectId);
        if (health != null) {
            int totalActivities = nz(health.totalActivities());
            int critical = nz(health.criticalActivities());
            int nearCritical = nz(health.nearCriticalActivities());
            double score = nz(health.healthScore());
            double missingLogicPct = nz(health.missingLogicPct());
            double highFloatPct = nz(health.highFloatPct());
            double slipRatio = nz(health.deadlineSlipRatio());
            int slipDays = nz(health.deadlineSlipDays());
            double avgFloat = nz(health.totalFloatAverage());

            ObjectNode h = snapshot.putObject("health");
            h.put("totalActivities", totalActivities);
            h.put("critical", critical);
            h.put("nearCritical", nearCritical);
            h.put("healthScore", round(score));
            h.put("missingLogicPct", round(missingLogicPct));
            h.put("highFloatPct", round(highFloatPct));
            h.put("deadlineSlipRatio", round(slipRatio));
            h.put("deadlineSlipDays", slipDays);
            h.put("avgTotalFloat", round(avgFloat));

            if (slipDays > 0) {
                candidates.add(criticalPathSlip(projectId, health, score, totalActivities,
                        slipDays, slipRatio, critical, overAllocated, validUntil));
            }
            double tightPct = totalActivities > 0
                    ? (double) (critical + nearCritical) / totalActivities : 0.0;
            if (tightPct >= 0.25 && totalActivities >= 5) {
                candidates.add(floatErosion(projectId, score, totalActivities,
                        critical, nearCritical, tightPct, avgFloat, validUntil));
            }
            if (missingLogicPct > 0.05) {
                candidates.add(logicQuality(projectId, score, totalActivities,
                        missingLogicPct, highFloatPct, validUntil));
            }
        }

        // --- Baseline drift (PRIMARY baseline vs current schedule) ---
        UUID baselineId = projectRepository.findById(projectId)
                .map(Project::getPrimaryBaselineId).orElse(null);
        if (baselineId != null) {
            List<BaselineVarianceResponse> variance = safeVariance(projectId, baselineId);
            int comparable = 0;
            int drifted = 0;
            long maxSlip = 0;
            BaselineVarianceResponse worst = null;
            for (BaselineVarianceResponse v : variance) {
                if (!v.comparable()) continue;
                comparable++;
                long finishVar = v.finishVarianceDays() != null ? v.finishVarianceDays() : 0L;
                if (finishVar > 0) {
                    drifted++;
                    if (finishVar > maxSlip) {
                        maxSlip = finishVar;
                        worst = v;
                    }
                }
            }

            if (comparable > 0) {
                ObjectNode b = snapshot.putObject("baseline");
                b.put("baselineId", baselineId.toString());
                b.put("comparable", comparable);
                b.put("drifted", drifted);
                b.put("maxFinishSlipDays", maxSlip);
            }

            if (drifted > 0 && maxSlip >= 3) {
                candidates.add(baselineDrift(projectId, baselineId, comparable, drifted, maxSlip,
                        worst, overAllocated, validUntil));
            }
        }

        // Most-severe first, so narration order is stable and meaningful.
        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    // ---------------------------------------------------------------- findings

    private AgentFindingDraft criticalPathSlip(UUID projectId, ScheduleHealthResponse health,
                                               double score, int totalActivities, int slipDays,
                                               double slipRatio, int critical,
                                               List<String> overAllocated, Instant validUntil) {
        Severity severity = slipRatio >= 0.20 ? Severity.CRITICAL
                : slipRatio >= 0.10 ? Severity.HIGH
                : slipRatio >= 0.05 ? Severity.MEDIUM
                : Severity.LOW;
        String plannedFinish = health.plannedFinish() != null ? health.plannedFinish().toString() : "the plan date";
        String scheduledFinish = health.scheduledFinish() != null ? health.scheduledFinish().toString() : "later";
        String impact = "A finish " + slipDays + " days past the committed date exposes the project to "
                + "liquidated-damages and milestone-payment risk; the " + critical
                + " critical activities have no float to absorb further delay."
                + capacitySuffix(overAllocated);
        return new AgentFindingDraft(
                "CRITICAL_PATH_SLIP",
                "PROJECT",
                severity,
                healthConfidence(totalActivities),
                confidenceBasis(score, totalActivities),
                "Critical path slips " + slipDays + " days past the planned finish",
                "The scheduled project finish (" + scheduledFinish + ") is " + slipDays
                        + " days later than the planned finish (" + plannedFinish + "), a "
                        + pct(slipRatio) + " overrun of the planned duration.",
                "Critical-path activities have consumed their float and the network end date has pushed "
                        + "beyond the committed deadline.",
                impact,
                "Fast-track or crash the driving critical activities, or re-sequence within available logic; "
                        + "if the date is unrecoverable, escalate a schedule change request now.",
                List.of(
                        EvidenceRef.metric("Deadline slip", slipDays + " days"),
                        EvidenceRef.metric("Slip vs planned duration", pct(slipRatio)),
                        EvidenceRef.metric("Critical activities", String.valueOf(critical)),
                        EvidenceRef.metric("Schedule health score", scoreLabel(score)),
                        EvidenceRef.entity("Schedule health", "Open", "schedule", projectId,
                                "/projects/" + projectId + "/schedule-health")),
                Map.of("PROJECT_MANAGER", List.of(), "SITE_MANAGER", List.of()),
                validUntil);
    }

    private AgentFindingDraft floatErosion(UUID projectId, double score, int totalActivities,
                                           int critical, int nearCritical, double tightPct,
                                           double avgFloat, Instant validUntil) {
        Severity severity = tightPct >= 0.60 ? Severity.CRITICAL
                : tightPct >= 0.45 ? Severity.HIGH
                : Severity.MEDIUM;
        return new AgentFindingDraft(
                "FLOAT_EROSION",
                "PROJECT",
                severity,
                healthConfidence(totalActivities),
                confidenceBasis(score, totalActivities),
                pct(tightPct) + " of activities are critical or near-critical",
                critical + " critical and " + nearCritical + " near-critical (≤5 days float) activities make up "
                        + pct(tightPct) + " of the " + totalActivities + "-activity network; the average total "
                        + "float is " + fmt(avgFloat) + " days.",
                "Float has eroded across the network — concurrent chains and tight logic leave little slack, "
                        + "so a delay on almost any path now threatens the end date.",
                "With so little float remaining, small disruptions cascade directly into schedule slip and there is "
                        + "minimal room to re-level resources or absorb rework.",
                "Review the near-critical chains for logic that can be relaxed or resequenced, and protect the "
                        + "driving paths; prioritise progress on the " + critical + " zero-float activities.",
                List.of(
                        EvidenceRef.metric("Critical + near-critical share", pct(tightPct)),
                        EvidenceRef.metric("Critical activities", String.valueOf(critical)),
                        EvidenceRef.metric("Near-critical activities", String.valueOf(nearCritical)),
                        EvidenceRef.metric("Average total float", fmt(avgFloat) + " days"),
                        EvidenceRef.entity("Schedule health", "Open", "schedule", projectId,
                                "/projects/" + projectId + "/schedule-health")),
                Map.of("PROJECT_MANAGER", List.of()),
                validUntil);
    }

    private AgentFindingDraft logicQuality(UUID projectId, double score, int totalActivities,
                                           double missingLogicPct, double highFloatPct,
                                           Instant validUntil) {
        Severity severity = missingLogicPct >= 0.35 ? Severity.CRITICAL
                : missingLogicPct >= 0.20 ? Severity.HIGH
                : missingLogicPct >= 0.10 ? Severity.MEDIUM
                : Severity.LOW;
        int openActivities = (int) Math.round(missingLogicPct * totalActivities);
        return new AgentFindingDraft(
                "LOGIC_QUALITY",
                "PROJECT",
                severity,
                healthConfidence(totalActivities),
                confidenceBasis(score, totalActivities),
                pct(missingLogicPct) + " of activities are missing schedule logic",
                "About " + openActivities + " of " + totalActivities + " activities (" + pct(missingLogicPct)
                        + ") have no predecessor or successor link, exceeding the DCMA 5% missing-logic threshold; "
                        + pct(highFloatPct) + " also carry excessive (>44-day) float.",
                "Open ends and dangling activities mean the CPM calculation cannot propagate dates through those "
                        + "nodes, so the critical path and float values are unreliable.",
                "An incompletely linked network produces a schedule that looks healthier than it is: forecast dates, "
                        + "float, and any Monte Carlo risk analysis built on it are all understated.",
                "Close the open ends by adding the missing predecessor/successor logic, then re-run the schedule so "
                        + "the critical path and float reflect the true network.",
                List.of(
                        EvidenceRef.metric("Missing logic", pct(missingLogicPct)),
                        EvidenceRef.metric("Activities without logic", openActivities + " of " + totalActivities),
                        EvidenceRef.metric("High-float (>44d) share", pct(highFloatPct)),
                        EvidenceRef.metric("Schedule health score", scoreLabel(score)),
                        EvidenceRef.entity("Schedule health", "Open", "schedule", projectId,
                                "/projects/" + projectId + "/schedule-health")),
                Map.of("PROJECT_MANAGER", List.of()),
                validUntil);
    }

    private AgentFindingDraft baselineDrift(UUID projectId, UUID baselineId, int comparable,
                                            int drifted, long maxSlip, BaselineVarianceResponse worst,
                                            List<String> overAllocated, Instant validUntil) {
        Severity severity = maxSlip >= 30 ? Severity.CRITICAL
                : maxSlip >= 14 ? Severity.HIGH
                : maxSlip >= 5 ? Severity.MEDIUM
                : Severity.LOW;
        double driftedShare = comparable > 0 ? (double) drifted / comparable : 0.0;
        String worstName = worst != null ? worst.activityName() : "an activity";
        List<EvidenceRef> evidence = new ArrayList<>();
        evidence.add(EvidenceRef.metric("Activities drifted late", drifted + " of " + comparable));
        evidence.add(EvidenceRef.metric("Drifted share", pct(driftedShare)));
        evidence.add(EvidenceRef.metric("Worst finish slip", maxSlip + " days"));
        if (worst != null) {
            evidence.add(EvidenceRef.entity("Worst-drifting activity", worstName, "activity",
                    worst.activityId(),
                    "/projects/" + projectId + "/activities/" + worst.activityId()));
        }
        evidence.add(EvidenceRef.entity("Baseline", "Open baseline comparison", "baseline", baselineId,
                "/projects/" + projectId + "/baselines"));

        return new AgentFindingDraft(
                "BASELINE_DRIFT",
                "PROJECT",
                severity,
                baselineConfidence(comparable),
                comparable + " of the baseline's activities are comparable to the current schedule",
                drifted + " activities have drifted late of the baseline (worst " + maxSlip + " days)",
                drifted + " of " + comparable + " comparable activities now finish later than the approved baseline; "
                        + worstName + " is the worst, finishing " + maxSlip + " days late.",
                "Progress has slipped against the plan of record — actual and forecast finish dates have moved out "
                        + "relative to the baseline the project committed to.",
                "Unchecked baseline drift erodes stakeholder confidence and, if the trend continues, pushes the "
                        + "end date and cost-to-complete beyond the approved plan." + capacitySuffix(overAllocated),
                "Investigate the drivers behind " + worstName + " and the other late activities; recover within float "
                        + "where possible, or trigger a formal re-baseline if the variance is now structural.",
                evidence,
                Map.of("PROJECT_MANAGER", List.of(), "SITE_MANAGER", List.of()),
                validUntil);
    }

    // ---------------------------------------------------------------- helpers

    /** Titles of active capacity over-allocation findings, for business-impact enrichment. Null-safe. */
    private List<String> overAllocatedResourceTitles(UUID projectId) {
        if (runtime == null) return List.of();
        try {
            return runtime.memory()
                    .activeFindings(projectId, Set.of("capacity_utilisation"), Severity.MEDIUM)
                    .stream()
                    .filter(f -> "RESOURCE_OVERALLOCATION".equals(f.getFindingType()))
                    .map(AgentFinding::getTitle)
                    .filter(Objects::nonNull)
                    .limit(3)
                    .toList();
        } catch (Exception e) {
            log.debug("Capacity memory read failed for project {}: {}", projectId, e.getMessage());
            return List.of();
        }
    }

    private List<BaselineVarianceResponse> safeVariance(UUID projectId, UUID baselineId) {
        try {
            return baselineService.getVariance(projectId, baselineId);
        } catch (Exception e) {
            log.debug("Baseline variance unavailable for project {} baseline {}: {}",
                    projectId, baselineId, e.getMessage());
            return List.of();
        }
    }

    private static String capacitySuffix(List<String> overAllocated) {
        if (overAllocated == null || overAllocated.isEmpty()) return "";
        return " Capacity pressure is already flagged on: " + String.join("; ", overAllocated) + ".";
    }

    /** Confidence rises with the number of scheduled activities: 100 acts ≈ 0.80, 350+ ≈ 0.95. */
    private static double healthConfidence(int totalActivities) {
        return Math.min(0.95, 0.6 + totalActivities / 500.0);
    }

    private static double baselineConfidence(int comparable) {
        return Math.min(0.95, 0.55 + comparable / 200.0);
    }

    private static String confidenceBasis(double score, int totalActivities) {
        return "Schedule health index (score " + scoreLabel(score) + "/100) computed over "
                + totalActivities + " scheduled activities";
    }

    private static String scoreLabel(double score) {
        return String.format(Locale.ROOT, "%.0f", score);
    }

    private static String pct(double ratio) {
        return String.format(Locale.ROOT, "%.0f%%", ratio * 100.0);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static double nz(Double v) {
        return v == null ? 0.0 : v;
    }
}
