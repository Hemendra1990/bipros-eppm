package com.bipros.security.application.dto;

import com.bipros.security.domain.model.IcpmsModule;
import com.bipros.security.domain.model.ModuleAccessLevel;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IC-PMS per-user access matrix: module→level map plus corridor scope (NULL = all corridors).
 */
public record UserAccessResponse(
        UUID userId,
        Map<IcpmsModule, ModuleAccessLevel> moduleAccess,
        List<UUID> corridorScopes,
        boolean allCorridors
) {
}
