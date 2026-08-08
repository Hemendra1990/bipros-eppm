package com.bipros.ai.security;

import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.common.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Pre-authorization helper for the AI chat endpoints. Distinct from the generic
 * {@code @projectAccess.canRead(...)} because the AI panel must work in a
 * cross-project ("general") mode where no current project is selected — in
 * that case authorization falls back to "user has at least one accessible
 * project, or is admin".
 */
@Slf4j
@Component("aiAccess")
@RequiredArgsConstructor
public class AiAccessGuard {

    private final ProjectAccessGuard projectAccess;
    private final SecurityContextHelper securityContextHelper;
    private final com.bipros.project.domain.repository.ProjectTeamRepository projectTeamRepository;

    /**
     * Notification-log visibility (owner decision 2026-08-05): ADMIN sees every project's log;
     * the project's PM (a PM seat on {@code project_team}) sees their own project's. Everyone
     * else is denied — the log reveals other users' delivery details.
     */
    public boolean canViewNotificationLog(UUID projectId) {
        if (securityContextHelper.hasRole("ADMIN")) {
            return true;
        }
        if (projectId == null) {
            return false;
        }
        try {
            UUID userId = securityContextHelper.getCurrentUserId();
            return userId != null && projectTeamRepository
                    .findByProjectIdAndUserIdAndRole(projectId, userId,
                            com.bipros.project.domain.model.ProjectRole.PM)
                    .isPresent();
        } catch (IllegalStateException | IllegalArgumentException noAuth) {
            return false;
        }
    }

    /** Read access for the agent APIs (findings, runs, registry). Same semantics as {@link #canChat}. */
    public boolean canRead(UUID projectId) {
        return canChat(projectId);
    }

    /**
     * Write access for the agent APIs (run an agent/pipeline, investigate). Requires edit rights on
     * the project; in cross-project mode requires ADMIN. Findings ack/resolve stay on read access.
     */
    public boolean canWrite(UUID projectId) {
        if (projectId != null) {
            return projectAccess.canEdit(projectId);
        }
        return securityContextHelper.hasRole("ADMIN");
    }

    public boolean canChat(UUID projectId) {
        if (projectId != null) {
            return projectAccess.canRead(projectId);
        }
        // Cross-project ("general") mode: admin always allowed; otherwise the
        // user must have at least one accessible project. We check the role
        // first because some seed users (notably "admin") authenticate with a
        // non-UUID principal name, which would make getCurrentUserId() throw
        // IllegalArgumentException.
        if (securityContextHelper.hasRole("ADMIN")) {
            return true;
        }
        try {
            UUID userId = securityContextHelper.getCurrentUserId();
            if (userId == null) {
                return false;
            }
        } catch (IllegalStateException | IllegalArgumentException noAuth) {
            return false;
        }
        Set<UUID> scoped = projectAccess.getAccessibleProjectIdsForCurrentUser();
        return scoped != null && !scoped.isEmpty();
    }
}
