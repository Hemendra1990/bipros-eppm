package com.bipros.ai.tool.role.bim_coordinator;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.testsupport.AiContextFixtures;
import com.bipros.ai.tool.ToolResult;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.DprEquipmentRepository;
import com.bipros.project.domain.repository.DprManpowerRepository;
import com.bipros.project.domain.repository.DprMaterialRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import com.bipros.project.domain.model.DprEquipment;
import com.bipros.project.domain.model.DprManpower;
import com.bipros.project.domain.model.DprMaterial;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditDprDataQualityToolTest {

    private final DailyProgressReportRepository dprRepo     = mock(DailyProgressReportRepository.class);
    private final DprManpowerRepository         manpowerRepo = mock(DprManpowerRepository.class);
    private final DprEquipmentRepository        equipRepo    = mock(DprEquipmentRepository.class);
    private final DprMaterialRepository         materialRepo = mock(DprMaterialRepository.class);
    private final ObjectMapper om = new ObjectMapper();

    private final AuditDprDataQualityTool tool =
            new AuditDprDataQualityTool(dprRepo, manpowerRepo, equipRepo, materialRepo, om);

    // ------------------------------------------------------------------ tests

    @Test
    void nameAndRoles() {
        assertEquals("audit_dpr_data_quality", tool.name());
        assertTrue(tool.allowedRoles().contains("BIM_DATA_COORDINATOR"),
                "Must include BIM_DATA_COORDINATOR");
        assertTrue(tool.allowedRoles().contains("PROJECT_MANAGER"),
                "Must include PROJECT_MANAGER");
        assertTrue(tool.allowedRoles().contains("PORTFOLIO_MANAGER"),
                "Must include PORTFOLIO_MANAGER");
    }

    /**
     * Two DPRs in the last 30 days:
     *  - dpr1 (today): all critical fields populated → 100 % completeness
     *  - dpr2 (yesterday): several blanks → lower completeness
     *
     * Tool must return rows ordered completeness ASC (dpr2 first).
     */
    @Test
    void scoresCompletenessPerDpr() {
        UUID pid   = UUID.randomUUID();
        UUID dpr1Id = UUID.randomUUID();
        UUID dpr2Id = UUID.randomUUID();

        LocalDate today     = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // DPR 1 — fully populated
        DailyProgressReport dpr1 = DailyProgressReport.builder()
                .projectId(pid)
                .reportDate(today)
                .supervisorName("Ahmed Al-Rashidi")
                .activityName("Concrete Pouring - Pier 3")
                .unit("m3")
                .qtyExecuted(BigDecimal.valueOf(12.5))
                .weatherCondition("Clear")
                .safetyObservation("No incidents")
                .remarks("All good")
                .approvalStatus(DprApprovalStatus.APPROVED)
                .contractorName("ABC Corp")
                .shift(com.bipros.project.domain.model.Shift.DAY)
                .build();
        dpr1.setId(dpr1Id);

        // DPR 2 — missing weatherCondition, safetyObservation, remarks, approvalStatus
        DailyProgressReport dpr2 = DailyProgressReport.builder()
                .projectId(pid)
                .reportDate(yesterday)
                .supervisorName("Farhan Siddiqui")
                .activityName("Bitumen Laying - Ch 14+200")
                .unit("m2")
                .qtyExecuted(BigDecimal.valueOf(340.0))
                .weatherCondition(null)        // missing
                .safetyObservation(null)       // missing
                .remarks(null)                 // missing
                .approvalStatus(null)          // missing
                .contractorName(null)          // missing
                .shift(null)                   // missing
                .build();
        dpr2.setId(dpr2Id);

        // repo returns both DPRs for the last-30-days window
        when(dprRepo.findByProjectIdAndReportDateBetweenOrderByReportDateAscIdAsc(
                eq(pid), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(dpr1, dpr2));

        // dpr1 has all child lines; dpr2 has none — so dpr2 is also missing has* fields
        DprManpower mp = new DprManpower();
        mp.setDprId(dpr1Id);
        mp.setTrade("Concrete Finisher");

        DprEquipment eq1 = new DprEquipment();
        eq1.setDprId(dpr1Id);
        eq1.setEquipmentType("Excavator");

        DprMaterial mat = new DprMaterial();
        mat.setDprId(dpr1Id);
        mat.setMaterialName("OPC Cement");

        when(manpowerRepo.findByDprIdIn(any())).thenReturn(List.of(mp));
        when(equipRepo.findByDprIdIn(any())).thenReturn(List.of(eq1));
        when(materialRepo.findByDprIdIn(any())).thenReturn(List.of(mat));

        AiContext ctx = AiContextFixtures.forProfile("BIM_DATA_COORDINATOR", pid);
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success(), "Expected success=true");
        assertNotNull(r.data(), "Expected data payload");
        assertTrue(r.data().path("rows").isArray(), "Expected rows array");

        // Must have 2 rows
        var rows = r.data().path("rows");
        assertEquals(2, rows.size(), "Expected exactly 2 DPR rows");

        // First row (lowest completeness) must be dpr2
        var first  = rows.get(0);
        var second = rows.get(1);

        assertTrue(first.path("completeness_pct").asDouble()
                        <= second.path("completeness_pct").asDouble(),
                "Rows must be sorted by completeness_pct ASC");

        // dpr2 row checks
        assertEquals(dpr2Id.toString(), first.path("dpr_id").asText(),
                "First (least complete) row should be dpr2");
        assertTrue(first.path("missing_fields").isArray(),
                "missing_fields must be an array");
        assertTrue(first.path("missing_fields").size() > 0,
                "dpr2 must have at least one missing field listed");

        // dpr1 row — should be 100 %
        assertEquals(dpr1Id.toString(), second.path("dpr_id").asText(),
                "Second (most complete) row should be dpr1");
        assertEquals(100.0, second.path("completeness_pct").asDouble(), 0.01,
                "Fully-populated DPR must score 100 %");

        // Summary fields
        assertTrue(r.data().has("total_dprs"), "Expected total_dprs summary field");
        assertTrue(r.data().has("avg_completeness_pct"), "Expected avg_completeness_pct summary field");
    }

    /**
     * When the project has no DPRs in the last 30 days, the tool must return
     * {@code status=data_unavailable} (success=true with payload, not error).
     */
    @Test
    void dataUnavailableNoDprs() {
        UUID pid = UUID.randomUUID();
        when(dprRepo.findByProjectIdAndReportDateBetweenOrderByReportDateAscIdAsc(
                eq(pid), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        AiContext ctx = AiContextFixtures.forProfile("BIM_DATA_COORDINATOR", pid);
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success(), "data_unavailable must be success=true");
        assertEquals("data_unavailable", r.data().path("status").asText(),
                "Expected status=data_unavailable");
        assertFalse(r.data().path("reason").asText().isBlank(),
                "Reason must not be blank");
    }
}
