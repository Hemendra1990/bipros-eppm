package com.bipros.analytics.application.tool;

import java.util.Set;
import java.util.UUID;

/**
 * Per-request authorization snapshot. Built once by AuthContextResolver and threaded
 * through every tool call. {@code accessibleProjectIds == null} means "ADMIN —
 * unrestricted". An empty set means "deny-all".
 */
public record AuthContext(
        UUID userId,
        Set<String> roles,
        Set<UUID> accessibleProjectIds,
        ViewTier viewTier
) {
    public boolean isAdmin() {
        return roles != null && roles.contains("ROLE_ADMIN");
    }

    public boolean canSeeProject(UUID projectId) {
        return accessibleProjectIds == null || accessibleProjectIds.contains(projectId);
    }
}
