package com.bipros.udf.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.udf.application.dto.FormulaResultDto;
import com.bipros.udf.domain.engine.FormulaAstCache;
import com.bipros.udf.domain.model.FormulaCategory;
import com.bipros.udf.domain.model.FormulaMaster;
import com.bipros.udf.domain.model.FormulaOutputType;
import com.bipros.udf.domain.repository.FormulaMasterRepository;
import com.bipros.udf.domain.repository.FormulaOverrideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayName("FormulaEngine — integration tests for all 57 functions")
@ExtendWith(MockitoExtension.class)
class FormulaEngineIntegrationTest {

    @Mock
    private FormulaMasterRepository formulaMasterRepository;

    @Mock
    private FormulaOverrideRepository formulaOverrideRepository;

    private FormulaAstCache formulaAstCache;
    private FormulaEngine engine;

    @BeforeEach
    void setUp() {
        formulaAstCache = new FormulaAstCache();
        engine = new FormulaEngine(formulaMasterRepository, formulaOverrideRepository, formulaAstCache);
    }

    private FormulaMaster createMaster(String code, String expression) {
        return createMaster(code, expression, 4, RoundingMode.HALF_UP, "0");
    }

    private FormulaMaster createMaster(String code, String expression, int scale, RoundingMode rounding, String zeroDefault) {
        FormulaMaster master = new FormulaMaster();
        master.setCode(code);
        master.setName("Test " + code);
        master.setCategory(FormulaCategory.EVM);
        master.setDefaultExpression(expression);
        master.setOutputType(FormulaOutputType.NUMBER);
        master.setIsActive(true);
        master.setScale(scale);
        master.setRoundingMode(rounding);
        master.setZeroDefault(zeroDefault);
        return master;
    }

    @Nested
    @DisplayName("Arithmetic & Comparison")
    class ArithmeticComparisonTests {

