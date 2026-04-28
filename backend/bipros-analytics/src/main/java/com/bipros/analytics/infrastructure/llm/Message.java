package com.bipros.analytics.infrastructure.llm;

public record Message(Role role, String content) {
    public enum Role { USER, ASSISTANT, SYSTEM }
}
