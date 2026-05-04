package com.bipros.ai.activity.context;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WbsCodeResolverTest {

    private static final Set<String> CODES = Set.of("1", "1.1", "1.2", "2", "2.1", "PRJ.1", "PRJ.1.1", "PRJ.2");

    @Test
    void exactMatchReturnsExact() {
        WbsCodeResolver.Result r = WbsCodeResolver.resolve("PRJ.1.1", CODES);
        assertThat(r.kind()).isEqualTo(WbsCodeResolver.Kind.EXACT);
        assertThat(r.resolvedCode()).isEqualTo("PRJ.1.1");
        assertThat(r.isResolved()).isTrue();
    }

    @Test
    void caseInsensitiveSnapsToExisting() {
        // Common: model emits "prj.1" instead of "PRJ.1".
        WbsCodeResolver.Result r = WbsCodeResolver.resolve("prj.1", CODES);
        assertThat(r.kind()).isEqualTo(WbsCodeResolver.Kind.CASE_INSENSITIVE);
        assertThat(r.resolvedCode()).isEqualTo("PRJ.1");
        assertThat(r.isResolved()).isTrue();
    }

    @Test
    void singleEditTypoWithUniqueNearestIsNearMatch() {
        // Distinct candidates so only one is within distance 2:
        //   "alphq" → "alpha" is 1 edit; "beta" is far.
        Set<String> codes = Set.of("alpha", "beta", "gamma", "delta");
        WbsCodeResolver.Result r = WbsCodeResolver.resolve("alphq", codes);
        assertThat(r.kind()).isEqualTo(WbsCodeResolver.Kind.NEAR_MATCH);
        assertThat(r.resolvedCode()).isEqualTo("alpha");
    }

    @Test
    void twoEditTypoIsNearMatch() {
        // "PRJ-1" (one substitution from "PRJ.1") → NEAR_MATCH to "PRJ.1".
        WbsCodeResolver.Result r = WbsCodeResolver.resolve("PRJ-1", CODES);
        assertThat(r.kind()).isEqualTo(WbsCodeResolver.Kind.NEAR_MATCH);
        assertThat(r.resolvedCode()).isEqualTo("PRJ.1");
    }

    @Test
    void farFromAnythingIsMissing() {
        WbsCodeResolver.Result r = WbsCodeResolver.resolve("ZZ.99.42", CODES);
        assertThat(r.kind()).isEqualTo(WbsCodeResolver.Kind.MISSING);
        assertThat(r.resolvedCode()).isNull();
    }

    @Test
    void emptyOrNullInputIsMissing() {
        assertThat(WbsCodeResolver.resolve(null, CODES).kind())
                .isEqualTo(WbsCodeResolver.Kind.MISSING);
        assertThat(WbsCodeResolver.resolve("", CODES).kind())
                .isEqualTo(WbsCodeResolver.Kind.MISSING);
        assertThat(WbsCodeResolver.resolve("   ", CODES).kind())
                .isEqualTo(WbsCodeResolver.Kind.MISSING);
    }

    @Test
    void emptyExistingSetIsMissing() {
        assertThat(WbsCodeResolver.resolve("anything", Set.of()).kind())
                .isEqualTo(WbsCodeResolver.Kind.MISSING);
    }

    @Test
    void ambiguousNearMatchYieldsMissing() {
        // "1.X" is equidistant (1 edit) from both "1.1" and "1.2"; resolver
        // should refuse to guess on a tie and return MISSING.
        Set<String> codes = Set.of("1.1", "1.2");
        WbsCodeResolver.Result r = WbsCodeResolver.resolve("1.3", codes);
        assertThat(r.kind()).isEqualTo(WbsCodeResolver.Kind.MISSING);
    }

    @Test
    void caseInsensitivePrefersDirectMatchOverNear() {
        // "1.1" exactly is in the set as "1.1"; case-insensitive doesn't apply
        // (no case difference). Verify we go straight to EXACT.
        WbsCodeResolver.Result r = WbsCodeResolver.resolve("1.1", CODES);
        assertThat(r.kind()).isEqualTo(WbsCodeResolver.Kind.EXACT);
    }

    @Test
    void levenshteinHonorsThreshold() {
        // Within threshold: "abc" vs "abd" is 1 edit.
        assertThat(WbsCodeResolver.levenshtein("abc", "abd", 2)).isEqualTo(1);
        // Length difference exceeds threshold → short-circuits to MAX_VALUE.
        assertThat(WbsCodeResolver.levenshtein("a", "abcdefgh", 2)).isEqualTo(Integer.MAX_VALUE);
        // Far apart inside the dp grid → MAX_VALUE.
        assertThat(WbsCodeResolver.levenshtein("abc", "xyz", 2)).isEqualTo(Integer.MAX_VALUE);
    }
}
