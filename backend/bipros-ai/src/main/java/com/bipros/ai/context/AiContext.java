package com.bipros.ai.context;

import java.util.List;
import java.util.UUID;

/**
 * Context carried through every AI request: user identity, project scope, module.
 * Used for RBAC injection and tool scoping.
 */
public record AiContext(
    UUID userId,
    UUID projectId,
    String module,
    String role,
    List<UUID> scopedProjectIds
) {
}
