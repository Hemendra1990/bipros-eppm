package com.bipros.ai.tool.formula;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.ToolResult;
import com.bipros.cost.application.dto.CostSummaryDto;
import com.bipros.cost.application.service.CostService;
import com.bipros.evm.domain.entity.EvmCalculation;
import com.bipros.evm.domain.repository.EvmCalculationRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that project-scope EVM math is sourced from the live Costs-tab summary
 * (so the AI's CPI/SPI match the tab), while activity-scope still reads the
 * per-activity EvmCalculation snapshot.
 */
class FormulaValidatorToolTest {

    private EvmCalculationRepository evmRepository;
    private ActivityRepository activityRepository;
    private CostService costService;
    private ProjectRepository projectRepository;
    private FormulaValidatorTool tool;

    private static final UUID PID = UUID.randomUUID();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        evmRepository = Mockito.mock(EvmCalculationRepository.class);
        activityRepository = Mockito.mock(ActivityRepository.class);
        costService = Mockito.mock(CostService.class);
        projectRepository = Mockito.mock(ProjectRepository.class);
        tool = new FormulaValidatorTool(evmRepository, activityRepository,
                costService, projectRepository, mapper);
        when(projectRepository.findById(PID)).thenReturn(Optional.empty());
    }

    private static AiContext ctx() {
        return new AiContext(UUID.randomUUID(), PID, "general", "ADMIN", "ADMIN", List.of());
    }

    private CostSummaryDto costTabDto() {
        // BAC=1000, EV=500, PV=400, AC=250 ⇒ CPI=2.0, SPI=1.25.
        return CostSummaryDto.ofEvm(
                new BigDecimal("1000"), new BigDecimal("400"), new BigDecimal("250"),
                new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("0.4"),
                0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("800"), null);
    }

    @Test
    void projectScopeCpiComesFromCostTab() {
        when(costService.getCostSummary(PID)).thenReturn(costTabDto());
        ObjectNode input = mapper.createObjectNode();
        input.put("metric", "CPI");

        ToolResult result = tool.execute(input, ctx());
        assertThat(result.success()).isTrue();
        JsonNode out = result.data();

        assertThat(new BigDecimal(out.get("computed").asText())).isEqualByComparingTo("2");
        assertThat(out.get("source").get("entity").asText()).isEqualTo("CostSummary (Costs tab, live)");
        assertThat(out.get("source").get("scope").asText()).isEqualTo("project");
        // Never touches the EvmCalculation snapshot lineage for project scope.
        verify(evmRepository, never()).findTopByProjectIdOrderByDataDateDesc(any());
    }

    @Test
    void projectScopeSpiComesFromCostTab() {
        when(costService.getCostSummary(PID)).thenReturn(costTabDto());
        ObjectNode input = mapper.createObjectNode();
        input.put("metric", "SPI");

        JsonNode out = tool.execute(input, ctx()).data();
        assertThat(new BigDecimal(out.get("computed").asText())).isEqualByComparingTo("1.25");
    }

    @Test
    void activityScopeStillReadsEvmCalculation() {
        UUID aid = UUID.randomUUID();
        Activity activity = new Activity();
        activity.setId(aid);
        EvmCalculation evm = new EvmCalculation();
        evm.setBudgetAtCompletion(new BigDecimal("1000"));
        evm.setPlannedValue(new BigDecimal("800"));
        evm.setEarnedValue(new BigDecimal("900"));
        evm.setActualCost(new BigDecimal("300"));
        evm.setProjectId(PID);
        evm.setActivityId(aid);
        evm.setDataDate(LocalDate.of(2026, 1, 1));

        when(activityRepository.findByProjectIdAndCode(PID, "ACT-1")).thenReturn(Optional.of(activity));
        when(evmRepository.findTopByProjectIdAndActivityIdOrderByDataDateDesc(PID, aid))
                .thenReturn(Optional.of(evm));

        ObjectNode input = mapper.createObjectNode();
        input.put("metric", "CPI");
        input.put("activityCode", "ACT-1");

        JsonNode out = tool.execute(input, ctx()).data();
        // EV/AC = 900/300 = 3.0 — the activity snapshot, NOT the Cost-tab 2.0.
        assertThat(new BigDecimal(out.get("computed").asText())).isEqualByComparingTo("3");
        assertThat(out.get("source").get("entity").asText()).isEqualTo("EvmCalculation");
        assertThat(out.get("source").get("scope").asText()).isEqualTo("activity");
        verify(costService, never()).getCostSummary(any());
    }
}
