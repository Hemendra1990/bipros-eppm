package com.bipros.udf.domain.engine;

import com.bipros.common.exception.BusinessRuleException;

/**
 * Thrown when a syntactically valid formula fails at evaluation time
 * (e.g. division by zero, missing variable, type mismatch).
 */
public class FormulaEvaluationException extends BusinessRuleException {

    public FormulaEvaluationException(String message) {
        super("FORMULA_EVAL_ERROR", message);
    }
}
