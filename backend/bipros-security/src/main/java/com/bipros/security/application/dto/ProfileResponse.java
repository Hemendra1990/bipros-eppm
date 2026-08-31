package com.bipros.security.application.dto;

import com.bipros.security.domain.model.Profile;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean systemDefault,
        String legacyRoleName,
        Set<String> permissions,
        /** Row-visibility level: OWN | PROJECT | ALL (gate 3; null column reads as PROJECT). */
        String dataScope
) {
    public static ProfileResponse from(Profile p) {
        return new ProfileResponse(
                p.getId(),
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.isSystemDefault(),
                p.getLegacyRoleName(),
                new TreeSet<>(p.getPermissions()),
                p.dataScopeOrDefault().name()
        );
    }
}
