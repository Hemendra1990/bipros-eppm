package com.bipros.udf.domain.engine;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Thread-safe cache for parsed ANTLR abstract syntax trees (ASTs).
 * <p>
 * Uses Caffeine with a maximum of 10,000 entries and a 1-hour expiry after last access.
 * Parsed expressions are cached to avoid re-parsing the same formula repeatedly.
 */
@Component
public class FormulaAstCache {

    private final LoadingCache<String, ParseTree> cache;

    public FormulaAstCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Duration.ofHours(1))
                .build(this::parse);
    }

    /**
     * Retrieves the cached AST for the given expression.
     * If not present, parses and caches the expression.
     *
     * @param expression the formula expression to parse
     * @return the parsed AST, or {@code null} if the expression is null or blank
     */
    public ParseTree get(String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        return cache.get(expression.trim());
    }

    /**
     * Validates the given expression by parsing it and caching the result.
     * Throws {@link FormulaParseException} if parsing fails.
     *
     * @param expression the formula expression to validate
     */
    public void validate(String expression) {
        if (expression == null || expression.isBlank()) {
            return;
        }
        cache.get(expression.trim());
    }

    /**
     * Parses the given expression using ANTLR, collecting any syntax errors.
     *
     * @param expression the trimmed formula expression
     * @return the parsed ANTLR AST
     * @throws FormulaParseException if the expression contains syntax errors
     */
    private ParseTree parse(String expression) {
        CharStream input = CharStreams.fromString(expression);
        FormulaLexer lexer = new FormulaLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FormulaParser parser = new FormulaParser(tokens);

        parser.removeErrorListeners();
        lexer.removeErrorListeners();

        FormulaErrorListener errorListener = new FormulaErrorListener();
        parser.addErrorListener(errorListener);
        lexer.addErrorListener(errorListener);

        ParseTree tree = parser.expression();

        if (errorListener.hasErrors()) {
            throw new FormulaParseException(
                    errorListener.getFirstErrorMessage(),
                    errorListener.getFirstErrorLine(),
                    errorListener.getFirstErrorColumn());
        }

        return tree;
    }

    /**
     * Invalidates all cached ASTs, forcing re-parse on next access.
     */
    public void invalidateAll() {
        cache.invalidateAll();
    }
}
