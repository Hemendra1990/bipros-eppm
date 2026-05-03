package com.bipros.ai.context;

import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.common.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AiContextResolver {

    private final ProjectAccessGuard projectAccess;
    private final SecurityContextHelper securityContextHelper;

    public AiContext resolve(UUID projectId, String module) {
        UUID userId;
        try {
            userId = securityContextHelper.getCurrentUserId();
        } catch (Exception e) {
            userId = null;
        }
        String role = securityContextHelper.hasRole("ADMIN") ? "ADMIN"
                : securityContextHelper.hasRole("PROJECT_MANAGER") ? "PROJECT_MANAGER" : "USER";
        List<UUID> scoped = projectAccess.getAccessibleProjectIdsForCurrentUser() != null
                ? List.copyOf(projectAccess.getAccessibleProjectIdsForCurrentUser())
                : List.of();

        UUID effectiveProjectId = projectId;
        if (effectiveProjectId == null
                && !"ADMIN".equals(role)
                && scoped.size() == 1) {
            effectiveProjectId = scoped.get(0);
        }

        return new AiContext(userId, effectiveProjectId, module, role, scoped);
    }
}
