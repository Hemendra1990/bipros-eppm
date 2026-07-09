package com.bipros.ai.agent.pipeline;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The fixed catalogue of agent pipelines and their staged membership.
 *
 * <p>Each pipeline groups agents into ordered stages: agents in the same stage run in parallel,
 * stages run sequentially. Membership is grouped by domain so that leaf data-reading agents run
 * first and derived / synthesis agents run in a later stage (letting them read the earlier stage's
 * findings from agent-memory).
 *
 * <p>Pipelines are the forward-compatible source of truth: {@code AgentPipelineRunner} runs only the
 * agent keys currently present in the {@code AgentRegistry} and skips any listed key that has not yet
 * been registered (with a warn). New agents therefore auto-join their pipeline as soon as they ship.
 */
public final class AgentPipelines {

    /** Nightly full sweep of every project-scoped agent, in dependency stages. */
    public static final String DAILY_PROJECT_SWEEP = "DAILY_PROJECT_SWEEP";

    /** Reactive pipeline for field-operations changes (DPR, daily output, labour, deployment, material, expense). */
    public static final String OPERATIONS_REACTIVE = "OPERATIONS_REACTIVE";

    /** Reactive pipeline for schedule / baseline / EVM / cost changes. */
    public static final String SCHEDULE_REACTIVE = "SCHEDULE_REACTIVE";

    /** Reactive pipeline for risk / QC / issue changes. */
    public static final String RISK_REACTIVE = "RISK_REACTIVE";

    /** Weekly cross-project executive / portfolio brief (runs with a null projectId). */
    public static final String PORTFOLIO_WEEKLY = "PORTFOLIO_WEEKLY";

    /** Reactive pipeline for a document upload — analyse the doc, then dispatch. */
    public static final String DOCUMENT_REACTIVE = "DOCUMENT_REACTIVE";

    /** Reactive pipeline for a GIS snapshot analysis — check field progress, then dispatch. */
    public static final String GIS_REACTIVE = "GIS_REACTIVE";

    private static final Map<String, PipelineDefinition> BY_KEY = new LinkedHashMap<>();

    static {
        // Stage order matters: later stages read earlier stages' findings from agent memory
        // (forecasting reads planning+risk; executive reads all; notification dispatches notifiable).
        register(new PipelineDefinition(DAILY_PROJECT_SWEEP, List.of(
                // Stage 1 — leaf agents that read raw domain data.
                Set.of("capacity_utilisation", "field_utilisation", "supervisor_performance",
                        "dpr_intelligence", "dpr_anomaly", "productivity_analysis", "root_cause",
                        "dbs_validation", "issue_intelligence", "document_intelligence",
                        "gis_intelligence", "weather_risk"),
                // Stage 2 — schedule + risk (read stage-1 findings, e.g. planning reads capacity).
                Set.of("planning_intelligence", "progress_variance", "risk_intelligence"),
                // Stage 3 — forecasting (reads planning + risk).
                Set.of("forecasting"),
                // Stage 4 — synthesis over all prior findings (baseline health + executive + role briefs + history).
                Set.of("baseline_intelligence", "executive_insights", "role_briefings", "historical_learning"),
                // Stage 5 — notification dispatch (LLM-free; routes notifiable findings).
                Set.of("notification"))));

        register(new PipelineDefinition(OPERATIONS_REACTIVE, List.of(
                Set.of("dpr_intelligence", "dpr_anomaly", "productivity_analysis", "root_cause",
                        "dbs_validation", "capacity_utilisation", "field_utilisation",
                        "supervisor_performance"),
                Set.of("notification"))));

        register(new PipelineDefinition(SCHEDULE_REACTIVE, List.of(
                Set.of("planning_intelligence", "progress_variance"),
                Set.of("forecasting"),
                Set.of("baseline_intelligence"),
                Set.of("notification"))));

        register(new PipelineDefinition(RISK_REACTIVE, List.of(
                Set.of("risk_intelligence", "issue_intelligence"),
                Set.of("forecasting"),
                Set.of("notification"))));

        register(new PipelineDefinition(PORTFOLIO_WEEKLY, List.of(
                Set.of("executive_insights"),
                Set.of("notification"))));

        register(new PipelineDefinition(DOCUMENT_REACTIVE, List.of(
                Set.of("document_intelligence"),
                Set.of("notification"))));

        register(new PipelineDefinition(GIS_REACTIVE, List.of(
                Set.of("gis_intelligence"),
                Set.of("notification"))));
    }

    private static void register(PipelineDefinition def) {
        BY_KEY.put(def.key(), def);
    }

    /** Pipeline definition for {@code key}, or {@code null} if no such pipeline. */
    public static PipelineDefinition byKey(String key) {
        return BY_KEY.get(key);
    }

    public static Collection<PipelineDefinition> all() {
        return List.copyOf(BY_KEY.values());
    }

    private AgentPipelines() {
    }
}
