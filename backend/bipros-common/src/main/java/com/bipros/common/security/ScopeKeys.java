package com.bipros.common.security;

import java.util.Set;
import java.util.UUID;

/**
 * The resolved gate-3 identity of the current caller (access-control round, 2026-08-11):
 * their {@link DataScope} plus the keys module services need to build "my rows" predicates.
 *
 * <ul>
 *   <li>{@code userId} — the caller's primary key (null only for ALL/system).</li>
 *   <li>{@code nameAliases} — the caller's username and display name, for the id-else-name
 *       matching legacy rows require.</li>
 *   <li>{@code memberIds} / {@code memberAliases} — the person set the predicates filter on:
 *       for OWN just the caller; for TEAM the caller plus their transitive Team-tab downline
 *       (project-scoped — populated by {@code resolveForProject}); empty for PROJECT/ALL.</li>
 * </ul>
 */
public record ScopeKeys(DataScope scope, UUID userId, Set<String> nameAliases,
                        Set<UUID> memberIds, Set<String> memberAliases) {

    /** Compatibility shape: members default to the caller alone (OWN semantics). */
    public ScopeKeys(DataScope scope, UUID userId, Set<String> nameAliases) {
        this(scope, userId, nameAliases,
                userId == null ? Set.of() : Set.of(userId), nameAliases);
    }

    public boolean own() {
        return scope == DataScope.OWN;
    }

    /** True when the caller's rows must be narrowed to a person set (OWN or TEAM). */
    public boolean personScoped() {
        return scope == DataScope.OWN || scope == DataScope.TEAM;
    }

    /** Unrestricted (admin / system context). */
    public static ScopeKeys all() {
        return new ScopeKeys(DataScope.ALL, null, Set.of(), Set.of(), Set.of());
    }
}
