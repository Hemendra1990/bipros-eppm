package com.bipros.ai.tool;

import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.tool.role.bim_coordinator.AuditDprDataQualityTool;
import com.bipros.ai.tool.role.bim_coordinator.ReportDataLagTool;
import com.bipros.ai.tool.role.project_engineer.AnalyzeEquipmentCycleTimeTool;
import com.bipros.ai.tool.role.project_engineer.AnalyzeProductivityFactorTool;
import com.bipros.ai.tool.role.project_engineer.AnalyzeYieldVarianceTool;
import com.bipros.ai.tool.role.project_manager.AnalyzeEquipmentUtilizationCostTool;
import com.bipros.ai.tool.role.project_manager.AnalyzeLabourCostPerUnitTool;
import com.bipros.ai.tool.role.project_manager.AnalyzeMaterialBurnRateTool;
import com.bipros.ai.tool.role.qc_manager.AnalyzeNcrTrendsTool;
import com.bipros.ai.tool.role.qc_manager.AnalyzeQualityDataGapsTool;
import com.bipros.ai.tool.role.qc_manager.AuditTraceabilityTool;
import com.bipros.ai.tool.role.site_manager.AnalyzeLabourUtilizationTool;
import com.bipros.ai.tool.role.site_manager.AnalyzeMachineIdleTimeTool;
import com.bipros.ai.tool.role.site_manager.AnalyzeMaterialWastageTool;
import com.bipros.ai.tool.role.site_manager.CheckStockpileVsPlanTool;
import com.bipros.analytics.store.ClickHouseTemplate;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.DprEquipmentRepository;
import com.bipros.project.domain.repository.DprManpowerRepository;
import com.bipros.project.domain.repository.DprMaterialRepository;
import com.bipros.resource.domain.repository.MaterialBoqLinkRepository;
import com.bipros.resource.domain.repository.MaterialIssueRepository;
import com.bipros.resource.domain.repository.MaterialReconciliationRepository;
import com.bipros.resource.domain.repository.MaterialStockRepository;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.risk.domain.repository.RiskCategoryTypeRepository;
import com.bipros.risk.domain.repository.RiskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = ToolsAvailableByProfileIT.TestConfig.class)
class ToolsAvailableByProfileIT {

    /**
     * Minimal Spring Boot context: only the ToolRegistry and the role-specific
     * tool beans whose {@code allowedRoles()} this IT exercises. All external
     * dependencies (ClickHouse, JPA repositories) are replaced with Mockito mocks
     * — execution is not tested here, only name() + allowedRoles() metadata.
     */
    @SpringBootConfiguration
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        // ── Site-Manager tools ──────────────────────────────────────────────

        @Bean
        AnalyzeLabourUtilizationTool analyzeLabourUtilizationTool(ObjectMapper om) {
            return new AnalyzeLabourUtilizationTool(mock(ClickHouseTemplate.class), om);
        }

        @Bean
        AnalyzeMachineIdleTimeTool analyzeMachineIdleTimeTool(ObjectMapper om) {
            return new AnalyzeMachineIdleTimeTool(mock(ClickHouseTemplate.class), om);
        }

        @Bean
        AnalyzeMaterialWastageTool analyzeMaterialWastageTool(ObjectMapper om) {
            return new AnalyzeMaterialWastageTool(mock(MaterialReconciliationRepository.class), om);
        }

        @Bean
        CheckStockpileVsPlanTool checkStockpileVsPlanTool(ObjectMapper om) {
            return new CheckStockpileVsPlanTool(
                    mock(MaterialStockRepository.class),
                    mock(ResourceAssignmentRepository.class),
                    om);
        }

        // ── Project-Engineer tools ──────────────────────────────────────────

        @Bean
        AnalyzeProductivityFactorTool analyzeProductivityFactorTool(ObjectMapper om) {
            return new AnalyzeProductivityFactorTool(mock(ClickHouseTemplate.class), om);
        }

        @Bean
        AnalyzeYieldVarianceTool analyzeYieldVarianceTool(ObjectMapper om) {
            return new AnalyzeYieldVarianceTool(
                    mock(MaterialReconciliationRepository.class),
                    mock(MaterialBoqLinkRepository.class),
                    mock(BoqItemRepository.class),
                    om);
        }

        @Bean
        AnalyzeEquipmentCycleTimeTool analyzeEquipmentCycleTimeTool(ObjectMapper om) {
            return new AnalyzeEquipmentCycleTimeTool(om);
        }

        // ── QC-Manager tools ───────────────────────────────────────────────

        @Bean
        AnalyzeNcrTrendsTool analyzeNcrTrendsTool(ObjectMapper om) {
            return new AnalyzeNcrTrendsTool(
                    mock(RiskRepository.class),
                    mock(RiskCategoryTypeRepository.class),
                    om);
        }

        @Bean
        AuditTraceabilityTool auditTraceabilityTool(ObjectMapper om) {
            return new AuditTraceabilityTool(
                    mock(DailyProgressReportRepository.class),
                    mock(DprManpowerRepository.class),
                    mock(DprMaterialRepository.class),
                    mock(ActivityRepository.class),
                    om);
        }

        @Bean
        AnalyzeQualityDataGapsTool analyzeQualityDataGapsTool(ObjectMapper om) {
            return new AnalyzeQualityDataGapsTool(
                    mock(DailyProgressReportRepository.class),
                    om);
        }

