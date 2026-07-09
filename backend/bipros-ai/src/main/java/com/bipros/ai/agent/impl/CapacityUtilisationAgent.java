package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.resource.application.dto.UtilizationProfileEntry;
import com.bipros.resource.application.service.ResourceLevelingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Capacity Utilisation agent (#2). Reference implementation for all agents: a deterministic
 * {@link #gather} that reads a domain service, aggregates with rule thresholds, and emits fully
 * templated {@link AgentFindingDraft}s (numbers, severity, confidence, evidence) that the LLM
 * narrator may only reword.
 *
 * <p>Data source: {@link ResourceLevelingService#getUtilizationProfile} — the daily planned demand
 * vs capacity profile per resource. Findings: {@code RESOURCE_OVERALLOCATION}, {@code IDLE_CAPACITY}.
 * Confidence scales with the number of profile days sampled.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CapacityUtilisationAgent extends AbstractAgent {

    private static final String KEY = "capacity_utilisation";
    private static final Duration TTL = Duration.ofDays(7);

    private final ResourceLevelingService resourceLevelingService;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Capacity Utilisation";
    }

    @Override
    public boolean supportsPortfolio() {
        return false;
    }

    @Override
    public GatherResult gather(AgentRunContext ctx) {
        UUID projectId = ctx.projectId();
        List<UtilizationProfileEntry> profile = resourceLevelingService.getUtilizationProfile(projectId);

        // Aggregate the daily profile per resource.
        Map<UUID, Agg> byResource = new LinkedHashMap<>();
        for (UtilizationProfileEntry e : profile) {
            Agg a = byResource.computeIfAbsent(e.resourceId(), id -> new Agg(e.resourceName()));
            a.days++;
            a.sumUtil += e.utilization();
            a.peak = Math.max(a.peak, e.utilization());
            if (e.utilization() > 1.0) a.overDays++;
        }

        List<Agg> aggs = new ArrayList<>(byResource.values());
        aggs.sort((x, y) -> Double.compare(y.peak, x.peak));

        Instant validUntil = (ctx.now() == null ? Instant.now() : ctx.now()).plus(TTL);
        List<AgentFindingDraft> candidates = new ArrayList<>();
        ArrayNode snapshot = objectMapper.createArrayNode();

        for (var entry : byResource.entrySet()) {
            UUID resourceId = entry.getKey();
            Agg a = entry.getValue();
            double avg = a.days == 0 ? 0 : a.sumUtil / a.days;

            ObjectNode row = snapshot.addObject();
            row.put("resourceId", resourceId.toString());
            row.put("name", a.name);
            row.put("peak", round(a.peak));
            row.put("avg", round(avg));
            row.put("overDays", a.overDays);
            row.put("days", a.days);

            if (a.peak > 1.0) {
                candidates.add(overallocation(projectId, resourceId, a, avg, validUntil));
            } else if (avg < 0.4 && a.days >= 10) {
                candidates.add(idle(projectId, resourceId, a, avg, validUntil));
            }
        }

        // Re-order candidates most-severe first for a stable, meaningful narration order.
        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    private AgentFindingDraft overallocation(UUID projectId, UUID resourceId, Agg a, double avg, Instant validUntil) {
        Severity severity = a.peak >= 1.5 ? Severity.CRITICAL : a.peak >= 1.2 ? Severity.HIGH : Severity.MEDIUM;
        double confidence = confidenceForSample(a.days);
        String peakPct = pct(a.peak);
        return new AgentFindingDraft(
                "RESOURCE_OVERALLOCATION",
                "resource:" + resourceId,
                severity,
                confidence,
                "Utilisation sampled over " + a.days + " planned days",
                a.name + " is over-allocated (peak " + peakPct + ")",
                a.name + " peaks at " + peakPct + " of daily capacity and exceeds 100% on "
                        + a.overDays + " of " + a.days + " days.",
                "Concurrent activity assignments schedule more of this resource than its daily capacity supports.",
                "Sustained over-allocation risks schedule slippage and quality issues as the resource cannot meet "
                        + "all committed demand; overtime or sub-contracting cost is likely.",
                "Re-level assignments within float or add capacity for the peak window; review the activities driving "
                        + "the " + a.overDays + " over-allocated days.",
                List.of(
                        EvidenceRef.metric("Peak utilisation", peakPct),
                        EvidenceRef.metric("Average utilisation", pct(avg)),
                        EvidenceRef.metric("Days over 100%", a.overDays + " of " + a.days),
                        EvidenceRef.entity("Resource", a.name, "resource", resourceId,
                                "/projects/" + projectId + "/resources?focus=" + resourceId)),
                Map.of("SITE_MANAGER", List.of(), "PROJECT_MANAGER", List.of()),
                validUntil);
    }

    private AgentFindingDraft idle(UUID projectId, UUID resourceId, Agg a, double avg, Instant validUntil) {
        double confidence = confidenceForSample(a.days);
        return new AgentFindingDraft(
                "IDLE_CAPACITY",
                "resource:" + resourceId,
                Severity.LOW,
                confidence,
                "Utilisation sampled over " + a.days + " planned days",
                a.name + " is under-utilised (avg " + pct(avg) + ")",
                a.name + " averages only " + pct(avg) + " of daily capacity across " + a.days + " planned days.",
                "The resource is assigned to few or short-duration activities relative to its available capacity.",
                "Idle capacity is unrecovered cost and a re-deployment opportunity to relieve over-allocated "
                        + "resources elsewhere on the project.",
                "Consider re-deploying " + a.name + " to peak-demand activities or releasing the surplus capacity.",
                List.of(
                        EvidenceRef.metric("Average utilisation", pct(avg)),
                        EvidenceRef.metric("Days sampled", String.valueOf(a.days)),
                        EvidenceRef.entity("Resource", a.name, "resource", resourceId,
                                "/projects/" + projectId + "/resources?focus=" + resourceId)),
                Map.of("SITE_MANAGER", List.of()),
                validUntil);
    }

    /** Confidence rises with sample size: 30 days ≈ 0.80, 100+ days ≈ 0.95. */
    private static double confidenceForSample(int days) {
        return Math.min(0.95, 0.5 + days / 200.0);
    }

    private static String pct(double ratio) {
        return String.format(Locale.ROOT, "%.0f%%", ratio * 100.0);
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static final class Agg {
        final String name;
        int days;
        int overDays;
        double sumUtil;
        double peak;

        Agg(String name) {
            this.name = name == null ? "Unknown" : name;
        }
    }
}
