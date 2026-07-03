package com.bipros.ai.chat;

import com.bipros.ai.orchestrator.AiOrchestrator.ChatEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the fix for the /v1/ai/chat duplication bug: the orchestrator emits one full-content
 * {@code token} event per ReAct round (initial draft + gate/verification re-drafts) followed by a
 * terminal {@code done}. {@link ChatController#reduceFinalText} must collapse that to the single
 * final answer, not concatenate every draft.
 */
class ChatControllerReduceTextTest {

    private static ChatEvent token(String delta) {
        return new ChatEvent("token", Map.of("delta", delta));
    }

    private static ChatEvent done(String text) {
        return new ChatEvent("done", Map.of("text", text));
    }

    @Test
    void multipleTokenDraftsCollapseToDoneText() {
        List<ChatEvent> events = List.of(
                token("Answer (draft 1) with 5-bar chart"),
                token("Answer (draft 2) with 1-bar chart"),
                token("Answer (draft 3) with 1-bar chart"),
                done("Answer FINAL with 1-bar chart"));

        assertThat(ChatController.reduceFinalText(events)).isEqualTo("Answer FINAL with 1-bar chart");
    }

    @Test
    void blankDoneFallsBackToLastNonBlankToken() {
        // Gate C: the post-verification round returns empty content, so `done` is blank even
        // though a good draft was already emitted. Must return the last real draft, not "".
        List<ChatEvent> events = List.of(
                token("Answer (draft 1)"),
                token("Answer (draft 2 — refined)"),
                done(""));

        assertThat(ChatController.reduceFinalText(events)).isEqualTo("Answer (draft 2 — refined)");
    }

    @Test
    void fallsBackToLastTokenWhenNoDone() {
        // Error path: tokens emitted but no terminal done event.
        List<ChatEvent> events = List.of(
                token("first partial"),
                token("second partial"),
                new ChatEvent("error", Map.of("code", "LLM_CALL_FAILED", "message", "boom")));

        assertThat(ChatController.reduceFinalText(events)).isEqualTo("second partial");
    }

    @Test
    void emptyOrNullYieldsEmptyString() {
        assertThat(ChatController.reduceFinalText(List.of())).isEmpty();
        assertThat(ChatController.reduceFinalText(null)).isEmpty();
    }

    @Test
    void ignoresNonTokenNonDoneEvents() {
        List<ChatEvent> events = List.of(
                new ChatEvent("tool_call", Map.of("name", "risk_register", "status", "started")),
                new ChatEvent("tool_result", Map.of("name", "risk_register", "success", true)),
                token("draft"),
                new ChatEvent("verifying", Map.of("note", "cross-checking")),
                done("final"));

        assertThat(ChatController.reduceFinalText(events)).isEqualTo("final");
    }
}
