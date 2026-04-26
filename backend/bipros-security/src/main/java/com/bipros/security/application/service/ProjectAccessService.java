package com.bipros.security.application.service;

import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.security.domain.model.AccessLevel;
import com.bipros.security.domain.model.ProjectMember;
import com.bipros.security.domain.model.ProjectMemberRole;
import com.bipros.security.domain.repository.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Central authority for project-scoped read/write decisions. Combines:
 * <ul>
 *   <li>OBS-tree access via {@link ObsSecurityService} (existing) — coarse "what region of the
 *       org tree can this user see".</li>
 *   <li>Per-project role membership via {@link ProjectMemberRepository} (new) — fine-grained
 *       "what role does the user hold on this specific project".</li>
 * </ul>
 *
 * <p><b>Read access</b> is the UNION of (OBS subtree projects) ∪ (project_members rows).
 * <b>Write access</b> requires a project_members row whose role is in
 * {@link ProjectMemberRole#EDITORS} AND OBS access ≥ {@link AccessLevel#EDIT}, OR global
 * {@code ROLE_ADMIN}. {@code ROLE_ADMIN} short-circuits everything.
 *
 * <p>Returns the sentinel value {@code null} from {@link #getAccessibleProjectIds} when the
 * caller is ADMIN, signalling "no row-level filter — all projects". Callers must treat
 * {@code null} as the unrestricted case (see {@link com.bipros.common.security.AccessSpecifications}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectAccessService implements ProjectAccessGuard {

    private final ObsSecurityService obsSecurityService;
    private final ProjectMemberRepository projectMemberRepository;
    private final CurrentUserService currentUserService;

    @Override
    public UUID currentUserId() {
        return currentUserService.getCurrentUserId();
    }

    @Override
    public boolean canRead(UUID projectId) {
        return canRead(currentUserService.getCurrentUserId(), projectId);
    }

    @Override
    public boolean canEdit(UUID projectId) {
        return canEdit(currentUserService.getCurrentUserId(), projectId);
    }

    @Override
    public boolean canDelete(UUID projectId) {
        return canDelete(currentUserService.getCurrentUserId(), projectId);
    }

    @Override
    public void requireRead(UUID projectId) {
        requireRead(currentUserService.getCurrentUserId(), projectId);
    }

    @Override
    public void requireEdit(UUID projectId) {
        requireEdit(currentUserService.getCurrentUserId(), projectId);
    }

    @Override
    public void requireDelete(UUID projectId) {
        requireDelete(currentUserService.getCurrentUserId(), projectId);
    }

    /**
     * @return the set of project IDs the user may READ, or {@code null} if ADMIN (no filter).
     *         Empty set means "no projects" (deny-all).
     */
    @Transactional(readOnly = true)
    public Set<UUID> getAccessibleProjectIds(UUID userId) {
        if (currentUserService.isSystemContext() || currentUserService.isAdmin()) {
            return null;
        }
        if (userId == null) {
            return Collections.emptySet();
        }
        Set<UUID> ids = new HashSet<>();
        // OBS-tree-derived projects
        List<UUID> obsProjects = obsSecurityService.getAccessibleProjectIds(userId);
        if (obsProjects != null) {
            ids.addAll(obsProjects);
        }
        // ProjectMember-derived projects
        ids.addAll(projectMemberRepository.findProjectIdsByUserId(userId));
        return ids;
    }

    /** Convenience: call from request scope without passing the userId. */
    @Transactional(readOnly = true)
    public Set<UUID> getAccessibleProjectIdsForCurrentUser() {
        return getAccessibleProjectIds(currentUserService.getCurrentUserId());
    }

    @Transactional(readOnly = true)
    public boolean canRead(UUID userId, UUID projectId) {
        if (currentUserService.isSystemContext() || currentUserService.isAdmin()) {
            return true;
        }
        if (userId == null || projectId == null) {
            return false;
        }
        if (!projectMemberRepository.findByUserIdAndProjectId(userId, projectId).isEmpty()) {
            return true;
        }
        return obsSecurityService.hasAccess(userId, projectId, AccessLevel.VIEW);
    }

    @Transactional(readOnly = true)
    public boolean canEdit(UUID userId, UUID projectId) {
        if (currentUserService.isSystemContext() || currentUserService.isAdmin()) {
            return true;
        }
        if (userId == null || projectId == null) {
            return false;
        }
        boolean hasEditingRole = projectMemberRepository.findByUserIdAndProjectId(userId, projectId).stream()
                .map(ProjectMember::getProjectRole)
                .anyMatch(ProjectMemberRole::canEdit);
        if (!hasEditingRole) {
            return false;
        }
        return obsSecurityService.hasAccess(userId, projectId, AccessLevel.EDIT);
    }

    @Transactional(readOnly = true)
    public boolean canDelete(UUID userId, UUID projectId) {
        if (currentUserService.isSystemContext() || currentUserService.isAdmin()) {
            return true;
        }
        if (userId == null || projectId == null) {
            return false;
        }
        boolean hasDeletingRole = projectMemberRepository.findByUserIdAndProjectId(userId, projectId).stream()
                .map(ProjectMember::getProjectRole)
                .anyMatch(ProjectMemberRole::canDelete);
        if (!hasDeletingRole) {
            return false;
        }
        return obsSecurityService.hasAccess(userId, projectId, AccessLevel.FULL);
    }

    @Transactional(readOnly = true)
    public boolean hasProjectRole(UUID userId, UUID projectId, ProjectMemberRole role) {
        if (currentUserService.isSystemContext() || currentUserService.isAdmin()) {
            return true;
        }
        if (userId == null || projectId == null || role == null) {
            return false;
        }
        return projectMemberRepository.existsByUserIdAndProjectIdAndProjectRole(userId, projectId, role);
    }

    /** Throws {@link AccessDeniedException} (mapped to 403) if the user cannot read. */
    public void requireRead(UUID userId, UUID projectId) {
        if (!canRead(userId, projectId)) {
            log.info("Access denied (read): userId={} projectId={}", userId, projectId);
            throw new AccessDeniedException(
                    "User " + userId + " is not authorised to read project " + projectId);
        }
    }

    /** Throws {@link AccessDeniedException} (mapped to 403) if the user cannot edit. */
    public void requireEdit(UUID userId, UUID projectId) {
        if (!canEdit(userId, projectId)) {
            log.info("Access denied (edit): userId={} projectId={}", userId, projectId);
            throw new AccessDeniedException(
                    "User " + userId + " is not authorised to edit project " + projectId);
        }
    }

    public void requireDelete(UUID userId, UUID projectId) {
        if (!canDelete(userId, projectId)) {
            log.info("Access denied (delete): userId={} projectId={}", userId, projectId);
            throw new AccessDeniedException(
                    "User " + userId + " is not authorised to delete project " + projectId);
        }
    }
}
