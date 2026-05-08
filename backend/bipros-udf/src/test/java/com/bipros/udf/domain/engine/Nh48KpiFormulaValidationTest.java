package com.bipros.udf.domain.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validation of all 40+ KPI formulas from the NH-48 Highway Project Management System
 * against the ANTLR4 formula engine.
 *
 * Covers: Manpower KPIs, Machinery KPIs, Material KPIs, and EVM/Task-level KPIs.
 */
@DisplayName("NH-48 PMS KPI Formulas — ANTLR4 Engine Validation")
class Nh48KpiFormulaValidationTest {

    private FormulaAstCache cache;

    @BeforeEach
    void setUp() {
        cache = new FormulaAstCache();
    }

    private BigDecimal eval(String expression, Map<String, BigDecimal> ctx) {
        var tree = cache.get(expression);
        var visitor = new BigDecimalFormulaVisitor(ctx, 4, RoundingMode.HALF_UP, BigDecimal.ZERO);
        return visitor.visit(tree);
    }

    // ---- A. MANPOWER KPIs ----

    @Nested
    @DisplayName("A. Manpower KPIs")
    class ManpowerKpiTests {

        @Test
        @DisplayName("Manpower Utilization % = Productive Hrs / Available Hrs × 100")
        void manpowerUtilizationPct() {
            Map<String, BigDecimal> ctx = Map.of(
                    "PRODUCTIVE_HRS", bd(6),
                    "AVAILABLE_HRS", bd(8));
            assertThat(eval("($PRODUCTIVE_HRS / $AVAILABLE_HRS) * 100", ctx))
                    .isEqualByComparingTo(bd("75"));
        }

        @Test
        @DisplayName("Idle Time Ratio % = Idle Man-hrs / Total Man-hrs × 100")
        void idleTimeRatioPct() {
            Map<String, BigDecimal> ctx = Map.of(
                    "IDLE_HRS", bd(2),
                    "TOTAL_HRS", bd(10));
            assertThat(eval("($IDLE_HRS / $TOTAL_HRS) * 100", ctx))
                    .isEqualByComparingTo(bd("20"));
        }

        @Test
        @DisplayName("OT Hours Ratio % = OT Hrs / Total Working Hrs × 100")
        void otHoursRatioPct() {
            Map<String, BigDecimal> ctx = Map.of(
                    "OT_HRS", bd(2),
                    "TOTAL_WORKING_HRS", bd(10));
            assertThat(eval("($OT_HRS / $TOTAL_WORKING_HRS) * 100", ctx))
                    .isEqualByComparingTo(bd("20"));
        }

        @Test
        @DisplayName("Labour Productivity Index (LPI) = Output Qty / (Workforce × Hours Worked)")
        void labourProductivityIndex() {
            Map<String, BigDecimal> ctx = Map.of(
                    "OUTPUT_QTY", bd(50),
                    "WORKFORCE", bd(5),
                    "HOURS_WORKED", bd(8));
            assertThat(eval("$OUTPUT_QTY / ($WORKFORCE * $HOURS_WORKED)", ctx))
                    .isEqualByComparingTo(bd("1.25"));
        }

        @Test
        @DisplayName("Output Achievement % = Actual Output / Planned Output × 100")
        void outputAchievementPct() {
            Map<String, BigDecimal> ctx = Map.of(
                    "ACTUAL_OUTPUT", bd(80),
                    "PLANNED_OUTPUT", bd(100));
            assertThat(eval("($ACTUAL_OUTPUT / $PLANNED_OUTPUT) * 100", ctx))
                    .isEqualByComparingTo(bd("80"));
        }

        @Test
        @DisplayName("Absenteeism Rate % = Absent Days / Scheduled Days × 100")
        void absenteeismRatePct() {
            Map<String, BigDecimal> ctx = Map.of(
                    "ABSENT_DAYS", bd(2),
                    "SCHEDULED_DAYS", bd(10));
            assertThat(eval("($ABSENT_DAYS / $SCHEDULED_DAYS) * 100", ctx))
                    .isEqualByComparingTo(bd("20"));
        }

        @Test
        @DisplayName("Planned Labour Cost = Planned HC × Daily Rate × (Std Hrs / 8)")
        void plannedLabourCost() {
            Map<String, BigDecimal> ctx = Map.of(
                    "PLANNED_HC", bd(10),
                    "DAILY_RATE", bd(500),
                    "STD_HRS", bd(8));
            assertThat(eval("$PLANNED_HC * $DAILY_RATE * ($STD_HRS / 8)", ctx))
                    .isEqualByComparingTo(bd("5000"));
        }

