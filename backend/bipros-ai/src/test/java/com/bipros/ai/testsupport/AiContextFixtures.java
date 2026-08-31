package com.bipros.ai.testsupport;

import com.bipros.ai.context.AiContext;

import java.util.List;
import java.util.UUID;

public final class AiContextFixtures {
    private AiContextFixtures() {}

    public static AiContext forProfile(String profileCode, UUID projectId) {
        UUID userId = UUID.randomUUID();
        UUID pid = projectId == null ? UUID.randomUUID() : projectId;
        return new AiContext(
                userId,
                pid,
                "general",
                profileCode,    // role
                profileCode,    // profile
                List.of(pid)
        );
    }
}
