package com.bipros.udf.domain.engine;

import com.bipros.common.exception.BusinessRuleException;

/**
 * Thrown when a formula expression has syntax errors.
 * Carries line/column info from the ANTLR parser.
 */
public class FormulaParseException extends BusinessRuleException {

    private final int line;
    private final int column;

    public FormulaParseException(String message, int line, int column) {
        super("FORMULA_SYNTAX_ERROR", message);
        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
