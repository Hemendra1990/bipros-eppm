package com.bipros.common.security;

import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.UUID;

/**
 * JPA {@link Specification} factories used by every "list" repository call to enforce row-level
 * security. Lives in {@code bipros-common} so any module can use it without depending on
 * {@code bipros-security}.
 *
 * <p>Usage:
 * <pre>
 *   Set&lt;UUID&gt; allowed = projectAccessService.getAccessibleProjectIds(currentUserId);
 *   Specification&lt;Activity&gt; spec = AccessSpecifications.&lt;Activity&gt;projectScopedTo(allowed)
 *       .and(callerFilter);
 *   activityRepository.findAll(spec, pageable);
 * </pre>
 *
 * <p>{@code allowed == null} is the ADMIN sentinel: returns the no-op specification. {@code
 * allowed.isEmpty()} returns the deny-all specification (a literal {@code WHERE 1=0}).
 */
public final class AccessSpecifications {

    private AccessSpecifications() {}

    /**
     * Restrict an entity that has a {@code projectId} field to the supplied set.
     *
     * @param allowedProjectIds the set of project IDs the caller may see; {@code null} means
     *                          unrestricted (ADMIN), {@code empty} means deny-all.
     */
    public static <T> Specification<T> projectScopedTo(Collection<UUID> allowedProjectIds) {
        return projectScopedTo("projectId", allowedProjectIds);
    }

    /** Same as {@link #projectScopedTo(Collection)} with a custom field path. */
    public static <T> Specification<T> projectScopedTo(String fieldName, Collection<UUID> allowedProjectIds) {
        return (root, query, cb) -> {
            if (allowedProjectIds == null) {
                return cb.conjunction(); // no-op: matches everything
            }
            if (allowedProjectIds.isEmpty()) {
                return cb.disjunction(); // matches nothing
            }
            Path<UUID> projectId = root.get(fieldName);
            return projectId.in(allowedProjectIds);
        };
    }

    /** Restrict to entities owned by the given user via an {@code ownerId} column. */
    public static <T> Specification<T> ownedBy(UUID userId) {
        return ownedBy("ownerId", userId);
    }

    public static <T> Specification<T> ownedBy(String fieldName, UUID userId) {
        return (root, query, cb) -> {
            if (userId == null) {
                return cb.disjunction();
            }
            return cb.equal(root.get(fieldName), userId);
        };
    }

    /** Restrict to entities assigned to the given user via an {@code assignedTo} column. */
    public static <T> Specification<T> assignedTo(UUID userId) {
        return assignedTo("assignedTo", userId);
    }

    public static <T> Specification<T> assignedTo(String fieldName, UUID userId) {
        return (root, query, cb) -> {
            if (userId == null) {
                return cb.disjunction();
            }
            return cb.equal(root.get(fieldName), userId);
        };
    }
}
