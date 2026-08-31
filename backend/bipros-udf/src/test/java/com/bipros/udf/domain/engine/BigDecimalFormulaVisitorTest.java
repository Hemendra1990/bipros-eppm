package com.bipros.udf.domain.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BigDecimalFormulaVisitor — ANTLR4 formula evaluation")
class BigDecimalFormulaVisitorTest {

    private FormulaAstCache cache;

    @BeforeEach
    void setUp() {
        cache = new FormulaAstCache();
    }

    private BigDecimal eval(String expression, Map<String, BigDecimal> context) {
        return eval(expression, context, 4, RoundingMode.HALF_UP, BigDecimal.ZERO);
    }

    private BigDecimal eval(String expression, Map<String, BigDecimal> context,
                            int scale, RoundingMode rounding, BigDecimal zeroDefault) {
        var tree = cache.get(expression);
        var visitor = new BigDecimalFormulaVisitor(context, scale, rounding, zeroDefault);
        return visitor.visit(tree);
    }

    @Test
    @DisplayName("EVM_CPI: EV / AC")
    void evmCpi() {
        Map<String, BigDecimal> ctx = Map.of("EV", bd(80), "AC", bd(100));
        assertThat(eval("IF($AC = 0, 0, $EV / $AC)", ctx))
                .isEqualByComparingTo(bd("0.8"));
    }

    @Test
    @DisplayName("EVM_SPI: EV / PV")
    void evmSpi() {
        Map<String, BigDecimal> ctx = Map.of("EV", bd(80), "PV", bd(100));
        assertThat(eval("IF($PV = 0, 0, $EV / $PV)", ctx))
                .isEqualByComparingTo(bd("0.8"));
    }

    @Test
    @DisplayName("EVM_SV: EV - PV")
    void evmSv() {
        Map<String, BigDecimal> ctx = Map.of("EV", bd(80), "PV", bd(100));
        assertThat(eval("$EV - $PV", ctx))
                .isEqualByComparingTo(bd("-20"));
    }

    @Test
    @DisplayName("EVM_CV: EV - AC")
    void evmCv() {
        Map<String, BigDecimal> ctx = Map.of("EV", bd(80), "AC", bd(100));
        assertThat(eval("$EV - $AC", ctx))
                .isEqualByComparingTo(bd("-20"));
    }

    @Test
    @DisplayName("EVM_EAC_CPI: BAC / CPI")
    void evmEacCpi() {
        Map<String, BigDecimal> ctx = Map.of("BAC", bd(1000), "CPI", bd("0.8"));
        assertThat(eval("IF($CPI = 0, $BAC, $BAC / $CPI)", ctx))
                .isEqualByComparingTo(bd("1250"));
    }

    @Test
    @DisplayName("EVM_TCPI: (BAC - EV) / (EAC - AC)")
    void evmTcpi() {
        Map<String, BigDecimal> ctx = Map.of(
                "BAC", bd(1000),
                "EV", bd(400),
                "EAC", bd(1250),
                "AC", bd(500));
        assertThat(eval("IF($EAC - $AC = 0, 0, ($BAC - $EV) / ($EAC - $AC))", ctx))
                .isEqualByComparingTo(bd("0.8"));
    }

    @Test
    @DisplayName("EVM_PERF_PCT: (EV / BAC) * 100")
    void evmPerfPct() {
        Map<String, BigDecimal> ctx = Map.of("EV", bd(400), "BAC", bd(1000));
        assertThat(eval("IF($BAC = 0, 0, ($EV / $BAC) * 100)", ctx))
                .isEqualByComparingTo(bd("40"));
    }

    @Test
    @DisplayName("SCHED_HEALTH_SCORE clamped to 0-100")
    void scheduleHealthScore() {
        Map<String, BigDecimal> ctx = Map.of(
                "CRITICAL_PCT", bd("0.2"),
                "NEAR_CRITICAL_PCT", bd("0.1"),
                "DURATION_VARIANCE", bd("0.05"));
        BigDecimal result = eval("MAX(0, MIN(100, 100 - ($CRITICAL_PCT * 40) - ($NEAR_CRITICAL_PCT * 20) - MAX(0, $DURATION_VARIANCE * 40)))", ctx);
        assertThat(result).isEqualByComparingTo(bd("88"));
    }

    @Test
    @DisplayName("BOQ_PCT_COMPLETE: qty executed / boq qty")
    void boqPctComplete() {
        Map<String, BigDecimal> ctx = Map.of("QTY_EXECUTED", bd(75), "BOQ_QTY", bd(100));
        assertThat(eval("IF($BOQ_QTY = 0, 0, ($QTY_EXECUTED / $BOQ_QTY) * 100)", ctx))
                .isEqualByComparingTo(bd("75"));
    }

