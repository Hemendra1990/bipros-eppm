package com.bipros.ai.tool.role.site_manager;

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

class AnalyzeMachineIdleTimeToolTest {

    private final ClickHouseTemplate ch = mock(ClickHouseTemplate.class);
    private final ObjectMapper om = new ObjectMapper();
    private final AnalyzeMachineIdleTimeTool tool = new AnalyzeMachineIdleTimeTool(ch, om);

    @Test
    void nameAndRoles() {
        assertEquals("analyze_machine_idle_time", tool.name());
        assertTrue(tool.allowedRoles().contains("SITE_MANAGER"));
    }

    @Test
    void surfacesEquipmentOverThreshold() {
        UUID pid = UUID.randomUUID();
        when(ch.queryForList(anyString(), anyMap())).thenReturn(List.of(
                Map.of("equipment_id", "e1", "equipment_name", "EQ-CRN-50T",
                        "idle_hours", 4.5, "breakdown_reason", "hydraulic leak", "log_date", "2026-05-07")
        ));
        ObjectNode in = JsonNodeFactory.instance.objectNode();
        in.put("threshold_hours", 2);

        AiContext ctx = AiContextFixtures.forProfile("SITE_MANAGER", pid);
        ToolResult r = tool.execute(in, ctx);
        assertTrue(r.success());
        assertEquals(1, r.data().path("rows").size());
    }

    @Test
    void dataUnavailableOnEmptyResult() {
        when(ch.queryForList(anyString(), anyMap())).thenReturn(List.of());
        AiContext ctx = AiContextFixtures.forProfile("SITE_MANAGER", UUID.randomUUID());
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);
        assertEquals("data_unavailable", r.data().path("status").asText());
    }
}
