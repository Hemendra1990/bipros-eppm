package com.bipros.udf.domain.engine;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class FormulaAstCache {

    private final LoadingCache<String, ParseTree> cache;

    public FormulaAstCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Duration.ofHours(1))
                .build(this::parse);
    }

    public ParseTree get(String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        return cache.get(expression.trim());
    }

    public void validate(String expression) {
        if (expression == null || expression.isBlank()) {
            return;
        }
        cache.get(expression.trim());
    }

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

    public void invalidateAll() {
        cache.invalidateAll();
    }
}
