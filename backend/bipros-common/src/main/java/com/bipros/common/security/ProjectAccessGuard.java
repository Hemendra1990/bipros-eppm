package com.bipros.common.security;

import java.util.Set;
import java.util.UUID;

/**
 * Cross-cutting access decisions used by every domain module's services. Implemented by
 * {@code com.bipros.security.application.service.ProjectAccessService} at runtime; declared
 * here so {@code bipros-project}, {@code bipros-cost}, {@code bipros-contract}, etc. can inject
 * it without depending on {@code bipros-security} (the security module already depends on
 * {@code bipros-project}, so the reverse would cycle).
 *
 * <p>Semantics:
 * <ul>
 *   <li>{@link #getAccessibleProjectIdsForCurrentUser()} returns {@code null} when the caller is
 *       ADMIN — meaning "no row-level filter, all projects". Empty set means deny-all.
 *       {@link AccessSpecifications#projectScopedTo(java.util.Collection)} respects this
 *       sentinel.</li>
 *   <li>{@code requireRead/Edit/Delete} throw {@link org.springframework.security.access.AccessDeniedException}
 *       (mapped to HTTP 403 by {@code GlobalExceptionHandler}) on failure.</li>
 * </ul>
 */
public interface ProjectAccessGuard {

    Set<UUID> getAccessibleProjectIdsForCurrentUser();

    boolean canRead(UUID projectId);

    boolean canEdit(UUID projectId);

    boolean canDelete(UUID projectId);

    void requireRead(UUID projectId);

    void requireEdit(UUID projectId);

    void requireDelete(UUID projectId);

    /** @return the current authenticated user's ID, or {@code null} if no auth context. */
    UUID currentUserId();
}
