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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("FormulaConfigurationService — save-time validation")
@ExtendWith(MockitoExtension.class)
class FormulaConfigurationServiceValidationTest {

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
    }

    @Test
    @DisplayName("rejects formula with syntax error")
    void rejectsSyntaxError() {
        when(formulaMasterRepository.existsByCode("BAD_FORMULA")).thenReturn(false);

        CreateFormulaRequest request = buildRequest("BAD_FORMULA", "IF(1, 2");

        assertThatThrownBy(() -> service.createMasterFormula(request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getRuleCode())
                .isEqualTo("FORMULA_SYNTAX_ERROR");
    }

    @Test
    @DisplayName("rejects formula with empty expression")
    void rejectsEmptyExpression() {
        when(formulaMasterRepository.existsByCode("EMPTY_FORMULA")).thenReturn(false);

        CreateFormulaRequest request = buildRequest("EMPTY_FORMULA", "");

        assertThatThrownBy(() -> service.createMasterFormula(request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getRuleCode())
                .isEqualTo("FORMULA_EXPRESSION_EMPTY");
    }

    @Test
    @DisplayName("accepts valid complex formula")
    void acceptsValidFormula() {
        when(formulaMasterRepository.existsByCode("GOOD_FORMULA")).thenReturn(false);
        when(formulaMasterRepository.save(any(FormulaMaster.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateFormulaRequest request = buildRequest("GOOD_FORMULA", "IF($AC = 0, 0, $EV / $AC)");

        // Should not throw during validation
        assertThatCode(() -> service.createMasterFormula(request))
                .doesNotThrowAnyException();
    }

    private CreateFormulaRequest buildRequest(String code, String expression) {
        return CreateFormulaRequest.builder()
                .code(code)
                .name("Test Formula")
                .category(FormulaCategory.EVM)
                .defaultExpression(expression)
                .outputType(FormulaOutputType.NUMBER)
                .scale(4)
                .roundingMode(RoundingMode.HALF_UP)
                .build();
    }
}
