package com.bipros.ai.tool.role.bim_coordinator;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.testsupport.AiContextFixtures;
import com.bipros.ai.tool.ToolResult;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportDataLagToolTest {

    private final DailyProgressReportRepository dprRepo = mock(DailyProgressReportRepository.class);
    private final ObjectMapper om = new ObjectMapper();
    private final ReportDataLagTool tool = new ReportDataLagTool(dprRepo, om);

    // ------------------------------------------------------------------ helpers

    /**
     * Build a minimal DPR whose {@code createdAt} is exactly {@code lagDays} after
     * {@code reportDate}, simulating the submission delay.
     */
    private static DailyProgressReport dprWithLag(UUID projectId, LocalDate reportDate, long lagDays) {
        DailyProgressReport dpr = DailyProgressReport.builder()
                .projectId(projectId)
                .reportDate(reportDate)
                .activityName("Test Activity")
                .unit("m3")
                .qtyExecuted(BigDecimal.ONE)
                .build();
        dpr.setId(UUID.randomUUID());
        // createdAt = reportDate + lagDays, at noon UTC
        Instant createdAt = reportDate.plusDays(lagDays).atTime(12, 0)
                .toInstant(ZoneOffset.UTC);
        dpr.setCreatedAt(createdAt);
        return dpr;
    }

    // ------------------------------------------------------------------ tests

    @Test
    void nameAndRoles() {
        assertEquals("report_data_lag", tool.name());
        assertTrue(tool.allowedRoles().contains("BIM_DATA_COORDINATOR"),
                "Must include BIM_DATA_COORDINATOR");
        assertTrue(tool.allowedRoles().contains("PROJECT_MANAGER"),
                "Must include PROJECT_MANAGER");
    }

    /**
     * Three DPRs with lag 0d, 2d, and 8d respectively.
     * Expected bucket counts: {0d=1, 2d=1, ">7d"=1}.
     * Worst list must contain the 8-day DPR as the first entry.
     */
    @Test
    void bucketsLagAndIdentifiesWorst() {
        UUID pid = UUID.randomUUID();
        LocalDate base = LocalDate.now().minusDays(10);

        DailyProgressReport dpr0 = dprWithLag(pid, base,      0);
        DailyProgressReport dpr2 = dprWithLag(pid, base.plusDays(1), 2);
        DailyProgressReport dpr8 = dprWithLag(pid, base.plusDays(2), 8);

        when(dprRepo.findByProjectIdAndReportDateBetweenOrderByReportDateAscIdAsc(
                eq(pid), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(dpr0, dpr2, dpr8));

        AiContext ctx = AiContextFixtures.forProfile("BIM_DATA_COORDINATOR", pid);
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success(), "Expected success=true");
        assertNotNull(r.data(), "Expected data payload");

        // Check buckets
        JsonNode buckets = r.data().path("buckets");
        assertFalse(buckets.isMissingNode(), "Expected 'buckets' node");
        assertEquals(1, buckets.path("0d").asInt(),  "0d bucket should be 1");
        assertEquals(0, buckets.path("1d").asInt(),  "1d bucket should be 0");
        assertEquals(1, buckets.path("2d").asInt(),  "2d bucket should be 1");
        assertEquals(0, buckets.path("3-7d").asInt(), "3-7d bucket should be 0");
        assertEquals(1, buckets.path(">7d").asInt(), ">7d bucket should be 1");

        // Percentiles present
        assertTrue(r.data().has("p50_lag_days"), "Expected p50_lag_days");
        assertTrue(r.data().has("p90_lag_days"), "Expected p90_lag_days");

        // Worst array — first entry must be the 8-day DPR
        JsonNode worst = r.data().path("worst");
        assertTrue(worst.isArray(), "Expected 'worst' array");
        assertFalse(worst.isEmpty(), "Worst list must not be empty");

        JsonNode worstFirst = worst.get(0);
        assertEquals(dpr8.getId().toString(), worstFirst.path("dpr_id").asText(),
                "Worst DPR must be the 8-day-lag one");
        assertEquals(8, worstFirst.path("lag_days").asInt(),
                "Worst entry lag_days must be 8");
        assertFalse(worstFirst.path("report_date").asText().isBlank(),
                "report_date must be present");
        assertFalse(worstFirst.path("created_at").asText().isBlank(),
                "created_at must be present");
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
