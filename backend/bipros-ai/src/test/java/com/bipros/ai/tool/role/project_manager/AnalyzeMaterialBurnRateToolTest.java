package com.bipros.ai.tool.role.project_manager;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.testsupport.AiContextFixtures;
import com.bipros.ai.tool.ToolResult;
import com.bipros.resource.domain.model.MaterialIssue;
import com.bipros.resource.domain.model.MaterialStock;
import com.bipros.resource.domain.repository.MaterialIssueRepository;
import com.bipros.resource.domain.repository.MaterialStockRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyzeMaterialBurnRateToolTest {

    private final MaterialIssueRepository issueRepo = mock(MaterialIssueRepository.class);
    private final MaterialStockRepository stockRepo = mock(MaterialStockRepository.class);
    private final ObjectMapper om = new ObjectMapper();
    private final AnalyzeMaterialBurnRateTool tool =
            new AnalyzeMaterialBurnRateTool(issueRepo, stockRepo, om);

    @Test
    void nameAndRoles() {
        assertEquals("analyze_material_burn_rate", tool.name());
        assertTrue(tool.allowedRoles().contains("PROJECT_MANAGER"));
        assertTrue(tool.allowedRoles().contains("PORTFOLIO_MANAGER"));
        assertTrue(tool.allowedRoles().contains("COST_CONTROLLER"));
        assertTrue(tool.allowedRoles().contains("RESOURCE_MANAGER"));
    }

    /**
     * stock=10, 7-day total issued=35 (daily_burn=5) → days_remaining=2 → at_risk=true.
     */
    @Test
    void flagsAtRiskMaterial() {
        UUID pid = UUID.randomUUID();
        UUID matId = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        // Seven daily issue records totalling 35 units over the last 7 days
        List<MaterialIssue> issues = List.of(
                issueOf(pid, matId, today.minusDays(6), new BigDecimal("5.0")),
                issueOf(pid, matId, today.minusDays(5), new BigDecimal("5.0")),
                issueOf(pid, matId, today.minusDays(4), new BigDecimal("5.0")),
                issueOf(pid, matId, today.minusDays(3), new BigDecimal("5.0")),
                issueOf(pid, matId, today.minusDays(2), new BigDecimal("5.0")),
                issueOf(pid, matId, today.minusDays(1), new BigDecimal("5.0")),
                issueOf(pid, matId, today,               new BigDecimal("5.0"))
        );

        MaterialStock stock = new MaterialStock();
        stock.setProjectId(pid);
        stock.setMaterialId(matId);
        stock.setCurrentStock(new BigDecimal("10.0"));

        when(issueRepo.findByProjectIdAndIssueDateBetween(any(), any(), any())).thenReturn(issues);
        when(stockRepo.findByProjectId(any(UUID.class))).thenReturn(List.of(stock));

        AiContext ctx = AiContextFixtures.forProfile("PROJECT_MANAGER", pid);
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success());
        assertNotNull(r.data());
        assertTrue(r.data().path("rows").isArray());
        assertEquals(1, r.data().path("rows").size());

        var row = r.data().path("rows").get(0);
        assertEquals(matId.toString(), row.path("material_id").asText());
        assertEquals(5.0, row.path("daily_burn").asDouble(), 0.01);   // 35/7=5
        assertEquals(10.0, row.path("current_stock").asDouble(), 0.01);
        assertEquals(2.0, row.path("days_remaining").asDouble(), 0.01); // 10/5=2
        assertTrue(row.path("at_risk").asBoolean());                   // 2 < 5
    }

    /**
     * Empty repositories → data_unavailable.
     */
    @Test
    void dataUnavailableNoMaterials() {
        UUID pid = UUID.randomUUID();
        when(issueRepo.findByProjectIdAndIssueDateBetween(any(), any(), any())).thenReturn(List.of());
        when(stockRepo.findByProjectId(any(UUID.class))).thenReturn(List.of());

        AiContext ctx = AiContextFixtures.forProfile("PROJECT_MANAGER", pid);
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success(), "data_unavailable should still be success=true with payload");
        assertEquals("data_unavailable", r.data().path("status").asText());
        assertFalse(r.data().path("reason").asText().isBlank());
    }

    // ---- helpers ----

    private MaterialIssue issueOf(UUID projectId, UUID materialId, LocalDate date, BigDecimal qty) {
        MaterialIssue mi = new MaterialIssue();
        mi.setProjectId(projectId);
        mi.setMaterialId(materialId);
        mi.setIssueDate(date);
        mi.setQuantity(qty);
        return mi;
    }
}
