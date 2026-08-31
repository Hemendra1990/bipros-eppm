package com.bipros.project.domain.model;

/**
 * Roles a user can hold on the project-scoped reporting line (see {@link ProjectTeamMember}).
 * Deliberately narrower than the global RBAC role list — only the roles that participate in
 * the Daily Balance Sheet rollup (Supervisor → Engineer → Site Manager → PM) plus QS/Safety.
 */
public enum ProjectRole {
    PM,
    CONSTRUCTION_MANAGER,
    SITE_MANAGER,
    ENGINEER,
    SUPERVISOR,
    QS,
    SAFETY,

    // Seats named in the client requirements workbook (01 Aug 2026). They take part in
    // notification addressing and the access matrices, not in the DBS cost rollup.
    /** "Project Control Engineer" — Access-Input rows 7-12, and the "project control team" that
     *  the AI_Agent sheet asks to be mailed on DPR, capacity, DBS, costing and EVM. */
    PROJECT_CONTROL,
    /** "Quality Engineer" — Access-Input rows 6 and 14 (weather, specifications/ITP). */
    QUALITY_ENGINEER,
    /** "Store keeper" — Access-Input row 11 (material consumption). */
    STORE_KEEPER,
    /** "Design Coordinator" — Access-Input row 13 (approved drawings). */
    DESIGN_COORDINATOR
}
