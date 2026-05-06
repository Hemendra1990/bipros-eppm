package com.bipros.udf.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.udf.application.dto.FormulaResultDto;
import com.bipros.udf.domain.engine.BigDecimalFormulaEvaluator;
import com.bipros.udf.domain.model.FormulaMaster;
import com.bipros.udf.domain.model.FormulaOverride;
import com.bipros.udf.domain.repository.FormulaMasterRepository;
import com.bipros.udf.domain.repository.FormulaOverrideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Core formula engine that resolves the effective expression for a formula code
 * (master or project override) and evaluates it against a variable context.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FormulaEngine {

    private final FormulaMasterRepository formulaMasterRepository;
    private final FormulaOverrideRepository formulaOverrideRepository;

    /**
     * Resolves the effective expression for a formula code and project.
     * Priority: active project override > master default.
     */
    @Transactional(readOnly = true)
    public String resolveExpression(String formulaCode, UUID projectId) {
        // 1. Check for active project override
        if (projectId != null) {
            var overrideOpt = formulaOverrideRepository.findByFormulaCodeAndProjectId(formulaCode, projectId);
            if (overrideOpt.isPresent()) {
                FormulaOverride override = overrideOpt.get();
                if (Boolean.TRUE.equals(override.getIsActive())
                        && isEffective(override.getEffectiveFrom(), override.getEffectiveTo())) {
                    log.debug("Using project override for {} on project {}", formulaCode, projectId);
                    return override.getOverrideExpression();
                }
            }
        }

        // 2. Fall back to master
        FormulaMaster master = formulaMasterRepository.findByCode(formulaCode)
                .orElseThrow(() -> new ResourceNotFoundException("FormulaMaster", formulaCode));

        if (!Boolean.TRUE.equals(master.getIsActive())) {
            throw new BusinessRuleException("FORMULA_INACTIVE",
                    "Formula " + formulaCode + " is inactive");
        }

        return master.getDefaultExpression();
    }

    /**
     * Evaluates a formula with the given variable context.
     */
    @Transactional(readOnly = true)
    public FormulaResultDto evaluate(String formulaCode, UUID projectId, Map<String, BigDecimal> context) {
        FormulaMaster master = formulaMasterRepository.findByCode(formulaCode)
                .orElseThrow(() -> new ResourceNotFoundException("FormulaMaster", formulaCode));

        String expression = resolveExpression(formulaCode, projectId);
        int scale = master.getScale() != null ? master.getScale() : 4;
        RoundingMode rounding = master.getRoundingMode() != null ? master.getRoundingMode() : RoundingMode.HALF_UP;
        BigDecimal zeroDefault = parseZeroDefault(master.getZeroDefault());

        Map<String, BigDecimal> safeContext = context != null ? context : Collections.emptyMap();

        BigDecimalFormulaEvaluator evaluator = new BigDecimalFormulaEvaluator(
                expression, safeContext, scale, rounding, zeroDefault);

        BigDecimalFormulaEvaluator.FormulaResult result = evaluator.evaluate();

        return FormulaResultDto.builder()
                .formulaCode(formulaCode)
                .expressionUsed(expression)
                .value(result.value())
                .formatted(result.formatted())
                .error(result.error())
                .errorMessage(result.errorMessage())
                .build();
    }

    /**
     * Evaluates a formula and returns the raw BigDecimal value.
     */
    @Transactional(readOnly = true)
    public BigDecimal evaluateBigDecimal(String formulaCode, UUID projectId, Map<String, BigDecimal> context) {
        FormulaResultDto dto = evaluate(formulaCode, projectId, context);
        if (dto.isError()) {
            throw new BusinessRuleException("FORMULA_EVAL_ERROR",
                    "Error evaluating " + formulaCode + ": " + dto.getErrorMessage());
        }
        return dto.getValue();
    }

    /**
     * Evaluates a formula and returns a Double (convenience for existing APIs).
     */
    @Transactional(readOnly = true)
    public Double evaluateDouble(String formulaCode, UUID projectId, Map<String, BigDecimal> context) {
        return evaluateBigDecimal(formulaCode, projectId, context).doubleValue();
    }

    private boolean isEffective(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        if (from != null && today.isBefore(from)) return false;
        if (to != null && today.isAfter(to)) return false;
        return true;
    }

    private BigDecimal parseZeroDefault(String raw) {
        if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
