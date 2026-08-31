package com.bipros.udf.domain.engine;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates syntax errors reported by ANTLR parser and lexer.
 * <p>
 * Intended for single-use per parse; create a new instance for each parsing operation.
 */
public class FormulaErrorListener extends BaseErrorListener {

    private final List<ErrorEntry> errors = new ArrayList<>();

    /**
     * Records a syntax error encountered during parsing or lexing.
     *
     * @param recognizer         the recognizer that detected the error
     * @param offendingSymbol    the offending token or symbol
     * @param line               the line number where the error occurred (1-based)
     * @param charPositionInLine the character position in the line where the error occurred (0-based)
     * @param msg                the error message
     * @param e                  the recognition exception, if any
     */
    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine, String msg,
                            RecognitionException e) {
        errors.add(new ErrorEntry(msg, line, charPositionInLine));
    }

    /**
     * Returns whether any syntax errors were recorded.
     *
     * @return {@code true} if at least one error was recorded
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Returns the message of the first recorded error, or a fallback if none.
     *
     * @return the first error message, or "Unknown parse error" if no errors exist
     */
    public String getFirstErrorMessage() {
        return errors.isEmpty() ? "Unknown parse error" : errors.get(0).message();
    }

    /**
     * Returns the line number of the first recorded error.
     *
     * @return the 1-based line number, or {@code 0} if no errors exist
     */
    public int getFirstErrorLine() {
        return errors.isEmpty() ? 0 : errors.get(0).line();
    }

    /**
     * Returns the column position of the first recorded error.
     *
     * @return the 0-based column position, or {@code 0} if no errors exist
     */
    public int getFirstErrorColumn() {
        return errors.isEmpty() ? 0 : errors.get(0).column();
    }

    private record ErrorEntry(String message, int line, int column) {}
}
