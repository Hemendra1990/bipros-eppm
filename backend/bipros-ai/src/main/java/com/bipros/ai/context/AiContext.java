package com.bipros.ai.context;

import java.util.List;
import java.util.UUID;

/**
 * Context carried through every AI request: user identity, project scope, module.
 * Used for RBAC injection and tool scoping.
 *
 * <p>{@code role} retains the legacy role string ("ADMIN", "PROJECT_MANAGER", "USER")
 * for backward-compat with tools that already read it. {@code profile} carries the
 * fine-grained Profile.code (e.g. "SITE_MANAGER") used for tool filtering and persona
 * selection.
 */
public record AiContext(
    UUID userId,
    UUID projectId,
    String module,
    String role,
    String profile,
    List<UUID> scopedProjectIds
) {
}
