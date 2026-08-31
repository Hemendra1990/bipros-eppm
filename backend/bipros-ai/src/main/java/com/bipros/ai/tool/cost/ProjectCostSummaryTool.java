package com.bipros.ai.tool.cost;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.cost.application.dto.CostSummaryDto;
import com.bipros.cost.application.service.CostService;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Project-level cost KPIs (total budget, total actual, CPI, BAC, material ledger).
 * Delegates to {@link CostService#getCostSummary(UUID)} so the AI returns the same
 * numbers the Cost tab renders — independent of whether the project defines any
 * {@code cost_account} rows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectCostSummaryTool implements Tool {

    private final CostService costService;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "project_cost_summary";
    }

    @Override
    public String description() {
        return "Use this for any question about the project's overall cost + earned-value KPIs — "
                + "total budgeted cost, total actual cost, total remaining, BAC, CPI, SPI, cost "
                + "variance (CV), schedule variance (SV), earned value (EV), planned value (PV), "
                + "cost %% complete, EAC, ETC, VAC, TCPI, and material procurement / open stock / "
                + "issued. These are the SAME numbers the Costs tab renders (computed live from BOQ "
                + "earned value + BAC + actual cost) — ALWAYS use this tool for project-level "
                + "CPI/SPI/CV/SV/EAC/BAC so the answer matches the Costs tab. Do NOT use "
                + "`formula_validate` or `analyze_cost` for headline project CPI/SPI — those read a "
                + "different (snapshot / warehouse) lineage and will disagree with the tab. Sources "
                + "include ActivityExpense rows, ResourceAssignment planned cost, DPR-sourced "
                + "actuals, and the material ledger. Does NOT require any cost_account to exist. "
                + "Project-scoped (no input).";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", objectMapper.createObjectNode());
        return schema;
    }

    @Override
    @Transactional(readOnly = true)
    public ToolResult execute(JsonNode input, AiContext ctx) {
        UUID projectId = ctx.projectId();
        if (projectId == null) {
            return ToolResult.error("project_cost_summary needs a project in scope.");
        }
        if (!"ADMIN".equals(ctx.role())
                && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
            throw new AccessDeniedException("project not in user scope");
        }

        CostSummaryDto s = costService.getCostSummary(projectId);
        String currency = projectRepository.findById(projectId)
                .map(Project::getBudgetCurrency)
                .orElse("INR");

        ObjectNode out = objectMapper.createObjectNode();
        out.put("project_id", projectId.toString());
        out.put("currency", currency);
        putAmount(out, "total_budget", s.totalBudget());
        putAmount(out, "total_actual", s.totalActual());
        putAmount(out, "total_remaining", s.totalRemaining());
        putAmount(out, "at_completion", s.atCompletion());
        putAmount(out, "cost_variance", s.costVariance());
        out.put("cpi", s.costPerformanceIndex() == null ? null : s.costPerformanceIndex().doubleValue());
        out.put("expense_count", s.expenseCount());
        putAmount(out, "material_procurement_cost", s.materialProcurementCost());
        putAmount(out, "open_stock_value", s.openStockValue());
        putAmount(out, "material_issued_cost", s.materialIssuedCost());
        putAmount(out, "project_original_budget", s.projectOriginalBudget());
        putAmount(out, "project_current_budget", s.projectCurrentBudget());

        // Full live EVM block — identical to what the Cost tab renders. Exposing
        // SPI / EV / PV / BAC here means the AI can answer project-level CPI AND
        // SPI (and the rest of the EVM family) from THIS single Cost-tab-consistent
        // source, instead of routing SPI to formula_validate (the EvmCalculation
        // snapshot lineage), which produced numbers that disagreed with the tab.
        putAmount(out, "bac", s.bac());
        putAmount(out, "planned_cost", s.plannedCost());
        putAmount(out, "earned_value", s.earnedValue());
        putAmount(out, "planned_value", s.plannedValue());
        putAmount(out, "cost_percent_complete", s.costPercentComplete());
        putAmount(out, "schedule_variance", s.scheduleVariance());
        out.put("spi", s.schedulePerformanceIndex() == null ? null
                : s.schedulePerformanceIndex().doubleValue());
        putAmount(out, "estimate_at_completion", s.estimateAtCompletion());
        putAmount(out, "estimate_to_complete", s.estimateToComplete());
        putAmount(out, "variance_at_completion", s.varianceAtCompletion());
        putAmount(out, "contract_value", s.contractValue());
        out.put("tcpi", s.toCompletePerformanceIndex() == null ? null
                : s.toCompletePerformanceIndex().doubleValue());

        String cpiTxt = s.costPerformanceIndex() == null ? "N/A"
                : String.format("%.4f", s.costPerformanceIndex().doubleValue());
        String spiTxt = s.schedulePerformanceIndex() == null ? "N/A"
                : String.format("%.4f", s.schedulePerformanceIndex().doubleValue());
        String summary = String.format(
                "Total budget %,.0f %s; total actual %,.0f %s; CPI %s; SPI %s.",
                nz(s.totalBudget()).doubleValue(), currency,
                nz(s.totalActual()).doubleValue(), currency,
                cpiTxt, spiTxt);
        return ToolResult.ok(summary, out);
    }

    private void putAmount(ObjectNode node, String key, BigDecimal value) {
        if (value == null) {
            node.putNull(key);
        } else {
            node.put(key, value.doubleValue());
        }
    }

    private static BigDecimal nz(BigDecimal b) {
        return b == null ? BigDecimal.ZERO : b;
    }
}