        @Test
        @DisplayName("Actual Labour Cost = Actual HC × Rate × (Hours + OT × 1.5) / 8")
        void actualLabourCost() {
            Map<String, BigDecimal> ctx = Map.of(
                    "ACTUAL_HC", bd(10),
                    "RATE", bd(500),
                    "HOURS", bd(8),
                    "OT", bd(2));
            assertThat(eval("$ACTUAL_HC * $RATE * (($HOURS + $OT * 1.5) / 8)", ctx))
                    .isEqualByComparingTo(bd("6875"));
        }

        @Test
        @DisplayName("Labour Cost Variance = Planned Labour Cost − Actual Labour Cost")
        void labourCostVariance() {
            Map<String, BigDecimal> ctx = Map.of(
                    "PLANNED_LC", bd(5000),
                    "ACTUAL_LC", bd(4500));
            assertThat(eval("$PLANNED_LC - $ACTUAL_LC", ctx))
                    .isEqualByComparingTo(bd("500"));
        }

        @Test
        @DisplayName("Labour Cost Performance Index = Budgeted LC / Actual LC")
        void labourCostPerformanceIndex() {
            Map<String, BigDecimal> ctx = Map.of(
                    "BUDGETED_LC", bd(5000),
                    "ACTUAL_LC", bd(4500));
            assertThat(eval("$BUDGETED_LC / $ACTUAL_LC", ctx))
                    .isEqualByComparingTo(bd("1.1111"));
        }
    }

    // ---- B. MACHINERY / EQUIPMENT KPIs ----

    @Nested
    @DisplayName("B. Machinery / Equipment KPIs")
    class MachineryKpiTests {

        @Test
        @DisplayName("Machine Utilization % = Productive Hrs / Available Hrs × 100")
        void machineUtilizationPct() {
            Map<String, BigDecimal> ctx = Map.of(
                    "PRODUCTIVE_HRS", bd(7),
                    "AVAILABLE_HRS", bd(8));
            assertThat(eval("($PRODUCTIVE_HRS / $AVAILABLE_HRS) * 100", ctx))
                    .isEqualByComparingTo(bd("87.5"));
        }

        @Test
        @DisplayName("Mechanical Availability % = (Available Hrs − Breakdown Hrs) / Available Hrs × 100")
        void mechanicalAvailabilityPct() {
            Map<String, BigDecimal> ctx = Map.of(
                    "AVAILABLE_HRS", bd(8),
                    "BREAKDOWN_HRS", bd(1));
            assertThat(eval("(($AVAILABLE_HRS - $BREAKDOWN_HRS) / $AVAILABLE_HRS) * 100", ctx))
                    .isEqualByComparingTo(bd("87.5"));
        }

        @Test
        @DisplayName("Physical Availability % = Scheduled Hrs / Total Calendar Hrs × 100")
        void physicalAvailabilityPct() {
            Map<String, BigDecimal> ctx = Map.of(
                    "SCHEDULED_HRS", bd(168),
                    "TOTAL_CALENDAR_HRS", bd(168));
            assertThat(eval("($SCHEDULED_HRS / $TOTAL_CALENDAR_HRS) * 100", ctx))
                    .isEqualByComparingTo(bd("100"));
        }

        @Test
        @DisplayName("Idle Hours = Available − Productive − Breakdown − Standby")
        void idleHours() {
            Map<String, BigDecimal> ctx = Map.of(
                    "AVAILABLE_HRS", bd(8),
                    "PRODUCTIVE_HRS", bd(5),
                    "BREAKDOWN_HRS", bd(1),
                    "STANDBY_HRS", bd(1));
            assertThat(eval("$AVAILABLE_HRS - $PRODUCTIVE_HRS - $BREAKDOWN_HRS - $STANDBY_HRS", ctx))
                    .isEqualByComparingTo(bd("1"));
        }

        @Test
        @DisplayName("Idle Machine Cost = Idle Hours × Hire Rate")
        void idleMachineCost() {
            Map<String, BigDecimal> ctx = Map.of(
                    "IDLE_HRS", bd(2),
                    "HIRE_RATE", bd(1500));
            assertThat(eval("$IDLE_HRS * $HIRE_RATE", ctx))
                    .isEqualByComparingTo(bd("3000"));
        }

