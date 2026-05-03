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
        Set<String> permissions
) {
    public static ProfileResponse from(Profile p) {
        return new ProfileResponse(
                p.getId(),
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.isSystemDefault(),
                p.getLegacyRoleName(),
                new TreeSet<>(p.getPermissions())
        );
    }
}
