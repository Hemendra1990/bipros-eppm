package com.bipros.ai.tool.role.project_engineer;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.testsupport.AiContextFixtures;
import com.bipros.ai.tool.ToolResult;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.resource.domain.model.MaterialBoqLink;
import com.bipros.resource.domain.model.MaterialReconciliation;
import com.bipros.resource.domain.repository.MaterialBoqLinkRepository;
import com.bipros.resource.domain.repository.MaterialReconciliationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyzeYieldVarianceToolTest {

    private final MaterialReconciliationRepository reconciliationRepo =
            mock(MaterialReconciliationRepository.class);
    private final MaterialBoqLinkRepository boqLinkRepo =
            mock(MaterialBoqLinkRepository.class);
    private final BoqItemRepository boqItemRepo =
            mock(BoqItemRepository.class);
    private final ObjectMapper om = new ObjectMapper();

    private final AnalyzeYieldVarianceTool tool =
            new AnalyzeYieldVarianceTool(reconciliationRepo, boqLinkRepo, boqItemRepo, om);

    // --- Test 1: name + allowedRoles ---

    @Test
    void nameAndRoles() {
        assertEquals("analyze_yield_variance", tool.name());
        assertTrue(tool.allowedRoles().contains("PROJECT_ENGINEER"));
        assertTrue(tool.allowedRoles().contains("PROJECT_MANAGER"));
        assertFalse(tool.allowedRoles().contains("SITE_MANAGER"),
                "SITE_MANAGER is NOT in the contracted allowed roles for this tool");
    }

    // --- Test 2: happyPath — BOQ entity exists, join produces real variance rows ---

    @Test
    void happyPath() {
        UUID pid = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        UUID boqItemId = UUID.randomUUID();

        // Actual: material consumed 120 m3 in two reconciliation entries
        MaterialReconciliation rec1 = new MaterialReconciliation();
        rec1.setProjectId(pid);
        rec1.setResourceId(materialId);
        rec1.setPeriod("2026-03");
        rec1.setConsumed(70.0);
        rec1.setWastage(0.0);
        rec1.setOpeningBalance(0.0);
        rec1.setReceived(70.0);
        rec1.setClosingBalance(0.0);
        rec1.setUnit("m3");

        MaterialReconciliation rec2 = new MaterialReconciliation();
        rec2.setProjectId(pid);
        rec2.setResourceId(materialId);
        rec2.setPeriod("2026-04");
        rec2.setConsumed(50.0);
        rec2.setWastage(0.0);
        rec2.setOpeningBalance(0.0);
        rec2.setReceived(50.0);
        rec2.setClosingBalance(0.0);
        rec2.setUnit("m3");

        when(reconciliationRepo.findByProjectId(pid)).thenReturn(List.of(rec1, rec2));

        // BOQ link: materialId → boqItemId
        MaterialBoqLink link = new MaterialBoqLink();
        link.setMaterialId(materialId);
        link.setBoqItemId(boqItemId);
        when(boqLinkRepo.findByMaterialId(materialId)).thenReturn(List.of(link));

        // BOQ item: design qty = 100 m3
        BoqItem boqItem = new BoqItem();
        boqItem.setProjectId(pid);
        boqItem.setItemNo("1.1");
        boqItem.setDescription("Earthwork excavation");
        boqItem.setUnit("m3");
        boqItem.setBoqQty(new BigDecimal("100.000"));
        when(boqItemRepo.findById(boqItemId)).thenReturn(Optional.of(boqItem));

        AiContext ctx = AiContextFixtures.forProfile("PROJECT_ENGINEER", pid);
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success());
        assertNotNull(r.data());
        assertTrue(r.data().path("rows").isArray());
        assertEquals(1, r.data().path("rows").size());

        // variance_pct = (120 - 100) / 100 * 100 = 20.0
        double variancePct = r.data().path("rows").get(0).path("variance_pct").asDouble();
        assertEquals(20.0, variancePct, 0.1);

        // actual_quantity should be 120.0
        double actual = r.data().path("rows").get(0).path("actual_quantity").asDouble();
        assertEquals(120.0, actual, 0.01);

        // design_quantity should be 100.0
        double design = r.data().path("rows").get(0).path("design_quantity").asDouble();
        assertEquals(100.0, design, 0.01);
    }

    // --- Test 3: dataUnavailableEmptyMaterials — no MaterialReconciliation entries ---

    @Test
    void dataUnavailableEmptyMaterials() {
        UUID pid = UUID.randomUUID();
        when(reconciliationRepo.findByProjectId(any(UUID.class))).thenReturn(List.of());

        AiContext ctx = AiContextFixtures.forProfile("PROJECT_ENGINEER", pid);
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success(), "data_unavailable is still success=true with a payload");
        assertEquals("data_unavailable", r.data().path("status").asText());
        assertFalse(r.data().path("reason").asText().isBlank());
        assertFalse(r.data().path("what_would_be_needed").asText().isBlank());
    }
}
