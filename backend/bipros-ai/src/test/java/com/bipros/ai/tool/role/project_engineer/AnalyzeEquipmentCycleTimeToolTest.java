package com.bipros.ai.tool.role.project_engineer;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.testsupport.AiContextFixtures;
import com.bipros.ai.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzeEquipmentCycleTimeToolTest {

    private final ObjectMapper om = new ObjectMapper();
    private final AnalyzeEquipmentCycleTimeTool tool = new AnalyzeEquipmentCycleTimeTool(om);

    @Test
    void nameAndRoles() {
        assertEquals("analyze_equipment_cycle_time", tool.name());
        assertTrue(tool.allowedRoles().contains("PROJECT_ENGINEER"));
        assertTrue(tool.allowedRoles().contains("PROJECT_MANAGER"));
        assertTrue(tool.allowedRoles().contains("SITE_MANAGER"));
    }

    @Test
    void alwaysReturnsDataUnavailable() {
        AiContext ctx = AiContextFixtures.forProfile("PROJECT_ENGINEER", UUID.randomUUID());
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success());
        assertEquals("data_unavailable", r.data().path("status").asText());
        assertTrue(r.data().path("reason").asText().toLowerCase().contains("cycle"));
        assertEquals("analyze_machine_idle_time", r.data().path("closest_available").asText());
    }
}
