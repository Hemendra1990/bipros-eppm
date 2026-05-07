package com.bipros.ai.voice.dpr;

/**
 * One turn in a voice form-fill session. {@code role} is "user" or "assistant"; {@code content}
 * is the spoken transcript (user) or the assistant's text reply (assistant). Sessions are
 * frontend-managed — the backend never persists them.
 */
public record DprVoiceTurn(String role, String content) {
}
