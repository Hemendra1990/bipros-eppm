package com.bipros.udf.domain.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 20 edge-case and real-world test cases for the ANTLR4 formula engine.
 * Covers deep nesting, variable naming, precision, boundary conditions,
 * and scenarios found in production formulas.
 */
@DisplayName("Formula Engine — 20 Edge-Case & Real-World Scenarios")
class FormulaEngineEdgeCaseTest {

    private FormulaAstCache cache;

    @BeforeEach
    void setUp() {
        cache = new FormulaAstCache();
    }

    // ---- Helpers ----

    private BigDecimal evalBd(String expression, Map<String, BigDecimal> ctx) {
        var tree = cache.get(expression);
        var visitor = new BigDecimalFormulaVisitor(ctx, 4, RoundingMode.HALF_UP, BigDecimal.ZERO);
        return visitor.visit(tree);
    }

    private BigDecimal evalBd(String expression) {
        return evalBd(expression, Map.of());
    }

    private String evalObj(String expression, Map<String, Object> ctx) {
        var tree = cache.get(expression);
        var visitor = new ObjectFormulaVisitor(ctx);
        Object result = visitor.visit(tree);
        return result != null ? String.valueOf(result) : "";
    }

    // ---- 1. Deeply nested IF (real-world: portfolio scoring) ----

    @Test
    @DisplayName("1. Deeply nested IF chain (portfolio score)")
    void deeplyNestedIfChain() {
        Map<String, BigDecimal> ctx = Map.of(
                "SPI", bd("0.85"),
                "CPI", bd("0.92"),
                "RISK", bd("3"));

        // SPI=0.85 (<0.9, >=0.8) → CPI=0.92 (>=0.9) → 80
        String expr = "IF($SPI >= 0.9, 100, IF($SPI >= 0.8, IF($CPI >= 0.9, 80, 60), IF($RISK < 5, 40, 20)))";
        assertThat(evalBd(expr, ctx)).isEqualByComparingTo(bd("80"));
    }

    // ---- 2. Variable names that match keywords (now supported) ----

    @Test
    @DisplayName("2. Variable named 'MAX' with dollar syntax")
    void variableNamedMax() {
        Map<String, BigDecimal> ctx = Map.of("MAX", bd(42));
        assertThat(evalBd("$MAX + 8", ctx)).isEqualByComparingTo(bd("50"));
    }

    @Test
    @DisplayName("3. Variable named 'if' with bracket syntax (case-insensitive)")
    void variableNamedIfBracket() {
        Map<String, BigDecimal> ctx = Map.of("if", bd(99));
        assertThat(evalBd("[if] * 2", ctx)).isEqualByComparingTo(bd("198"));
    }

    // ---- 4. Division by zero with custom zero-default ----

    @Test
    @DisplayName("4. Division by zero returns custom zero-default (not ZERO)")
    void divisionByZeroWithCustomDefault() {
        Map<String, BigDecimal> ctx = Map.of("NUM", bd(100), "DEN", bd(0));
        var tree = cache.get("$NUM / $DEN");
        var visitor = new BigDecimalFormulaVisitor(ctx, 4, RoundingMode.HALF_UP, bd("9999"));
        assertThat(visitor.visit(tree)).isEqualByComparingTo(bd("9999"));
    }

    // ---- 5. BigDecimal scale/precision edge case ----

    @Test
    @DisplayName("5. High-precision decimal arithmetic")
    void highPrecisionArithmetic() {
        Map<String, BigDecimal> ctx = Map.of("RATE", bd("0.123456789"), "HOURS", bd("40"));
        var tree = cache.get("$RATE * $HOURS");
        var visitor = new BigDecimalFormulaVisitor(ctx, 8, RoundingMode.HALF_UP, BigDecimal.ZERO);
        assertThat(visitor.visit(tree)).isEqualByComparingTo(bd("4.93827156"));
    }

    // ---- 6. Boolean with fractional BigDecimal values ----

    @Test
    @DisplayName("6. Boolean AND with fractional truthy values")
    void booleanAndWithFractions() {
        Map<String, BigDecimal> ctx = Map.of("A", bd("0.5"), "B", bd("-0.1"));
        assertThat(evalBd("$A AND $B", ctx)).isEqualByComparingTo(bd("1"));
    }

    @Test
    @DisplayName("7. Boolean NOT with very small value")
    void booleanNotWithTinyValue() {
        Map<String, BigDecimal> ctx = Map.of("X", bd("0.0001"));
        assertThat(evalBd("NOT $X", ctx)).isEqualByComparingTo(bd("0"));
    }

    // ---- 8. Comparison with exact equality on decimals ----

    @Test
    @DisplayName("8. Exact decimal equality comparison")
    void exactDecimalEquality() {
        Map<String, BigDecimal> ctx = Map.of("A", bd("1.0000"), "B", bd("1"));
        assertThat(evalBd("$A = $B", ctx)).isEqualByComparingTo(bd("1"));
    }

    // ---- 9. Chained comparisons in one expression ----

    @Test
    @DisplayName("9. Chained arithmetic and comparison")
    void chainedArithmeticAndComparison() {
        Map<String, BigDecimal> ctx = Map.of("A", bd(10), "B", bd(3), "C", bd(5));
        assertThat(evalBd("($A + $B) * 2 > $C * 4", ctx)).isEqualByComparingTo(bd("1"));
    }

