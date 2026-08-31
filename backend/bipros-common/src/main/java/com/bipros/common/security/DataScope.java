package com.bipros.common.security;

/**
 * Row-visibility level carried by a permission profile (access-control round, 2026-08-11).
 * Gate 3 of the request model: after the capability check (gate 1) and project-membership
 * check (gate 2), this decides WHICH ROWS the capabilities apply to.
 *
 * <ul>
 *   <li>{@link #OWN} — only rows the user is personally involved in (their DPRs, their
 *       assigned activities). Narrows only surfaces with a person dimension; project-level
 *       computed numbers (EVM, cost summary) stay project-wide.</li>
 *   <li>{@link #TEAM} — the user plus everyone below them in the project's Team-tab
 *       reporting chain (transitive reports-to walk). Role-agnostic and per-project: an
 *       engineer sees their supervisors' rows, a CM their engineers' whole downline.</li>
 *   <li>{@link #PROJECT} — every row of the projects the user is a member of. The default.</li>
 *   <li>{@link #ALL} — no filter (admin / executive oversight).</li>
 * </ul>
 */
public enum DataScope {
    OWN, TEAM, PROJECT, ALL;

    /** Null-safe, typo-safe parse for the DB column: null/unknown → {@link #PROJECT}. */
    public static DataScope fromDb(String value) {
        if (value == null) return PROJECT;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return PROJECT;
        }
    }
}
