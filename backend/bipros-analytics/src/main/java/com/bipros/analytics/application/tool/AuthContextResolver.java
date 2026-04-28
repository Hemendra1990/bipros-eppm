package com.bipros.analytics.application.tool;

import com.bipros.security.application.service.CurrentUserService;
import com.bipros.security.application.service.ProjectAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds an {@link AuthContext} from the current security context. Called once per
 * analytics request by the orchestrator; the result is passed by reference into every
 * tool, avoiding repeated Spring Security lookups inside tool implementations.
 */
@Component
@RequiredArgsConstructor
public class AuthContextResolver {

    private final CurrentUserService currentUserService;
    private final ProjectAccessService projectAccessService;

    public AuthContext resolve() {
        UUID userId = currentUserService.getCurrentUserId();
        Set<String> roles = collectRoles();
        Set<UUID> accessible = projectAccessService.getAccessibleProjectIdsForCurrentUser();
        ViewTier tier = pickTier(roles);
        return new AuthContext(userId, roles, accessible, tier);
    }

    private static Set<String> collectRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Set.of();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static ViewTier pickTier(Set<String> roles) {
        if (roles.contains("ROLE_ADMIN")) return ViewTier.ADMIN;
        if (roles.contains("ROLE_FINANCE") || roles.contains("ROLE_FINANCE_MANAGER")) {
            return ViewTier.FINANCE_CONFIDENTIAL;
        }
        if (roles.contains("ROLE_PROJECT_MANAGER")
                || roles.contains("ROLE_PMO")
                || roles.contains("ROLE_EXECUTIVE")) {
            return ViewTier.INTERNAL;
        }
        return ViewTier.PUBLIC;
    }
}