    // ---- 10. SUM with many arguments (real-world: cost rollup) ----

    @Test
    @DisplayName("10. SUM with 8 arguments (cost rollup)")
    void sumWithManyArguments() {
        String expr = "SUM(100, 200, 300, 400, 500, 600, 700, 800)";
        assertThat(evalBd(expr)).isEqualByComparingTo(bd("3600"));
    }

    // ---- 11. MAX/MIN with negative and zero ----

    @Test
    @DisplayName("11. MAX with all negative values")
    void maxWithAllNegative() {
        assertThat(evalBd("MAX(-5, -10, -3, -100)")).isEqualByComparingTo(bd("-3"));
    }

    // ---- 12. POWER with negative exponent ----

    @Test
    @DisplayName("12. POWER with negative exponent")
    void powerWithNegativeExponent() {
        assertThat(evalBd("POWER(2, -3)")).isEqualByComparingTo(bd("0.1250"));
    }

    // ---- 13. ROUND with negative places ----

    @Test
    @DisplayName("13. ROUND to negative decimal places")
    void roundWithNegativePlaces() {
        assertThat(evalBd("ROUND(1234, -2)")).isEqualByComparingTo(bd("1200"));
    }

    // ---- 14. Complex health-score-like expression (full real-world) ----

    @Test
    @DisplayName("14. Full SCHED_HEALTH_SCORE with all edge values")
    void fullHealthScoreWithEdgeValues() {
        Map<String, BigDecimal> ctx = Map.of(
                "CRITICAL_PCT", bd("0.0"),
                "NEAR_CRITICAL_PCT", bd("0.0"),
                "DURATION_VARIANCE", bd("0.0"));
        String expr = "MAX(0, MIN(100, 100 - ($CRITICAL_PCT * 40) - ($NEAR_CRITICAL_PCT * 20) - MAX(0, $DURATION_VARIANCE * 40)))";
        assertThat(evalBd(expr, ctx)).isEqualByComparingTo(bd("100"));
    }

    // ---- 15. Whitespace variations (tabs, newlines) ----

    @Test
    @DisplayName("15. Formula with tabs and newlines")
    void formulaWithWhitespaceVariations() {
        Map<String, BigDecimal> ctx = Map.of("X", bd(5), "Y", bd(3));
        String expr = "\t$X\n+\t$Y\n*\n2";
        assertThat(evalBd(expr, ctx)).isEqualByComparingTo(bd("11"));
    }

    // ---- 16. Case-insensitive function names (mixed case) ----

    @Test
    @DisplayName("16. Mixed-case function names")
    void mixedCaseFunctionNames() {
        Map<String, BigDecimal> ctx = Map.of("A", bd(10), "B", bd(20));
        assertThat(evalBd("Max($A, $B) + Min($A, $B)", ctx)).isEqualByComparingTo(bd("30"));
        assertThat(evalBd("If($A > $B, 1, 0)", ctx)).isEqualByComparingTo(bd("0"));
    }

    // ---- 17. Missing variable falls back to zero (both syntaxes) ----

    @Test
    @DisplayName("17. Missing $variable and [bracket] both fall back to zero")
    void missingVariablesFallbackToZero() {
        Map<String, BigDecimal> ctx = Map.of(); // empty
        assertThat(evalBd("$MISSING + 5", ctx)).isEqualByComparingTo(bd("5"));
        assertThat(evalBd("[MISSING] + 5", ctx)).isEqualByComparingTo(bd("5"));
    }

    // ---- 18. ObjectFormulaVisitor: CONCAT with booleans and numbers ----

    @Test
    @DisplayName("18. CONCAT with boolean, number, and string")
    void concatWithMixedTypes() {
        Map<String, Object> ctx = Map.of("FLAG", true, "COUNT", 42);
        String result = evalObj("CONCAT(\"Status:\", [FLAG], \" Count:\", [COUNT])", ctx);
        assertThat(result).isEqualTo("Status:true Count:42");
    }

    // ---- 19. ObjectFormulaVisitor: string equality with case difference ----

    @Test
    @DisplayName("19. String equality is case-insensitive")
    void stringEqualityCaseInsensitive() {
        Map<String, Object> ctx = Map.of("STATUS", "ACTIVE");
        assertThat(evalObj("[STATUS] = \"active\"", ctx)).isEqualTo("true");
    }

    // ---- 20. Complex nested MAX/MIN/IF (risk score formula) ----

    @Test
    @DisplayName("20. Complex risk-score formula with nested MAX/MIN/IF")
    void complexRiskScoreFormula() {
        Map<String, BigDecimal> ctx = Map.of(
                "HIGH_RISKS", bd("2"),
                "MEDIUM_RISKS", bd("5"),
                "LOW_RISKS", bd("10"));
        String expr = "MIN(100, MAX(0, ($HIGH_RISKS * 25) + ($MEDIUM_RISKS * 10) + ($LOW_RISKS * 2)))";
        assertThat(evalBd(expr, ctx)).isEqualByComparingTo(bd("100"));
    }

    // ---- Utility ----

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }

    private static BigDecimal bd(int val) {
        return BigDecimal.valueOf(val);
    }
}
