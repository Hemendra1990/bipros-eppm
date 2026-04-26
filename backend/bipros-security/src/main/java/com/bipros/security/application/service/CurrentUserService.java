package com.bipros.security.application.service;

import com.bipros.security.domain.model.User;
import com.bipros.security.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves the currently-authenticated {@link User} from the Spring {@code SecurityContext}.
 *
 * <p>The {@code SecurityContextHelper.getCurrentUserId()} in {@code bipros-common} casts the
 * username string straight to UUID, which is wrong for this app — Spring's principal carries
 * the literal username (e.g. {@code "admin"}), not the user's primary key. This service does the
 * proper lookup via {@link UserRepository}.
 *
 * <p>Holds no state; safe to inject anywhere.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    /** @return the current user's ID, or {@code null} if no authenticated user is in scope. */
    public UUID getCurrentUserId() {
        return getCurrentUser().map(User::getId).orElse(null);
    }

    public Optional<User> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return Optional.empty();
        }
        return userRepository.findByUsername(auth.getName());
    }

    public boolean isAdmin() {
        return currentRoles().contains("ROLE_ADMIN");
    }

    /**
     * @return {@code true} when there's no Spring {@code Authentication} at all — i.e. we're
     *         running inside a CommandLineRunner / startup seeder / scheduled job, NOT an HTTP
     *         request. ProjectAccessService treats this as "system mode" and bypasses RBAC.
     *         Anonymous authenticated requests (set by Spring's anonymous filter) are NOT system.
     */
    public boolean isSystemContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null;
    }

    public boolean hasRole(String role) {
        Set<String> roles = currentRoles();
        return roles.contains(role) || roles.contains("ROLE_" + role);
    }

    public Set<String> currentRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
    }
}
