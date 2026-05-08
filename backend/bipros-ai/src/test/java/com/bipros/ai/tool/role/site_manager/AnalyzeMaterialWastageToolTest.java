package com.bipros.ai.tool.role.site_manager;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.testsupport.AiContextFixtures;
import com.bipros.ai.tool.ToolResult;
import com.bipros.resource.domain.model.MaterialReconciliation;
import com.bipros.resource.domain.repository.MaterialReconciliationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyzeMaterialWastageToolTest {

    private final MaterialReconciliationRepository repo = mock(MaterialReconciliationRepository.class);
    private final ObjectMapper om = new ObjectMapper();
    private final AnalyzeMaterialWastageTool tool = new AnalyzeMaterialWastageTool(repo, om);

    @Test
    void nameAndRoles() {
        assertEquals("analyze_material_wastage", tool.name());
        assertTrue(tool.allowedRoles().contains("SITE_MANAGER"));
        assertTrue(tool.allowedRoles().contains("PROJECT_MANAGER"));
        assertTrue(tool.allowedRoles().contains("PROJECT_ENGINEER"));
    }

    @Test
    void happyPath() {
        UUID pid = UUID.randomUUID();
        UUID rid = UUID.randomUUID();

        MaterialReconciliation entry = new MaterialReconciliation();
        entry.setResourceId(rid);
        entry.setProjectId(pid);
        entry.setWbsNodeId(null);
        entry.setPeriod("2026-04");
        entry.setConsumed(100.0);
        entry.setWastage(15.0);
        entry.setUnit("m3");

        when(repo.findByProjectId(any(UUID.class))).thenReturn(List.of(entry));

        AiContext ctx = AiContextFixtures.forProfile("SITE_MANAGER", pid);
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success());
        assertNotNull(r.data());
        assertTrue(r.data().path("rows").isArray());
        assertEquals(1, r.data().path("rows").size());
        // wastage_pct = 15 / 100 * 100 = 15.0
        assertEquals(15.0, r.data().path("rows").get(0).path("wastage_pct").asDouble(), 0.01);
    }

    @Test
    void dataUnavailableOnEmpty() {
        UUID pid = UUID.randomUUID();
        when(repo.findByProjectId(any(UUID.class))).thenReturn(List.of());

        AiContext ctx = AiContextFixtures.forProfile("SITE_MANAGER", pid);
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success(), "data_unavailable is still success=true with payload");
        assertEquals("data_unavailable", r.data().path("status").asText());
        assertFalse(r.data().path("reason").asText().isBlank());
    }
}
