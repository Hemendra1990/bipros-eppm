package com.bipros.common.security;

/**
 * Gate 3 of the request model: resolves the current caller's row-visibility scope. Implemented
 * in bipros-security ({@code ScopeResolverService}); consumed by module services that push the
 * scope into their repository queries — the filtering itself always happens in SQL, never on a
 * fetched list (pagination/totals/exports must stay correct by construction).
 *
 * <p>Same dependency-breaking pattern as {@code UserPermissionPort}.
 */
public interface ScopeResolverPort {

    /**
     * Never null. System context (schedulers, seeders, AI agents) and ADMIN resolve to
     * {@link ScopeKeys#all()}; a user with a profile gets the profile's scope; a user with no
     * profile gets PROJECT (legacy behaviour — membership is their only wall).
     */
    ScopeKeys resolveForCurrentUser();

    /**
     * Project-aware resolution: for TEAM scope the member set is the caller plus their
     * transitive Team-tab downline ON THIS PROJECT. Defaults to the project-agnostic
     * resolution (correct for OWN/PROJECT/ALL; TEAM degrades to self-only).
     */
    default ScopeKeys resolveForProject(java.util.UUID projectId) {
        return resolveForCurrentUser();
    }
}
