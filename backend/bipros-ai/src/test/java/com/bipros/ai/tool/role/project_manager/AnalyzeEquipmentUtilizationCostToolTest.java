package com.bipros.ai.tool.role.project_manager;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.testsupport.AiContextFixtures;
import com.bipros.ai.tool.ToolResult;
import com.bipros.analytics.store.ClickHouseTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyzeEquipmentUtilizationCostToolTest {

    private final ClickHouseTemplate ch = mock(ClickHouseTemplate.class);
    private final ObjectMapper om = new ObjectMapper();
    private final AnalyzeEquipmentUtilizationCostTool tool = new AnalyzeEquipmentUtilizationCostTool(ch, om);

    @Test
    void nameAndRoles() {
        assertEquals("analyze_equipment_utilization_cost", tool.name());
        assertTrue(tool.allowedRoles().contains("PROJECT_MANAGER"));
        assertTrue(tool.allowedRoles().contains("PORTFOLIO_MANAGER"));
        assertTrue(tool.allowedRoles().contains("COST_CONTROLLER"));
        assertTrue(tool.allowedRoles().contains("RESOURCE_MANAGER"));
    }

    /**
     * Happy path: 1 equipment row with active_hours=8, idle_hours=2, hourly_rate=null (not in schema),
     * ownership=OWNED.
     * Tool must return a table with utilization_pct, cost_per_active_hour, ownership per row
     * plus an ownership-level summary.
     */
    @Test
    void happyPath() {
        UUID pid = UUID.randomUUID();

        // working_hours=8, idle_hours=2, breakdown_hours=0 → available=10, utilization=80%
        // hourly_rate not in fact table → cost_per_active_hour is null
        when(ch.queryForList(anyString(), anyMap())).thenReturn(List.of(
                Map.of(
                        "equipment_id",        "eq-row-001",
                        "equipment_name",      "Excavator CAT-320",
                        "ownership",           "OWNED",
                        "active_hours",        8.0,
                        "idle_hours",          2.0,
                        "utilization_pct",     80.0,
                        "cost_per_active_hour", 0.0   // placeholder — null coalesced to 0 in ClickHouse
                )
        ));

        AiContext ctx = AiContextFixtures.forProfile("PROJECT_MANAGER", pid);
        ObjectNode in = JsonNodeFactory.instance.objectNode();
        ToolResult r = tool.execute(in, ctx);

        assertTrue(r.success());
        assertNotNull(r.data());
        assertTrue(r.data().path("rows").isArray(), "data must contain a rows array");
        assertEquals(1, r.data().path("rows").size(), "should have 1 equipment row");

        var row = r.data().path("rows").get(0);
        assertTrue(row.has("equipment_id"),        "row must have equipment_id");
        assertTrue(row.has("equipment_name"),      "row must have equipment_name");
        assertTrue(row.has("ownership"),           "row must have ownership");
        assertTrue(row.has("active_hours"),        "row must have active_hours");
        assertTrue(row.has("idle_hours"),          "row must have idle_hours");
        assertTrue(row.has("utilization_pct"),     "row must have utilization_pct");
        assertTrue(row.has("cost_per_active_hour"),"row must have cost_per_active_hour");

        assertEquals("OWNED", row.path("ownership").asText());
        assertEquals(80.0,    row.path("utilization_pct").asDouble(), 0.01);

        // Summary section — must exist
        assertTrue(r.data().has("summary"), "result must include an ownership-level summary");
    }

    /**
     * Empty result set from ClickHouse → tool returns data_unavailable payload (success=true).
     */
    @Test
    void dataUnavailableEmpty() {
        UUID pid = UUID.randomUUID();
        when(ch.queryForList(anyString(), anyMap())).thenReturn(List.of());

        AiContext ctx = AiContextFixtures.forProfile("PROJECT_MANAGER", pid);
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success(), "data_unavailable should still be success=true with an explanatory payload");
        assertEquals("data_unavailable", r.data().path("status").asText());
        assertFalse(r.data().path("reason").asText().isBlank(), "reason must not be blank");
    }
}
