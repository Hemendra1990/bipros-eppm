package com.bipros.ai.tool.role.qc_manager;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.context.AiContext;
import com.bipros.ai.testsupport.AiContextFixtures;
import com.bipros.ai.tool.ToolResult;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprManpower;
import com.bipros.project.domain.model.DprMaterial;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.DprManpowerRepository;
import com.bipros.project.domain.repository.DprMaterialRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditTraceabilityToolTest {

    private final DailyProgressReportRepository dprRepo = mock(DailyProgressReportRepository.class);
    private final DprManpowerRepository manpowerRepo = mock(DprManpowerRepository.class);
    private final DprMaterialRepository materialRepo = mock(DprMaterialRepository.class);
    private final ActivityRepository activityRepo = mock(ActivityRepository.class);
    private final ObjectMapper om = new ObjectMapper();

    private final AuditTraceabilityTool tool =
            new AuditTraceabilityTool(dprRepo, manpowerRepo, materialRepo, activityRepo, om);

    @Test
    void nameAndRoles() {
        assertEquals("audit_traceability", tool.name());
        assertTrue(tool.allowedRoles().contains("QC_MANAGER"));
        assertTrue(tool.allowedRoles().contains("PROJECT_MANAGER"));
        assertTrue(tool.allowedRoles().contains("BIM_DATA_COORDINATOR"));
    }

    @Test
    void flagsTraceableAndUntraceableRows() {
        UUID pid = UUID.randomUUID();
        UUID dprId = UUID.randomUUID();

        // Build a minimal DPR
        DailyProgressReport dpr = new DailyProgressReport();
        dpr.setId(dprId);
        dpr.setProjectId(pid);
        dpr.setReportDate(LocalDate.of(2026, 4, 10));
        dpr.setActivityName("Concrete Laying");
        dpr.setSupervisorName("Test Supervisor");
        dpr.setUnit("m3");
        dpr.setQtyExecuted(java.math.BigDecimal.TEN);

        // Manpower row: trade present → traceable
        DprManpower mp = new DprManpower();
        mp.setDprId(dprId);
        mp.setTrade("Concrete Finisher");
        mp.setContractorName("ABC Contractors");

        // Material row 1: batch_no + quantity present → traceable
        DprMaterial mat1 = new DprMaterial();
        mat1.setDprId(dprId);
        mat1.setMaterialName("OPC Cement");
        mat1.setBatchNo("BATCH-2026-042");
        mat1.setQuantity(new java.math.BigDecimal("50.0"));

        // Material row 2: no batch_no → NOT traceable
        DprMaterial mat2 = new DprMaterial();
        mat2.setDprId(dprId);
        mat2.setMaterialName("River Sand");
        mat2.setBatchNo(null);
        mat2.setQuantity(new java.math.BigDecimal("25.0"));

        when(dprRepo.findById(dprId)).thenReturn(Optional.of(dpr));
        when(manpowerRepo.findByDprIdOrderByTradeAsc(dprId)).thenReturn(List.of(mp));
        when(materialRepo.findByDprIdOrderByMaterialNameAsc(dprId)).thenReturn(List.of(mat1, mat2));

        AiContext ctx = AiContextFixtures.forProfile("QC_MANAGER", pid);
        ObjectNode input = om.createObjectNode();
        input.put("dpr_id", dprId.toString());

        ToolResult r = tool.execute(input, ctx);

        assertTrue(r.success(), "Expected success");
        assertNotNull(r.data());
        assertTrue(r.data().path("rows").isArray(), "Expected rows array");

        // Should have 3 rows: 1 manpower + 2 material
        assertEquals(3, r.data().path("rows").size(), "Expected 3 audit rows");

        // Find the manpower row
        var rows = r.data().path("rows");
        boolean foundTraceableManpower = false;
        boolean foundTraceableMaterial = false;
        boolean foundUntraceableMaterial = false;

        for (var row : rows) {
            String kind = row.path("kind").asText();
            boolean traceable = row.path("traceable").asBoolean();
            String identifier = row.path("identifier").asText();

            if ("manpower".equals(kind) && "Concrete Finisher".equals(identifier)) {
                assertTrue(traceable, "Manpower with trade should be traceable");
                foundTraceableManpower = true;
            }
            if ("material".equals(kind) && "OPC Cement".equals(identifier)) {
                assertTrue(traceable, "Material with batch_no + quantity should be traceable");
                foundTraceableMaterial = true;
            }
            if ("material".equals(kind) && "River Sand".equals(identifier)) {
                assertFalse(traceable, "Material without batch_no should NOT be traceable");
                assertFalse(row.path("gap").asText().isBlank(), "gap should explain missing fields");
                foundUntraceableMaterial = true;
            }
        }

        assertTrue(foundTraceableManpower, "Should have found traceable manpower row");
        assertTrue(foundTraceableMaterial, "Should have found traceable material row");
        assertTrue(foundUntraceableMaterial, "Should have found untraceable material row");
    }

    @Test
    void dataUnavailableForUnknownDpr() {
        UUID pid = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();

        when(dprRepo.findById(any(UUID.class))).thenReturn(Optional.empty());

        AiContext ctx = AiContextFixtures.forProfile("QC_MANAGER", pid);
        ObjectNode input = om.createObjectNode();
        input.put("dpr_id", unknownId.toString());

        ToolResult r = tool.execute(input, ctx);

        assertTrue(r.success(), "data_unavailable is still success=true with payload");
        assertNotNull(r.data());
        assertEquals("data_unavailable", r.data().path("status").asText());
        String reason = r.data().path("reason").asText();
        assertFalse(reason.isBlank(), "Reason should not be blank");
        assertTrue(reason.toLowerCase().contains("dpr") || reason.toLowerCase().contains("found"),
                "Reason should mention DPR or not found: " + reason);
    }
}
