package com.bipros.security.domain.model;

import java.util.Set;

/**
 * Project-scoped roles. A user can hold a different role on each project they're a member of.
 * Coexists with the global {@link Role} (ROLE_ADMIN, ROLE_FINANCE, etc.) and the
 * {@link UserObsAssignment} OBS-tree access level — see plan
 * {@code /Users/hemendra/.claude/plans/you-are-a-senior-prancy-micali.md}.
 */
public enum ProjectMemberRole {

    PROJECT_MANAGER,
    SCHEDULER,
    RESOURCE_MANAGER,
    TEAM_MEMBER,
    CLIENT;

    /** Roles that may modify project structure (activities, baselines, contracts, risks). */
    public static final Set<ProjectMemberRole> EDITORS =
            Set.of(PROJECT_MANAGER, SCHEDULER, RESOURCE_MANAGER);

    /** Roles that may delete a project. */
    public static final Set<ProjectMemberRole> DELETERS =
            Set.of(PROJECT_MANAGER);

    public boolean canEdit() {
        return EDITORS.contains(this);
    }

    public boolean canDelete() {
        return DELETERS.contains(this);
    }
}
