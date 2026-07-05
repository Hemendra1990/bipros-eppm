package com.bipros.ai.voice.dpr;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DprLabelResolverTest {

    private static List<String> alias(String s) {
        return List.of(s);
    }

    @Test
    void exactUniqueMatchIsConfident() {
        var r = DprLabelResolver.resolve("Mechanical Excavation",
            List.of("Mechanical Excavation", "Borrow Excavation"), DprLabelResolverTest::alias);
        assertThat(r.confident()).isTrue();
        assertThat(r.best()).isEqualTo("Mechanical Excavation");
    }

    @Test
    void uniqueSubstringMatchIsConfident() {
        var r = DprLabelResolver.resolve("mechanical",
            List.of("Mechanical Excavation", "Borrow Excavation"), DprLabelResolverTest::alias);
        assertThat(r.confident()).isTrue();
        assertThat(r.best()).isEqualTo("Mechanical Excavation");
    }

    @Test
    void ambiguousSubstringIsNotConfident() {
        // every candidate contains "excavation" → tie → ask, don't guess
        var r = DprLabelResolver.resolve("excavation",
            List.of("Mechanical Excavation", "Borrow Excavation", "Unclassified Excavation"),
            DprLabelResolverTest::alias);
        assertThat(r.confident()).isFalse();
    }

    @Test
    void twoIdenticalNamesStayAmbiguous() {
        var r = DprLabelResolver.resolve("Mason", List.of("Mason", "Mason"), DprLabelResolverTest::alias);
        assertThat(r.confident()).isFalse();
    }

    @Test
    void pureEditDistanceGuessIsNotConfident() {
        // "Vijaykumar" vs "Vijay Kumar": normalization keeps the space gap → no substring hit →
        // Levenshtein branch caps at 74 < ACCEPT_SCORE, so it must NOT auto-select.
        var r = DprLabelResolver.resolve("Vijaykumar",
            List.of("Vijay Kumar", "Rahul"), DprLabelResolverTest::alias);
        assertThat(r.confident()).isFalse();
    }

    @Test
    void noReasonableMatchReturnsNull() {
        var r = DprLabelResolver.resolve("Zzz", List.of("Alpha", "Beta"), DprLabelResolverTest::alias);
        assertThat(r.best()).isNull();
        assertThat(r.confident()).isFalse();
    }

    @Test
    void blankQueryReturnsNull() {
        var r = DprLabelResolver.resolve("   ", List.of("Alpha"), DprLabelResolverTest::alias);
        assertThat(r.best()).isNull();
        assertThat(r.confident()).isFalse();
    }
}