        @Test
        @DisplayName("Fuel Cost per Shift = Fuel Litres × Fuel Rate")
        void fuelCostPerShift() {
            Map<String, BigDecimal> ctx = Map.of(
                    "FUEL_LITRES", bd(50),
                    "FUEL_RATE", bd(95));
            assertThat(eval("$FUEL_LITRES * $FUEL_RATE", ctx))
                    .isEqualByComparingTo(bd("4750"));
        }

        @Test
        @DisplayName("Total Machine Cost = Productive Hrs × Hire Rate + Fuel Cost + Maintenance")
        void totalMachineCost() {
            Map<String, BigDecimal> ctx = Map.of(
                    "PRODUCTIVE_HRS", bd(7),
                    "HIRE_RATE", bd(1500),
                    "FUEL_COST", bd(4750),
                    "MAINTENANCE", bd(500));
            assertThat(eval("$PRODUCTIVE_HRS * $HIRE_RATE + $FUEL_COST + $MAINTENANCE", ctx))
                    .isEqualByComparingTo(bd("15750"));
        }

        @Test
        @DisplayName("Machine Cost Variance = Budgeted Machine Cost − Actual Machine Cost")
        void machineCostVariance() {
            Map<String, BigDecimal> ctx = Map.of(
                    "BUDGETED_MC", bd(15000),
                    "ACTUAL_MC", bd(15750));
            assertThat(eval("$BUDGETED_MC - $ACTUAL_MC", ctx))
                    .isEqualByComparingTo(bd("-750"));
        }

        @Test
        @DisplayName("Equipment Productivity Index (EPI%) = Actual Output / Planned Output × 100")
        void equipmentProductivityIndex() {
            Map<String, BigDecimal> ctx = Map.of(
                    "ACTUAL_OUTPUT", bd(450),
                    "PLANNED_OUTPUT", bd(500));
            assertThat(eval("($ACTUAL_OUTPUT / $PLANNED_OUTPUT) * 100", ctx))
                    .isEqualByComparingTo(bd("90"));
        }

        @Test
        @DisplayName("Machine Output Rate = Actual Output / Productive Hours")
        void machineOutputRate() {
            Map<String, BigDecimal> ctx = Map.of(
                    "ACTUAL_OUTPUT", bd(450),
                    "PRODUCTIVE_HRS", bd(7));
            assertThat(eval("$ACTUAL_OUTPUT / $PRODUCTIVE_HRS", ctx))
                    .isEqualByComparingTo(bd("64.2857"));
        }

        @Test
        @DisplayName("MTBF = Total Uptime / Number of Breakdowns")
        void mtbf() {
            Map<String, BigDecimal> ctx = Map.of(
                    "TOTAL_UPTIME", bd(720),
                    "NUM_BREAKDOWNS", bd(3));
            assertThat(eval("$TOTAL_UPTIME / $NUM_BREAKDOWNS", ctx))
                    .isEqualByComparingTo(bd("240"));
        }

        @Test
        @DisplayName("MTTR = Total Downtime / Number of Repairs")
        void mttr() {
            Map<String, BigDecimal> ctx = Map.of(
                    "TOTAL_DOWNTIME", bd(24),
                    "NUM_REPAIRS", bd(6));
            assertThat(eval("$TOTAL_DOWNTIME / $NUM_REPAIRS", ctx))
                    .isEqualByComparingTo(bd("4"));
        }

        @Test
        @DisplayName("Planned Maintenance Compliance % = Completed PM Tasks / Scheduled PM Tasks × 100")
        void plannedMaintenanceCompliancePct() {
            Map<String, BigDecimal> ctx = Map.of(
                    "COMPLETED_PM", bd(18),
                    "SCHEDULED_PM", bd(20));
            assertThat(eval("($COMPLETED_PM / $SCHEDULED_PM) * 100", ctx))
                    .isEqualByComparingTo(bd("90"));
        }
    }

    // ---- C. MATERIAL KPIs ----

    @Nested
    @DisplayName("C. Material KPIs")
    class MaterialKpiTests {

        @Test
        @DisplayName("Material Utilization % = Consumed Qty / Issued Qty × 100")
        void materialUtilizationPct() {
            Map<String, BigDecimal> ctx = Map.of(
                    "CONSUMED_QTY", bd(850),
                    "ISSUED_QTY", bd(1000));
            assertThat(eval("($CONSUMED_QTY / $ISSUED_QTY) * 100", ctx))
                    .isEqualByComparingTo(bd("85"));
        }

        @Test
        @DisplayName("Wastage Qty = Issued − Consumed − Returned")
        void wastageQty() {
            Map<String, BigDecimal> ctx = Map.of(
                    "ISSUED_QTY", bd(1000),
                    "CONSUMED_QTY", bd(850),
                    "RETURNED_QTY", bd(100));
            assertThat(eval("$ISSUED_QTY - $CONSUMED_QTY - $RETURNED_QTY", ctx))
                    .isEqualByComparingTo(bd("50"));
        }

