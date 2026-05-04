package com.bipros.ai.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelCapabilityRegistryTest {

    private final ModelCapabilityRegistry reg = new ModelCapabilityRegistry();

    @Test
    void gpt5AcceptsLowAsRequested() {
        // gpt-5 family supports {none, low, medium, high, xhigh} → "low" passes through.
        assertThat(reg.resolveReasoningEffort("gpt-5.5", "low")).isEqualTo("low");
        assertThat(reg.resolveReasoningEffort("gpt-5.5-2026-04-23", "low")).isEqualTo("low");
        assertThat(reg.resolveReasoningEffort("gpt-5", "high")).isEqualTo("high");
        assertThat(reg.resolveReasoningEffort("gpt-5-mini", "xhigh")).isEqualTo("xhigh");
    }

    @Test
    void gpt5DowngradesMinimalToLow() {
        // "minimal" is an o-family value not supported by gpt-5; closest accepted is "low".
        assertThat(reg.resolveReasoningEffort("gpt-5.5", "minimal")).isEqualTo("low");
    }

    @Test
    void o1AcceptsMinimal() {
        assertThat(reg.resolveReasoningEffort("o1", "minimal")).isEqualTo("minimal");
        assertThat(reg.resolveReasoningEffort("o1-mini", "low")).isEqualTo("low");
        assertThat(reg.resolveReasoningEffort("o3-mini", "medium")).isEqualTo("medium");
    }

    @Test
    void o1DowngradesXhighToHigh() {
        // "xhigh" is a gpt-5 value; o1 family caps at "high".
        assertThat(reg.resolveReasoningEffort("o1", "xhigh")).isEqualTo("high");
    }

    @Test
    void nonReasoningModelGetsNullEffort() {
        assertThat(reg.resolveReasoningEffort("gpt-4o", "low")).isNull();
        assertThat(reg.resolveReasoningEffort("gpt-4.1", "high")).isNull();
        assertThat(reg.resolveReasoningEffort("gpt-3.5-turbo", "low")).isNull();
    }

    @Test
    void blankOrNullEffortReturnsNull() {
        assertThat(reg.resolveReasoningEffort("gpt-5", null)).isNull();
        assertThat(reg.resolveReasoningEffort("gpt-5", "")).isNull();
        assertThat(reg.resolveReasoningEffort("gpt-5", "  ")).isNull();
    }

    @Test
    void unknownModelIsTreatedAsNonReasoning() {
        // We don't know whether an unknown model accepts reasoning_effort, so we
        // suppress the parameter entirely. Safer than guessing and risking a 400.
        assertThat(reg.resolveReasoningEffort("some-future-model", "minimal")).isNull();
        assertThat(reg.resolveReasoningEffort("entirely-unknown", "low")).isNull();
    }
}
