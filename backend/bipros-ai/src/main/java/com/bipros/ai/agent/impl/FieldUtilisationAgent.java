package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.project.domain.model.DailyResourceDeployment;
import com.bipros.project.domain.model.DeploymentResourceType;
import com.bipros.project.domain.repository.DailyResourceDeploymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Field Utilisation agent. The DPR-actuals counterpart to {@link CapacityUtilisationAgent}: where
 * that agent reads the <em>planned</em> demand-vs-capacity profile, this one reads what the site
 * actually reported deploying each day (Section B of the Supervisor Daily Report) and measures how
 * hard that mobilised capacity worked.
 *
 * <p>Data source: {@link DailyResourceDeploymentRepository} — one row per resource per day with
 * planned vs deployed counts and worked vs idle hours. Aggregated per {@link DeploymentResourceType}
 * (manpower, equipment) over the most recent {@value #WINDOW_DAYS}-day window, it emits:
 *
 * <ul>
 *   <li>{@code UNDER_DEPLOYMENT} — a resource class mobilised well below plan (a "Labour at 88% of
 *       plan / Equipment at 63%" gap);</li>
 *   <li>{@code HIGH_IDLE_TIME} — a resource class paid but sitting idle a large share of its on-site
 *       hours (idle excavators / standing crews).</li>
 * </ul>
 *
 * <p>All numbers are direct sums of reported counts/hours, so confidence is high and scales with the
 * number of days sampled.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FieldUtilisationAgent extends AbstractAgent {

    private static final String KEY = "field_utilisation";
    private static final Duration TTL = Duration.ofDays(7);

    /** Aggregation window, counted back from the latest reported deployment date. */
    private static final int WINDOW_DAYS = 14;
    /** A class needs at least this many reported days to be worth judging. */
    private static final int MIN_DAYS = 3;
    /** Deployment ratio below this (deployed / planned) is an under-deployment gap. */
    private static final double UNDER_DEPLOY_THRESHOLD = 0.85;
    /** Idle-hours share above this (idle / worked+idle) is a high-idle finding. */
    private static final double IDLE_THRESHOLD = 0.15;

    private final DailyResourceDeploymentRepository deploymentRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Field Utilisation";
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

        List<DailyResourceDeployment> all =
                deploymentRepository.findByProjectIdOrderByLogDateAscIdAsc(projectId);
        if (all.isEmpty()) {
            return new GatherResult(snapshot, candidates);
        }

        // Window: the last WINDOW_DAYS calendar days up to the latest reported log date.
        LocalDate latest = all.get(all.size() - 1).getLogDate();
        LocalDate windowStart = latest.minusDays(WINDOW_DAYS - 1L);

        Map<DeploymentResourceType, Agg> byType = new EnumMap<>(DeploymentResourceType.class);
        for (DailyResourceDeployment d : all) {
            if (d.getLogDate() == null || d.getLogDate().isBefore(windowStart)) continue;
            Agg a = byType.computeIfAbsent(d.getResourceType(), t -> new Agg());
            a.add(d);
        }

        snapshot.put("windowStart", windowStart.toString());
        snapshot.put("windowEnd", latest.toString());

        Instant now = ctx.now() == null ? Instant.now() : ctx.now();
        Instant validUntil = now.plus(TTL);

        ArrayNode typesNode = snapshot.putArray("types");
        for (Map.Entry<DeploymentResourceType, Agg> e : byType.entrySet()) {
            DeploymentResourceType type = e.getKey();
            Agg a = e.getValue();

            ObjectNode n = typesNode.addObject();
            n.put("type", type.name());
            n.put("days", a.days.size());
            n.put("planned", a.planned);
            n.put("deployed", a.deployed);
            n.put("workedHours", round(a.worked));
            n.put("idleHours", round(a.idle));
            Double deployRatio = a.deployRatio();
            Double idleRatio = a.idleRatio();
            if (deployRatio != null) n.put("deployRatio", round(deployRatio));
            if (idleRatio != null) n.put("idleRatio", round(idleRatio));

            // Only the operational resource classes carry findings; ADMIN / CATERING stay in the
            // snapshot for context but do not raise utilisation alerts.
            if (type != DeploymentResourceType.MANPOWER && type != DeploymentResourceType.EQUIPMENT) {
                continue;
            }
            if (a.days.size() < MIN_DAYS) continue;

            if (deployRatio != null && a.planned > 0 && deployRatio < UNDER_DEPLOY_THRESHOLD) {
                candidates.add(underDeployment(projectId, type, a, deployRatio, validUntil));
            }
            if (idleRatio != null && (a.worked + a.idle) > 0 && idleRatio > IDLE_THRESHOLD) {
                candidates.add(highIdle(projectId, type, a, idleRatio, validUntil));
            }
        }

        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    private AgentFindingDraft underDeployment(UUID projectId, DeploymentResourceType type, Agg a,
                                              double deployRatio, Instant validUntil) {
        Severity severity = deployRatio < 0.6 ? Severity.HIGH : deployRatio < 0.75 ? Severity.MEDIUM : Severity.LOW;
        String label = label(type);
        int shortfall = Math.max(0, a.planned - a.deployed);
        return new AgentFindingDraft(
                "UNDER_DEPLOYMENT",
                "deployment:" + type.name(),
                severity,
                confidenceForDays(a.days.size()),
                "Reported nos_deployed vs nos_planned summed over " + a.days.size() + " days",
                label + " deployed at " + pct(deployRatio) + " of plan",
                label + " was deployed at " + pct(deployRatio) + " of the planned strength over the last "
                        + a.days.size() + " reported days (" + a.deployed + " of " + a.planned
                        + " planned resource-days; a shortfall of " + shortfall + ").",
                "The site mobilised fewer " + label.toLowerCase(Locale.ROOT) + " units than the plan called "
                        + "for — absences, late mobilisation, or resources diverted to another front.",
                "Under-deploying against plan slows the work fronts those resources were sized for and pushes "
                        + "planned progress into later days, eroding schedule float even when reported productivity looks fine.",
                "Confirm whether the plan or the mobilisation is wrong: close the " + shortfall
                        + "-unit gap where the front is schedule-critical, or re-baseline the planned strength if it is overstated.",
                List.of(
                        EvidenceRef.metric("Deployment vs plan", pct(deployRatio)),
                        EvidenceRef.metric("Deployed / planned", a.deployed + " / " + a.planned),
                        EvidenceRef.metric("Shortfall", shortfall + " resource-days"),
                        EvidenceRef.metric("Days sampled", String.valueOf(a.days.size())),
                        EvidenceRef.entity("Daily deployment log", "Open", "project", projectId,
                                "/projects/" + projectId + "/dpr")),
                Map.of("SITE_MANAGER", List.of(), "PROJECT_MANAGER", List.of()),
                validUntil);
    }

    private AgentFindingDraft highIdle(UUID projectId, DeploymentResourceType type, Agg a,
                                       double idleRatio, Instant validUntil) {
        Severity severity = idleRatio > 0.35 ? Severity.HIGH : idleRatio > 0.25 ? Severity.MEDIUM : Severity.LOW;
        String label = label(type);
        return new AgentFindingDraft(
                "HIGH_IDLE_TIME",
                "deployment:" + type.name(),
                severity,
                confidenceForDays(a.days.size()),
                "Reported idle_hours vs worked hours summed over " + a.days.size() + " days",
                label + " idle " + pct(idleRatio) + " of on-site hours",
                label + " sat idle for " + pct(idleRatio) + " of its on-site hours over the last "
                        + a.days.size() + " reported days (" + round(a.idle) + " idle vs " + round(a.worked)
                        + " worked hours).",
                type == DeploymentResourceType.EQUIPMENT
                        ? "Equipment is standing — waiting on a working face, an operator, fuel, breakdown, or the "
                                + "preceding activity — rather than producing."
                        : "Crews are on site but waiting — on materials, drawings, a work front, or the preceding "
                                + "activity — rather than producing.",
                "Idle on-site hours are paid capacity that produces nothing; sustained idle time is direct cost "
                        + "leakage and a signal the work fronts are not being fed fast enough to keep resources productive.",
                "Trace the top idle days to their cause (face not ready, material/operator gap, breakdown) and "
                        + "either resequence to keep " + label.toLowerCase(Locale.ROOT)
                        + " fed or demobilise the surplus until the front is ready.",
                List.of(
                        EvidenceRef.metric("Idle share", pct(idleRatio)),
                        EvidenceRef.metric("Idle hours", round(a.idle) + " h"),
                        EvidenceRef.metric("Worked hours", round(a.worked) + " h"),
                        EvidenceRef.metric("Days sampled", String.valueOf(a.days.size())),
                        EvidenceRef.entity("Daily deployment log", "Open", "project", projectId,
                                "/projects/" + projectId + "/dpr")),
                Map.of("SITE_MANAGER", List.of(), "PROJECT_MANAGER", List.of()),
                validUntil);
    }

    /** Confidence rises with days sampled: 3 days ≈ 0.62, 14+ ≈ 0.90. */
    private static double confidenceForDays(int days) {
        return Math.min(0.90, 0.55 + days / 200.0 * 5.0);
    }

    private static String label(DeploymentResourceType type) {
        return switch (type) {
            case MANPOWER -> "Manpower";
            case EQUIPMENT -> "Equipment";
            case ADMIN -> "Admin staff";
            case CATERING -> "Catering";
        };
    }

    private static String pct(double ratio) {
        return String.format(Locale.ROOT, "%.0f%%", ratio * 100.0);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Per-resource-type accumulator over the window. */
    private static final class Agg {
        final java.util.Set<LocalDate> days = new java.util.HashSet<>();
        int planned;
        int deployed;
        double worked;       // Σ hoursWorked (raw, for display)
        double idle;         // Σ idleHours (raw, for display)
        double workedNosH;   // Σ nosDeployed × hoursWorked (nos-weighted)
        double idleNosH;     // Σ nosDeployed × idleHours (nos-weighted)

        void add(DailyResourceDeployment d) {
            if (d.getLogDate() != null) days.add(d.getLogDate());
            if (d.getNosPlanned() != null) planned += d.getNosPlanned();
            int nos = d.getNosDeployed() != null ? d.getNosDeployed() : 0;
            deployed += nos;
            if (d.getHoursWorked() != null) {
                worked += d.getHoursWorked();
                workedNosH += nos * d.getHoursWorked();
            }
            if (d.getIdleHours() != null) {
                idle += d.getIdleHours();
                idleNosH += nos * d.getIdleHours();
            }
        }

        Double deployRatio() {
            return planned <= 0 ? null : (double) deployed / planned;
        }

        /** Nos-weighted idle ratio = Σ(nos×idle) / Σ(nos×(idle+worked)) — matches the canonical Idle Time Ratio KPI. */
        Double idleRatio() {
            double total = workedNosH + idleNosH;
            return total <= 0 ? null : idleNosH / total;
        }
    }
}