        @Test
        @DisplayName("Wastage / Shrinkage % = Wastage Qty / Issued Qty × 100")
        void wastageShrinkagePct() {
            Map<String, BigDecimal> ctx = Map.of(
                    "WASTAGE_QTY", bd(50),
                    "ISSUED_QTY", bd(1000));
            assertThat(eval("($WASTAGE_QTY / $ISSUED_QTY) * 100", ctx))
                    .isEqualByComparingTo(bd("5"));
        }

        @Test
        @DisplayName("Consumption Efficiency % = Consumed Qty / Planned Qty × 100")
        void consumptionEfficiencyPct() {
            Map<String, BigDecimal> ctx = Map.of(
                    "CONSUMED_QTY", bd(850),
                    "PLANNED_QTY", bd(800));
            assertThat(eval("($CONSUMED_QTY / $PLANNED_QTY) * 100", ctx))
                    .isEqualByComparingTo(bd("106.25"));
        }

        @Test
        @DisplayName("Material Price Variance = (Std Rate − Actual Rate) × Consumed Qty")
        void materialPriceVariance() {
            Map<String, BigDecimal> ctx = Map.of(
                    "STD_RATE", bd(100),
                    "ACTUAL_RATE", bd(95),
                    "CONSUMED_QTY", bd(850));
            assertThat(eval("($STD_RATE - $ACTUAL_RATE) * $CONSUMED_QTY", ctx))
                    .isEqualByComparingTo(bd("4250"));
        }

        @Test
        @DisplayName("Material Usage Variance = (Planned Qty − Consumed Qty) × Std Rate")
        void materialUsageVariance() {
            Map<String, BigDecimal> ctx = Map.of(
                    "PLANNED_QTY", bd(800),
                    "CONSUMED_QTY", bd(850),
                    "STD_RATE", bd(100));
            assertThat(eval("($PLANNED_QTY - $CONSUMED_QTY) * $STD_RATE", ctx))
                    .isEqualByComparingTo(bd("-5000"));
        }

        @Test
        @DisplayName("Total Material Cost Variance = Price Variance + Usage Variance")
        void totalMaterialCostVariance() {
            Map<String, BigDecimal> ctx = Map.of(
                    "PRICE_VARIANCE", bd(4250),
                    "USAGE_VARIANCE", bd(-5000));
            assertThat(eval("$PRICE_VARIANCE + $USAGE_VARIANCE", ctx))
                    .isEqualByComparingTo(bd("-750"));
        }

        @Test
        @DisplayName("Stock Turnover Ratio = Material Consumed / Average Stock Held")
        void stockTurnoverRatio() {
            Map<String, BigDecimal> ctx = Map.of(
                    "MATERIAL_CONSUMED", bd(5000),
                    "AVG_STOCK", bd(1000));
            assertThat(eval("$MATERIAL_CONSUMED / $AVG_STOCK", ctx))
                    .isEqualByComparingTo(bd("5"));
        }

        @Test
        @DisplayName("Procurement Lead Time Compliance % = On-time Deliveries / Total Deliveries × 100")
        void procurementLeadTimeCompliancePct() {
            Map<String, BigDecimal> ctx = Map.of(
                    "ON_TIME_DELIVERIES", bd(45),
                    "TOTAL_DELIVERIES", bd(50));
            assertThat(eval("($ON_TIME_DELIVERIES / $TOTAL_DELIVERIES) * 100", ctx))
                    .isEqualByComparingTo(bd("90"));
        }

        @Test
        @DisplayName("Material Reconciliation Balance Check")
        void materialReconciliation() {
            Map<String, BigDecimal> ctx = Map.of(
                    "ISSUED", bd(1000),
                    "USED", bd(850),
                    "RETURNED", bd(100),
                    "WASTAGE", bd(50));
            assertThat(eval("$ISSUED - $USED - $RETURNED - $WASTAGE", ctx))
                    .isEqualByComparingTo(bd("0"));
        }
    }

    // ---- D. EVM / TASK-LEVEL KPIs ----

    @Nested
    @DisplayName("D. EVM / Task-Level KPIs")
    class EvmKpiTests {

