package com.bipros.udf.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.udf.application.dto.CreateFormulaRequest;
import com.bipros.udf.domain.engine.FormulaAstCache;
import com.bipros.udf.domain.model.FormulaCategory;
import com.bipros.udf.domain.model.FormulaMaster;
import com.bipros.udf.domain.model.FormulaOutputType;
import com.bipros.udf.domain.repository.FormulaMasterRepository;
import com.bipros.udf.domain.repository.FormulaOverrideRepository;
import com.bipros.udf.domain.repository.FormulaVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@DisplayName("FormulaConfigurationService — extended validation for all new functions")
@ExtendWith(MockitoExtension.class)
class FormulaConfigurationServiceExtendedValidationTest {

    @Mock
    private FormulaMasterRepository formulaMasterRepository;

    @Mock
    private FormulaOverrideRepository formulaOverrideRepository;

    @Mock
    private FormulaVersionRepository formulaVersionRepository;

    private FormulaAstCache formulaAstCache;
    private FormulaConfigurationService service;

    @BeforeEach
    void setUp() {
        formulaAstCache = new FormulaAstCache();
        service = new FormulaConfigurationService(
                formulaMasterRepository,
                formulaOverrideRepository,
                formulaVersionRepository,
                formulaAstCache);
        lenient().when(formulaMasterRepository.save(any(FormulaMaster.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CreateFormulaRequest buildRequest(String code, String expression) {
        return CreateFormulaRequest.builder()
                .code(code)
                .name("Test " + code)
                .category(FormulaCategory.EVM)
                .defaultExpression(expression)
                .outputType(FormulaOutputType.NUMBER)
                .scale(4)
                .roundingMode(RoundingMode.HALF_UP)
                .build();
    }

    @Nested
    @DisplayName("Phase 1: String Functions")
    class Phase1StringValidation {

        @Test
        @DisplayName("LEFT with valid args")
        void leftValid() {
            when(formulaMasterRepository.existsByCode("LEFT_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("LEFT_TEST", "LEFT(\"hello\", 2)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("RIGHT with valid args")
        void rightValid() {
            when(formulaMasterRepository.existsByCode("RIGHT_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("RIGHT_TEST", "RIGHT(\"hello\", 3)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("MID with valid args")
        void midValid() {
            when(formulaMasterRepository.existsByCode("MID_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("MID_TEST", "MID(\"hello\", 2, 3)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("LENGTH with valid args")
        void lengthValid() {
            when(formulaMasterRepository.existsByCode("LEN_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("LEN_TEST", "LENGTH(\"test\")");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("UPPER with valid args")
        void upperValid() {
            when(formulaMasterRepository.existsByCode("UPPER_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("UPPER_TEST", "UPPER(\"hello\")");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("LOWER with valid args")
        void lowerValid() {
            when(formulaMasterRepository.existsByCode("LOWER_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("LOWER_TEST", "LOWER(\"HELLO\")");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("TRIM with valid args")
        void trimValid() {
            when(formulaMasterRepository.existsByCode("TRIM_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("TRIM_TEST", "TRIM(\" hello \")");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("SUBSTITUTE with valid args")
        void substituteValid() {
            when(formulaMasterRepository.existsByCode("SUB_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("SUB_TEST", "SUBSTITUTE(\"hello\", \"l\", \"L\")");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Phase 1: Math Functions")
    class Phase1MathValidation {

        @Test
        @DisplayName("MOD with valid args")
        void modValid() {
            when(formulaMasterRepository.existsByCode("MOD_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("MOD_TEST", "MOD(17, 5)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("FLOOR with valid args")
        void floorValid() {
            when(formulaMasterRepository.existsByCode("FLOOR_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("FLOOR_TEST", "FLOOR(3.7)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("CEILING with valid args")
        void ceilingValid() {
            when(formulaMasterRepository.existsByCode("CEIL_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("CEIL_TEST", "CEILING(3.2)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("LOG with valid args")
        void logValid() {
            when(formulaMasterRepository.existsByCode("LOG_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("LOG_TEST", "LOG(10)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("EXP with valid args")
        void expValid() {
            when(formulaMasterRepository.existsByCode("EXP_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("EXP_TEST", "EXP(2)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("SIN with valid args")
        void sinValid() {
            when(formulaMasterRepository.existsByCode("SIN_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("SIN_TEST", "SIN(0)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("COS with valid args")
        void cosValid() {
            when(formulaMasterRepository.existsByCode("COS_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("COS_TEST", "COS(0)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Phase 1: Constants")
    class Phase1ConstantsValidation {

        @Test
        @DisplayName("PI constant")
        void piValid() {
            when(formulaMasterRepository.existsByCode("PI_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("PI_TEST", "PI * 2");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("E constant")
        void eValid() {
            when(formulaMasterRepository.existsByCode("E_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("E_TEST", "E + 1");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("TRUE constant")
        void trueValid() {
            when(formulaMasterRepository.existsByCode("TRUE_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("TRUE_TEST", "IF(TRUE, 1, 0)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("FALSE constant")
        void falseValid() {
            when(formulaMasterRepository.existsByCode("FALSE_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("FALSE_TEST", "IF(FALSE, 1, 0)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Phase 2: Statistical Functions")
    class Phase2StatisticalValidation {

        @Test
        @DisplayName("AVERAGE with valid args")
        void averageValid() {
            when(formulaMasterRepository.existsByCode("AVG_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("AVG_TEST", "AVERAGE(10, 20, 30)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("COUNT with valid args")
        void countValid() {
            when(formulaMasterRepository.existsByCode("CNT_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("CNT_TEST", "COUNT(1, 2, 3, 4, 5)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("STDEV with valid args")
        void stdevValid() {
            when(formulaMasterRepository.existsByCode("STD_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("STD_TEST", "STDEV(2, 4, 4, 4, 5, 5, 7, 9)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("MEDIAN with valid args")
        void medianValid() {
            when(formulaMasterRepository.existsByCode("MED_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("MED_TEST", "MEDIAN(10, 20, 30, 40)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("PERCENTILE with valid args")
        void percentileValid() {
            when(formulaMasterRepository.existsByCode("PCTL_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("PCTL_TEST", "PERCENTILE(10, 20, 30, 40, 50, 0.75)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Phase 3: Conditional Aggregation")
    class Phase3ConditionalValidation {

        @Test
        @DisplayName("SUMIF with valid args")
        void sumifValid() {
            when(formulaMasterRepository.existsByCode("SUMIF_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("SUMIF_TEST", "SUMIF(5, 5, 10, 5)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("COUNTIF with valid args")
        void countifValid() {
            when(formulaMasterRepository.existsByCode("COUNTIF_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("COUNTIF_TEST", "COUNTIF(5, 5, 3, 5, 2)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AVERAGEIF with valid args")
        void averageifValid() {
            when(formulaMasterRepository.existsByCode("AVGIF_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("AVGIF_TEST", "AVERAGEIF(10, 10, 20, 10)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("SUMIF with variable references")
        void sumifWithVars() {
            when(formulaMasterRepository.existsByCode("SUMIF_VAR")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("SUMIF_VAR", "SUMIF($threshold, $a, $b, $c)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Phase 4: Date/Time Functions")
    class Phase4DateTimeValidation {

        @Test
        @DisplayName("TODAY with valid syntax")
        void todayValid() {
            when(formulaMasterRepository.existsByCode("TODAY_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("TODAY_TEST", "TODAY()");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("DATEDIFF with string dates")
        void datediffStringValid() {
            when(formulaMasterRepository.existsByCode("DDIFF_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("DDIFF_TEST", "DATEDIFF(\"2024-01-01\", \"2024-12-31\")");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("DATEDIFF with epoch days")
        void datediffEpochValid() {
            when(formulaMasterRepository.existsByCode("DDIFF_EPOCH")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("DDIFF_EPOCH", "DATEDIFF(19723, 19732)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("DAYSOFMONTH with valid date")
        void daysofmonthValid() {
            when(formulaMasterRepository.existsByCode("DAYS_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("DAYS_TEST", "DAYSOFMONTH(\"2024-02-15\")");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("YEAR with valid date")
        void yearValid() {
            when(formulaMasterRepository.existsByCode("YEAR_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("YEAR_TEST", "YEAR(\"2024-06-15\")");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("MONTH with valid date")
        void monthValid() {
            when(formulaMasterRepository.existsByCode("MONTH_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("MONTH_TEST", "MONTH(\"2024-06-15\")");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Complex date formula")
        void complexDateFormula() {
            when(formulaMasterRepository.existsByCode("DATE_COMPLEX")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("DATE_COMPLEX", "DATEDIFF(\"2024-01-01\", \"2024-12-31\") / DAYSOFMONTH(\"2024-02-15\")");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Phase 4: Lookup Functions")
    class Phase4LookupValidation {

        @Test
        @DisplayName("LOOKUP with valid key-value pairs")
        void lookupValid() {
            when(formulaMasterRepository.existsByCode("LOOKUP_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("LOOKUP_TEST", "LOOKUP(5, 1, 10, 5, 50, 3, 30)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("INDEX with valid args")
        void indexValid() {
            when(formulaMasterRepository.existsByCode("INDEX_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("INDEX_TEST", "INDEX(10, 20, 30, 2)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("LOOKUP with variable key")
        void lookupWithVar() {
            when(formulaMasterRepository.existsByCode("LOOKUP_VAR")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("LOOKUP_VAR", "LOOKUP($key, 1, 10, 2, 20, 3, 30)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("INDEX with computed position")
        void indexComputed() {
            when(formulaMasterRepository.existsByCode("IDX_COMP")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("IDX_COMP", "INDEX(10, 20, 30, $pos + 1)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Complex Composite Formulas")
    class CompositeValidation {

        @Test
        @DisplayName("EVM CPI with division guard")
        void evmCpiValid() {
            when(formulaMasterRepository.existsByCode("CPI_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("CPI_TEST", "IF($AC = 0, 0, $EV / $AC)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("EVM TCPI complex formula")
        void evmTcpiValid() {
            when(formulaMasterRepository.existsByCode("TCPI_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("TCPI_TEST", "IF($EAC - $AC = 0, 0, ($BAC - $EV) / ($EAC - $AC))");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Schedule health with clamping")
        void healthClampedValid() {
            when(formulaMasterRepository.existsByCode("HEALTH_TEST")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("HEALTH_TEST", "MAX(0, MIN(100, 100 - ($CRIT * 40) - ($NEAR * 20)))");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Statistical + arithmetic composite")
        void statArithmeticValid() {
            when(formulaMasterRepository.existsByCode("STAT_ARITH")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("STAT_ARITH", "AVERAGE(10, 20, 30) + STDEV(10, 20, 30)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Conditional + statistical composite")
        void conditionalStatValid() {
            when(formulaMasterRepository.existsByCode("COND_STAT")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("COND_STAT", "SUMIF(5, 5, 10, 5, 20) + AVERAGE(1, 2, 3)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Date + arithmetic composite")
        void dateArithmeticValid() {
            when(formulaMasterRepository.existsByCode("DATE_ARITH")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("DATE_ARITH", "TODAY() - DATEDIFF(\"2024-01-01\", \"2024-01-10\")");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Lookup + arithmetic composite")
        void lookupArithmeticValid() {
            when(formulaMasterRepository.existsByCode("LOOKUP_ARITH")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("LOOKUP_ARITH", "LOOKUP(2, 1, 10, 2, 20) * 2");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("All function types in single formula")
        void allTypesComposite() {
            when(formulaMasterRepository.existsByCode("ALL_TYPES")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("ALL_TYPES",
                    "IF($X > PI, MAX(10, 20), AVERAGE(1, 2, 3)) + DATEDIFF(\"2024-01-01\", \"2024-12-31\")");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Invalid Formulas")
    class InvalidFormulaValidation {

        @Test
        @DisplayName("Rejects unclosed parenthesis")
        void unclosedParen() {
            when(formulaMasterRepository.existsByCode("BAD_PAREN")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("BAD_PAREN", "IF(1, 2");
            assertThatThrownBy(() -> service.createMasterFormula(req))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(ex -> ((BusinessRuleException) ex).getRuleCode())
                    .isEqualTo("FORMULA_SYNTAX_ERROR");
        }

        @Test
        @DisplayName("Rejects unknown function")
        void unknownFunction() {
            when(formulaMasterRepository.existsByCode("BAD_FUNC")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("BAD_FUNC", "UNKNOWN(1, 2)");
            assertThatThrownBy(() -> service.createMasterFormula(req))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(ex -> ((BusinessRuleException) ex).getRuleCode())
                    .isEqualTo("FORMULA_SYNTAX_ERROR");
        }

        @Test
        @DisplayName("Rejects empty expression")
        void emptyExpression() {
            when(formulaMasterRepository.existsByCode("EMPTY")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("EMPTY", "");
            assertThatThrownBy(() -> service.createMasterFormula(req))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(ex -> ((BusinessRuleException) ex).getRuleCode())
                    .isEqualTo("FORMULA_EXPRESSION_EMPTY");
        }

        @Test
        @DisplayName("Rejects whitespace-only expression")
        void whitespaceOnly() {
            when(formulaMasterRepository.existsByCode("WS_ONLY")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("WS_ONLY", "   ");
            assertThatThrownBy(() -> service.createMasterFormula(req))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(ex -> ((BusinessRuleException) ex).getRuleCode())
                    .isEqualTo("FORMULA_EXPRESSION_EMPTY");
        }

        @Test
        @DisplayName("Rejects mismatched quotes")
        void mismatchedQuotes() {
            when(formulaMasterRepository.existsByCode("BAD_QUOTE")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("BAD_QUOTE", "LEFT(\"hello, 2)");
            assertThatThrownBy(() -> service.createMasterFormula(req))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(ex -> ((BusinessRuleException) ex).getRuleCode())
                    .isEqualTo("FORMULA_SYNTAX_ERROR");
        }

        @Test
        @DisplayName("Rejects function with wrong arity: LEFT missing args")
        void wrongArityLeft() {
            when(formulaMasterRepository.existsByCode("BAD_LEFT")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("BAD_LEFT", "LEFT(\"hello\")");
            assertThatThrownBy(() -> service.createMasterFormula(req))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(ex -> ((BusinessRuleException) ex).getRuleCode())
                    .isEqualTo("FORMULA_SYNTAX_ERROR");
        }

        @Test
        @DisplayName("Rejects function with wrong arity: MID missing args")
        void wrongArityMid() {
            when(formulaMasterRepository.existsByCode("BAD_MID")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("BAD_MID", "MID(\"hello\", 2)");
            assertThatThrownBy(() -> service.createMasterFormula(req))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(ex -> ((BusinessRuleException) ex).getRuleCode())
                    .isEqualTo("FORMULA_SYNTAX_ERROR");
        }

        @Test
        @DisplayName("Rejects function with wrong arity: SUBSTITUTE missing args")
        void wrongAritySubstitute() {
            when(formulaMasterRepository.existsByCode("BAD_SUB")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("BAD_SUB", "SUBSTITUTE(\"hello\", \"l\")");
            assertThatThrownBy(() -> service.createMasterFormula(req))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(ex -> ((BusinessRuleException) ex).getRuleCode())
                    .isEqualTo("FORMULA_SYNTAX_ERROR");
        }

        @Test
        @DisplayName("PERCENTILE without rank argument is valid syntax")
        void percentileNoRank() {
            when(formulaMasterRepository.existsByCode("PCTL_BAD")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("PCTL_BAD", "PERCENTILE(10, 20, 30)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("INDEX without position argument is valid syntax")
        void indexNoPosition() {
            when(formulaMasterRepository.existsByCode("IDX_BAD")).thenReturn(false);
            CreateFormulaRequest req = buildRequest("IDX_BAD", "INDEX(10, 20, 30)");
            assertThatCode(() -> service.createMasterFormula(req)).doesNotThrowAnyException();
        }
    }
}
