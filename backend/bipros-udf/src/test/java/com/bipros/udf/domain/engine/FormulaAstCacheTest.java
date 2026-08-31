package com.bipros.udf.domain.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FormulaAstCache — ANTLR parse tree caching")
class FormulaAstCacheTest {

    private FormulaAstCache cache;

    @BeforeEach
    void setUp() {
        cache = new FormulaAstCache();
    }

    @Test
    @DisplayName("parses and caches valid expression")
    void validExpression() {
        var tree = cache.get("1 + 2");
        assertThat(tree).isNotNull();

        // Second call returns cached instance
        var tree2 = cache.get("1 + 2");
        assertThat(tree2).isSameAs(tree);
    }

    @Test
    @DisplayName("null expression returns null")
    void nullExpression() {
        assertThat(cache.get(null)).isNull();
    }

    @Test
    @DisplayName("blank expression returns null")
    void blankExpression() {
        assertThat(cache.get("   ")).isNull();
    }

    @Test
    @DisplayName("invalid expression throws FormulaParseException")
    void invalidExpression() {
        assertThatThrownBy(() -> cache.get("1 + * 2"))
                .isInstanceOf(FormulaParseException.class);
    }

    @Test
    @DisplayName("validate succeeds for valid expression")
    void validateValid() {
        cache.validate("$VAR * 10");
        // No exception
    }

    @Test
    @DisplayName("validate throws for invalid expression")
    void validateInvalid() {
        assertThatThrownBy(() -> cache.validate("IF(1, 2"))
                .isInstanceOf(FormulaParseException.class);
    }

    @Test
    @DisplayName("invalidateAll clears cache")
    void invalidateAll() {
        cache.get("1 + 2");
        cache.invalidateAll();
        // Cache is empty; next get will re-parse
        var tree = cache.get("1 + 2");
        assertThat(tree).isNotNull();
    }
}