        // ── Project-Manager tools ──────────────────────────────────────────

        @Bean
        AnalyzeLabourCostPerUnitTool analyzeLabourCostPerUnitTool(ObjectMapper om) {
            return new AnalyzeLabourCostPerUnitTool(mock(ClickHouseTemplate.class), om);
        }

        @Bean
        AnalyzeMaterialBurnRateTool analyzeMaterialBurnRateTool(ObjectMapper om) {
            return new AnalyzeMaterialBurnRateTool(
                    mock(MaterialIssueRepository.class),
                    mock(MaterialStockRepository.class),
                    om);
        }

        @Bean
        AnalyzeEquipmentUtilizationCostTool analyzeEquipmentUtilizationCostTool(ObjectMapper om) {
            return new AnalyzeEquipmentUtilizationCostTool(mock(ClickHouseTemplate.class), om);
        }

        @Bean
        PortfolioKpiTool portfolioKpiTool(ObjectMapper om) {
            return new PortfolioKpiTool(mock(ClickHouseTemplate.class), om);
        }

        @Bean
        AnalyzeCostTool analyzeCostTool(ObjectMapper om) {
            return new AnalyzeCostTool(mock(ClickHouseTemplate.class), om);
        }

        @Bean
        ForecastCompletionTool forecastCompletionTool(ObjectMapper om) {
            return new ForecastCompletionTool(mock(ClickHouseTemplate.class), om);
        }

        // ── BIM-Data-Coordinator tools ─────────────────────────────────────

        @Bean
        AuditDprDataQualityTool auditDprDataQualityTool(ObjectMapper om) {
            return new AuditDprDataQualityTool(
                    mock(DailyProgressReportRepository.class),
                    mock(DprManpowerRepository.class),
                    mock(DprEquipmentRepository.class),
                    mock(DprMaterialRepository.class),
                    om);
        }

        @Bean
        ReportDataLagTool reportDataLagTool(ObjectMapper om) {
            return new ReportDataLagTool(
                    mock(DailyProgressReportRepository.class),
                    om);
        }

        // ── Registry ───────────────────────────────────────────────────────

        @Bean
        ToolRegistry toolRegistry(List<Tool> tools) {
            return new ToolRegistry(tools);
        }
    }

    @Autowired
    ToolRegistry registry;

    @Test
    void siteManagerSeesItsFourTools() {
        Set<String> names = registry.toolsForProfile("SITE_MANAGER").stream()
                .map(Tool::name).collect(Collectors.toSet());
        assertTrue(names.contains("analyze_labour_utilization"));
        assertTrue(names.contains("analyze_machine_idle_time"));
        assertTrue(names.contains("analyze_material_wastage"));
        assertTrue(names.contains("check_stockpile_vs_plan"));
    }

    @Test
    void projectEngineerSeesItsThreeTools() {
        Set<String> names = registry.toolsForProfile("PROJECT_ENGINEER").stream()
                .map(Tool::name).collect(Collectors.toSet());
        assertTrue(names.contains("analyze_productivity_factor"));
        assertTrue(names.contains("analyze_yield_variance"));
        assertTrue(names.contains("analyze_equipment_cycle_time"));
    }

    @Test
    void qcManagerSeesItsThreeTools() {
        Set<String> names = registry.toolsForProfile("QC_MANAGER").stream()
                .map(Tool::name).collect(Collectors.toSet());
        assertTrue(names.contains("analyze_ncr_trends"));
        assertTrue(names.contains("audit_traceability"));
        assertTrue(names.contains("analyze_quality_data_gaps"));
    }

    @Test
    void projectManagerSeesItsThreeNewToolsAndExisting() {
        Set<String> names = registry.toolsForProfile("PROJECT_MANAGER").stream()
                .map(Tool::name).collect(Collectors.toSet());
        assertTrue(names.contains("analyze_labour_cost_per_unit"));
        assertTrue(names.contains("analyze_material_burn_rate"));
        assertTrue(names.contains("analyze_equipment_utilization_cost"));
        assertTrue(names.contains("portfolio_kpi"));
        assertTrue(names.contains("analyze_cost"));
    }

    @Test
    void bimCoordinatorSeesItsTwoTools() {
        Set<String> names = registry.toolsForProfile("BIM_DATA_COORDINATOR").stream()
                .map(Tool::name).collect(Collectors.toSet());
        assertTrue(names.contains("audit_dpr_data_quality"));
        assertTrue(names.contains("report_data_lag"));
    }

    @Test
    void siteManagerDoesNotSeePmTools() {
        Set<String> names = registry.toolsForProfile("SITE_MANAGER").stream()
                .map(Tool::name).collect(Collectors.toSet());
        // PM-tier tools tagged in Task 10 — Site Manager must NOT see them.
        assertTrue(!names.contains("portfolio_kpi"));
        assertTrue(!names.contains("forecast_completion"));
    }

    @Test
    void systemAdminSeesEverything() {
        List<Tool> all = registry.toolsForProfile("SYSTEM_ADMIN");
        Set<String> names = all.stream().map(Tool::name).collect(Collectors.toSet());
        assertTrue(names.contains("analyze_labour_utilization"));
        assertTrue(names.contains("portfolio_kpi"));
    }
}
