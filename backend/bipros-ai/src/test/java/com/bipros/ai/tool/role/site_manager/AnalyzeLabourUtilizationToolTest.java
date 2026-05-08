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

class AnalyzeLabourUtilizationToolTest {

    private final ClickHouseTemplate ch = mock(ClickHouseTemplate.class);
    private final ObjectMapper om = new ObjectMapper();
    private final AnalyzeLabourUtilizationTool tool = new AnalyzeLabourUtilizationTool(ch, om);

    @Test
    void hasCorrectNameAndRoleTags() {
        assertEquals("analyze_labour_utilization", tool.name());
        assertTrue(tool.allowedRoles().contains("SITE_MANAGER"));
    }

    @Test
    void returnsRowsByCrewWhenDataPresent() {
        UUID pid = UUID.randomUUID();
        when(ch.queryForList(anyString(), anyMap())).thenReturn(List.of(
                Map.of("crew_id", "c1", "crew_name", "ABC Skilled",
                        "actual_hours", 88, "planned_hours", 96, "utilization_pct", 91.7)
        ));

        AiContext ctx = AiContextFixtures.forProfile("SITE_MANAGER", pid);
        ObjectNode in = JsonNodeFactory.instance.objectNode();
        ToolResult r = tool.execute(in, ctx);

        assertTrue(r.success());
        assertNotNull(r.data());
        assertTrue(r.data().path("rows").isArray());
        assertEquals(1, r.data().path("rows").size());
    }

    @Test
    void returnsDataUnavailableWhenNoRows() {
        UUID pid = UUID.randomUUID();
        when(ch.queryForList(anyString(), anyMap())).thenReturn(List.of());

        AiContext ctx = AiContextFixtures.forProfile("SITE_MANAGER", pid);
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success(), "data_unavailable is still success=true with payload");
        assertEquals("data_unavailable", r.data().path("status").asText());
        assertFalse(r.data().path("reason").asText().isBlank());
    }
}
