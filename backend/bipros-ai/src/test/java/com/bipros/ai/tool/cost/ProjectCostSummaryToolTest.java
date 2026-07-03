package com.bipros.ai.tool.cost;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.ToolResult;
import com.bipros.cost.application.dto.CostSummaryDto;
import com.bipros.cost.application.service.CostService;
import com.bipros.project.domain.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Verifies the tool surfaces the full EVM block from {@link CostSummaryDto} — the
 * SAME numbers the Costs tab renders — so the AI can answer project-level CPI AND
 * SPI (plus EV/PV/BAC) from this single Cost-tab-consistent source instead of
 * routing SPI to the divergent EvmCalculation-snapshot lineage.
 */
class ProjectCostSummaryToolTest {

    private CostService costService;
    private ProjectRepository projectRepository;
    private ProjectCostSummaryTool tool;

    private static final UUID PID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        costService = Mockito.mock(CostService.class);
        projectRepository = Mockito.mock(ProjectRepository.class);
        tool = new ProjectCostSummaryTool(costService, projectRepository, new ObjectMapper());
    }

    private static AiContext ctx() {
        return new AiContext(UUID.randomUUID(), PID, "general", "ADMIN", "ADMIN", List.of());
    }

    @Test
    void emitsFullEvmBlock() {
        // BAC=1000, costPct=50/100=0.5 → EV=500; plannedPct=0.4 → PV=400; AC=250.
        // ⇒ CPI = 500/250 = 2.0, SPI = 500/400 = 1.25.
        CostSummaryDto dto = CostSummaryDto.ofEvm(
                new BigDecimal("1000"), new BigDecimal("400"), new BigDecimal("250"),
                new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("0.4"),
                0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("800"), null);

        when(costService.getCostSummary(PID)).thenReturn(dto);
        when(projectRepository.findById(PID)).thenReturn(Optional.empty());

        ToolResult result = tool.execute(new ObjectMapper().createObjectNode(), ctx());
        assertThat(result.success()).isTrue();
        JsonNode out = result.data();

        assertThat(out.get("cpi").asDouble()).isEqualTo(2.0);
        assertThat(out.get("spi").asDouble()).isEqualTo(1.25);
        assertThat(out.get("earned_value").asDouble()).isEqualTo(500.0);
        assertThat(out.get("planned_value").asDouble()).isEqualTo(400.0);
        assertThat(out.get("bac").asDouble()).isEqualTo(1000.0);
        // summary now advertises SPI alongside CPI
        assertThat(result.summary()).contains("SPI");
    }
}
