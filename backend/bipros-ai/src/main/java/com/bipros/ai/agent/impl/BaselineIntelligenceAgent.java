package com.bipros.ai.agent.impl;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.model.ActivityType;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.baseline.application.dto.BaselineResponse;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Baseline Intelligence agent — the "Senior Planning Engineer". A synthesis agent (stage 4) that
 * validates and scores the project's baseline schedule and nudges the team to keep it live.
 *
 * <p>It complements, rather than duplicates, the existing schedule agents: {@code planning_intelligence}
 * already emits critical-path slip / float erosion / logic quality / baseline drift, {@code forecasting}
 * owns the probabilistic completion forecast, {@code progress_variance} owns SPI/milestone variance and
 * {@code capacity_utilisation} owns resource feasibility. This agent adds what none of them do:
 *
 * <ul>
 *   <li>{@code BASELINE_HEALTH_SCORE} — a single composite 0–100 over schedule / execution / resource /
 *       risk, with the weakest dimension called out (responsibility #9);</li>
 *   <li>governance nudges — {@code SCHEDULE_NOT_RUN}, {@code NO_BASELINE}, {@code BASELINE_STALE} — the
 *       "create a baseline and compare against actuals" prompt for PM/CM;</li>
 *   <li>quality-gap checks the DCMA health index misses — {@code OPEN_ENDED_ACTIVITIES},
 *       {@code NEGATIVE_FLOAT}, {@code DUPLICATE_ACTIVITIES}, {@code MISSING_MILESTONES} (#1);</li>
 *   <li>{@code CONSTRUCTION_LOGIC_VIOLATION} — technically-infeasible sequencing (#2);</li>
 *   <li>{@code SCHEDULE_COMPRESSION_OPPORTUNITY} — the project is behind but the critical / near-critical
 *       activities leave room to crash, fast-track or parallelise and recover time (#4).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaselineIntelligenceAgent extends AbstractAgent {

    private static final String KEY = "baseline_intelligence";
    private static final Duration TTL = Duration.ofDays(7);
    private static final int MAX_EXAMPLES = 6;

    private final ScheduleHealthService scheduleHealthService;
    private final BaselineService baselineService;
    private final ProjectRepository projectRepository;
    private final ActivityRepository activityRepository;
    private final ActivityRelationshipRepository relationshipRepository;
    private final ObjectMapper objectMapper;

    /** Construction phase tiers (earlier tier must precede later tier). First keyword hit wins. */
    private static final List<String[]> PHASE_TIERS = List.of(
            new String[]{"clearing", "site clearance", "excavation", "earthwork", "subgrade", "cut", "fill"},
            new String[]{"piling", "foundation", "pile cap", "footing", "blinding"},
            new String[]{"structural", "concrete", "pier", "abutment", "deck", "column", "beam", "slab"},
            new String[]{"sub-base", "subbase", "gsb", "wmm", "granular", "base course"},
            new String[]{"asphalt", "bitumen", "paving", "wearing course", "surfacing", "dbm", "bc "},
            new String[]{"finishing", "marking", "signage", "electrical", "lighting", "landscaping", "handover"});

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Baseline Intelligence";
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

        Project project = projectRepository.findById(projectId).orElse(null);
        ScheduleHealthResponse health = safe(() -> scheduleHealthService.getLatestHealth(projectId));
        List<BaselineResponse> baselines = safe(() -> baselineService.listBaselines(projectId));
        boolean hasBaseline = (project != null && project.getPrimaryBaselineId() != null)
                || (baselines != null && !baselines.isEmpty());

        snapshot.put("scheduleRun", health != null);
        snapshot.put("hasBaseline", hasBaseline);
        snapshot.put("healthScore", health != null && health.healthScore() != null ? health.healthScore() : -1);

        // --- Governance nudges: get the baseline created, run and kept live ---
        if (!hasBaseline) {
            candidates.add(noBaseline(projectId, validUntil));
        }
        if (health == null) {
            candidates.add(scheduleNotRun(projectId, hasBaseline, validUntil));
            // Without a computed schedule most checks are dormant; still run the structural ones below.
        } else if (Boolean.TRUE.equals(health.stale())) {
            candidates.add(baselineStale(projectId, validUntil));
        }

        // --- Composite Baseline Health Score (#9) — needs a computed schedule ---
        if (health != null && health.healthScore() != null) {
            candidates.add(healthScore(projectId, health, validUntil));
        }

        // --- Structural quality checks (read from activities + relationships) ---
        List<Activity> activities = activityRepository.findByProjectId(projectId);
        List<ActivityRelationship> rels = relationshipRepository.findByProjectId(projectId);
        int negativeFloat = qualityChecks(projectId, activities, rels, snapshot, candidates, validUntil);

        // --- Schedule optimisation (#4): behind, but there is room to recover time ---
        if (health != null) {
            Severity execSeverity = maxSeverity(projectId, Set.of("forecasting", "progress_variance"));
            boolean behind = (health.deadlineSlipDays() != null && health.deadlineSlipDays() > 0)
                    || negativeFloat > 0
                    || execSeverity.ordinal() >= Severity.HIGH.ordinal();
            snapshot.put("compressionOpportunity", behind);
            if (behind) {
                candidates.add(scheduleCompression(projectId, health, execSeverity, validUntil));
            }
        }

        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    // ---------------------------------------------------------------- health score

    private AgentFindingDraft healthScore(UUID projectId, ScheduleHealthResponse h, Instant validUntil) {
        double schedule = clamp(h.healthScore());
        double execution = 100 - penalty(maxSeverity(projectId, Set.of("progress_variance", "forecasting")));
        double resource = 100 - penalty(maxSeverity(projectId,
                Set.of("capacity_utilisation", "field_utilisation")));
        double risk = 100 - penalty(maxSeverity(projectId, Set.of("risk_intelligence")));

        double composite = schedule * 0.40 + execution * 0.20 + resource * 0.20 + risk * 0.20;
        int score = (int) Math.round(composite);

        // Weakest weighted dimension drives the recommendation.
        Map<String, Double> dims = new LinkedHashMap<>();
        dims.put("Schedule quality", schedule);
        dims.put("Execution (progress/forecast)", execution);
        dims.put("Resource feasibility", resource);
        dims.put("Risk exposure", risk);
        String weakest = dims.entrySet().stream().min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("Schedule quality");

        // Bands match the project's single RiskLevel (ScheduleHealthService.determineRiskLevel):
        // >=80 low, 60-79 medium, 40-59 high, <40 critical — so this score reads on the same scale.
        Severity severity = score < 40 ? Severity.CRITICAL : score < 60 ? Severity.HIGH
                : score < 80 ? Severity.MEDIUM : Severity.INFO;
        String grade = score < 40 ? "critical" : score < 60 ? "high risk"
                : score < 80 ? "medium risk" : "low risk";

        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Baseline Readiness Score", score + " / 100"));
        dims.forEach((k, v) -> ev.add(EvidenceRef.metric(k, (int) Math.round(v) + " / 100")));
        ev.add(EvidenceRef.metric("Missing logic", pct(h.missingLogicPct())));
        ev.add(EvidenceRef.metric("High float", pct(h.highFloatPct())));
        ev.add(EvidenceRef.entity("Schedule health page", "Open", "project", projectId,
                "/projects/" + projectId + "/activities"));

        return new AgentFindingDraft(
                "BASELINE_HEALTH_SCORE", "PROJECT", severity, 0.85,
                "Composite of schedule quality (40%), execution (20%), resource feasibility (20%) and risk (20%)",
                "Baseline Readiness " + score + "/100 — " + grade + " (weakest: " + weakest + ")",
                "The baseline scores " + score + "/100 overall — schedule quality " + (int) schedule
                        + ", execution " + (int) execution + ", resource feasibility " + (int) resource
                        + ", risk exposure " + (int) risk + ". The weakest dimension is " + weakest + ".",
                "A single health score turns the scattered schedule, execution, resource and risk signals into "
                        + "one planner-grade verdict on whether the baseline is realistic and executable.",
                score < 60
                        ? "A " + grade + " baseline means execution is being steered by a plan that is not sound — "
                        + "slippage and rework are being built in before they are visible in the actuals."
                        : "A healthy baseline is a reliable control benchmark; keep it current as the schedule evolves.",
                "Prioritise the weakest dimension (" + weakest + "): "
                        + recForDimension(weakest) + " Then re-run the schedule and re-check the score.",
                ev, Map.of("PLANNING_ENGINEER", List.of(), "PROJECT_MANAGER", List.of()), validUntil);
    }

    private static String recForDimension(String weakest) {
        return switch (weakest) {
            case "Execution (progress/forecast)" ->
                    "close the actual-vs-plan gap on the lagging fronts and re-forecast completion.";
            case "Resource feasibility" ->
                    "resolve the resource over-allocation / idle-capacity findings before committing the plan.";
            case "Risk exposure" ->
                    "mitigate the open schedule-linked risks and add time contingency where they bite.";
            default -> "fix the open-ended logic, negative float and sequencing findings, then re-run CPM.";
        };
    }

    // ---------------------------------------------------------------- schedule optimisation

    private AgentFindingDraft scheduleCompression(UUID projectId, ScheduleHealthResponse h,
                                                  Severity execSeverity, Instant validUntil) {
        int slip = h.deadlineSlipDays() != null ? h.deadlineSlipDays() : 0;
        int critical = h.criticalActivities() != null ? h.criticalActivities() : 0;
        int nearCritical = h.nearCriticalActivities() != null ? h.nearCriticalActivities() : 0;
        boolean memCritical = execSeverity == Severity.CRITICAL;
        // Recoverable time is measured in DAYS = the deadline slip we must claw back. near-critical is an
        // activity COUNT (not days) and must not enter this figure; the crash/fast-track analysis quantifies
        // the precise recoverable time and cost.
        int recoverableDays = slip;
        String recPhrase = recoverableDays > 0 ? "~" + recoverableDays + " days recoverable" : "recoverable time available";
        String recEst = recoverableDays > 0 ? "~" + recoverableDays + " days" : "meaningful time";

        Severity severity = (slip >= 30 || memCritical) ? Severity.HIGH
                : slip >= 10 ? Severity.MEDIUM : Severity.LOW;

        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Critical activities", String.valueOf(critical)));
        ev.add(EvidenceRef.metric("Near-critical", String.valueOf(nearCritical)));
        ev.add(EvidenceRef.metric("Deadline slip", slip + " days"));
        ev.add(EvidenceRef.entity("Schedule", "Open", "project", projectId,
                "/projects/" + projectId + "/activities"));

        return new AgentFindingDraft(
                "SCHEDULE_COMPRESSION_OPPORTUNITY", "PROJECT", severity, 0.75,
                "Project is behind (deadline slip / negative float / forecast risk) with critical and near-critical "
                        + "activities that can be crashed, fast-tracked or parallelised",
                "Schedule is behind — " + recPhrase + " by crashing/fast-tracking the critical path",
                "The project is running behind"
                        + (slip > 0 ? " (deadline slip " + slip + " days)" : "")
                        + " with " + critical + " critical and " + nearCritical + " near-critical activities. "
                        + "Crashing or fast-tracking the critical path and parallelising near-critical work can "
                        + "recover an estimated " + recEst + ".",
                "The current logic and durations finish later than the committed deadline, but the critical and "
                        + "near-critical activities leave room to compress the plan.",
                "Time lost to slippage compounds into liquidated damages and downstream resource clashes unless it is "
                        + "actively recovered rather than absorbed.",
                "Run a crash/fast-track analysis on the critical path and parallelise the near-critical activities "
                        + "where predecessors allow; the " + recEst + " is an indicative estimate — the "
                        + "crash analysis will quantify the recoverable time and cost precisely. Then re-run the schedule.",
                ev, Map.of("PLANNING_ENGINEER", List.of(), "PROJECT_MANAGER", List.of()), validUntil);
    }

    // ---------------------------------------------------------------- governance nudges

    private AgentFindingDraft noBaseline(UUID projectId, Instant validUntil) {
        return new AgentFindingDraft(
                "NO_BASELINE", "PROJECT", Severity.HIGH, 0.95,
                "Project has no PRIMARY baseline captured",
                "No baseline set — capture one to measure progress against",
                "This project has no baseline schedule captured, so there is nothing to compare actual progress "
                        + "against — variance, SPI and slippage cannot be computed.",
                "A baseline has not yet been captured from the current schedule.",
                "Without a baseline the project is flying blind: every downstream schedule/EVM comparison is "
                        + "unavailable and slippage is invisible until it is severe.",
                "PM/CM: capture a baseline from the current schedule (Baselines → Create), set it PRIMARY, then "
                        + "compare it against the live DPR-driven progress as execution proceeds.",
                List.of(EvidenceRef.entity("Baselines", "Create a baseline", "project", projectId,
                        "/projects/" + projectId + "/baselines")),
                Map.of("PROJECT_MANAGER", List.of()), validUntil);
    }

    private AgentFindingDraft scheduleNotRun(UUID projectId, boolean hasBaseline, Instant validUntil) {
        return new AgentFindingDraft(
                "SCHEDULE_NOT_RUN", "PROJECT", Severity.MEDIUM, 0.95,
                "No computed schedule (CPM has not been run) — no float / critical path / health index",
                "Schedule not calculated — run CPM to unlock schedule intelligence",
                "The activities and logic exist but the CPM scheduler has not been run, so there is no critical "
                        + "path, float, or schedule-health index for this project.",
                "The schedule has been built but never calculated.",
                "Until the schedule is calculated, critical-path, float-erosion, health-score and baseline-quality "
                        + "analysis all stay dark — the planning intelligence is unavailable.",
                "Run the schedule (Schedule → Calculate) to compute the critical path and float"
                        + (hasBaseline ? "" : ", then capture a baseline") + "; the schedule agents activate automatically.",
                List.of(EvidenceRef.entity("Schedule", "Run schedule", "project", projectId,
                        "/projects/" + projectId + "/activities")),
                Map.of("PLANNING_ENGINEER", List.of(), "PROJECT_MANAGER", List.of()), validUntil);
    }

    private AgentFindingDraft baselineStale(UUID projectId, Instant validUntil) {
        return new AgentFindingDraft(
                "BASELINE_STALE", "PROJECT", Severity.MEDIUM, 0.9,
                "Schedule-health index is stale — activities/logic changed since it was computed",
                "Schedule changed since last calculation — re-run CPM",
                "Activities or relationships have been edited since the schedule was last calculated, so the "
                        + "critical path, float and health score no longer reflect the current plan.",
                "The schedule was edited after the last CPM run.",
                "Stale schedule outputs mislead every downstream decision — the displayed critical path and float "
                        + "may already be wrong.",
                "Re-run the schedule to refresh the critical path, float and health; if the plan has genuinely "
                        + "moved on, capture a fresh baseline.",
                List.of(EvidenceRef.entity("Schedule", "Re-run schedule", "project", projectId,
                        "/projects/" + projectId + "/activities")),
                Map.of("PLANNING_ENGINEER", List.of()), validUntil);
    }

    // ---------------------------------------------------------------- structural quality checks

    private int qualityChecks(UUID projectId, List<Activity> activities, List<ActivityRelationship> rels,
                              ObjectNode snapshot, List<AgentFindingDraft> candidates, Instant validUntil) {
        Map<UUID, Activity> byId = new HashMap<>();
        for (Activity a : activities) byId.put(a.getId(), a);

        Set<UUID> hasPred = new HashSet<>(); // activity appears as a successor → it has a predecessor
        Set<UUID> hasSucc = new HashSet<>(); // activity appears as a predecessor → it has a successor
        for (ActivityRelationship r : rels) {
            hasSucc.add(r.getPredecessorActivityId());
            hasPred.add(r.getSuccessorActivityId());
        }

        List<Activity> openEnded = new ArrayList<>();
        List<Activity> negativeFloat = new ArrayList<>();
        Map<String, Integer> nameCounts = new LinkedHashMap<>();
        int milestones = 0;
        int workActivities = 0;

        for (Activity a : activities) {
            ActivityType t = a.getActivityType();
            if (t == ActivityType.START_MILESTONE || t == ActivityType.FINISH_MILESTONE) {
                milestones++;
                continue;
            }
            if (t == ActivityType.WBS_SUMMARY) continue;
            workActivities++;
            if (!hasPred.contains(a.getId()) || !hasSucc.contains(a.getId())) openEnded.add(a);
            if (a.getTotalFloat() != null && a.getTotalFloat() < 0) negativeFloat.add(a);
            if (a.getName() != null) {
                nameCounts.merge(a.getName().trim().toLowerCase(Locale.ROOT), 1, Integer::sum);
            }
        }
        List<String> duplicates = nameCounts.entrySet().stream()
                .filter(e -> e.getValue() >= 2).map(e -> e.getKey() + " ×" + e.getValue()).toList();

        snapshot.put("openEnded", openEnded.size());
        snapshot.put("negativeFloat", negativeFloat.size());
        snapshot.put("duplicateNames", duplicates.size());
        snapshot.put("milestones", milestones);

        if (!openEnded.isEmpty()) {
            candidates.add(openEndedFinding(projectId, openEnded, workActivities, validUntil));
        }
        if (!negativeFloat.isEmpty()) {
            candidates.add(negativeFloatFinding(projectId, negativeFloat, validUntil));
        }
        if (!duplicates.isEmpty()) {
            candidates.add(duplicateFinding(projectId, duplicates, validUntil));
        }
        if (milestones == 0 && workActivities > 0) {
            candidates.add(missingMilestonesFinding(projectId, validUntil));
        }

        List<String> violations = constructionLogicViolations(byId, rels);
        snapshot.put("logicViolations", violations.size());
        if (!violations.isEmpty()) {
            candidates.add(constructionLogicFinding(projectId, violations, validUntil));
        }
        return negativeFloat.size();
    }

    private AgentFindingDraft openEndedFinding(UUID projectId, List<Activity> open, int work, Instant validUntil) {
        int n = open.size();
        double share = work == 0 ? 0 : (double) n / work;
        Severity sev = share >= 0.4 || n >= 20 ? Severity.HIGH : share >= 0.15 || n >= 5 ? Severity.MEDIUM : Severity.LOW;
        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Open-ended activities", n + " of " + work));
        for (Activity a : open.subList(0, Math.min(MAX_EXAMPLES, n))) {
            ev.add(EvidenceRef.entity(label(a), "missing predecessor or successor", "activity", a.getId(),
                    "/projects/" + projectId + "/activities/" + a.getId()));
        }
        return new AgentFindingDraft(
                "OPEN_ENDED_ACTIVITIES", "PROJECT", sev, 0.9,
                "Work activities that are missing a predecessor and/or a successor link",
                n + " open-ended activit" + (n == 1 ? "y" : "ies") + " (dangling schedule logic)",
                n + " of " + work + " work activities are open-ended — missing a predecessor and/or a successor — "
                        + "so the network does not fully constrain them.",
                "Logic links were not completed when the schedule was built or activities were added later "
                        + "without wiring them into the network.",
                "Open ends let CPM compute optimistic dates and float that do not react to real drivers — the "
                        + "critical path and completion date are unreliable until the logic is closed.",
                "Add the missing predecessor/successor links for the flagged activities and re-run the schedule; "
                        + "only true start/finish milestones should be legitimately open-ended.",
                ev, Map.of("PLANNING_ENGINEER", List.of()), validUntil);
    }

    private AgentFindingDraft negativeFloatFinding(UUID projectId, List<Activity> neg, Instant validUntil) {
        int n = neg.size();
        double worst = neg.stream().mapToDouble(a -> a.getTotalFloat() == null ? 0 : a.getTotalFloat()).min().orElse(0);
        Severity sev = worst <= -10 || n >= 5 ? Severity.HIGH : Severity.MEDIUM;
        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Negative-float activities", String.valueOf(n)));
        ev.add(EvidenceRef.metric("Worst float", (int) worst + " days"));
        for (Activity a : neg.subList(0, Math.min(MAX_EXAMPLES, n))) {
            ev.add(EvidenceRef.entity(label(a), "float " + (int) (double) a.getTotalFloat() + "d",
                    "activity", a.getId(), "/projects/" + projectId + "/activities/" + a.getId()));
        }
        return new AgentFindingDraft(
                "NEGATIVE_FLOAT", "PROJECT", sev, 0.95,
                "Activities with total float < 0 after CPM (a constraint/deadline cannot be met on the current plan)",
                n + " activit" + (n == 1 ? "y has" : "ies have") + " negative float (worst " + (int) worst + "d)",
                n + " activit" + (n == 1 ? "y is" : "ies are") + " computed with negative total float — the "
                        + "current logic and durations cannot meet an imposed constraint or deadline; the worst is "
                        + (int) worst + " days short.",
                "A hard constraint or must-finish date is tighter than the driving logic allows.",
                "Negative float means the plan is already infeasible against its own constraints — the flagged "
                        + "activities must be shortened, resequenced, or the constraint relaxed, or the finish slips.",
                "Resolve each negative-float chain: crash or fast-track the driving activities, or renegotiate the "
                        + "constraint/deadline; re-run CPM to confirm float returns to ≥ 0.",
                ev, Map.of("PLANNING_ENGINEER", List.of(), "PROJECT_MANAGER", List.of()), validUntil);
    }

    private AgentFindingDraft duplicateFinding(UUID projectId, List<String> dups, Instant validUntil) {
        int n = dups.size();
        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Duplicate activity names", String.valueOf(n)));
        for (String d : dups.subList(0, Math.min(MAX_EXAMPLES, n))) ev.add(EvidenceRef.metric("Duplicate", d));
        ev.add(EvidenceRef.entity("Schedule", "Open", "project", projectId, "/projects/" + projectId + "/activities"));
        return new AgentFindingDraft(
                "DUPLICATE_ACTIVITIES", "PROJECT", n >= 5 ? Severity.MEDIUM : Severity.LOW, 0.85,
                "Activities sharing an identical name (possible double-entry)",
                n + " duplicate activity name" + (n == 1 ? "" : "s") + " in the schedule",
                n + " activity name" + (n == 1 ? " is" : "s are") + " used by more than one activity, which "
                        + "usually means a work item was entered twice.",
                "The same scope was added more than once during schedule build or import.",
                "Duplicate activities double-count scope and progress and confuse the critical path and BOQ "
                        + "linkage.",
                "Review the duplicated names; merge or rename the genuine duplicates so each work item appears once.",
                ev, Map.of("PLANNING_ENGINEER", List.of()), validUntil);
    }

    private AgentFindingDraft missingMilestonesFinding(UUID projectId, Instant validUntil) {
        return new AgentFindingDraft(
                "MISSING_MILESTONES", "PROJECT", Severity.LOW, 0.9,
                "Schedule contains no start/finish milestone activities",
                "No milestones defined in the schedule",
                "The schedule has work activities but no milestone activities, so there are no committed "
                        + "checkpoints to track delivery against.",
                "Milestones were not added when the schedule was built.",
                "Without milestones there is no contractual/stage checkpoint to measure milestone variance or "
                        + "report status against to the client.",
                "Add the key project milestones (major stage completions, handover) and link them into the logic "
                        + "so milestone variance can be tracked.",
                List.of(EvidenceRef.entity("Schedule", "Open", "project", projectId,
                        "/projects/" + projectId + "/activities")),
                Map.of("PLANNING_ENGINEER", List.of()), validUntil);
    }

    private AgentFindingDraft constructionLogicFinding(UUID projectId, List<String> violations, Instant validUntil) {
        int n = violations.size();
        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Sequencing violations", String.valueOf(n)));
        for (String v : violations.subList(0, Math.min(MAX_EXAMPLES, n))) ev.add(EvidenceRef.metric("Violation", v));
        ev.add(EvidenceRef.entity("Schedule", "Open", "project", projectId, "/projects/" + projectId + "/activities"));
        return new AgentFindingDraft(
                "CONSTRUCTION_LOGIC_VIOLATION", "PROJECT", n >= 3 ? Severity.HIGH : Severity.MEDIUM, 0.75,
                "Relationships where a later construction phase is sequenced before an earlier one",
                n + " construction-sequence violation" + (n == 1 ? "" : "s") + " detected",
                n + " relationship" + (n == 1 ? "" : "s") + " sequence a later construction phase before an "
                        + "earlier one (e.g. surfacing before sub-base, or structure before foundation).",
                "The activity logic was linked in an order that is not technically buildable.",
                "Building out of sequence is either impossible or forces rework and standing time; if the logic is "
                        + "wrong the schedule dates are wrong, and if the intent is real it is a serious method risk.",
                "Review each flagged pair with the construction team and correct the predecessor/successor order "
                        + "so the physical build sequence is respected.",
                ev, Map.of("PLANNING_ENGINEER", List.of(), "SITE_MANAGER", List.of()), validUntil);
    }

    // ---------------------------------------------------------------- construction logic rules

    private List<String> constructionLogicViolations(Map<UUID, Activity> byId, List<ActivityRelationship> rels) {
        List<String> out = new ArrayList<>();
        for (ActivityRelationship r : rels) {
            Activity pred = byId.get(r.getPredecessorActivityId());
            Activity succ = byId.get(r.getSuccessorActivityId());
            if (pred == null || succ == null) continue;
            int tp = tier(pred.getName());
            int ts = tier(succ.getName());
            if (tp < 0 || ts < 0) continue;
            // predecessor is a strictly-later phase than successor → the build order is inverted.
            if (tp > ts + 1) {
                out.add(label(pred) + " → " + label(succ) + " (later phase precedes earlier)");
            }
        }
        return out;
    }

    private static int tier(String name) {
        if (name == null) return -1;
        String lower = name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < PHASE_TIERS.size(); i++) {
            for (String kw : PHASE_TIERS.get(i)) {
                if (lower.contains(kw)) return i;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- helpers

    /** Max severity across active findings from the given agent keys (Severity.INFO if none). */
    private Severity maxSeverity(UUID projectId, Set<String> agentKeys) {
        Severity max = Severity.INFO;
        for (AgentFinding f : runtime.memory().activeFindings(projectId, agentKeys, Severity.LOW)) {
            if (f.getSeverity().ordinal() > max.ordinal()) max = f.getSeverity();
        }
        return max;
    }

    private static double penalty(Severity s) {
        return switch (s) {
            case CRITICAL -> 40;
            case HIGH -> 25;
            case MEDIUM -> 12;
            case LOW -> 5;
            default -> 0;
        };
    }

    private static double clamp(Double v) {
        if (v == null) return 0;
        return Math.max(0, Math.min(100, v));
    }

    private static String label(Activity a) {
        String name = a.getName() != null ? a.getName() : (a.getCode() != null ? a.getCode() : "activity");
        return name.length() > 44 ? name.substring(0, 43) + "…" : name;
    }

    private static String pct(Double ratio) {
        return ratio == null ? "n/a" : String.format(Locale.ROOT, "%.0f%%", ratio * 100.0);
    }

    private interface Sup<T> {
        T get();
    }

    private static <T> T safe(Sup<T> s) {
        try {
            return s.get();
        } catch (Exception e) {
            return null;
        }
    }
}
