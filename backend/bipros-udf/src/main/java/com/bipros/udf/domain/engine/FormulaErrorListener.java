package com.bipros.udf.domain.engine;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.ArrayList;
import java.util.List;

public class FormulaErrorListener extends BaseErrorListener {

    private final List<ErrorEntry> errors = new ArrayList<>();

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine, String msg,
                            RecognitionException e) {
        errors.add(new ErrorEntry(msg, line, charPositionInLine));
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public String getFirstErrorMessage() {
        return errors.isEmpty() ? "Unknown parse error" : errors.get(0).message();
    }

    public int getFirstErrorLine() {
        return errors.isEmpty() ? 0 : errors.get(0).line();
    }

    public int getFirstErrorColumn() {
        return errors.isEmpty() ? 0 : errors.get(0).column();
    }

    private record ErrorEntry(String message, int line, int column) {}
}
