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

class AnalyzeLabourCostPerUnitToolTest {

    private final ClickHouseTemplate ch = mock(ClickHouseTemplate.class);
    private final ObjectMapper om = new ObjectMapper();
    private final AnalyzeLabourCostPerUnitTool tool = new AnalyzeLabourCostPerUnitTool(ch, om);

    @Test
    void nameAndRoles() {
        assertEquals("analyze_labour_cost_per_unit", tool.name());
        assertTrue(tool.allowedRoles().contains("PROJECT_MANAGER"));
        assertTrue(tool.allowedRoles().contains("PORTFOLIO_MANAGER"));
        assertTrue(tool.allowedRoles().contains("COST_CONTROLLER"));
    }

    @Test
    void happyPath() {
        UUID pid = UUID.randomUUID();
        when(ch.queryForList(anyString(), anyMap())).thenReturn(List.of(
                Map.of(
                        "activity_code",   "ACT-001",
                        "actual_cost",     125000.0,
                        "qty_executed",    500.0,
                        "actual_per_unit", 250.0,
                        "budget_per_unit", 220.0,
                        "delta_pct",       13.64,
                        "unit",            "m3"
                )
        ));

        AiContext ctx = AiContextFixtures.forProfile("PROJECT_MANAGER", pid);
        ObjectNode in = JsonNodeFactory.instance.objectNode();
        ToolResult r = tool.execute(in, ctx);

        assertTrue(r.success());
        assertNotNull(r.data());
        assertTrue(r.data().path("rows").isArray());
        assertEquals(1, r.data().path("rows").size());

        // Verify key output fields are present
        com.fasterxml.jackson.databind.JsonNode row = r.data().path("rows").get(0);
        assertEquals("ACT-001", row.path("activity_code").asText());
        assertTrue(row.has("actual_per_unit"));
        assertTrue(row.has("budget_per_unit"));
        assertTrue(row.has("delta_pct"));
        assertTrue(row.has("unit"));
    }

    @Test
    void dataUnavailableWhenNoCost() {
        UUID pid = UUID.randomUUID();
        when(ch.queryForList(anyString(), anyMap())).thenReturn(List.of());

        AiContext ctx = AiContextFixtures.forProfile("PROJECT_MANAGER", pid);
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success(), "data_unavailable is still success=true with payload");
        assertEquals("data_unavailable", r.data().path("status").asText());
        assertFalse(r.data().path("reason").asText().isBlank());
    }
}
