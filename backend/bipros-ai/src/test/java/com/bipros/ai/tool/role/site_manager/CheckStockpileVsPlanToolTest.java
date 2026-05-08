package com.bipros.ai.tool.role.site_manager;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.testsupport.AiContextFixtures;
import com.bipros.ai.tool.ToolResult;
import com.bipros.resource.domain.model.MaterialStock;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.MaterialStockRepository;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckStockpileVsPlanToolTest {

    private final MaterialStockRepository stockRepo = mock(MaterialStockRepository.class);
    private final ResourceAssignmentRepository assignmentRepo = mock(ResourceAssignmentRepository.class);
    private final ObjectMapper om = new ObjectMapper();
    private final CheckStockpileVsPlanTool tool = new CheckStockpileVsPlanTool(stockRepo, assignmentRepo, om);

    @Test
    void nameAndRoles() {
        assertEquals("check_stockpile_vs_plan", tool.name());
        assertTrue(tool.allowedRoles().contains("SITE_MANAGER"));
        assertTrue(tool.allowedRoles().contains("PROJECT_MANAGER"));
        assertTrue(tool.allowedRoles().contains("RESOURCE_MANAGER"));
    }

    @Test
    void surfacesAtRiskMaterial() {
        UUID pid = UUID.randomUUID();
        UUID matId = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        // Material has 5 units in stock
        MaterialStock stock = new MaterialStock();
        stock.setProjectId(pid);
        stock.setMaterialId(matId);
        stock.setCurrentStock(new BigDecimal("5.0"));

        // Assignment demands 20 units within lookahead window (starts today)
        ResourceAssignment assignment = new ResourceAssignment();
        assignment.setProjectId(pid);
        assignment.setResourceId(matId);
        assignment.setPlannedUnits(20.0);
        assignment.setPlannedStartDate(today);
        assignment.setPlannedFinishDate(today.plusDays(1));

        when(stockRepo.findByProjectId(any(UUID.class))).thenReturn(List.of(stock));
        when(assignmentRepo.findByProjectId(any(UUID.class))).thenReturn(List.of(assignment));

        AiContext ctx = AiContextFixtures.forProfile("SITE_MANAGER", pid);
        // lookahead_days = 3 (default)
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success());
        assertNotNull(r.data());
        assertTrue(r.data().path("rows").isArray());
        assertEquals(1, r.data().path("rows").size());

        var row = r.data().path("rows").get(0);
        assertEquals(matId.toString(), row.path("material_id").asText());
        assertEquals(5.0, row.path("current_stock").asDouble(), 0.01);
        assertEquals(20.0, row.path("lookahead_demand").asDouble(), 0.01);
        // ratio = 5 / 20 = 0.25 — at_risk = true
        assertTrue(row.path("stock_to_need_ratio").asDouble() < 1.0);
        assertTrue(row.path("at_risk").asBoolean());
    }

    @Test
    void dataUnavailableWhenSourceMissing() {
        UUID pid = UUID.randomUUID();

        // Both sources return empty — no stock rows at all
        when(stockRepo.findByProjectId(any(UUID.class))).thenReturn(List.of());
        when(assignmentRepo.findByProjectId(any(UUID.class))).thenReturn(List.of());

        AiContext ctx = AiContextFixtures.forProfile("SITE_MANAGER", pid);
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success(), "data_unavailable is still success=true with payload");
        assertEquals("data_unavailable", r.data().path("status").asText());
        assertFalse(r.data().path("reason").asText().isBlank());
        assertFalse(r.data().path("closest_available").asText().isBlank());
    }
}
