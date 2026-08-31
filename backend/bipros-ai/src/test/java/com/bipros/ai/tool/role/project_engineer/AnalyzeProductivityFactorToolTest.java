package com.bipros.ai.tool.role.project_engineer;

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

class AnalyzeProductivityFactorToolTest {

    private final ClickHouseTemplate ch = mock(ClickHouseTemplate.class);
    private final ObjectMapper om = new ObjectMapper();
    private final AnalyzeProductivityFactorTool tool = new AnalyzeProductivityFactorTool(ch, om);

    @Test
    void nameAndRoles() {
        assertEquals("analyze_productivity_factor", tool.name());
        assertTrue(tool.allowedRoles().contains("PROJECT_ENGINEER"));
        assertTrue(tool.allowedRoles().contains("PROJECT_MANAGER"));
        assertTrue(tool.allowedRoles().contains("SITE_MANAGER"));
    }

    @Test
    void happyPath() {
        UUID pid = UUID.randomUUID();
        when(ch.queryForList(anyString(), anyMap())).thenReturn(List.of(
                Map.of(
                        "crew_id",        "CREW-001",
                        "crew_name",      "Masonry Crew A",
                        "activity_code",  "ACT-001",
                        "actual_per_hour", 2.5,
                        "norm_per_hour",   3.0,
                        "variance_pct",   -16.67
                )
        ));

        AiContext ctx = AiContextFixtures.forProfile("PROJECT_ENGINEER", pid);
        ObjectNode in = JsonNodeFactory.instance.objectNode();
        ToolResult r = tool.execute(in, ctx);

        assertTrue(r.success());
        assertNotNull(r.data());
        assertTrue(r.data().path("rows").isArray());
        assertEquals(1, r.data().path("rows").size());
    }

    @Test
    void dataUnavailableOnEmpty() {
        UUID pid = UUID.randomUUID();
        when(ch.queryForList(anyString(), anyMap())).thenReturn(List.of());

        AiContext ctx = AiContextFixtures.forProfile("PROJECT_ENGINEER", pid);
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success(), "data_unavailable is still success=true with payload");
        assertEquals("data_unavailable", r.data().path("status").asText());
        assertFalse(r.data().path("reason").asText().isBlank());
    }
}
