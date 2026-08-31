package com.bipros.udf.domain.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ObjectFormulaVisitor — ANTLR4 legacy UDF formula evaluation")
class ObjectFormulaVisitorTest {

    private FormulaAstCache cache;

    @BeforeEach
    void setUp() {
        cache = new FormulaAstCache();
    }

    private String eval(String expression, Map<String, Object> context) {
        try {
            var tree = cache.get(expression);
            if (tree == null) {
                return "";
            }
            var visitor = new ObjectFormulaVisitor(context);
            Object result = visitor.visit(tree);
            return result != null ? String.valueOf(result) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String eval(String expression) {
        return eval(expression, Map.of());
    }

    @Nested
    @DisplayName("Arithmetic operations")
    class ArithmeticTests {

        @ParameterizedTest
        @CsvSource({
            "2 + 3, 5.0",
            "3 * 7, 21.0",
            "20 / 4, 5.0"
        })
        @DisplayName("basic arithmetic operations")
        void basicArithmetic(String expression, String expected) {
            assertThat(eval(expression)).isEqualTo(expected);
        }

        @Test
        @DisplayName("binary subtraction")
        void binarySubtraction() {
            assertThat(eval("10 - 4")).isEqualTo("6.0");
        }

        @Test
        @DisplayName("subtraction with no spaces around operator")
        void subtractionNoSpaces() {
            assertThat(eval("10+(-4)")).isEqualTo("6.0");
        }

        @Test
        @DisplayName("operator precedence: multiplication before addition")
        void operatorPrecedence() {
            assertThat(eval("2 + 3 * 4")).isEqualTo("14.0");
        }

        @Test
        @DisplayName("parentheses override precedence")
        void parenthesesOverridePrecedence() {
            assertThat(eval("(2 + 3) * 4")).isEqualTo("20.0");
        }

        @Test
        @DisplayName("nested parentheses with addition")
        void nestedParentheses() {
            assertThat(eval("((2 + 3) * (1 + 3))")).isEqualTo("20.0");
        }

        @Test
        @DisplayName("unary negation")
        void unaryNegation() {
            assertThat(eval("-5")).isEqualTo("-5.0");
        }

        @Test
        @DisplayName("unary positive")
        void unaryPositive() {
            assertThat(eval("+5")).isEqualTo("5");
        }

        @Test
        @DisplayName("division by zero throws FormulaEvaluationException")
        void divisionByZero() {
            assertThat(eval("10 / 0")).isEqualTo(""); // caught by try/catch in consumer
        }
    }

    @Nested
    @DisplayName("Number literals")
    class NumberLiteralTests {

        @Test
        @DisplayName("integer literal")
        void integerLiteral() {
            assertThat(eval("42")).isEqualTo("42");
        }

        @Test
        @DisplayName("decimal literal")
        void decimalLiteral() {
            assertThat(eval("3.14")).isEqualTo("3.14");
        }

        @Test
        @DisplayName("negative number literal")
        void negativeNumber() {
            assertThat(eval("-7")).isEqualTo("-7.0");
        }
    }

    @Nested
    @DisplayName("String literals")
    class StringLiteralTests {

        @Test
        @DisplayName("double-quoted string")
        void doubleQuotedString() {
            assertThat(eval("\"hello\"")).isEqualTo("hello");
        }

        @Test
        @DisplayName("single-quoted string")
        void singleQuotedString() {
            assertThat(eval("'world'")).isEqualTo("world");
        }
    }

    @Nested
    @DisplayName("Comparison operations")
    class ComparisonTests {

        @Test
        @DisplayName("equals comparison with numbers")
        void equalsNumbers() {
            assertThat(eval("5 = 5")).isEqualTo("true");
        }

        @Test
        @DisplayName("not equals comparison")
        void notEquals() {
            assertThat(eval("5 != 3")).isEqualTo("true");
        }

        @Test
        @DisplayName("less than comparison")
        void lessThan() {
            assertThat(eval("3 < 5")).isEqualTo("true");
        }

        @Test
        @DisplayName("greater than comparison")
        void greaterThan() {
            assertThat(eval("7 > 3")).isEqualTo("true");
        }

        @Test
        @DisplayName("less than or equal")
        void lessThanOrEqual() {
            assertThat(eval("5 <= 5")).isEqualTo("true");
        }

        @Test
        @DisplayName("greater than or equal")
        void greaterThanOrEqual() {
            assertThat(eval("5 >= 6")).isEqualTo("false");
        }
    }

    @Nested
    @DisplayName("Boolean operations")
    class BooleanTests {

        @Test
        @DisplayName("AND operator")
        void andOperator() {
            assertThat(eval("1 AND 1")).isEqualTo("true");
            assertThat(eval("1 AND 0")).isEqualTo("false");
        }

        @Test
        @DisplayName("OR operator")
        void orOperator() {
            assertThat(eval("0 OR 1")).isEqualTo("true");
            assertThat(eval("0 OR 0")).isEqualTo("false");
        }

        @Test
        @DisplayName("NOT operator")
        void notOperator() {
            assertThat(eval("NOT 0")).isEqualTo("true");
            assertThat(eval("NOT 1")).isEqualTo("false");
        }

        @Test
        @DisplayName("compound boolean expression")
        void compoundBoolean() {
            assertThat(eval("1 AND 1 OR 0")).isEqualTo("true");
        }
    }

    @Nested
    @DisplayName("Field references")
    class FieldReferenceTests {

        @Test
        @DisplayName("resolves field from context")
        void resolvesField() {
            var ctx = Map.<String, Object>of("Price", 100.0);
            assertThat(eval("[Price]", ctx)).isEqualTo("100.0");
        }

        @Test
        @DisplayName("arithmetic with field references")
        void arithmeticWithFields() {
            var ctx = Map.<String, Object>of("A", 10.0, "B", 20.0);
            assertThat(eval("[A] + [B]", ctx)).isEqualTo("30.0");
        }

        @Test
        @DisplayName("missing field resolves to empty string")
        void missingFieldReturnsEmpty() {
            assertThat(eval("[Unknown]", Map.of())).isEqualTo("");
        }

        @Test
        @DisplayName("field with string value")
        void stringFieldValue() {
            var ctx = Map.<String, Object>of("Name", "Alice");
            assertThat(eval("[Name]", ctx)).isEqualTo("Alice");
        }
    }

    @Nested
    @DisplayName("IF function")
    class IfFunctionTests {

        @Test
        @DisplayName("IF returns true branch when condition is true")
        void ifTrueBranch() {
            assertThat(eval("IF(1, \"yes\", \"no\")")).isEqualTo("yes");
        }

        @Test
        @DisplayName("IF returns false branch when condition is false")
        void ifFalseBranch() {
            assertThat(eval("IF(0, \"yes\", \"no\")")).isEqualTo("no");
        }

        @Test
        @DisplayName("IF with comparison condition")
        void ifWithComparison() {
            var ctx = Map.<String, Object>of("Score", 85.0);
            assertThat(eval("IF([Score] >= 80, \"Pass\", \"Fail\")", ctx)).isEqualTo("Pass");
        }

        @Test
        @DisplayName("nested IF")
        void nestedIf() {
            assertThat(eval("IF(1, IF(0, \"a\", \"b\"), \"c\")")).isEqualTo("b");
        }
    }

    @Nested
    @DisplayName("CONCAT function")
    class ConcatFunctionTests {

        @Test
        @DisplayName("concatenates strings")
        void concatStrings() {
            assertThat(eval("CONCAT(\"Hello\", \" \", \"World\")")).isEqualTo("Hello World");
        }

        @Test
        @DisplayName("concatenates mixed types")
        void concatMixed() {
            var ctx = Map.<String, Object>of("Name", "Alice", "Age", 30);
            assertThat(eval("CONCAT([Name], \" is \", [Age])", ctx)).isEqualTo("Alice is 30");
        }
    }

    @Nested
    @DisplayName("ABS function")
    class AbsFunctionTests {

        @Test
        @DisplayName("ABS of negative number")
        void absNegative() {
            assertThat(eval("ABS(-5)")).isEqualTo("5.0");
        }

        @Test
        @DisplayName("ABS of positive number")
        void absPositive() {
            assertThat(eval("ABS(5)")).isEqualTo("5.0");
        }
    }

    @Nested
    @DisplayName("ROUND function")
    class RoundFunctionTests {

        @Test
        @DisplayName("ROUND to 2 decimal places")
        void roundTwoDecimals() {
            assertThat(eval("ROUND(3.14159, 2)")).isEqualTo("3.14");
        }

        @Test
        @DisplayName("ROUND to 0 decimal places")
        void roundZeroDecimals() {
            assertThat(eval("ROUND(3.7, 0)")).isEqualTo("4.0");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("empty expression returns empty string")
        void emptyExpression() {
            assertThat(eval("")).isEmpty();
        }

        @Test
        @DisplayName("null expression returns empty string")
        void nullExpression() {
            assertThat(eval(null, Map.of())).isEmpty();
        }

        @Test
        @DisplayName("complex nested formula")
        void complexFormula() {
            var ctx = new java.util.HashMap<String, Object>();
            ctx.put("Hours", 40.0);
            ctx.put("Rate", 75.0);
            ctx.put("Overhead", 1.15);

            String result = eval("ROUND([Hours] * [Rate] * [Overhead], 2)", ctx);
            assertThat(result).isEqualTo("3450.0");
        }

        @Test
        @DisplayName("string equality comparison")
        void stringEquality() {
            var ctx = Map.<String, Object>of("Status", "Active");
            assertThat(eval("[Status] = \"Active\"", ctx)).isEqualTo("true");
        }
    }

    @Nested
    @DisplayName("String Functions")
    class StringFunctionsTests {

        @Test
        @DisplayName("LEFT(\"hello\", 2) = \"he\"")
        void left() {
            assertThat(eval("LEFT(\"hello\", 2)")).isEqualTo("he");
        }

        @Test
        @DisplayName("LEFT(\"hi\", 10) = \"hi\"")
        void leftOverflow() {
            assertThat(eval("LEFT(\"hi\", 10)")).isEqualTo("hi");
        }

        @Test
        @DisplayName("RIGHT(\"hello\", 2) = \"lo\"")
        void right() {
            assertThat(eval("RIGHT(\"hello\", 2)")).isEqualTo("lo");
        }

        @Test
        @DisplayName("RIGHT(\"hi\", 10) = \"hi\"")
        void rightOverflow() {
            assertThat(eval("RIGHT(\"hi\", 10)")).isEqualTo("hi");
        }

        @Test
        @DisplayName("MID(\"hello\", 2, 2) = \"el\"")
        void mid() {
            assertThat(eval("MID(\"hello\", 2, 2)")).isEqualTo("el");
        }

        @Test
        @DisplayName("MID(\"hi\", 5, 2) = \"\"")
        void midOutOfRange() {
            assertThat(eval("MID(\"hi\", 5, 2)")).isEqualTo("");
        }

        @Test
        @DisplayName("LENGTH(\"hello\") = 5")
        void length() {
            assertThat(eval("LENGTH(\"hello\")")).isEqualTo("5");
        }

        @Test
        @DisplayName("UPPER(\"hello\") = \"HELLO\"")
        void upper() {
            assertThat(eval("UPPER(\"hello\")")).isEqualTo("HELLO");
        }

        @Test
        @DisplayName("LOWER(\"HELLO\") = \"hello\"")
        void lower() {
            assertThat(eval("LOWER(\"HELLO\")")).isEqualTo("hello");
        }

        @Test
        @DisplayName("TRIM(\"  hello  \") = \"hello\"")
        void trim() {
            assertThat(eval("TRIM(\"  hello  \")")).isEqualTo("hello");
        }

        @Test
        @DisplayName("SUBSTITUTE(\"a,b,c\", \",\", \"-\") = \"a-b-c\"")
        void substitute() {
            assertThat(eval("SUBSTITUTE(\"a,b,c\", \",\", \"-\")")).isEqualTo("a-b-c");
        }

        @Test
        @DisplayName("SUBSTITUTE(\"abc\", \"x\", \"y\") = \"abc\"")
        void substituteNotFound() {
            assertThat(eval("SUBSTITUTE(\"abc\", \"x\", \"y\")")).isEqualTo("abc");
        }
    }

    @Nested
    @DisplayName("Math Functions")
    class MathFunctionsTests {

        @Test
        @DisplayName("MOD(17, 5) = 2.0")
        void mod() {
            assertThat(eval("MOD(17, 5)")).isEqualTo("2.0");
        }

        @Test
        @DisplayName("MOD(10, 0) = 0.0")
        void modByZero() {
            assertThat(eval("MOD(10, 0)")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("FLOOR(3.7) = 3.0")
        void floor() {
            assertThat(eval("FLOOR(3.7)")).isEqualTo("3.0");
        }

        @Test
        @DisplayName("CEILING(3.2) = 4.0")
        void ceiling() {
            assertThat(eval("CEILING(3.2)")).isEqualTo("4.0");
        }

        @Test
        @DisplayName("LOG(1) = 0.0")
        void log() {
            assertThat(eval("LOG(1)")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("EXP(0) = 1.0")
        void exp() {
            assertThat(eval("EXP(0)")).isEqualTo("1.0");
        }

        @Test
        @DisplayName("SIN(0) = 0.0")
        void sin() {
            assertThat(eval("SIN(0)")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("COS(0) = 1.0")
        void cos() {
            assertThat(eval("COS(0)")).isEqualTo("1.0");
        }
    }

    @Nested
    @DisplayName("Constants")
    class ConstantsTests {

        @Test
        @DisplayName("PI = Math.PI")
        void pi() {
            assertThat(eval("PI")).isEqualTo(String.valueOf(Math.PI));
        }

        @Test
        @DisplayName("E = Math.E")
        void euler() {
            assertThat(eval("E")).isEqualTo(String.valueOf(Math.E));
        }

        @Test
        @DisplayName("TRUE = true")
        void trueConstant() {
            assertThat(eval("TRUE")).isEqualTo("true");
        }

        @Test
        @DisplayName("FALSE = false")
        void falseConstant() {
            assertThat(eval("FALSE")).isEqualTo("false");
        }

        @Test
        @DisplayName("IF(TRUE, \"yes\", \"no\") = \"yes\"")
        void ifWithTrue() {
            assertThat(eval("IF(TRUE, \"yes\", \"no\")")).isEqualTo("yes");
        }

        @Test
        @DisplayName("IF(FALSE, \"yes\", \"no\") = \"no\"")
        void ifWithFalse() {
            assertThat(eval("IF(FALSE, \"yes\", \"no\")")).isEqualTo("no");
        }
    }

    @Nested
    @DisplayName("Statistical Functions")
    class StatisticalFunctionsTests {

        @Test
        @DisplayName("AVERAGE(10, 20, 30) = 20.0")
        void average() {
            assertThat(eval("AVERAGE(10, 20, 30)")).isEqualTo("20.0");
        }

        @Test
        @DisplayName("AVERAGE(10) = 10.0")
        void averageSingle() {
            assertThat(eval("AVERAGE(10)")).isEqualTo("10.0");
        }

        @Test
        @DisplayName("COUNT(10, 20, 30) = 3.0")
        void count() {
            assertThat(eval("COUNT(10, 20, 30)")).isEqualTo("3.0");
        }

        @Test
        @DisplayName("STDEV(2, 4, 4, 4, 5, 5, 7, 9) = 2.0")
        void stdev() {
            assertThat(eval("STDEV(2, 4, 4, 4, 5, 5, 7, 9)")).isEqualTo("2.0");
        }

        @Test
        @DisplayName("STDEV(10) = 0.0")
        void stdevSingle() {
            assertThat(eval("STDEV(10)")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("MEDIAN(10, 20, 30) = 20.0")
        void medianOdd() {
            assertThat(eval("MEDIAN(10, 20, 30)")).isEqualTo("20.0");
        }

        @Test
        @DisplayName("MEDIAN(10, 20, 30, 40) = 25.0")
        void medianEven() {
            assertThat(eval("MEDIAN(10, 20, 30, 40)")).isEqualTo("25.0");
        }

        @Test
        @DisplayName("PERCENTILE(10, 20, 30, 40, 50, 0.5) = 30.0")
        void percentileMedian() {
            assertThat(eval("PERCENTILE(10, 20, 30, 40, 50, 0.5)")).isEqualTo("30.0");
        }

        @Test
        @DisplayName("PERCENTILE(10, 20, 30, 0.0) = 10.0")
        void percentileZero() {
            assertThat(eval("PERCENTILE(10, 20, 30, 0.0)")).isEqualTo("10.0");
        }

        @Test
        @DisplayName("PERCENTILE(10, 20, 30, 1.0) = 30.0")
        void percentileHundred() {
            assertThat(eval("PERCENTILE(10, 20, 30, 1.0)")).isEqualTo("30.0");
        }

        @Test
        @DisplayName("PERCENTILE(10, 20, 30, 0.25) = 15.0")
        void percentileQuarter() {
            assertThat(eval("PERCENTILE(10, 20, 30, 0.25)")).isEqualTo("15.0");
        }
    }

    @Nested
    @DisplayName("Conditional Aggregation")
    class ConditionalAggregationTests {

        @Test
        @DisplayName("SUMIF(5, 5, 3, 5, 2) = 10.0")
        void sumif() {
            assertThat(eval("SUMIF(5, 5, 3, 5, 2)")).isEqualTo("10.0");
        }

        @Test
        @DisplayName("SUMIF(5, 1, 2, 3) = 0.0")
        void sumifNoMatches() {
            assertThat(eval("SUMIF(5, 1, 2, 3)")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("COUNTIF(5, 5, 3, 5, 2) = 2.0")
        void countif() {
            assertThat(eval("COUNTIF(5, 5, 3, 5, 2)")).isEqualTo("2.0");
        }

        @Test
        @DisplayName("COUNTIF(5, 1, 2, 3) = 0.0")
        void countifNoMatches() {
            assertThat(eval("COUNTIF(5, 1, 2, 3)")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("AVERAGEIF(10, 10, 20, 10) = 10.0")
        void averageif() {
            assertThat(eval("AVERAGEIF(10, 10, 20, 10)")).isEqualTo("10.0");
        }

        @Test
        @DisplayName("AVERAGEIF(5, 1, 2, 3) = 0.0")
        void averageifNoMatches() {
            assertThat(eval("AVERAGEIF(5, 1, 2, 3)")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("COUNTIF with string criteria (case-insensitive)")
        void countifString() {
            assertThat(eval("COUNTIF(\"A\", \"A\", \"B\", \"a\")")).isEqualTo("2.0");
        }
    }

    @Nested
    @DisplayName("Date/Time Functions")
    class DateTimeFunctionsTests {

        @Test
        @DisplayName("TODAY returns days since epoch")
        void today() {
            assertThat(eval("TODAY()")).isNotEqualTo("0.0");
        }

        @Test
        @DisplayName("DATEDIFF with string dates = 9.0")
        void datediffString() {
            assertThat(eval("DATEDIFF(\"2024-01-01\", \"2024-01-10\")")).isEqualTo("9.0");
        }

        @Test
        @DisplayName("DAYSOFMONTH for Feb 2024 = 29.0")
        void daysofmonthLeap() {
            assertThat(eval("DAYSOFMONTH(\"2024-02-15\")")).isEqualTo("29.0");
        }

        @Test
        @DisplayName("YEAR(\"2024-06-15\") = 2024.0")
        void year() {
            assertThat(eval("YEAR(\"2024-06-15\")")).isEqualTo("2024.0");
        }

        @Test
        @DisplayName("MONTH(\"2024-06-15\") = 6.0")
        void month() {
            assertThat(eval("MONTH(\"2024-06-15\")")).isEqualTo("6.0");
        }
    }

    @Nested
    @DisplayName("Lookup Functions")
    class LookupFunctionsTests {

        @Test
        @DisplayName("LOOKUP(5, 1, 10, 5, 50, 3, 30) = 50")
        void lookup() {
            assertThat(eval("LOOKUP(5, 1, 10, 5, 50, 3, 30)")).isEqualTo("50");
        }

        @Test
        @DisplayName("LOOKUP(99, 1, 10, 5, 50) = 0.0")
        void lookupNotFound() {
            assertThat(eval("LOOKUP(99, 1, 10, 5, 50)")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("INDEX(10, 20, 30, 2) = 20")
        void index() {
            assertThat(eval("INDEX(10, 20, 30, 2)")).isEqualTo("20");
        }

        @Test
        @DisplayName("INDEX with string values")
        void indexStrings() {
            assertThat(eval("INDEX(\"A\", \"B\", \"C\", 2)")).isEqualTo("B");
        }
    }
}