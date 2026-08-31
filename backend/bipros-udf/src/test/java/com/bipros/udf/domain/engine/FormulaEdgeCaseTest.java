package com.bipros.udf.domain.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Edge cases for statistical, conditional, date/time and lookup functions")
class FormulaEdgeCaseTest {

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

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }

    private static BigDecimal bd(int val) {
        return BigDecimal.valueOf(val);
    }

    private static BigDecimal bd(double val) {
        return BigDecimal.valueOf(val);
    }

    @Nested
    @DisplayName("Statistical Function Edge Cases")
    class StatisticalEdgeCases {

        @Test
        @DisplayName("AVERAGE with single value")
        void averageSingle() {
            assertThat(eval("AVERAGE(42)", Map.of())).isEqualByComparingTo(bd(42));
        }

        @Test
        @DisplayName("AVERAGE with negative values")
        void averageNegative() {
            assertThat(eval("AVERAGE(-10, -20, -30)", Map.of())).isEqualByComparingTo(bd(-20));
        }

        @Test
        @DisplayName("AVERAGE with mixed signs")
        void averageMixed() {
            assertThat(eval("AVERAGE(-10, 10, 0)", Map.of())).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("COUNT with single value")
        void countSingle() {
            assertThat(eval("COUNT(1)", Map.of())).isEqualByComparingTo(BigDecimal.ONE);
        }

        @Test
        @DisplayName("STDEV with identical values = 0")
        void stdevIdentical() {
            assertThat(eval("STDEV(5, 5, 5, 5)", Map.of())).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("STDEV with two values")
        void stdevTwo() {
            assertThat(eval("STDEV(0, 10)", Map.of())).isEqualByComparingTo(bd(5));
        }

        @Test
        @DisplayName("MEDIAN with single value")
        void medianSingle() {
            assertThat(eval("MEDIAN(42)", Map.of())).isEqualByComparingTo(bd(42));
        }

        @Test
        @DisplayName("MEDIAN with two values")
        void medianTwo() {
            assertThat(eval("MEDIAN(10, 20)", Map.of())).isEqualByComparingTo(bd(15));
        }

        @Test
        @DisplayName("MEDIAN with negative values")
        void medianNegative() {
            assertThat(eval("MEDIAN(-30, -20, -10)", Map.of())).isEqualByComparingTo(bd(-20));
        }

        @Test
        @DisplayName("PERCENTILE with p=0.5 equals median")
        void percentileEqualsMedian() {
            assertThat(eval("PERCENTILE(1, 2, 3, 4, 5, 0.5)", Map.of()))
                    .isEqualByComparingTo(eval("MEDIAN(1, 2, 3, 4, 5)", Map.of()));
        }

        @Test
        @DisplayName("PERCENTILE with p=1.0 returns max")
        void percentileMax() {
            assertThat(eval("PERCENTILE(10, 20, 30, 1.0)", Map.of())).isEqualByComparingTo(bd(30));
        }

        @Test
        @DisplayName("PERCENTILE with p=0.0 returns min")
        void percentileMin() {
            assertThat(eval("PERCENTILE(10, 20, 30, 0.0)", Map.of())).isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("PERCENTILE with interpolation")
        void percentileInterpolation() {
            assertThat(eval("PERCENTILE(10, 20, 30, 0.5)", Map.of())).isEqualByComparingTo(bd(20));
        }

        @Test
        @DisplayName("PERCENTILE with large dataset")
        void percentileLarge() {
            assertThat(eval("PERCENTILE(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 0.9)", Map.of()))
                    .isEqualByComparingTo(bd(9.1));
        }
    }

    @Nested
    @DisplayName("Conditional Aggregation Edge Cases")
    class ConditionalEdgeCases {

        @Test
        @DisplayName("SUMIF with all values matching")
        void sumifAllMatch() {
            assertThat(eval("SUMIF(5, 5, 5)", Map.of())).isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("SUMIF with no values after criteria returns zero")
        void sumifNoValues() {
            assertThat(eval("SUMIF(5, 1, 2, 3)", Map.of())).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("COUNTIF with all values matching")
        void countifAllMatch() {
            assertThat(eval("COUNTIF(5, 5, 5)", Map.of())).isEqualByComparingTo(bd(2));
        }

        @Test
        @DisplayName("COUNTIF with no values after criteria returns zero")
        void countifNoValues() {
            assertThat(eval("COUNTIF(5, 1, 2, 3)", Map.of())).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("AVERAGEIF with all values matching")
        void averageifAllMatch() {
            assertThat(eval("AVERAGEIF(5, 5, 5)", Map.of())).isEqualByComparingTo(bd(5));
        }

        @Test
        @DisplayName("AVERAGEIF with single match")
        void averageifSingleMatch() {
            assertThat(eval("AVERAGEIF(5, 5, 10, 15)", Map.of())).isEqualByComparingTo(bd(5));
        }

        @Test
        @DisplayName("AVERAGEIF with no matches returns zero")
        void averageifNoMatch() {
            assertThat(eval("AVERAGEIF(99, 1, 2, 3)", Map.of())).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("SUMIF with zero as criteria")
        void sumifZeroCriteria() {
            assertThat(eval("SUMIF(0, 0, 10, 0)", Map.of())).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("COUNTIF with zero as criteria")
        void countifZeroCriteria() {
            assertThat(eval("COUNTIF(0, 0, 10, 0)", Map.of())).isEqualByComparingTo(bd(2));
        }

        @Test
        @DisplayName("SUMIF with decimal criteria")
        void sumifDecimalCriteria() {
            assertThat(eval("SUMIF(3.14, 3.14, 10)", Map.of())).isEqualByComparingTo(bd("3.14"));
        }

        @Test
        @DisplayName("SUMIF with variable criteria and values")
        void sumifAllVariables() {
            Map<String, BigDecimal> ctx = Map.of(
                    "criteria", bd(5),
                    "a", bd(5), "b", bd(3), "c", bd(5));
            assertThat(eval("SUMIF($criteria, $a, $b, $c)", ctx))
                    .isEqualByComparingTo(bd(10));
        }
    }

    @Nested
    @DisplayName("Date/Time Function Edge Cases")
    class DateTimeEdgeCases {

        @Test
        @DisplayName("DATEDIFF same date = 0")
        void datediffSame() {
            assertThat(eval("DATEDIFF(\"2024-06-15\", \"2024-06-15\")", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("DATEDIFF end before start = negative")
        void datediffNegative() {
            assertThat(eval("DATEDIFF(\"2024-12-31\", \"2024-01-01\")", Map.of()))
                    .isEqualByComparingTo(bd(-365));
        }

        @Test
        @DisplayName("DATEDIFF across year boundary")
        void datediffYearBoundary() {
            assertThat(eval("DATEDIFF(\"2023-12-31\", \"2024-01-01\")", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ONE);
        }

        @Test
        @DisplayName("DATEDIFF across leap year")
        void datediffLeapYear() {
            assertThat(eval("DATEDIFF(\"2023-02-01\", \"2024-02-01\")", Map.of()))
                    .isEqualByComparingTo(bd(365));
        }

        @Test
        @DisplayName("DAYSOFMONTH for January = 31")
        void daysofmonthJan() {
            assertThat(eval("DAYSOFMONTH(\"2024-01-15\")", Map.of())).isEqualByComparingTo(bd(31));
        }

        @Test
        @DisplayName("DAYSOFMONTH for March = 31")
        void daysofmonthMar() {
            assertThat(eval("DAYSOFMONTH(\"2024-03-15\")", Map.of())).isEqualByComparingTo(bd(31));
        }

        @Test
        @DisplayName("DAYSOFMONTH for December = 31")
        void daysofmonthDec() {
            assertThat(eval("DAYSOFMONTH(\"2024-12-15\")", Map.of())).isEqualByComparingTo(bd(31));
        }

        @Test
        @DisplayName("DAYSOFMONTH for April = 30")
        void daysofmonthApr() {
            assertThat(eval("DAYSOFMONTH(\"2024-04-15\")", Map.of())).isEqualByComparingTo(bd(30));
        }

        @Test
        @DisplayName("DAYSOFMONTH for June = 30")
        void daysofmonthJun() {
            assertThat(eval("DAYSOFMONTH(\"2024-06-15\")", Map.of())).isEqualByComparingTo(bd(30));
        }

        @Test
        @DisplayName("DAYSOFMONTH for September = 30")
        void daysofmonthSep() {
            assertThat(eval("DAYSOFMONTH(\"2024-09-15\")", Map.of())).isEqualByComparingTo(bd(30));
        }

        @Test
        @DisplayName("DAYSOFMONTH for November = 30")
        void daysofmonthNov() {
            assertThat(eval("DAYSOFMONTH(\"2024-11-15\")", Map.of())).isEqualByComparingTo(bd(30));
        }

        @Test
        @DisplayName("YEAR for year 2000")
        void year2000() {
            assertThat(eval("YEAR(\"2000-01-01\")", Map.of())).isEqualByComparingTo(bd(2000));
        }

        @Test
        @DisplayName("YEAR for year 1970 (epoch)")
        void year1970() {
            assertThat(eval("YEAR(\"1970-01-01\")", Map.of())).isEqualByComparingTo(bd(1970));
        }

        @Test
        @DisplayName("MONTH for January = 1")
        void monthJan() {
            assertThat(eval("MONTH(\"2024-01-15\")", Map.of())).isEqualByComparingTo(BigDecimal.ONE);
        }

        @Test
        @DisplayName("MONTH for December = 12")
        void monthDec() {
            assertThat(eval("MONTH(\"2024-12-15\")", Map.of())).isEqualByComparingTo(bd(12));
        }

        @Test
        @DisplayName("TODAY returns consistent value on repeated calls")
        void todayConsistent() {
            BigDecimal t1 = eval("TODAY()", Map.of());
            BigDecimal t2 = eval("TODAY()", Map.of());
            assertThat(t1).isEqualByComparingTo(t2);
        }

        @Test
        @DisplayName("DAYSOFMONTH with invalid date returns zero")
        void daysofmonthInvalid() {
            assertThat(eval("DAYSOFMONTH(\"not-a-date\")", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("YEAR with invalid date returns zero")
        void yearInvalid() {
            assertThat(eval("YEAR(\"invalid\")", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("MONTH with invalid date returns zero")
        void monthInvalid() {
            assertThat(eval("MONTH(\"invalid\")", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Lookup Function Edge Cases")
    class LookupEdgeCases {

        @Test
        @DisplayName("LOOKUP with first key matching")
        void lookupFirst() {
            assertThat(eval("LOOKUP(1, 1, 10, 2, 20, 3, 30)", Map.of()))
                    .isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("LOOKUP with last key matching")
        void lookupLast() {
            assertThat(eval("LOOKUP(3, 1, 10, 2, 20, 3, 30)", Map.of()))
                    .isEqualByComparingTo(bd(30));
        }

        @Test
        @DisplayName("LOOKUP with single pair")
        void lookupSinglePair() {
            assertThat(eval("LOOKUP(1, 1, 10)", Map.of()))
                    .isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("LOOKUP with single pair no match")
        void lookupSinglePairNoMatch() {
            assertThat(eval("LOOKUP(2, 1, 10)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("LOOKUP with only key (no pairs) returns zero")
        void lookupNoPairs() {
            assertThat(eval("LOOKUP(1, 2, 10)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("LOOKUP with duplicate keys returns first match")
        void lookupDuplicateKeys() {
            assertThat(eval("LOOKUP(2, 2, 10, 2, 20)", Map.of()))
                    .isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("LOOKUP with zero as key")
        void lookupZeroKey() {
            assertThat(eval("LOOKUP(0, 0, 10, 1, 20)", Map.of()))
                    .isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("LOOKUP with negative key")
        void lookupNegativeKey() {
            assertThat(eval("LOOKUP(-1, -1, 10, 1, 20)", Map.of()))
                    .isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("LOOKUP with decimal key")
        void lookupDecimalKey() {
            assertThat(eval("LOOKUP(3.14, 3.14, 10, 2.71, 20)", Map.of()))
                    .isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("INDEX with first position")
        void indexFirst() {
            assertThat(eval("INDEX(10, 20, 30, 1)", Map.of()))
                    .isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("INDEX with last position")
        void indexLast() {
            assertThat(eval("INDEX(10, 20, 30, 3)", Map.of()))
                    .isEqualByComparingTo(bd(30));
        }

        @Test
        @DisplayName("INDEX with single value")
        void indexSingle() {
            assertThat(eval("INDEX(42, 1)", Map.of()))
                    .isEqualByComparingTo(bd(42));
        }

        @Test
        @DisplayName("INDEX with position 0 returns zero")
        void indexZero() {
            assertThat(eval("INDEX(10, 20, 30, 0)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("INDEX with negative position returns zero")
        void indexNegative() {
            assertThat(eval("INDEX(10, 20, 30, -1)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("INDEX with large out-of-bounds position")
        void indexLargeOutOfBounds() {
            assertThat(eval("INDEX(10, 20, 30, 1000)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("INDEX with decimal values")
        void indexDecimal() {
            assertThat(eval("INDEX(1.1, 2.2, 3.3, 2)", Map.of()))
                    .isEqualByComparingTo(bd("2.2"));
        }

        @Test
        @DisplayName("INDEX with negative values")
        void indexNegativeValues() {
            assertThat(eval("INDEX(-10, -20, -30, 2)", Map.of()))
                    .isEqualByComparingTo(bd(-20));
        }

        @Test
        @DisplayName("LOOKUP with variable key")
        void lookupVariableKey() {
            Map<String, BigDecimal> ctx = Map.of("key", bd(2), "a", bd(1), "b", bd(10), "c", bd(2), "d", bd(20));
            assertThat(eval("LOOKUP($key, $a, $b, $c, $d)", ctx))
                    .isEqualByComparingTo(bd(20));
        }

        @Test
        @DisplayName("INDEX with variable position")
        void indexVariablePos() {
            Map<String, BigDecimal> ctx = Map.of("pos", bd(2), "a", bd(10), "b", bd(20), "c", bd(30));
            assertThat(eval("INDEX($a, $b, $c, $pos)", ctx))
                    .isEqualByComparingTo(bd(20));
        }
    }

    @Nested
    @DisplayName("Math Function Edge Cases")
    class MathEdgeCases {

        @Test
        @DisplayName("MOD with negative dividend")
        void modNegativeDividend() {
            assertThat(eval("MOD(-17, 5)", Map.of())).isEqualByComparingTo(bd(-2));
        }

        @Test
        @DisplayName("MOD with negative divisor")
        void modNegativeDivisor() {
            assertThat(eval("MOD(17, -5)", Map.of())).isEqualByComparingTo(bd(2));
        }

        @Test
        @DisplayName("MOD with both negative")
        void modBothNegative() {
            assertThat(eval("MOD(-17, -5)", Map.of())).isEqualByComparingTo(bd(-2));
        }

        @Test
        @DisplayName("FLOOR with integer")
        void floorInteger() {
            assertThat(eval("FLOOR(5)", Map.of())).isEqualByComparingTo(bd(5));
        }

        @Test
        @DisplayName("CEILING with integer")
        void ceilingInteger() {
            assertThat(eval("CEILING(5)", Map.of())).isEqualByComparingTo(bd(5));
        }

        @Test
        @DisplayName("LOG of e ≈ 1")
        void logE() {
            assertThat(eval("LOG(E)", Map.of())).isCloseTo(BigDecimal.ONE, within(new BigDecimal("0.0001")));
        }

        @Test
        @DisplayName("EXP of 1 ≈ e")
        void exp1() {
            assertThat(eval("EXP(1)", Map.of())).isCloseTo(bd(String.valueOf(Math.E)), within(new BigDecimal("0.0001")));
        }

        @Test
        @DisplayName("SIN of PI/2 ≈ 1")
        void sinPiHalf() {
            assertThat(eval("SIN(PI / 2)", Map.of())).isCloseTo(BigDecimal.ONE, within(new BigDecimal("0.0001")));
        }

        @Test
        @DisplayName("COS of PI ≈ -1")
        void cosPi() {
            assertThat(eval("COS(PI)", Map.of())).isCloseTo(bd(-1), within(new BigDecimal("0.0001")));
        }

        @Test
        @DisplayName("POWER with zero base")
        void powerZeroBase() {
            assertThat(eval("POWER(0, 5)", Map.of())).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("POWER with zero exponent")
        void powerZeroExp() {
            assertThat(eval("POWER(5, 0)", Map.of())).isEqualByComparingTo(BigDecimal.ONE);
        }

        @Test
        @DisplayName("SQRT of zero")
        void sqrtZero() {
            assertThat(eval("SQRT(0)", Map.of())).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("SQRT of one")
        void sqrtOne() {
            assertThat(eval("SQRT(1)", Map.of())).isEqualByComparingTo(BigDecimal.ONE);
        }
    }

    @Nested
    @DisplayName("Composite Edge Cases")
    class CompositeEdgeCases {

        @Test
        @DisplayName("Nested statistical functions")
        void nestedStatistical() {
            assertThat(eval("AVERAGE(MEDIAN(10, 20, 30), 40)", Map.of()))
                    .isEqualByComparingTo(bd(30));
        }

        @Test
        @DisplayName("Conditional inside IF")
        void conditionalInsideIf() {
            assertThat(eval("IF(SUMIF(5, 5, 10, 5, 20) > 0, 1, 0)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ONE);
        }

        @Test
        @DisplayName("Date function inside arithmetic")
        void dateInArithmetic() {
            assertThat(eval("DATEDIFF(\"2024-01-01\", \"2024-01-10\") + DAYSOFMONTH(\"2024-02-15\")", Map.of()))
                    .isEqualByComparingTo(bd(38));
        }

        @Test
        @DisplayName("Lookup inside arithmetic")
        void lookupInArithmetic() {
            assertThat(eval("LOOKUP(1, 1, 10, 2, 20) + LOOKUP(2, 1, 10, 2, 20)", Map.of()))
                    .isEqualByComparingTo(bd(30));
        }

        @Test
        @DisplayName("INDEX inside SUM")
        void indexInSum() {
            assertThat(eval("SUM(INDEX(10, 20, 30, 1), INDEX(10, 20, 30, 2), INDEX(10, 20, 30, 3))", Map.of()))
                    .isEqualByComparingTo(bd(60));
        }

        @Test
        @DisplayName("Multiple TODAY calls return same value")
        void multipleToday() {
            assertThat(eval("TODAY() - TODAY()", Map.of())).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("PERCENTILE with variable rank")
        void percentileVariableRank() {
            Map<String, BigDecimal> ctx = Map.of("p", bd("0.5"));
            assertThat(eval("PERCENTILE(10, 20, 30, $p)", ctx))
                    .isEqualByComparingTo(bd(20));
        }

        @Test
        @DisplayName("Complex nested: IF + AVERAGE + STDEV")
        void complexNested() {
            assertThat(eval("IF(AVERAGE(10, 20, 30) > 15, STDEV(10, 20, 30), 0)", Map.of()))
                    .isCloseTo(bd("8.1649"), within(new BigDecimal("0.0001")));
        }
    }
}