        @Test
        @DisplayName("Planned Value (PV) = BAC × Planned % Complete")
        void plannedValue() {
            Map<String, BigDecimal> ctx = Map.of(
                    "BAC", bd(1000000),
                    "PLANNED_PCT", bd("0.4"));
            assertThat(eval("$BAC * $PLANNED_PCT", ctx))
                    .isEqualByComparingTo(bd("400000"));
        }

        @Test
        @DisplayName("Earned Value (EV) = BAC × Actual % Complete")
        void earnedValue() {
            Map<String, BigDecimal> ctx = Map.of(
                    "BAC", bd(1000000),
                    "ACTUAL_PCT", bd("0.35"));
            assertThat(eval("$BAC * $ACTUAL_PCT", ctx))
                    .isEqualByComparingTo(bd("350000"));
        }

        @Test
        @DisplayName("Schedule Variance (SV) = EV − PV (negative = behind)")
        void scheduleVariance() {
            Map<String, BigDecimal> ctx = Map.of(
                    "EV", bd(350000),
                    "PV", bd(400000));
            assertThat(eval("$EV - $PV", ctx))
                    .isEqualByComparingTo(bd("-50000"));
        }

        @Test
        @DisplayName("Cost Variance (CV) = EV − AC (negative = over budget)")
        void costVariance() {
            Map<String, BigDecimal> ctx = Map.of(
                    "EV", bd(350000),
                    "AC", bd(380000));
            assertThat(eval("$EV - $AC", ctx))
                    .isEqualByComparingTo(bd("-30000"));
        }

        @Test
        @DisplayName("Schedule Performance Index (SPI) = EV / PV (< 1.0 = behind)")
        void schedulePerformanceIndex() {
            Map<String, BigDecimal> ctx = Map.of(
                    "EV", bd(350000),
                    "PV", bd(400000));
            assertThat(eval("$EV / $PV", ctx))
                    .isEqualByComparingTo(bd("0.875"));
        }

        @Test
        @DisplayName("Cost Performance Index (CPI) = EV / AC (< 1.0 = over budget)")
        void costPerformanceIndex() {
            Map<String, BigDecimal> ctx = Map.of(
                    "EV", bd(350000),
                    "AC", bd(380000));
            assertThat(eval("$EV / $AC", ctx))
                    .isEqualByComparingTo(bd("0.9211"));
        }

        @Test
        @DisplayName("Estimate at Completion (EAC) = BAC / CPI")
        void estimateAtCompletion() {
            Map<String, BigDecimal> ctx = Map.of(
                    "BAC", bd(1000000),
                    "CPI", bd("0.9211"));
            assertThat(eval("$BAC / $CPI", ctx))
                    .isEqualByComparingTo(bd("1085658.4519"));
        }

        @Test
        @DisplayName("Variance at Completion (VAC) = BAC − EAC")
        void varianceAtCompletion() {
            Map<String, BigDecimal> ctx = Map.of(
                    "BAC", bd(1000000),
                    "EAC", bd(1085658));
            assertThat(eval("$BAC - $EAC", ctx))
                    .isEqualByComparingTo(bd("-85658"));
        }

        @Test
        @DisplayName("To-Complete Performance Index (TCPI) = (BAC − EV) / (BAC − AC)")
        void toCompletePerformanceIndex() {
            Map<String, BigDecimal> ctx = Map.of(
                    "BAC", bd(1000000),
                    "EV", bd(350000),
                    "AC", bd(380000));
            assertThat(eval("($BAC - $EV) / ($BAC - $AC)", ctx))
                    .isEqualByComparingTo(bd("1.0484"));
        }

        @Test
        @DisplayName("Task Completion Rate = Tasks Completed / Tasks Planned × 100")
        void taskCompletionRate() {
            Map<String, BigDecimal> ctx = Map.of(
                    "TASKS_COMPLETED", bd(8),
                    "TASKS_PLANNED", bd(10));
            assertThat(eval("($TASKS_COMPLETED / $TASKS_PLANNED) * 100", ctx))
                    .isEqualByComparingTo(bd("80"));
        }

        @Test
        @DisplayName("Activity Float Consumption % = Consumed Float / Total Float × 100")
        void activityFloatConsumptionPct() {
            Map<String, BigDecimal> ctx = Map.of(
                    "CONSUMED_FLOAT", bd(5),
                    "TOTAL_FLOAT", bd(10));
            assertThat(eval("($CONSUMED_FLOAT / $TOTAL_FLOAT) * 100", ctx))
                    .isEqualByComparingTo(bd("50"));
        }
    }

    // ---- Utility ----

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }

    private static BigDecimal bd(int val) {
        return BigDecimal.valueOf(val);
    }
}
