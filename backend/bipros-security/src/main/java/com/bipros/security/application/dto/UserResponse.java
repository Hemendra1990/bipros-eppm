package com.bipros.security.application.dto;

import com.bipros.security.domain.model.AuthMethod;
import com.bipros.security.domain.model.User;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        List<String> roles,
        UUID organisationId,
        String designation,
        String primaryIcpmsRole,
        Set<AuthMethod> authMethods
) {
    public static UserResponse from(User user, List<String> roles) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.isEnabled(),
                roles,
                user.getOrganisationId(),
                user.getDesignation(),
                user.getPrimaryIcpmsRole(),
                user.getAuthMethods()
        );
    }
}
