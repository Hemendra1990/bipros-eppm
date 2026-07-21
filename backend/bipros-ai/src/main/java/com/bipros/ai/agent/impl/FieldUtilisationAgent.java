package com.bipros.ai.agent.impl;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.project.domain.repository.DprEquipmentRepository;
import com.bipros.project.domain.repository.DprManpowerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Field Utilisation agent. Reports how many manpower and equipment units the site has actually
 * deployed to date — summed from APPROVED Daily Progress Reports — per activity.
 *
 * <p>A planned-vs-deployed ratio is deliberately NOT computed: the resource plan's {@code planned_units}
 * is not a consistent count across manpower and equipment (manpower ≈ headcount, equipment is a much
 * larger non-count figure), so a ratio would be misleading. This agent surfaces the real deployed
 * effort; judging it against plan is left to the Capacity Utilisation view, which reads a canonical
 * planned profile.
 *
 * <p>Emits one INFO {@code FIELD_DEPLOYMENT_SUMMARY} with project totals and the activities that have
 * absorbed the most deployment. Dormant when no approved DPR has recorded any manpower or equipment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FieldUtilisationAgent extends AbstractAgent {

    private static final String KEY = "field_utilisation";
    private static final Duration TTL = Duration.ofDays(7);
    private static final int MAX_EXAMPLES = 6;

    private final DprManpowerRepository dprManpowerRepository;
    private final DprEquipmentRepository dprEquipmentRepository;
    private final ActivityRepository activityRepository;
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

        Map<UUID, Double> mpDeployed = toMap(dprManpowerRepository.sumDeployedNosByActivityApproved(projectId));
        Map<UUID, Double> eqDeployed = toMap(dprEquipmentRepository.sumDeployedNosByActivityApproved(projectId));

        if (mpDeployed.isEmpty() && eqDeployed.isEmpty()) {
            return new GatherResult(snapshot, candidates); // no approved-DPR deployment recorded
        }

        Map<UUID, String> names = new HashMap<>();
        for (Activity act : activityRepository.findByProjectId(projectId)) {
            names.put(act.getId(), act.getName());
        }

        double manpowerTotal = mpDeployed.values().stream().mapToDouble(Double::doubleValue).sum();
        double equipmentTotal = eqDeployed.values().stream().mapToDouble(Double::doubleValue).sum();

        // Per-activity deployment (manpower + equipment resource-days), most-deployed first.
        Set<UUID> ids = new HashSet<>();
        ids.addAll(mpDeployed.keySet());
        ids.addAll(eqDeployed.keySet());
        List<Row> rows = new ArrayList<>();
        for (UUID id : ids) {
            rows.add(new Row(id, name(names, id), mpDeployed.getOrDefault(id, 0.0), eqDeployed.getOrDefault(id, 0.0)));
        }
        rows.sort(Comparator.comparingDouble((Row r) -> r.manpower() + r.equipment()).reversed());

        snapshot.put("manpowerDeployed", round(manpowerTotal));
        snapshot.put("equipmentDeployed", round(equipmentTotal));
        snapshot.put("activitiesWithDeployment", rows.size());

        Instant now = ctx.now() == null ? Instant.now() : ctx.now();
        candidates.add(summary(projectId, manpowerTotal, equipmentTotal, rows, now.plus(TTL)));
        return new GatherResult(snapshot, candidates);
    }

    private AgentFindingDraft summary(UUID projectId, double manpowerTotal, double equipmentTotal,
                                      List<Row> rows, Instant validUntil) {
        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Manpower deployed", fmt(manpowerTotal) + " man-days"));
        ev.add(EvidenceRef.metric("Equipment deployed", fmt(equipmentTotal) + " equip-days"));
        ev.add(EvidenceRef.metric("Activities with deployment", String.valueOf(rows.size())));
        for (Row r : rows.subList(0, Math.min(MAX_EXAMPLES, rows.size()))) {
            ev.add(EvidenceRef.entity(activityLabel(r.name()),
                    fmt(r.manpower()) + " man-days · " + fmt(r.equipment()) + " equip-days",
                    "activity", r.activityId(),
                    "/projects/" + projectId + "/activities/" + r.activityId()));
        }
        return new AgentFindingDraft(
                "FIELD_DEPLOYMENT_SUMMARY",
                "PROJECT",
                Severity.INFO,
                0.9,
                "Σ nos from APPROVED DPR manpower/equipment lines, per activity",
                "Field deployment to date — " + fmt(manpowerTotal) + " man-days, " + fmt(equipmentTotal) + " equipment-days",
                "The site has deployed " + fmt(manpowerTotal) + " manpower-days and " + fmt(equipmentTotal)
                        + " equipment-days across " + rows.size() + " activit" + (rows.size() == 1 ? "y" : "ies")
                        + " to date (from approved DPRs).",
                "This is the actual field resource effort booked against the project so far.",
                "Use it to see where the crew and plant effort has concentrated; heavy deployment that isn't "
                        + "converting into progress is where cost leaks.",
                "Review the highest-deployment activities against their earned progress and follow up on any that "
                        + "have absorbed heavy deployment without matching output.",
                ev,
                Map.of("SITE_MANAGER", List.of(), "PROJECT_MANAGER", List.of()),
                validUntil);
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private static Map<UUID, Double> toMap(List<Object[]> rows) {
        Map<UUID, Double> m = new HashMap<>();
        for (Object[] r : rows) {
            if (r[0] instanceof UUID id && r[1] instanceof Number num) m.put(id, num.doubleValue());
        }
        return m;
    }

    private static String name(Map<UUID, String> names, UUID id) {
        String n = names.get(id);
        return n == null ? "Activity" : n;
    }

    private static String activityLabel(String name) {
        String n = name == null ? "Activity" : name;
        return n.length() > 48 ? n.substring(0, 47) + "…" : n;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private record Row(UUID activityId, String name, double manpower, double equipment) {
    }
}
