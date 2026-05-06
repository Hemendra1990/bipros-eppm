package com.bipros.api.config.seeder;

import com.bipros.udf.application.service.FormulaConfigurationService;
import com.bipros.udf.domain.model.FormulaCategory;
import com.bipros.udf.domain.model.FormulaOutputType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seeds the {@code formula_master} table with all discovered system formulas.
 * Safe to re-run: skips existing codes.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class FormulaMasterSeeder {

    private final FormulaConfigurationService formulaService;

    @Bean
    CommandLineRunner seedFormulas() {
        return args -> {
            log.info("[FormulaMasterSeeder] Starting formula seeding...");
            seedEvmFormulas();
            seedCostFormulas();
            seedSchedulingFormulas();
            seedResourceFormulas();
            seedReportingFormulas();
            seedPortfolioFormulas();
            seedBaselineFormulas();
            seedPredictionFormulas();
            seedBoqFormulas();
            seedGeneralFormulas();
            log.info("[FormulaMasterSeeder] Formula seeding complete.");
        };
    }

    private void seedEvmFormulas() {
        int sort = 0;
        formulaService.seedFormula("EVM_SV", "Schedule Variance", FormulaCategory.EVM,
                "Difference between Earned Value and Planned Value",
                "$EV - $PV",
                "{\"EV\": \"BigDecimal\", \"PV\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 4, "evm", sort++);

        formulaService.seedFormula("EVM_CV", "Cost Variance", FormulaCategory.EVM,
                "Difference between Earned Value and Actual Cost",
                "$EV - $AC",
                "{\"EV\": \"BigDecimal\", \"AC\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 4, "evm", sort++);

        formulaService.seedFormula("EVM_SPI", "Schedule Performance Index", FormulaCategory.EVM,
                "Ratio of Earned Value to Planned Value",
                "IF($PV = 0, 0, $EV / $PV)",
                "{\"EV\": \"BigDecimal\", \"PV\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 4, "evm", sort++);

        formulaService.seedFormula("EVM_CPI", "Cost Performance Index", FormulaCategory.EVM,
                "Ratio of Earned Value to Actual Cost",
                "IF($AC = 0, 0, $EV / $AC)",
                "{\"EV\": \"BigDecimal\", \"AC\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 4, "evm", sort++);

        formulaService.seedFormula("EVM_EAC_CPI", "Estimate at Completion (CPI-based)", FormulaCategory.EVM,
                "Forecast total cost assuming past CPI continues",
                "IF($CPI = 0, $BAC, $BAC / $CPI)",
                "{\"BAC\": \"BigDecimal\", \"CPI\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 4, "evm", sort++);

        formulaService.seedFormula("EVM_EAC_SPI", "Estimate at Completion (SPI-based)", FormulaCategory.EVM,
                "Forecast total cost assuming past SPI continues for remaining work",
                "$AC + IF($SPI = 0, $BAC - $EV, ($BAC - $EV) / $SPI)",
                "{\"AC\": \"BigDecimal\", \"BAC\": \"BigDecimal\", \"EV\": \"BigDecimal\", \"SPI\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 4, "evm", sort++);

        formulaService.seedFormula("EVM_EAC_COMPOSITE", "Estimate at Completion (CPIxSPI)", FormulaCategory.EVM,
                "Forecast total cost using composite CPI x SPI",
                "$AC + IF($CPI * $SPI = 0, $BAC - $EV, ($BAC - $EV) / ($CPI * $SPI))",
                "{\"AC\": \"BigDecimal\", \"BAC\": \"BigDecimal\", \"EV\": \"BigDecimal\", \"CPI\": \"BigDecimal\", \"SPI\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 4, "evm", sort++);

        formulaService.seedFormula("EVM_ETC", "Estimate to Complete", FormulaCategory.EVM,
                "Expected cost to finish remaining work",
                "$EAC - $AC",
                "{\"EAC\": \"BigDecimal\", \"AC\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 4, "evm", sort++);

        formulaService.seedFormula("EVM_TCPI", "To-Complete Performance Index", FormulaCategory.EVM,
                "Required CPI to complete within budget",
                "IF($EAC - $AC = 0, 0, ($BAC - $EV) / ($EAC - $AC))",
                "{\"BAC\": \"BigDecimal\", \"EV\": \"BigDecimal\", \"EAC\": \"BigDecimal\", \"AC\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 4, "evm", sort++);

        formulaService.seedFormula("EVM_VAC", "Variance at Completion", FormulaCategory.EVM,
                "Expected budget surplus or deficit at project end",
                "$BAC - $EAC",
                "{\"BAC\": \"BigDecimal\", \"EAC\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 4, "evm", sort++);

        formulaService.seedFormula("EVM_PERF_PCT", "Performance % Complete", FormulaCategory.EVM,
                "Percent complete based on earned value",
                "IF($BAC = 0, 0, ($EV / $BAC) * 100)",
                "{\"EV\": \"BigDecimal\", \"BAC\": \"BigDecimal\"}",
                FormulaOutputType.PERCENTAGE, 4, "evm", sort++);

        formulaService.seedFormula("EVM_EV_PCT", "Earned Value (Percent Complete)", FormulaCategory.EVM,
                "EV calculated from BAC and physical percent complete",
                "$BAC * ($PCT / 100)",
                "{\"BAC\": \"BigDecimal\", \"PCT\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 4, "evm", sort++);

        formulaService.seedFormula("EVM_EV_50_50", "Earned Value (50/50 Rule)", FormulaCategory.EVM,
                "50/50 technique: 50% at start, 100% at finish",
                "IF($PCT >= 100, $BAC, $BAC * 0.5)",
                "{\"BAC\": \"BigDecimal\", \"PCT\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 2, "evm", sort++);

        formulaService.seedFormula("EVM_EV_0_100", "Earned Value (0/100 Rule)", FormulaCategory.EVM,
                "0/100 technique: 0% until 100% complete",
                "IF($PCT >= 100, $BAC, 0)",
                "{\"BAC\": \"BigDecimal\", \"PCT\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 4, "evm", sort++);

        formulaService.seedFormula("EVM_EV_LOE", "Earned Value (Level of Effort)", FormulaCategory.EVM,
                "LOE technique: EV equals PV",
                "$PV",
                "{\"PV\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 4, "evm", sort++);

        formulaService.seedFormula("EVM_PV_TIME_PHASED", "Time-Phased Planned Value", FormulaCategory.EVM,
                "PV prorated by elapsed duration",
                "IF($TOTAL_DAYS <= 0, $BAC, $BAC * ($ELAPSED_DAYS / $TOTAL_DAYS))",
                "{\"BAC\": \"BigDecimal\", \"ELAPSED_DAYS\": \"BigDecimal\", \"TOTAL_DAYS\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 4, "evm", sort++);

        formulaService.seedFormula("EVM_SCI", "Schedule-Cost Index", FormulaCategory.EVM,
                "Composite index: CPI x SPI",
                "$CPI * $SPI",
                "{\"CPI\": \"BigDecimal\", \"SPI\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 4, "evm", sort++);
    }

    private void seedCostFormulas() {
        int sort = 0;
        formulaService.seedFormula("COST_VARIANCE", "Cost Variance (Budget - Actual)", FormulaCategory.COST,
                "Simple budget versus actual variance",
                "$BUDGET - $ACTUAL",
                "{\"BUDGET\": \"BigDecimal\", \"ACTUAL\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 4, "cost", sort++);

        formulaService.seedFormula("COST_CPI", "Cost Performance Index (Budget/Actual)", FormulaCategory.COST,
                "CPI from total budget and actual",
                "IF($ACTUAL = 0, 0, $BUDGET / $ACTUAL)",
                "{\"BUDGET\": \"BigDecimal\", \"ACTUAL\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 4, "cost", sort++);

        formulaService.seedFormula("COST_REMAINING", "Remaining Budget", FormulaCategory.COST,
                "Budget remaining to be spent",
                "MAX(0, $TOTAL_BUDGET - $TOTAL_ACTUAL)",
                "{\"TOTAL_BUDGET\": \"BigDecimal\", \"TOTAL_ACTUAL\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 2, "cost", sort++);

        formulaService.seedFormula("COST_BUDGET_UTILIZATION", "Budget Utilization %", FormulaCategory.COST,
                "Percentage of budget consumed",
                "IF($BAC = 0, 0, ($AC / $BAC) * 100)",
                "{\"AC\": \"BigDecimal\", \"BAC\": \"BigDecimal\"}",
                FormulaOutputType.PERCENTAGE, 2, "cost", sort++);

        formulaService.seedFormula("COST_AT_COMPLETION", "At-Completion Cost", FormulaCategory.COST,
                "Sum of actual and remaining costs",
                "$ACTUAL_COST + $REMAINING_COST",
                "{\"ACTUAL_COST\": \"BigDecimal\", \"REMAINING_COST\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 4, "cost", sort++);

        formulaService.seedFormula("COST_MATERIAL_ISSUED", "Material Issued Cost", FormulaCategory.COST,
                "Material procured minus open stock",
                "$PROCUREMENT - $OPEN_STOCK",
                "{\"PROCUREMENT\": \"BigDecimal\", \"OPEN_STOCK\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 2, "cost", sort++);

        formulaService.seedFormula("COST_RA_DEDUCTIONS", "RA Bill Total Deductions", FormulaCategory.COST,
                "Sum of all RA bill deductions",
                "$MOB_ADVANCE + $RETENTION + $TDS + $GST",
                "{\"MOB_ADVANCE\": \"BigDecimal\", \"RETENTION\": \"BigDecimal\", \"TDS\": \"BigDecimal\", \"GST\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 2, "cost", sort++);

        formulaService.seedFormula("COST_CPI_FORECAST", "CPI-Based Cash Flow Forecast", FormulaCategory.COST,
                "Distribute remaining budget adjusted by CPI",
                "IF($CPI = 0, 0, $REMAINING * ($PERIOD_BUDGET / $TOTAL_FUTURE_BUDGET) / $CPI)",
                "{\"REMAINING\": \"BigDecimal\", \"PERIOD_BUDGET\": \"BigDecimal\", \"TOTAL_FUTURE_BUDGET\": \"BigDecimal\", \"CPI\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 2, "cost", sort++);

        formulaService.seedFormula("COST_SPI_CPI_FORECAST", "SPIxCPI Cash Flow Forecast", FormulaCategory.COST,
                "Distribute remaining budget adjusted by composite SPI x CPI",
                "IF($CPI * $SPI = 0, 0, $REMAINING * ($PERIOD_BUDGET / $TOTAL_FUTURE_BUDGET) / ($CPI * $SPI))",
                "{\"REMAINING\": \"BigDecimal\", \"PERIOD_BUDGET\": \"BigDecimal\", \"TOTAL_FUTURE_BUDGET\": \"BigDecimal\", \"CPI\": \"BigDecimal\", \"SPI\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 2, "cost", sort++);

        formulaService.seedFormula("COST_PERIOD_DISTRIBUTION", "Period Budget Distribution", FormulaCategory.COST,
                "Distribute total budget proportionally by duration",
                "$TOTAL_BUDGET * ($DAYS / $TOTAL_DAYS)",
                "{\"TOTAL_BUDGET\": \"BigDecimal\", \"DAYS\": \"BigDecimal\", \"TOTAL_DAYS\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 2, "cost", sort++);

        formulaService.seedFormula("COST_CURRENT_BUDGET", "Current Budget Recomputation", FormulaCategory.COST,
                "Base budget plus additions minus reductions",
                "$BASE + $ADDITIONS - $REDUCTIONS",
                "{\"BASE\": \"BigDecimal\", \"ADDITIONS\": \"BigDecimal\", \"REDUCTIONS\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 2, "cost", sort++);
    }

    private void seedSchedulingFormulas() {
        int sort = 0;
        formulaService.seedFormula("SCHED_TOTAL_FLOAT", "Total Float", FormulaCategory.SCHEDULING,
                "Difference between late start and early start",
                "$LATE_START - $EARLY_START",
                "{\"LATE_START\": \"BigDecimal\", \"EARLY_START\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 2, "scheduling", sort++);

        formulaService.seedFormula("SCHED_FREE_FLOAT", "Free Float", FormulaCategory.SCHEDULING,
                "Minimum float to any successor",
                "MIN($SUCCESSOR_EARLY_START - $EARLY_FINISH, $TOTAL_FLOAT)",
                "{\"SUCCESSOR_EARLY_START\": \"BigDecimal\", \"EARLY_FINISH\": \"BigDecimal\", \"TOTAL_FLOAT\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 2, "scheduling", sort++);

        formulaService.seedFormula("SCHED_HEALTH_SCORE", "Schedule Health Score", FormulaCategory.SCHEDULING,
                "Composite health score 0-100",
                "MAX(0, MIN(100, 100 - ($CRITICAL_PCT * 40) - ($NEAR_CRITICAL_PCT * 20) - MAX(0, $DURATION_VARIANCE * 40)))",
                "{\"CRITICAL_PCT\": \"BigDecimal\", \"NEAR_CRITICAL_PCT\": \"BigDecimal\", \"DURATION_VARIANCE\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 2, "scheduling", sort++);

        formulaService.seedFormula("SCHED_DURATION_VARIANCE", "Duration Variance (months)", FormulaCategory.SCHEDULING,
                "Acceptable variance based on project duration in months",
                "$DAYS_DIFFERENCE / 30.0",
                "{\"DAYS_DIFFERENCE\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 2, "scheduling", sort++);

        formulaService.seedFormula("SCHED_FAST_TRACK_OVERLAP", "Fast-Track Overlap Estimate", FormulaCategory.SCHEDULING,
                "Estimated overlap for fast-tracking",
                "$PRED_DURATION * 0.5",
                "{\"PRED_DURATION\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 2, "scheduling", sort++);

        formulaService.seedFormula("SCHED_CRASH_DURATION", "Crashed Duration", FormulaCategory.SCHEDULING,
                "Maximum crashed duration (50% reduction)",
                "$ORIGINAL_DURATION * 0.5",
                "{\"ORIGINAL_DURATION\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 2, "scheduling", sort++);

        formulaService.seedFormula("SCHED_CRASH_COST", "Crash Cost Estimate", FormulaCategory.SCHEDULING,
                "Estimated cost to crash schedule",
                "$ORIGINAL_DURATION * 10 * ($ORIGINAL_DURATION * 0.5)",
                "{\"ORIGINAL_DURATION\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 2, "scheduling", sort++);

        formulaService.seedFormula("SCHED_PCT_PHYSICAL", "Physical Percent Complete", FormulaCategory.SCHEDULING,
                "Physical completion percentage clamped to 0-100",
                "MIN(MAX($PHYSICAL_PCT, 0), 100)",
                "{\"PHYSICAL_PCT\": \"BigDecimal\"}",
                FormulaOutputType.PERCENTAGE, 2, "scheduling", sort++);

        formulaService.seedFormula("SCHED_PCT_UNITS", "Units Percent Complete", FormulaCategory.SCHEDULING,
                "Completion based on units consumed",
                "MIN(($ACTUAL_SUM / $PLANNED_SUM) * 100, 100)",
                "{\"ACTUAL_SUM\": \"BigDecimal\", \"PLANNED_SUM\": \"BigDecimal\"}",
                FormulaOutputType.PERCENTAGE, 2, "scheduling", sort++);

        formulaService.seedFormula("SCHED_PCT_DURATION", "Duration Percent Complete", FormulaCategory.SCHEDULING,
                "Completion based on elapsed duration",
                "MIN(($ELAPSED_DAYS / $ORIGINAL_DURATION) * 100, 99.99)",
                "{\"ELAPSED_DAYS\": \"BigDecimal\", \"ORIGINAL_DURATION\": \"BigDecimal\"}",
                FormulaOutputType.PERCENTAGE, 2, "scheduling", sort++);
    }

    private void seedResourceFormulas() {
        int sort = 0;
        formulaService.seedFormula("RES_UTILIZATION_EQUIPMENT", "Equipment Utilization %", FormulaCategory.RESOURCE,
                "Operating hours over total available hours",
                "IF($OPERATING + $IDLE + $BREAKDOWN = 0, 0, ($OPERATING / ($OPERATING + $IDLE + $BREAKDOWN)) * 100)",
                "{\"OPERATING\": \"BigDecimal\", \"IDLE\": \"BigDecimal\", \"BREAKDOWN\": \"BigDecimal\"}",
                FormulaOutputType.PERCENTAGE, 2, "resource", sort++);

        formulaService.seedFormula("RES_UTILIZATION_ASSIGNMENT", "Assignment Utilization %", FormulaCategory.RESOURCE,
                "Actual units over planned units",
                "IF($PLANNED_UNITS = 0, 0, ($ACTUAL_UNITS / $PLANNED_UNITS) * 100)",
                "{\"ACTUAL_UNITS\": \"BigDecimal\", \"PLANNED_UNITS\": \"BigDecimal\"}",
                FormulaOutputType.PERCENTAGE, 2, "resource", sort++);

        formulaService.seedFormula("RES_PLANNED_COST", "Planned Cost", FormulaCategory.RESOURCE,
                "Rate times planned units",
                "$RATE * $PLANNED_UNITS",
                "{\"RATE\": \"BigDecimal\", \"PLANNED_UNITS\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 4, "resource", sort++);

        formulaService.seedFormula("RES_ACTUAL_COST", "Actual Cost", FormulaCategory.RESOURCE,
                "Rate times actual units",
                "$RATE * $ACTUAL_UNITS",
                "{\"RATE\": \"BigDecimal\", \"ACTUAL_UNITS\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 4, "resource", sort++);

        formulaService.seedFormula("RES_REMAINING_COST", "Remaining Cost", FormulaCategory.RESOURCE,
                "Rate times remaining units",
                "$RATE * $REMAINING_UNITS",
                "{\"RATE\": \"BigDecimal\", \"REMAINING_UNITS\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 4, "resource", sort++);

        formulaService.seedFormula("RES_AT_COMPLETION_COST", "At-Completion Cost (Assignment)", FormulaCategory.RESOURCE,
                "Sum of actual and remaining costs",
                "$ACTUAL_COST + $REMAINING_COST",
                "{\"ACTUAL_COST\": \"BigDecimal\", \"REMAINING_COST\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 4, "resource", sort++);

        formulaService.seedFormula("RES_UNITS_PER_DAY", "Daily Planned Units", FormulaCategory.RESOURCE,
                "Planned units spread over duration in days",
                "IF($DAYS = 0, 0, $PLANNED_UNITS / $DAYS)",
                "{\"PLANNED_UNITS\": \"BigDecimal\", \"DAYS\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 4, "resource", sort++);

        formulaService.seedFormula("RES_HOURS_TO_DAYS", "Hours to Days Conversion", FormulaCategory.RESOURCE,
                "Convert hours worked to days (8 hours/day)",
                "$HOURS_WORKED / 8.0",
                "{\"HOURS_WORKED\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 2, "resource", sort++);

        formulaService.seedFormula("RES_REMAINING_UNITS", "Remaining Units", FormulaCategory.RESOURCE,
                "Planned minus actual, minimum zero",
                "MAX(0, $PLANNED_UNITS - $ACTUAL_UNITS)",
                "{\"PLANNED_UNITS\": \"BigDecimal\", \"ACTUAL_UNITS\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 4, "resource", sort++);
    }

    private void seedReportingFormulas() {
        int sort = 0;
        formulaService.seedFormula("RPT_EAC", "Report EAC", FormulaCategory.REPORTING,
                "EAC used in reports",
                "IF($CPI = 0, $BAC, $BAC / $CPI)",
                "{\"BAC\": \"BigDecimal\", \"CPI\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 2, "reporting", sort++);

        formulaService.seedFormula("RPT_ETC", "Report ETC", FormulaCategory.REPORTING,
                "ETC used in reports",
                "$EAC - $AC",
                "{\"EAC\": \"BigDecimal\", \"AC\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 2, "reporting", sort++);

        formulaService.seedFormula("RPT_VAC", "Report VAC", FormulaCategory.REPORTING,
                "VAC used in reports",
                "$BAC - $EAC",
                "{\"BAC\": \"BigDecimal\", \"EAC\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 2, "reporting", sort++);

        formulaService.seedFormula("RPT_TCPI", "Report TCPI", FormulaCategory.REPORTING,
                "TCPI used in reports",
                "IF($EAC - $AC = 0, 0, ($BAC - $EV) / ($EAC - $AC))",
                "{\"BAC\": \"BigDecimal\", \"EV\": \"BigDecimal\", \"EAC\": \"BigDecimal\", \"AC\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 2, "reporting", sort++);

        formulaService.seedFormula("RPT_COMPLETION_PCT", "Completion Percentage", FormulaCategory.REPORTING,
                "Completion percentage capped at 100",
                "MIN(($EV / $PV) * 100, 100)",
                "{\"EV\": \"BigDecimal\", \"PV\": \"BigDecimal\"}",
                FormulaOutputType.PERCENTAGE, 2, "reporting", sort++);

        formulaService.seedFormula("RPT_EST_SLIPPAGE", "Estimated Slippage (days)", FormulaCategory.REPORTING,
                "Estimated schedule slippage in days",
                "IF($SPI = 0, 0, (1 / $SPI - 1) * 10)",
                "{\"SPI\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 2, "reporting", sort++);

        formulaService.seedFormula("RPT_RISK_SCORE", "Overall Risk Score", FormulaCategory.REPORTING,
                "Weighted risk score capped at 100",
                "MIN($HIGH_RISKS * 20 + $MEDIUM_RISKS * 10, 100)",
                "{\"HIGH_RISKS\": \"BigDecimal\", \"MEDIUM_RISKS\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 2, "reporting", sort++);

        formulaService.seedFormula("RPT_ALLOCATION_RATE", "Resource Allocation Rate %", FormulaCategory.REPORTING,
                "Percentage of resources allocated",
                "IF($TOTAL = 0, 0, ($ALLOCATED / $TOTAL) * 100)",
                "{\"ALLOCATED\": \"BigDecimal\", \"TOTAL\": \"BigDecimal\"}",
                FormulaOutputType.PERCENTAGE, 2, "reporting", sort++);

        formulaService.seedFormula("RPT_OVERALL_HEALTH", "Overall Project Health", FormulaCategory.REPORTING,
                "Weighted health score from cost, schedule, and risk",
                "IF($COST_HEALTHY, 33.3, 0) + IF($SCHEDULE_HEALTHY, 33.3, 0) + IF($RISK_HEALTHY, 33.4, 0)",
                "{\"COST_HEALTHY\": \"BigDecimal\", \"SCHEDULE_HEALTHY\": \"BigDecimal\", \"RISK_HEALTHY\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 2, "reporting", sort++);
    }

    private void seedPortfolioFormulas() {
        int sort = 0;
        formulaService.seedFormula("PORT_SCORE_COST_RATIO", "Score-to-Cost Ratio", FormulaCategory.PORTFOLIO,
                "Portfolio optimization ratio",
                "IF($BUDGET = 0, 0, $SCORE / $BUDGET)",
                "{\"SCORE\": \"BigDecimal\", \"BUDGET\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 4, "portfolio", sort++);

        formulaService.seedFormula("PORT_WEIGHTED_SCORE", "Weighted Score", FormulaCategory.PORTFOLIO,
                "Sum of score x weight for portfolio criteria",
                "$SCORE * ($WEIGHT / 100.0)",
                "{\"SCORE\": \"BigDecimal\", \"WEIGHT\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 4, "portfolio", sort++);
    }

    private void seedBaselineFormulas() {
        int sort = 0;
        formulaService.seedFormula("BASE_START_VARIANCE", "Start Date Variance (days)", FormulaCategory.BASELINE,
                "Difference in start dates in days",
                "$CURRENT_START - $BASELINE_START",
                "{\"CURRENT_START\": \"BigDecimal\", \"BASELINE_START\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 0, "baseline", sort++);

        formulaService.seedFormula("BASE_FINISH_VARIANCE", "Finish Date Variance (days)", FormulaCategory.BASELINE,
                "Difference in finish dates in days",
                "$CURRENT_FINISH - $BASELINE_FINISH",
                "{\"CURRENT_FINISH\": \"BigDecimal\", \"BASELINE_FINISH\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 0, "baseline", sort++);

        formulaService.seedFormula("BASE_DURATION_VARIANCE", "Duration Variance", FormulaCategory.BASELINE,
                "Current duration minus baseline duration",
                "$CURRENT_DURATION - $BASELINE_DURATION",
                "{\"CURRENT_DURATION\": \"BigDecimal\", \"BASELINE_DURATION\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 0, "baseline", sort++);

        formulaService.seedFormula("BASE_COST_VARIANCE", "Baseline Cost Variance", FormulaCategory.BASELINE,
                "Current actual cost minus baseline planned cost",
                "$CURRENT_ACTUAL_COST - $BASELINE_PLANNED_COST",
                "{\"CURRENT_ACTUAL_COST\": \"BigDecimal\", \"BASELINE_PLANNED_COST\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 2, "baseline", sort++);
    }

    private void seedPredictionFormulas() {
        int sort = 0;
        formulaService.seedFormula("PRED_SLIP_RISK", "Schedule Slip Risk Score", FormulaCategory.PREDICTION,
                "Composite slip risk score 0-100",
                "MIN(100, IF($SPI < 0.9, 35, 0) + IF($CRITICAL > 5, 25, 0) + IF($AVG_FLOAT < 3, 25, 0) + IF($DELAYED > 5, 15, 0))",
                "{\"SPI\": \"BigDecimal\", \"CRITICAL\": \"BigDecimal\", \"AVG_FLOAT\": \"BigDecimal\", \"DELAYED\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 0, "prediction", sort++);

        formulaService.seedFormula("PRED_OVERRUN_RISK", "Cost Overrun Risk Score", FormulaCategory.PREDICTION,
                "Composite overrun risk score 0-100",
                "MIN(100, IF($CPI < 0.9, 40, 0) + IF($VO_PCT > 8, 30, 0) + IF($VO_PCT > 15, 30, 0))",
                "{\"CPI\": \"BigDecimal\", \"VO_PCT\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 0, "prediction", sort++);

        formulaService.seedFormula("PRED_SLIP_DAYS", "Predicted Slip (days)", FormulaCategory.PREDICTION,
                "Predicted schedule slip in days",
                "IF($SPI = 0, 0, (1 / $SPI - 1) * 10)",
                "{\"SPI\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 2, "prediction", sort++);

        formulaService.seedFormula("PRED_OVERRUN_COST", "Predicted Cost Overrun", FormulaCategory.PREDICTION,
                "Predicted cost overrun amount",
                "IF($CPI = 0, 0, $CONTRACT_VALUE * (1 / $CPI - 1))",
                "{\"CONTRACT_VALUE\": \"BigDecimal\", \"CPI\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 2, "prediction", sort++);

        formulaService.seedFormula("PRED_ADJUSTED_REMAINING", "SPI-Adjusted Remaining Days", FormulaCategory.PREDICTION,
                "Remaining duration adjusted by SPI",
                "IF($SPI = 0, $REMAINING, $REMAINING / $SPI)",
                "{\"REMAINING\": \"BigDecimal\", \"SPI\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 2, "prediction", sort++);

        formulaService.seedFormula("PRED_COMPLETION_VARIANCE", "Completion Variance (days)", FormulaCategory.PREDICTION,
                "Predicted total days minus baseline end days",
                "$PREDICTED_TOTAL_DAYS - $BASELINE_END_DAYS",
                "{\"PREDICTED_TOTAL_DAYS\": \"BigDecimal\", \"BASELINE_END_DAYS\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 2, "prediction", sort++);
    }

    private void seedBoqFormulas() {
        int sort = 0;
        formulaService.seedFormula("BOQ_AMOUNT", "BOQ Amount", FormulaCategory.BOQ,
                "Quantity times rate",
                "$QTY * $RATE",
                "{\"QTY\": \"BigDecimal\", \"RATE\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 2, "boq", sort++);

        formulaService.seedFormula("BOQ_BUDGETED_AMOUNT", "Budgeted Amount", FormulaCategory.BOQ,
                "BOQ quantity times budgeted rate",
                "$BOQ_QTY * $BUDGETED_RATE",
                "{\"BOQ_QTY\": \"BigDecimal\", \"BUDGETED_RATE\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 2, "boq", sort++);

        formulaService.seedFormula("BOQ_ACTUAL_AMOUNT", "Actual Amount", FormulaCategory.BOQ,
                "Executed quantity times actual rate",
                "$QTY_EXECUTED * $ACTUAL_RATE",
                "{\"QTY_EXECUTED\": \"BigDecimal\", \"ACTUAL_RATE\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 2, "boq", sort++);

        formulaService.seedFormula("BOQ_PCT_COMPLETE", "BOQ Percent Complete", FormulaCategory.BOQ,
                "Executed quantity over BOQ quantity",
                "IF($BOQ_QTY = 0, 0, ($QTY_EXECUTED / $BOQ_QTY) * 100)",
                "{\"QTY_EXECUTED\": \"BigDecimal\", \"BOQ_QTY\": \"BigDecimal\"}",
                FormulaOutputType.PERCENTAGE, 6, "boq", sort++);

        formulaService.seedFormula("BOQ_COST_VARIANCE", "BOQ Cost Variance", FormulaCategory.BOQ,
                "Actual amount minus budgeted amount for executed quantity",
                "$ACTUAL_AMOUNT - ($QTY_EXECUTED * $BUDGETED_RATE)",
                "{\"ACTUAL_AMOUNT\": \"BigDecimal\", \"QTY_EXECUTED\": \"BigDecimal\", \"BUDGETED_RATE\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 2, "boq", sort++);

        formulaService.seedFormula("BOQ_COST_VARIANCE_PCT", "BOQ Cost Variance %", FormulaCategory.BOQ,
                "Cost variance as percentage of budgeted executed amount",
                "IF($QTY_EXECUTED * $BUDGETED_RATE = 0, 0, $COST_VARIANCE / ($QTY_EXECUTED * $BUDGETED_RATE))",
                "{\"COST_VARIANCE\": \"BigDecimal\", \"QTY_EXECUTED\": \"BigDecimal\", \"BUDGETED_RATE\": \"BigDecimal\"}",
                FormulaOutputType.PERCENTAGE, 6, "boq", sort++);
    }

    private void seedGeneralFormulas() {
        int sort = 0;
        formulaService.seedFormula("GEN_LENGTH_KM", "Length in Kilometers", FormulaCategory.GENERAL,
                "Convert chainage meters to kilometers",
                "($TO_CHAINAGE - $FROM_CHAINAGE) / 1000",
                "{\"TO_CHAINAGE\": \"BigDecimal\", \"FROM_CHAINAGE\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 3, "project", sort++);

        formulaService.seedFormula("GEN_RATE_VARIANCE", "Unit Rate Variance", FormulaCategory.GENERAL,
                "Difference between actual and budgeted rate",
                "$ACTUAL_RATE - $BUDGETED_RATE",
                "{\"ACTUAL_RATE\": \"BigDecimal\", \"BUDGETED_RATE\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 6, "resource", sort++);

        formulaService.seedFormula("GEN_RATE_VARIANCE_PCT", "Unit Rate Variance %", FormulaCategory.GENERAL,
                "Rate variance as percentage of budgeted rate",
                "IF($BUDGETED_RATE = 0, 0, ($ACTUAL_RATE - $BUDGETED_RATE) / $BUDGETED_RATE)",
                "{\"ACTUAL_RATE\": \"BigDecimal\", \"BUDGETED_RATE\": \"BigDecimal\"}",
                FormulaOutputType.PERCENTAGE, 6, "resource", sort++);

        formulaService.seedFormula("GEN_WBS_UNALLOCATED", "WBS Unallocated Budget", FormulaCategory.GENERAL,
                "Node budget minus sum of children budgets",
                "$NODE_BUDGET - $CHILDREN_BUDGET",
                "{\"NODE_BUDGET\": \"BigDecimal\", \"CHILDREN_BUDGET\": \"BigDecimal\"}",
                FormulaOutputType.CURRENCY, 2, "project", sort++);

        formulaService.seedFormula("GEN_CUMULATIVE_QTY", "Cumulative Quantity", FormulaCategory.GENERAL,
                "Prior cumulative plus today's quantity",
                "$PRIOR_SUM + $QTY_TODAY",
                "{\"PRIOR_SUM\": \"BigDecimal\", \"QTY_TODAY\": \"BigDecimal\"}",
                FormulaOutputType.NUMBER, 4, "project", sort++);
    }
}
