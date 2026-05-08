package com.bipros.ai.context;

import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.common.security.SecurityContextHelper;
import com.bipros.security.domain.repository.ProfileRepository;
import com.bipros.security.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AiContextResolver {

    private final ProjectAccessGuard projectAccess;
    private final SecurityContextHelper securityContextHelper;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    public AiContext resolve(UUID projectId, String module) {
        UUID userId;
        try {
            userId = securityContextHelper.getCurrentUserId();
        } catch (Exception e) {
            userId = null;
        }
        String role = securityContextHelper.hasRole("ADMIN") ? "ADMIN"
                : securityContextHelper.hasRole("PROJECT_MANAGER") ? "PROJECT_MANAGER" : "USER";

        String profileCode = resolveProfileCode(userId);

        List<UUID> scoped = projectAccess.getAccessibleProjectIdsForCurrentUser() != null
                ? List.copyOf(projectAccess.getAccessibleProjectIdsForCurrentUser())
                : List.of();

        UUID effectiveProjectId = projectId;
        if (effectiveProjectId == null
                && !"ADMIN".equals(role)
                && scoped.size() == 1) {
            effectiveProjectId = scoped.get(0);
        }

        return new AiContext(userId, effectiveProjectId, module, role, profileCode, scoped);
    }

    private String resolveProfileCode(UUID userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(u -> u.getProfileId())
                .flatMap(pid -> pid == null ? Optional.empty() : profileRepository.findById(pid))
                .map(p -> p.getCode())
                .orElse(null);
    }
}
