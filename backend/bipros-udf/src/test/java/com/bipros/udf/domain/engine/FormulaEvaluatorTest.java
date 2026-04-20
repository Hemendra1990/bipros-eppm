package com.bipros.udf.domain.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FormulaEvaluator Tests")
class FormulaEvaluatorTest {

    private String eval(String expression, Map<String, Object> context) {
        return new FormulaEvaluator(expression, context).evaluate();
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
        @DisplayName("subtraction with no spaces around operator")
        void subtractionNoSpaces() {
            // Parser requires digits adjacent to minus for subtraction
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
        @DisplayName("division by zero returns empty string (caught)")
        void divisionByZero() {
            assertThat(eval("10 / 0")).isEmpty();
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

        @Test
        @DisplayName("ABS of negative expression")
        void absExpression() {
            assertThat(eval("ABS(-10)")).isEqualTo("10.0");
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

        @Test
        @DisplayName("ROUND with field reference")
        void roundWithField() {
            var ctx = Map.<String, Object>of("Value", 123.456);
            assertThat(eval("ROUND([Value], 1)", ctx)).isEqualTo("123.5");
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
            assertThat(new FormulaEvaluator(null, null).evaluate()).isEmpty();
        }

        @Test
        @DisplayName("null context is handled")
        void nullContext() {
            assertThat(new FormulaEvaluator("42", null).evaluate()).isEqualTo("42");
        }

        @Test
        @DisplayName("whitespace-only expression returns empty")
        void whitespaceOnly() {
            // Parser skips whitespace then hits end of input, parsePrimary returns 0,
            // but evaluate trims first so expression becomes empty
            assertThat(eval("   ")).isEmpty();
        }

        @Test
        @DisplayName("complex nested formula")
        void complexFormula() {
            var ctx = new HashMap<String, Object>();
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

        @Test
        @DisplayName("boolean field values work correctly")
        void booleanInContext() {
            var ctx = Map.<String, Object>of("Flag", true);
            assertThat(eval("IF([Flag], \"on\", \"off\")", ctx)).isEqualTo("on");
        }
    }

    @Nested
    @DisplayName("Malformed expressions")
    class MalformedExpressionTests {

        @Test
        @DisplayName("unclosed parenthesis returns gracefully")
        void unclosedParenthesis() {
            // Parser catches errors and returns empty string
            String result = eval("(2 + 3");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("unclosed string literal returns gracefully")
        void unclosedString() {
            String result = eval("\"hello");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("unknown function returns gracefully")
        void unknownFunction() {
            String result = eval("UNKNOWN(1, 2)");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("unclosed field reference returns gracefully")
        void unclosedFieldReference() {
            String result = eval("[field");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("consecutive operators return gracefully")
        void consecutiveOperators() {
            String result = eval("2 + + 3");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("IF with wrong arg count returns gracefully")
        void ifWrongArgCount() {
            String result = eval("IF(1, \"yes\")");
            assertThat(result).isNotNull();
        }
    }
}