    @Test
    @DisplayName("Division by zero returns zero-default")
    void divisionByZero() {
        Map<String, BigDecimal> ctx = Map.of("EV", bd(80), "AC", bd(0));
        assertThat(eval("$EV / $AC", ctx)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("MAX and MIN functions")
    void maxMinFunctions() {
        assertThat(eval("MAX(5, 10, 3)", Map.of())).isEqualByComparingTo(bd("10"));
        assertThat(eval("MIN(5, 10, 3)", Map.of())).isEqualByComparingTo(bd("3"));
    }

    @Test
    @DisplayName("POWER and SQRT functions")
    void powerSqrtFunctions() {
        assertThat(eval("POWER(2, 3)", Map.of())).isEqualByComparingTo(bd("8"));
        assertThat(eval("SQRT(16)", Map.of())).isEqualByComparingTo(bd("4"));
    }

    @Test
    @DisplayName("SUM function")
    void sumFunction() {
        assertThat(eval("SUM(10, 20, 30)", Map.of())).isEqualByComparingTo(bd("60"));
    }

    @Test
    @DisplayName("Nested MAX/MIN")
    void nestedMaxMin() {
        assertThat(eval("MAX(0, MIN(100, 50))", Map.of()))
                .isEqualByComparingTo(bd("50"));
    }

    @Test
    @DisplayName("Bracket field references")
    void bracketReferences() {
        Map<String, BigDecimal> ctx = Map.of("FieldName", bd(42));
        assertThat(eval("[FieldName] + 8", ctx)).isEqualByComparingTo(bd("50"));
    }

    @Test
    @DisplayName("Boolean AND/OR/NOT")
    void booleanOperations() {
        assertThat(eval("1 AND 1", Map.of())).isEqualByComparingTo(bd("1"));
        assertThat(eval("1 AND 0", Map.of())).isEqualByComparingTo(bd("0"));
        assertThat(eval("0 OR 1", Map.of())).isEqualByComparingTo(bd("1"));
        assertThat(eval("NOT 0", Map.of())).isEqualByComparingTo(bd("1"));
    }

    @Test
    @DisplayName("String literal returns zero in numeric context")
    void stringLiteralNumericContext() {
        assertThat(eval("\"hello\" + 5", Map.of())).isEqualByComparingTo(bd("5"));
    }

    @Nested
    @DisplayName("Math Functions")
    class MathFunctionsTests {

        @Test
        @DisplayName("MOD(17, 5) = 2")
        void mod() {
            assertThat(eval("MOD(17, 5)", Map.of()))
                    .isEqualByComparingTo(bd(2));
        }

        @Test
        @DisplayName("MOD(10, 0) returns zero default")
        void modByZero() {
            assertThat(eval("MOD(10, 0)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("FLOOR(3.7) = 3")
        void floor() {
            assertThat(eval("FLOOR(3.7)", Map.of()))
                    .isEqualByComparingTo(bd(3));
        }

        @Test
        @DisplayName("FLOOR(-3.7) = -4")
        void floorNegative() {
            assertThat(eval("FLOOR(-3.7)", Map.of()))
                    .isEqualByComparingTo(bd(-4));
        }

        @Test
        @DisplayName("CEILING(3.2) = 4")
        void ceiling() {
            assertThat(eval("CEILING(3.2)", Map.of()))
                    .isEqualByComparingTo(bd(4));
        }

        @Test
        @DisplayName("CEILING(-3.2) = -3")
        void ceilingNegative() {
            assertThat(eval("CEILING(-3.2)", Map.of()))
                    .isEqualByComparingTo(bd(-3));
        }

        @Test
        @DisplayName("LOG(1) = 0")
        void log() {
            assertThat(eval("LOG(1)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("EXP(0) = 1")
        void exp() {
            assertThat(eval("EXP(0)", Map.of()))
                    .isEqualByComparingTo(bd(1));
        }

        @Test
        @DisplayName("SIN(0) = 0")
        void sin() {
            assertThat(eval("SIN(0)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("COS(0) = 1")
        void cos() {
            assertThat(eval("COS(0)", Map.of()))
                    .isEqualByComparingTo(bd(1));
        }
    }

    @Nested
    @DisplayName("Constants")
    class ConstantsTests {

        @Test
        @DisplayName("PI ≈ 3.14159...")
        void pi() {
            assertThat(eval("PI", Map.of()))
                    .isEqualByComparingTo(BigDecimal.valueOf(Math.PI));
        }

        @Test
        @DisplayName("E ≈ 2.71828...")
        void euler() {
            assertThat(eval("E", Map.of()))
                    .isEqualByComparingTo(BigDecimal.valueOf(Math.E));
        }

        @Test
        @DisplayName("TRUE = 1")
        void trueConstant() {
            assertThat(eval("TRUE", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ONE);
        }

        @Test
        @DisplayName("FALSE = 0")
        void falseConstant() {
            assertThat(eval("FALSE", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("IF(TRUE, 10, 20) = 10")
        void ifWithTrue() {
            assertThat(eval("IF(TRUE, 10, 20)", Map.of()))
                    .isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("IF(FALSE, 10, 20) = 20")
        void ifWithFalse() {
            assertThat(eval("IF(FALSE, 10, 20)", Map.of()))
                    .isEqualByComparingTo(bd(20));
        }
    }

    @Nested
    @DisplayName("String Functions in Numeric Context")
    class StringFunctionsZeroTests {

        @Test
        @DisplayName("String functions return 0 in BigDecimal context")
        void stringFunctionsReturnZero() {
            assertThat(eval("LEFT(\"hello\", 2)", Map.of())).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(eval("RIGHT(\"hello\", 2)", Map.of())).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(eval("MID(\"hello\", 2, 2)", Map.of())).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(eval("LENGTH(\"hello\")", Map.of())).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(eval("UPPER(\"hello\")", Map.of())).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(eval("LOWER(\"HELLO\")", Map.of())).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(eval("TRIM(\" hello \")", Map.of())).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(eval("SUBSTITUTE(\"a\", \"a\", \"b\")", Map.of())).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Statistical Functions")
    class StatisticalFunctionsTests {

        @Test
        @DisplayName("AVERAGE(10, 20, 30) = 20")
        void average() {
            assertThat(eval("AVERAGE(10, 20, 30)", Map.of()))
                    .isEqualByComparingTo(bd(20));
        }

        @Test
        @DisplayName("AVERAGE(10) = 10")
        void averageSingle() {
            assertThat(eval("AVERAGE(10)", Map.of()))
                    .isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("COUNT(10, 20, 30) = 3")
        void count() {
            assertThat(eval("COUNT(10, 20, 30)", Map.of()))
                    .isEqualByComparingTo(bd(3));
        }

        @Test
        @DisplayName("STDEV(2, 4, 4, 4, 5, 5, 7, 9) = 2")
        void stdev() {
            assertThat(eval("STDEV(2, 4, 4, 4, 5, 5, 7, 9)", Map.of()))
                    .isEqualByComparingTo(bd(2));
        }

        @Test
        @DisplayName("STDEV(10) = 0")
        void stdevSingle() {
            assertThat(eval("STDEV(10)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("MEDIAN(10, 20, 30) = 20")
        void medianOdd() {
            assertThat(eval("MEDIAN(10, 20, 30)", Map.of()))
                    .isEqualByComparingTo(bd(20));
        }

        @Test
        @DisplayName("MEDIAN(10, 20, 30, 40) = 25")
        void medianEven() {
            assertThat(eval("MEDIAN(10, 20, 30, 40)", Map.of()))
                    .isEqualByComparingTo(bd(25));
        }

        @Test
        @DisplayName("PERCENTILE(10, 20, 30, 40, 50, 0.5) = 30")
        void percentileMedian() {
            assertThat(eval("PERCENTILE(10, 20, 30, 40, 50, 0.5)", Map.of()))
                    .isEqualByComparingTo(bd(30));
        }

        @Test
        @DisplayName("PERCENTILE(10, 20, 30, 0.0) = 10")
        void percentileZero() {
            assertThat(eval("PERCENTILE(10, 20, 30, 0.0)", Map.of()))
                    .isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("PERCENTILE(10, 20, 30, 1.0) = 30")
        void percentileHundred() {
            assertThat(eval("PERCENTILE(10, 20, 30, 1.0)", Map.of()))
                    .isEqualByComparingTo(bd(30));
        }

        @Test
        @DisplayName("PERCENTILE(10, 20, 30, 0.25) = 15")
        void percentileQuarter() {
            assertThat(eval("PERCENTILE(10, 20, 30, 0.25)", Map.of()))
                    .isEqualByComparingTo(bd(15));
        }

        @Test
        @DisplayName("PERCENTILE(10) returns zero default")
        void percentileNoRank() {
            assertThat(eval("PERCENTILE(10)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Conditional Aggregation")
    class ConditionalAggregationTests {

        @Test
        @DisplayName("SUMIF(5, 5, 3, 5, 2) = 10")
        void sumif() {
            assertThat(eval("SUMIF(5, 5, 3, 5, 2)", Map.of()))
                    .isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("SUMIF(5, 1, 2, 3) = 0")
        void sumifNoMatches() {
            assertThat(eval("SUMIF(5, 1, 2, 3)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("COUNTIF(5, 5, 3, 5, 2) = 2")
        void countif() {
            assertThat(eval("COUNTIF(5, 5, 3, 5, 2)", Map.of()))
                    .isEqualByComparingTo(bd(2));
        }

        @Test
        @DisplayName("COUNTIF(5, 1, 2, 3) = 0")
        void countifNoMatches() {
            assertThat(eval("COUNTIF(5, 1, 2, 3)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("AVERAGEIF(10, 10, 20, 10) = 10")
        void averageif() {
            assertThat(eval("AVERAGEIF(10, 10, 20, 10)", Map.of()))
                    .isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("AVERAGEIF(5, 1, 2, 3) returns zero default")
        void averageifNoMatches() {
            assertThat(eval("AVERAGEIF(5, 1, 2, 3)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("SUMIF with variable criteria")
        void sumifWithVariable() {
            Map<String, BigDecimal> ctx = Map.of("threshold", bd(5), "a", bd(5), "b", bd(3), "c", bd(5));
            assertThat(eval("SUMIF($threshold, $a, $b, $c)", ctx))
                    .isEqualByComparingTo(bd(10));
        }
    }

    @Nested
    @DisplayName("Date/Time Functions")
    class DateTimeFunctionsTests {

        @Test
        @DisplayName("TODAY returns days since epoch > 20000")
        void today() {
            assertThat(eval("TODAY()", Map.of()))
                    .isGreaterThan(bd(20000));
        }

        @Test
        @DisplayName("DATEDIFF with string dates = 9")
        void datediffString() {
            assertThat(eval("DATEDIFF(\"2024-01-01\", \"2024-01-10\")", Map.of()))
                    .isEqualByComparingTo(bd(9));
        }

        @Test
        @DisplayName("DATEDIFF with epoch days = 9")
        void datediffEpoch() {
            assertThat(eval("DATEDIFF(19723, 19732)", Map.of()))
                    .isEqualByComparingTo(bd(9));
        }

        @Test
        @DisplayName("DATEDIFF with invalid date returns zero")
        void datediffInvalid() {
            assertThat(eval("DATEDIFF(\"invalid\", \"2024-01-10\")", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("DAYSOFMONTH for Feb 2024 (leap) = 29")
        void daysofmonthLeap() {
            assertThat(eval("DAYSOFMONTH(\"2024-02-15\")", Map.of()))
                    .isEqualByComparingTo(bd(29));
        }

        @Test
        @DisplayName("DAYSOFMONTH for Feb 2023 = 28")
        void daysofmonthNonLeap() {
            assertThat(eval("DAYSOFMONTH(\"2023-02-15\")", Map.of()))
                    .isEqualByComparingTo(bd(28));
        }

        @Test
        @DisplayName("DAYSOFMONTH for April = 30")
        void daysofmonthApril() {
            assertThat(eval("DAYSOFMONTH(\"2024-04-15\")", Map.of()))
                    .isEqualByComparingTo(bd(30));
        }

        @Test
        @DisplayName("YEAR(\"2024-06-15\") = 2024")
        void year() {
            assertThat(eval("YEAR(\"2024-06-15\")", Map.of()))
                    .isEqualByComparingTo(bd(2024));
        }

        @Test
        @DisplayName("MONTH(\"2024-06-15\") = 6")
        void month() {
            assertThat(eval("MONTH(\"2024-06-15\")", Map.of()))
                    .isEqualByComparingTo(bd(6));
        }
    }

    @Nested
    @DisplayName("Lookup Functions")
    class LookupFunctionsTests {

        @Test
        @DisplayName("LOOKUP(5, 1, 10, 5, 50, 3, 30) = 50")
        void lookup() {
            assertThat(eval("LOOKUP(5, 1, 10, 5, 50, 3, 30)", Map.of()))
                    .isEqualByComparingTo(bd(50));
        }

        @Test
        @DisplayName("LOOKUP(99, 1, 10, 5, 50) returns zero")
        void lookupNotFound() {
            assertThat(eval("LOOKUP(99, 1, 10, 5, 50)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("LOOKUP with odd pairs ignores last")
        void lookupOddPairs() {
            assertThat(eval("LOOKUP(1, 1, 10, 5)", Map.of()))
                    .isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("INDEX(10, 20, 30, 2) = 20")
        void index() {
            assertThat(eval("INDEX(10, 20, 30, 2)", Map.of()))
                    .isEqualByComparingTo(bd(20));
        }

        @Test
        @DisplayName("INDEX out of bounds returns zero")
        void indexOutOfBounds() {
            assertThat(eval("INDEX(10, 20, 30, 5)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }

    private static BigDecimal bd(int val) {
        return BigDecimal.valueOf(val);
    }
}
