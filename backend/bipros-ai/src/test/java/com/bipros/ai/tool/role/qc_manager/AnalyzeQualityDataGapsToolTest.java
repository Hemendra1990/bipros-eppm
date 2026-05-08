package com.bipros.ai.tool.role.qc_manager;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.testsupport.AiContextFixtures;
import com.bipros.ai.tool.ToolResult;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
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

class AnalyzeQualityDataGapsToolTest {

    private final DailyProgressReportRepository dprRepo = mock(DailyProgressReportRepository.class);
    private final ObjectMapper om = new ObjectMapper();
    private final AnalyzeQualityDataGapsTool tool = new AnalyzeQualityDataGapsTool(dprRepo, om);

    @Test
    void nameAndRoles() {
        assertEquals("analyze_quality_data_gaps", tool.name());
        assertTrue(tool.allowedRoles().contains("QC_MANAGER"));
        assertTrue(tool.allowedRoles().contains("BIM_DATA_COORDINATOR"));
        assertTrue(tool.allowedRoles().contains("PROJECT_MANAGER"));
    }

    @Test
    void flagsActivitiesWithIncompleteQualityFields() {
        UUID pid = UUID.randomUUID();

        // DPR 1: all QC fields populated — no gap
        DailyProgressReport complete = DailyProgressReport.builder()
                .projectId(pid)
                .reportDate(LocalDate.of(2026, 4, 15))
                .activityName("Concrete Pouring - Pier 3")
                .supervisorName("Ahmed Al-Rashidi")
                .unit("m3")
                .qtyExecuted(BigDecimal.valueOf(12.5))
                .weatherCondition("Clear")
                .approvalStatus(DprApprovalStatus.APPROVED)
                .safetyObservation("No incidents observed")
                .remarks("Sample collected for cube test")
                .build();

        // DPR 2: missing weatherCondition, approvalStatus, safetyObservation — has a gap
        DailyProgressReport incomplete = DailyProgressReport.builder()
                .projectId(pid)
                .reportDate(LocalDate.of(2026, 4, 16))
                .activityName("Bitumen Laying - Ch 14+200")
                .supervisorName("Farhan Siddiqui")
                .unit("m2")
                .qtyExecuted(BigDecimal.valueOf(340.0))
                .weatherCondition(null)          // missing
                .approvalStatus(null)            // missing
                .safetyObservation(null)         // missing
                .remarks(null)                   // missing
                .build();

        when(dprRepo.findByProjectIdOrderByReportDateAscIdAsc(any(UUID.class)))
                .thenReturn(List.of(complete, incomplete));

        AiContext ctx = AiContextFixtures.forProfile("QC_MANAGER", pid);
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success());
        assertNotNull(r.data());
        assertTrue(r.data().path("rows").isArray(), "Expected rows array");

        // Only the incomplete DPR's activity should appear as a gap row
        assertEquals(1, r.data().path("rows").size(), "Expected exactly 1 gap activity");

        var row = r.data().path("rows").get(0);
        assertEquals("Bitumen Laying - Ch 14+200", row.path("activity_code").asText());
        assertEquals(1, row.path("dpr_count").asInt());
        assertTrue(row.path("gap_count").asInt() >= 1, "gap_count should be >= 1");
        assertTrue(row.path("missing_fields").isArray(), "missing_fields should be an array");
        assertTrue(row.path("missing_fields").size() > 0, "Should list at least one missing field");
    }

    @Test
    void dataUnavailableWhenNoDprs() {
        UUID pid = UUID.randomUUID();
        when(dprRepo.findByProjectIdOrderByReportDateAscIdAsc(any(UUID.class)))
                .thenReturn(List.of());

        AiContext ctx = AiContextFixtures.forProfile("QC_MANAGER", pid);
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success(), "data_unavailable is still success=true with payload");
        assertEquals("data_unavailable", r.data().path("status").asText());
        assertFalse(r.data().path("reason").asText().isBlank());
        assertEquals("audit_dpr_data_quality", r.data().path("closest_available").asText());
    }
}