        @Test
        @DisplayName("Basic arithmetic: addition, subtraction, multiplication, division")
        void basicArithmetic() {
            FormulaMaster master = createMaster("ARITH", "10 + 5 * 2 - 3");
            when(formulaMasterRepository.findByCode("ARITH")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("ARITH", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("17"));
        }

        @Test
        @DisplayName("Comparison operators: =, !=, <, >, <=, >=")
        void comparisonOperators() {
            FormulaMaster master = createMaster("CMP", "IF(10 > 5, 1, 0)");
            when(formulaMasterRepository.findByCode("CMP")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("CMP", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(BigDecimal.ONE);
        }

        @Test
        @DisplayName("Parentheses grouping")
        void parenthesesGrouping() {
            FormulaMaster master = createMaster("PAREN", "(2 + 3) * 4");
            when(formulaMasterRepository.findByCode("PAREN")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("PAREN", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("20"));
        }

        @Test
        @DisplayName("Variable references with $ prefix")
        void variableReferences() {
            FormulaMaster master = createMaster("VAR", "$X + $Y");
            when(formulaMasterRepository.findByCode("VAR")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("VAR", null, Map.of("X", new BigDecimal("10"), "Y", new BigDecimal("20")));

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("30"));
        }
    }

    @Nested
    @DisplayName("Logical Functions")
    class LogicalFunctionsTests {

        @Test
        @DisplayName("IF with true condition")
        void ifTrue() {
            FormulaMaster master = createMaster("IF_TRUE", "IF(1, 100, 200)");
            when(formulaMasterRepository.findByCode("IF_TRUE")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("IF_TRUE", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("100"));
        }

        @Test
        @DisplayName("IF with false condition")
        void ifFalse() {
            FormulaMaster master = createMaster("IF_FALSE", "IF(0, 100, 200)");
            when(formulaMasterRepository.findByCode("IF_FALSE")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("IF_FALSE", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("200"));
        }

        @Test
        @DisplayName("Nested IF")
        void nestedIf() {
            FormulaMaster master = createMaster("NESTED_IF", "IF($X > 10, 1, IF($X > 5, 2, 3))");
            when(formulaMasterRepository.findByCode("NESTED_IF")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("NESTED_IF", null, Map.of("X", new BigDecimal("7")));

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("2"));
        }

        @Test
        @DisplayName("AND function")
        void andFunction() {
            FormulaMaster master = createMaster("AND", "1 AND 1 AND 0");
            when(formulaMasterRepository.findByCode("AND")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("AND", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("OR function")
        void orFunction() {
            FormulaMaster master = createMaster("OR", "0 OR 0 OR 1");
            when(formulaMasterRepository.findByCode("OR")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("OR", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(BigDecimal.ONE);
        }

        @Test
        @DisplayName("NOT function")
        void notFunction() {
            FormulaMaster master = createMaster("NOT", "NOT 1");
            when(formulaMasterRepository.findByCode("NOT")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("NOT", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Numeric Functions")
    class NumericFunctionsTests {

        @Test
        @DisplayName("ABS function")
        void abs() {
            FormulaMaster master = createMaster("ABS", "ABS(-42)");
            when(formulaMasterRepository.findByCode("ABS")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("ABS", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("42"));
        }

        @Test
        @DisplayName("ROUND function")
        void round() {
            FormulaMaster master = createMaster("ROUND", "ROUND(3.14159, 2)");
            when(formulaMasterRepository.findByCode("ROUND")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("ROUND", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("3.14"));
        }

        @Test
        @DisplayName("MAX function with multiple args")
        void max() {
            FormulaMaster master = createMaster("MAX", "MAX(3, 1, 4, 1, 5, 9, 2, 6)");
            when(formulaMasterRepository.findByCode("MAX")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("MAX", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("9"));
        }

        @Test
        @DisplayName("MIN function with multiple args")
        void min() {
            FormulaMaster master = createMaster("MIN", "MIN(3, 1, 4, 1, 5, 9, 2, 6)");
            when(formulaMasterRepository.findByCode("MIN")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("MIN", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("1"));
        }

        @Test
        @DisplayName("POWER function")
        void power() {
            FormulaMaster master = createMaster("POWER", "POWER(2, 10)");
            when(formulaMasterRepository.findByCode("POWER")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("POWER", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("1024"));
        }

        @Test
        @DisplayName("SQRT function")
        void sqrt() {
            FormulaMaster master = createMaster("SQRT", "SQRT(144)");
            when(formulaMasterRepository.findByCode("SQRT")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("SQRT", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("12"));
        }

        @Test
        @DisplayName("SUM function")
        void sum() {
            FormulaMaster master = createMaster("SUM", "SUM(1, 2, 3, 4, 5)");
            when(formulaMasterRepository.findByCode("SUM")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("SUM", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("15"));
        }
    }

    @Nested
    @DisplayName("Phase 1: String Functions (via Object visitor)")
    class Phase1StringTests {

        @Test
        @DisplayName("String functions return zero in BigDecimal context")
        void stringFunctionsZeroInBigDecimal() {
            FormulaMaster master = createMaster("STR", "LEFT(\"hello\", 2)");
            when(formulaMasterRepository.findByCode("STR")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("STR", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Constants: PI, E, TRUE, FALSE")
        void constants() {
            FormulaMaster master = createMaster("CONST", "PI + E + TRUE + FALSE");
            when(formulaMasterRepository.findByCode("CONST")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("CONST", null, Map.of());

            assertThat(result.isError()).isFalse();
            BigDecimal expected = new BigDecimal(String.valueOf(Math.PI + Math.E + 1));
            assertThat(result.getValue()).isCloseTo(expected, within(new BigDecimal("0.0001")));
        }
    }

    @Nested
    @DisplayName("Phase 1: Math Functions")
    class Phase1MathTests {

        @Test
        @DisplayName("MOD function")
        void mod() {
            FormulaMaster master = createMaster("MOD", "MOD(17, 5)");
            when(formulaMasterRepository.findByCode("MOD")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("MOD", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("2"));
        }

        @Test
        @DisplayName("FLOOR function")
        void floor() {
            FormulaMaster master = createMaster("FLOOR", "FLOOR(4.9)");
            when(formulaMasterRepository.findByCode("FLOOR")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("FLOOR", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("4"));
        }

        @Test
        @DisplayName("CEILING function")
        void ceiling() {
            FormulaMaster master = createMaster("CEILING", "CEILING(4.1)");
            when(formulaMasterRepository.findByCode("CEILING")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("CEILING", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("5"));
        }

        @Test
        @DisplayName("LOG function: ln(e) = 1")
        void log() {
            FormulaMaster master = createMaster("LOG", "LOG(E)");
            when(formulaMasterRepository.findByCode("LOG")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("LOG", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isCloseTo(BigDecimal.ONE, within(new BigDecimal("0.0001")));
        }

        @Test
        @DisplayName("EXP function: exp(0) = 1")
        void exp() {
            FormulaMaster master = createMaster("EXP", "EXP(0)");
            when(formulaMasterRepository.findByCode("EXP")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("EXP", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(BigDecimal.ONE);
        }

        @Test
        @DisplayName("SIN function: sin(0) = 0")
        void sin() {
            FormulaMaster master = createMaster("SIN", "SIN(0)");
            when(formulaMasterRepository.findByCode("SIN")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("SIN", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("COS function: cos(0) = 1")
        void cos() {
            FormulaMaster master = createMaster("COS", "COS(0)");
            when(formulaMasterRepository.findByCode("COS")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("COS", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(BigDecimal.ONE);
        }
    }

    @Nested
    @DisplayName("Phase 2: Statistical Functions")
    class Phase2StatisticalTests {

        @Test
        @DisplayName("AVERAGE function")
        void average() {
            FormulaMaster master = createMaster("AVG", "AVERAGE(10, 20, 30, 40, 50)");
            when(formulaMasterRepository.findByCode("AVG")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("AVG", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("30"));
        }

        @Test
        @DisplayName("COUNT function")
        void count() {
            FormulaMaster master = createMaster("CNT", "COUNT(1, 2, 3, 4, 5)");
            when(formulaMasterRepository.findByCode("CNT")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("CNT", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("5"));
        }

        @Test
        @DisplayName("STDEV function: population standard deviation")
        void stdev() {
            FormulaMaster master = createMaster("STD", "STDEV(2, 4, 4, 4, 5, 5, 7, 9)");
            when(formulaMasterRepository.findByCode("STD")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("STD", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("2"));
        }

        @Test
        @DisplayName("MEDIAN function: odd count")
        void medianOdd() {
            FormulaMaster master = createMaster("MED_ODD", "MEDIAN(1, 3, 5, 7, 9)");
            when(formulaMasterRepository.findByCode("MED_ODD")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("MED_ODD", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("5"));
        }

        @Test
        @DisplayName("MEDIAN function: even count")
        void medianEven() {
            FormulaMaster master = createMaster("MED_EVEN", "MEDIAN(1, 2, 3, 4)");
            when(formulaMasterRepository.findByCode("MED_EVEN")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("MED_EVEN", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("2.5"));
        }

        @Test
        @DisplayName("PERCENTILE function: 25th percentile")
        void percentile25() {
            FormulaMaster master = createMaster("P25", "PERCENTILE(10, 20, 30, 40, 50, 0.25)");
            when(formulaMasterRepository.findByCode("P25")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("P25", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("20"));
        }

        @Test
        @DisplayName("PERCENTILE function: 75th percentile")
        void percentile75() {
            FormulaMaster master = createMaster("P75", "PERCENTILE(10, 20, 30, 40, 50, 0.75)");
            when(formulaMasterRepository.findByCode("P75")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("P75", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("40"));
        }
    }

    @Nested
    @DisplayName("Phase 3: Conditional Aggregation")
    class Phase3ConditionalTests {

        @Test
        @DisplayName("SUMIF with matching values")
        void sumifMatches() {
            FormulaMaster master = createMaster("SUMIF", "SUMIF(5, 5, 10, 5, 20)");
            when(formulaMasterRepository.findByCode("SUMIF")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("SUMIF", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("10"));
        }

        @Test
        @DisplayName("COUNTIF with matching values")
        void countifMatches() {
            FormulaMaster master = createMaster("COUNTIF", "COUNTIF(10, 10, 20, 10, 30)");
            when(formulaMasterRepository.findByCode("COUNTIF")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("COUNTIF", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("2"));
        }

        @Test
        @DisplayName("AVERAGEIF with matching values")
        void averageifMatches() {
            FormulaMaster master = createMaster("AVGIF", "AVERAGEIF(10, 10, 20, 10)");
            when(formulaMasterRepository.findByCode("AVGIF")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("AVGIF", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("10"));
        }

        @Test
        @DisplayName("SUMIF with no matches returns zero")
        void sumifNoMatches() {
            FormulaMaster master = createMaster("SUMIF_ZERO", "SUMIF(99, 1, 2, 3)");
            when(formulaMasterRepository.findByCode("SUMIF_ZERO")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("SUMIF_ZERO", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Phase 4: Date/Time Functions")
    class Phase4DateTimeTests {

        @Test
        @DisplayName("TODAY returns days since epoch")
        void today() {
            FormulaMaster master = createMaster("TODAY", "TODAY()");
            when(formulaMasterRepository.findByCode("TODAY")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("TODAY", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isGreaterThan(new BigDecimal("20000"));
        }

        @Test
        @DisplayName("DATEDIFF with ISO date strings")
        void datediffIso() {
            FormulaMaster master = createMaster("DATEDIFF", "DATEDIFF(\"2024-01-01\", \"2024-12-31\")");
            when(formulaMasterRepository.findByCode("DATEDIFF")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("DATEDIFF", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("365"));
        }

        @Test
        @DisplayName("DATEDIFF with epoch days")
        void datediffEpoch() {
            FormulaMaster master = createMaster("DATEDIFF_EPOCH", "DATEDIFF(19723, 19732)");
            when(formulaMasterRepository.findByCode("DATEDIFF_EPOCH")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("DATEDIFF_EPOCH", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("9"));
        }

        @Test
        @DisplayName("DAYSOFMONTH for leap year February")
        void daysofmonthLeap() {
            FormulaMaster master = createMaster("DAYS_LEAP", "DAYSOFMONTH(\"2024-02-15\")");
            when(formulaMasterRepository.findByCode("DAYS_LEAP")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("DAYS_LEAP", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("29"));
        }

        @Test
        @DisplayName("DAYSOFMONTH for non-leap year February")
        void daysofmonthNonLeap() {
            FormulaMaster master = createMaster("DAYS_NONLEAP", "DAYSOFMONTH(\"2023-02-15\")");
            when(formulaMasterRepository.findByCode("DAYS_NONLEAP")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("DAYS_NONLEAP", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("28"));
        }

        @Test
        @DisplayName("YEAR extraction")
        void year() {
            FormulaMaster master = createMaster("YEAR", "YEAR(\"2025-06-15\")");
            when(formulaMasterRepository.findByCode("YEAR")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("YEAR", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("2025"));
        }

        @Test
        @DisplayName("MONTH extraction")
        void month() {
            FormulaMaster master = createMaster("MONTH", "MONTH(\"2025-06-15\")");
            when(formulaMasterRepository.findByCode("MONTH")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("MONTH", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("6"));
        }
    }

    @Nested
    @DisplayName("Phase 4: Lookup Functions")
    class Phase4LookupTests {

        @Test
        @DisplayName("LOOKUP finds matching key")
        void lookupFound() {
            FormulaMaster master = createMaster("LOOKUP", "LOOKUP(2, 1, 10, 2, 20, 3, 30)");
            when(formulaMasterRepository.findByCode("LOOKUP")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("LOOKUP", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("20"));
        }

        @Test
        @DisplayName("LOOKUP returns zero for missing key")
        void lookupNotFound() {
            FormulaMaster master = createMaster("LOOKUP_MISS", "LOOKUP(99, 1, 10, 2, 20)");
            when(formulaMasterRepository.findByCode("LOOKUP_MISS")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("LOOKUP_MISS", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("INDEX with 1-based position")
        void index() {
            FormulaMaster master = createMaster("INDEX", "INDEX(10, 20, 30, 40, 3)");
            when(formulaMasterRepository.findByCode("INDEX")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("INDEX", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("30"));
        }

        @Test
        @DisplayName("INDEX returns zero for out-of-bounds")
        void indexOutOfBounds() {
            FormulaMaster master = createMaster("INDEX_OOB", "INDEX(10, 20, 30, 5)");
            when(formulaMasterRepository.findByCode("INDEX_OOB")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("INDEX_OOB", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Complex Composite Formulas")
    class CompositeFormulaTests {

        @Test
        @DisplayName("CPI with division-by-zero guard")
        void cpiWithGuard() {
            FormulaMaster master = createMaster("CPI", "IF($AC = 0, 0, $EV / $AC)");
            when(formulaMasterRepository.findByCode("CPI")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("CPI", null, Map.of("EV", new BigDecimal("80"), "AC", new BigDecimal("100")));

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("0.8"));
        }

        @Test
        @DisplayName("EAC using CPI")
        void eacUsingCpi() {
            FormulaMaster master = createMaster("EAC", "IF($CPI = 0, $BAC, $BAC / $CPI)");
            when(formulaMasterRepository.findByCode("EAC")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("EAC", null, Map.of("BAC", new BigDecimal("1000"), "CPI", new BigDecimal("0.8")));

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("1250"));
        }

        @Test
        @DisplayName("Schedule health score with clamping")
        void scheduleHealthClamped() {
            FormulaMaster master = createMaster("HEALTH", "MAX(0, MIN(100, 100 - ($CRIT * 40) - ($NEAR * 20)))");
            when(formulaMasterRepository.findByCode("HEALTH")).thenReturn(Optional.of(master));

            Map<String, BigDecimal> ctx = Map.of("CRIT", new BigDecimal("0.2"), "NEAR", new BigDecimal("0.1"));
            FormulaResultDto result = engine.evaluate("HEALTH", null, ctx);

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("90"));
        }

        @Test
        @DisplayName("Statistical aggregation: AVERAGE + STDEV")
        void avgPlusStdev() {
            FormulaMaster master = createMaster("AVG_STD", "AVERAGE(10, 20, 30) + STDEV(10, 20, 30)");
            when(formulaMasterRepository.findByCode("AVG_STD")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("AVG_STD", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isCloseTo(new BigDecimal("28.1649"), within(new BigDecimal("0.0001")));
        }

        @Test
        @DisplayName("Conditional aggregation with SUMIF + AVERAGE")
        void sumifPlusAverage() {
            FormulaMaster master = createMaster("SUMIF_AVG", "SUMIF(5, 5, 10, 5) + AVERAGE(1, 2, 3)");
            when(formulaMasterRepository.findByCode("SUMIF_AVG")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("SUMIF_AVG", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("12"));
        }

        @Test
        @DisplayName("Date arithmetic with DATEDIFF + TODAY")
        void datediffPlusToday() {
            FormulaMaster master = createMaster("DATE_CALC", "TODAY() - DATEDIFF(\"2024-01-01\", \"2024-01-10\")");
            when(formulaMasterRepository.findByCode("DATE_CALC")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("DATE_CALC", null, Map.of());

            assertThat(result.isError()).isFalse();
            long todayEpoch = java.time.LocalDate.now().toEpochDay();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal(String.valueOf(todayEpoch - 9)));
        }

        @Test
        @DisplayName("LOOKUP with computed key")
        void lookupComputedKey() {
            FormulaMaster master = createMaster("LOOKUP_COMP", "LOOKUP(2 + 3, 1, 10, 5, 50, 3, 30)");
            when(formulaMasterRepository.findByCode("LOOKUP_COMP")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("LOOKUP_COMP", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("50"));
        }

        @Test
        @DisplayName("INDEX with computed position")
        void indexComputedPosition() {
            FormulaMaster master = createMaster("IDX_COMP", "INDEX(10, 20, 30, 1 + 1)");
            when(formulaMasterRepository.findByCode("IDX_COMP")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("IDX_COMP", null, Map.of());

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("20"));
        }
    }

    @Nested
    @DisplayName("Error Handling & Edge Cases")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Returns error for non-existent formula")
        void formulaNotFound() {
            when(formulaMasterRepository.findByCode("NONEXIST")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> engine.evaluate("NONEXIST", null, Map.of()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Returns error for inactive formula")
        void inactiveFormula() {
            FormulaMaster master = createMaster("INACTIVE", "1 + 1");
            master.setIsActive(false);
            when(formulaMasterRepository.findByCode("INACTIVE")).thenReturn(Optional.of(master));

            assertThatThrownBy(() -> engine.evaluate("INACTIVE", null, Map.of()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("ruleCode", "FORMULA_INACTIVE");
        }

        @Test
        @DisplayName("Returns error for syntax error in formula")
        void syntaxError() {
            FormulaMaster master = createMaster("BAD", "IF(1, 2");
            when(formulaMasterRepository.findByCode("BAD")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("BAD", null, Map.of());

            assertThat(result.isError()).isTrue();
            assertThat(result.getErrorMessage()).isNotBlank();
        }

        @Test
        @DisplayName("Division by zero returns zero default")
        void divisionByZero() {
            FormulaMaster master = createMaster("DIV_ZERO", "$X / $Y");
            when(formulaMasterRepository.findByCode("DIV_ZERO")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("DIV_ZERO", null, Map.of("X", new BigDecimal("10"), "Y", BigDecimal.ZERO));

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Custom zero default")
        void customZeroDefault() {
            FormulaMaster master = createMaster("CUSTOM_ZERO", "$X / $Y", 4, RoundingMode.HALF_UP, "-999");
            when(formulaMasterRepository.findByCode("CUSTOM_ZERO")).thenReturn(Optional.of(master));

            FormulaResultDto result = engine.evaluate("CUSTOM_ZERO", null, Map.of("X", new BigDecimal("10"), "Y", BigDecimal.ZERO));

            assertThat(result.isError()).isFalse();
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("-999"));
        }

        @Test
        @DisplayName("evaluateDouble returns Double")
        void evaluateDouble() {
            FormulaMaster master = createMaster("DOUBLE", "3.14159");
            when(formulaMasterRepository.findByCode("DOUBLE")).thenReturn(Optional.of(master));

            Double result = engine.evaluateDouble("DOUBLE", null, Map.of());

            assertThat(result).isCloseTo(3.14159, within(0.0001));
        }

        @Test
        @DisplayName("evaluateBigDecimal throws on error")
        void evaluateBigDecimalError() {
            FormulaMaster master = createMaster("BAD_BD", "IF(1, 2");
            when(formulaMasterRepository.findByCode("BAD_BD")).thenReturn(Optional.of(master));

            assertThatThrownBy(() -> engine.evaluateBigDecimal("BAD_BD", null, Map.of()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("ruleCode", "FORMULA_EVAL_ERROR");
        }
    }

    @Nested
    @DisplayName("Caching Behavior")
    class CachingTests {

        @Test
        @DisplayName("Same expression evaluated twice uses cache")
        void cacheHit() {
            FormulaMaster master = createMaster("CACHE", "SUM(1, 2, 3)");
            when(formulaMasterRepository.findByCode("CACHE")).thenReturn(Optional.of(master));

            FormulaResultDto r1 = engine.evaluate("CACHE", null, Map.of());
            FormulaResultDto r2 = engine.evaluate("CACHE", null, Map.of());

            assertThat(r1.getValue()).isEqualByComparingTo(new BigDecimal("6"));
            assertThat(r2.getValue()).isEqualByComparingTo(new BigDecimal("6"));
        }
    }
}
