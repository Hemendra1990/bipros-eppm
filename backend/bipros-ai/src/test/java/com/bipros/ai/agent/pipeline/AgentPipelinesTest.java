package com.bipros.ai.agent.pipeline;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPipelinesTest {

    @Test
    void catalogueExposesAllPipelines() {
        assertThat(AgentPipelines.all()).hasSize(7);
        assertThat(AgentPipelines.byKey(AgentPipelines.DAILY_PROJECT_SWEEP)).isNotNull();
        assertThat(AgentPipelines.byKey(AgentPipelines.OPERATIONS_REACTIVE)).isNotNull();
        assertThat(AgentPipelines.byKey(AgentPipelines.SCHEDULE_REACTIVE)).isNotNull();
        assertThat(AgentPipelines.byKey(AgentPipelines.RISK_REACTIVE)).isNotNull();
        assertThat(AgentPipelines.byKey(AgentPipelines.PORTFOLIO_WEEKLY)).isNotNull();
        assertThat(AgentPipelines.byKey(AgentPipelines.DOCUMENT_REACTIVE)).isNotNull();
        assertThat(AgentPipelines.byKey(AgentPipelines.GIS_REACTIVE)).isNotNull();
    }

    @Test
    void byKeyReturnsNullForUnknownPipeline() {
        assertThat(AgentPipelines.byKey("NOT_A_PIPELINE")).isNull();
        assertThat(AgentPipelines.byKey(null)).isNull();
    }

    @Test
    void operationsReactiveHasOrderedStages() {
        PipelineDefinition def = AgentPipelines.byKey(AgentPipelines.OPERATIONS_REACTIVE);
        assertThat(def.key()).isEqualTo(AgentPipelines.OPERATIONS_REACTIVE);
        assertThat(def.stages()).hasSize(2);
        assertThat(def.stages().get(0)).contains("dpr_intelligence", "capacity_utilisation", "dbs_validation");
        // Notification is always the final stage — it dispatches whatever the earlier agents flagged.
        assertThat(def.stages().get(1)).containsExactly("notification");
    }

    @Test
    void everyPipelineEndsWithNotification() {
        AgentPipelines.all().forEach(def -> {
            var stages = def.stages();
            assertThat(stages.get(stages.size() - 1))
                    .as("pipeline %s ends with notification", def.key())
                    .containsExactly("notification");
        });
    }

    @Test
    void pipelinesReferenceOnlyRealAgentKeys() {
        Set<String> known = Set.of("capacity_utilisation", "planning_intelligence", "dpr_intelligence",
                "dbs_validation", "forecasting", "risk_intelligence", "issue_intelligence",
                "gis_intelligence", "document_intelligence", "executive_insights", "notification",
                "weather_risk", "supervisor_performance", "field_utilisation", "dpr_anomaly",
                "progress_variance", "productivity_analysis", "root_cause",
                "role_briefings", "historical_learning", "baseline_intelligence");
        AgentPipelines.all().forEach(def ->
                assertThat(known).as("pipeline %s references only real agent keys", def.key())
                        .containsAll(def.allAgentKeys()));
    }

    @Test
    void allAgentKeysFlattensEveryStage() {
        Set<String> keys = AgentPipelines.byKey(AgentPipelines.DAILY_PROJECT_SWEEP).allAgentKeys();
        assertThat(keys).contains(
                "dpr_intelligence", "capacity_utilisation", "gis_intelligence",
                "document_intelligence", "planning_intelligence",
                "dbs_validation", "risk_intelligence", "issue_intelligence");
    }
}
