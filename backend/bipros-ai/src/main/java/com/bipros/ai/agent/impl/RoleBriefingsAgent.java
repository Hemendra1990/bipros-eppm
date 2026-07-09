package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.domain.AgentFinding;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Role Briefings agent. A synthesis agent (runs after the finding-producing agents) that turns the
 * project's active findings into concise, role-tailored briefs with prioritised recommendations —
 * the "management summary" layer. Complements {@code executive_insights} (which owns the Executive
 * brief) by producing the Supervisor, Planning-Engineer and Project-Manager cuts:
 *
 * <ul>
 *   <li>{@code SUPERVISOR_DAILY_BRIEF} — next-day field priorities (ops / safety / resource findings);</li>
 *   <li>{@code PLANNING_BRIEF} — schedule impacts (progress / forecast / productivity findings);</li>
 *   <li>{@code PM_ACTION_BRIEF} — the critical actions (all HIGH/CRITICAL findings), ranked.</li>
 * </ul>
 *
 * <p>Each brief cites the underlying findings as evidence and folds their recommended actions into a
 * numbered priority list. Dormant when the project has no active findings for that role.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleBriefingsAgent extends AbstractAgent {

    private static final String KEY = "role_briefings";
    private static final Duration TTL = Duration.ofDays(2);
    private static final int MAX_ITEMS = 5;

    /** Field-operations agents feeding the Supervisor's next-day brief. */
    private static final Set<String> SUPERVISOR_AGENTS = Set.of(
            "dpr_anomaly", "field_utilisation", "capacity_utilisation", "weather_risk",
            "issue_intelligence", "root_cause", "dpr_intelligence", "supervisor_performance",
            "gis_intelligence");
    /** Schedule / forecast agents feeding the Planning Engineer's brief. */
    private static final Set<String> PLANNING_AGENTS = Set.of(
            "progress_variance", "planning_intelligence", "forecasting", "productivity_analysis");

    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Role Briefings";
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

        // All active findings for the project (this agent's own briefs excluded to avoid recursion).
        List<AgentFinding> active = runtime.memory().activeFindings(projectId, null, Severity.LOW).stream()
                .filter(f -> !KEY.equals(f.getAgentKey()))
                .sorted(Comparator
                        .comparingInt((AgentFinding f) -> f.getSeverity().ordinal()).reversed()
                        .thenComparing(Comparator.comparingDouble(AgentFinding::getConfidence).reversed()))
                .toList();
        snapshot.put("activeFindings", active.size());
        if (active.isEmpty()) {
            return new GatherResult(snapshot, candidates); // dormant — nothing to brief
        }

        List<AgentFinding> supervisor = active.stream()
                .filter(f -> SUPERVISOR_AGENTS.contains(f.getAgentKey())).limit(MAX_ITEMS).toList();
        List<AgentFinding> planning = active.stream()
                .filter(f -> PLANNING_AGENTS.contains(f.getAgentKey())).limit(MAX_ITEMS).toList();
        List<AgentFinding> critical = active.stream()
                .filter(f -> f.getSeverity() == Severity.CRITICAL || f.getSeverity() == Severity.HIGH)
                .limit(MAX_ITEMS).toList();

        if (!supervisor.isEmpty()) {
            candidates.add(brief(projectId, "SUPERVISOR_DAILY_BRIEF", "SITE_MANAGER",
                    "Supervisor brief", "next-day site priorities", supervisor, validUntil));
        }
        if (!planning.isEmpty()) {
            candidates.add(brief(projectId, "PLANNING_BRIEF", "PLANNING_ENGINEER",
                    "Planning brief", "schedule impacts to action", planning, validUntil));
        }
        if (!critical.isEmpty()) {
            candidates.add(brief(projectId, "PM_ACTION_BRIEF", "PROJECT_MANAGER",
                    "Project Manager brief", "critical actions", critical, validUntil));
        }

        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    private AgentFindingDraft brief(UUID projectId, String findingType, String roleKey, String label,
                                    String focus, List<AgentFinding> items, Instant validUntil) {
        // A brief is as urgent as its worst cited item, but never escalates beyond MEDIUM on its own —
        // except the PM brief, which is meant to mirror the criticality it summarises.
        Severity worst = items.get(0).getSeverity();
        Severity severity = findingType.equals("PM_ACTION_BRIEF")
                ? worst
                : (worst.ordinal() >= Severity.HIGH.ordinal() ? Severity.MEDIUM : Severity.LOW);

        StringBuilder what = new StringBuilder(label + " — " + items.size() + " item"
                + (items.size() == 1 ? "" : "s") + " for " + focus + ": ");
        StringBuilder actions = new StringBuilder();
        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Items", String.valueOf(items.size())));
        int i = 1;
        for (AgentFinding f : items) {
            what.append(i > 1 ? "; " : "").append(f.getTitle());
            if (f.getRecommendedAction() != null && !f.getRecommendedAction().isBlank()) {
                actions.append(i).append(") ").append(firstSentence(f.getRecommendedAction())).append("  ");
            }
            ev.add(EvidenceRef.entity("[" + f.getSeverity() + "] " + f.getTitle(),
                    humanize(f.getAgentKey()), "finding", f.getId(),
                    "/projects/" + projectId + "/ai"));
            i++;
        }

        return new AgentFindingDraft(
                findingType, "PROJECT", severity, 0.9,
                "Digest of the project's active findings relevant to this role",
                label + " — " + items.size() + " " + focus,
                what.toString(),
                "These are the active, verified findings that most affect this role right now, ranked by "
                        + "severity — a single place to see what needs attention without reading every card.",
                "A role-focused daily digest turns " + items.size() + " separate findings into one prioritised "
                        + "list, so the right person acts on the right items first.",
                actions.length() == 0
                        ? "Review the cited findings and action them in severity order."
                        : "Priority actions — " + actions.toString().trim(),
                ev, Map.of(roleKey, List.of()), validUntil);
    }

    private static String firstSentence(String s) {
        int dot = s.indexOf(". ");
        String out = dot > 0 ? s.substring(0, dot + 1) : s;
        return out.length() > 140 ? out.substring(0, 137) + "…" : out;
    }

    private static String humanize(String agentKey) {
        String[] parts = agentKey.split("_");
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return b.toString().trim();
    }
}
