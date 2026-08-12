package com.bipros.security.application.service;

import com.bipros.common.security.PermissionChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The one implementation of {@link PermissionChecker}, resolving against the same effective
 * permission set every {@code @PreAuthorize} uses — profile-wins, then the role matrix — so a
 * redacted field and a guarded endpoint can never disagree about who may see what.
 *
 * <p>ADMIN short-circuits, matching {@code CustomPermissionEvaluator}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order
public class CurrentUserPermissionChecker implements PermissionChecker {

    private final CurrentUserService currentUserService;

    @Override
    public boolean has(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return false;
            }
            if (auth.getAuthorities() != null && auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
                return true;
            }
            return currentUserService.hasPermission(permissionCode);
        } catch (Exception ex) {
            // Never let a redaction check break a response — fail closed instead.
            log.debug("[PermissionChecker] {} check failed, treating as denied: {}",
                permissionCode, ex.getMessage());
            return false;
        }
    }
}
