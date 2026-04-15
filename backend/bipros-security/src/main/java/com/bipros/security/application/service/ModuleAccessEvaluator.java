package com.bipros.security.application.service;

import com.bipros.security.domain.model.IcpmsModule;
import com.bipros.security.domain.model.ModuleAccessLevel;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.model.UserCorridorScope;
import com.bipros.security.domain.repository.UserCorridorScopeRepository;
import com.bipros.security.domain.repository.UserModuleAccessRepository;
import com.bipros.security.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Exposes the IC-PMS module-access matrix to {@code @PreAuthorize} SpEL.
 *
 * <p>Usage:
 * <pre>
 *   &#64;PreAuthorize("@moduleAccess.check(authentication, 'M5_CONTRACTS', 'EDIT')")
 * </pre>
 *
 * <p>Corridor scope is a separate axis; {@link #inScope(Authentication, UUID)} returns true when
 * the user has the "All Corridors" sentinel (single row with {@code wbsNodeId = NULL}) OR an
 * explicit row matching the given WBS node.
 *
 * <p>ADMIN role short-circuits all checks.
 */
@Slf4j
@Component("moduleAccess")
@RequiredArgsConstructor
public class ModuleAccessEvaluator {

    private final UserRepository userRepository;
    private final UserModuleAccessRepository userModuleAccessRepository;
    private final UserCorridorScopeRepository userCorridorScopeRepository;

    public boolean check(Authentication authentication, String module, String requiredLevel) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (hasAdminRole(authentication)) {
            return true;
        }
        User user = resolveUser(authentication);
        if (user == null) {
            return false;
        }
        IcpmsModule m;
        ModuleAccessLevel required;
        try {
            m = IcpmsModule.valueOf(module);
            required = ModuleAccessLevel.valueOf(requiredLevel);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown module/level in @PreAuthorize: module={} level={}", module, requiredLevel);
            return false;
        }
        return userModuleAccessRepository.findByUserIdAndModule(user.getId(), m)
                .map(uma -> uma.getAccessLevel().ordinal() >= required.ordinal()
                        || uma.getAccessLevel() == ModuleAccessLevel.FULL)
                .orElse(false);
    }

    public boolean inScope(Authentication authentication, UUID wbsNodeId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (hasAdminRole(authentication)) {
            return true;
        }
        User user = resolveUser(authentication);
        if (user == null) {
            return false;
        }
        List<UserCorridorScope> scopes = userCorridorScopeRepository.findByUserId(user.getId());
        for (UserCorridorScope s : scopes) {
            // NULL = All Corridors sentinel
            if (s.getWbsNodeId() == null || s.getWbsNodeId().equals(wbsNodeId)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAdminRole(Authentication authentication) {
        return authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private User resolveUser(Authentication authentication) {
        String username = authentication.getName();
        if (username == null) {
            return null;
        }
        return userRepository.findByUsername(username).orElse(null);
    }
}
