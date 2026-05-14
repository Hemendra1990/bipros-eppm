package com.bipros.security.domain.model;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Canonical mapping from role name → default permission set for the 22-role RBAC matrix.
 *
 * <p>This is a read-only, code-shipped contract. Roles are keyed by their canonical names
 * (UPPER_SNAKE_CASE). Legacy aliases (e.g. {@code SITE_SUPERVISOR}, {@code COST_ENGINEER},
 * {@code STORE_KEEPER}, {@code QC_MANAGER}, {@code HSE_OFFICER}) are intentionally absent —
 * resolve them upstream before calling {@link #permissionsFor(String)}.</p>
 *
 * <p>Iteration order of {@link #DEFAULTS} matches insertion order to aid debugging.</p>
 */
public final class RolePermissionMatrix {

    public static final Map<String, Set<String>> DEFAULTS;

    static {
        LinkedHashMap<String, Set<String>> m = new LinkedHashMap<>();

        // 1. ADMIN — every permission in the catalog.
        m.put("ADMIN", PermissionCatalog.ALL_CODES);

        // 2. EXECUTIVE — portfolio oversight & cross-project reporting (mirrors PORTFOLIO_MANAGER).
        m.put("EXECUTIVE", Set.copyOf(
                Stream.concat(
                        Stream.concat(
                                PermissionCatalog.ALL_CODES.stream().filter(c -> c.startsWith("PORTFOLIO.")),
                                PermissionCatalog.ALL_CODES.stream().filter(c -> c.startsWith("REPORT."))
                        ),
                        Stream.of(
                                "PROJECT.READ", "ACTIVITY.READ", "SCHEDULE.READ", "COST.READ",
                                "EVM.READ", "EVM.EXPORT", "RISK.READ", "ADMIN_ORG.READ", "AI.READ"
                        )
                ).collect(Collectors.toSet())
        ));

        // 3. PMO — cross-portfolio governance.
        m.put("PMO", Set.of(
                "PORTFOLIO.READ", "PORTFOLIO.UPDATE",
                "REPORT.READ", "REPORT.EXPORT",
                "PROJECT.READ", "ACTIVITY.READ", "SCHEDULE.READ", "BASELINE.READ",
                "COST.READ", "EVM.READ", "RISK.READ", "CONTRACT.READ",
                "ADMIN_MASTER.READ", "ADMIN_MASTER.UPDATE",
                "AI.READ"
        ));

        // 4. FINANCE — equivalent to existing COST_CONTROLLER profile.
        m.put("FINANCE", Set.of(
                "PROJECT.READ", "ACTIVITY.READ",
                "COST.CREATE", "COST.READ", "COST.UPDATE", "COST.DELETE", "COST.EXPORT",
                "EVM.READ", "EVM.UPDATE", "EVM.EXPORT",
                "CONTRACT.READ", "CONTRACT.UPDATE",
                "REPORT.READ", "REPORT.EXPORT",
                "AI.READ"
        ));

        // 5. PROJECT_MANAGER — existing PROJECT_MANAGER profile + DPR + PROJECT_MEMBER + PERMIT.
        m.put("PROJECT_MANAGER", Set.of(
                "PROJECT.CREATE", "PROJECT.READ", "PROJECT.UPDATE", "PROJECT.DELETE", "PROJECT.EXPORT",
                "ACTIVITY.CREATE", "ACTIVITY.READ", "ACTIVITY.UPDATE", "ACTIVITY.DELETE",
                "SCHEDULE.READ", "SCHEDULE.UPDATE",
                "BASELINE.CREATE", "BASELINE.READ", "BASELINE.UPDATE",
                "RESOURCE.READ", "RESOURCE.UPDATE",
                "COST.READ", "COST.UPDATE", "COST.EXPORT",
                "EVM.READ", "EVM.UPDATE", "EVM.EXPORT",
                "RISK.CREATE", "RISK.READ", "RISK.UPDATE", "RISK.APPROVE",
                "DOCUMENT.CREATE", "DOCUMENT.READ", "DOCUMENT.UPDATE",
                "CONTRACT.READ", "CONTRACT.UPDATE",
                "DPR.READ", "DPR.APPROVE",
                "PROJECT_MEMBER.READ", "PROJECT_MEMBER.MANAGE",
                "PERMIT.READ", "PERMIT.APPROVE",
                "REPORT.READ", "REPORT.EXPORT",
                "AI.READ", "AI.WRITE"
        ));

        // 6. SCHEDULER — existing SCHEDULER profile.
        m.put("SCHEDULER", Set.of(
                "PROJECT.READ",
                "ACTIVITY.CREATE", "ACTIVITY.READ", "ACTIVITY.UPDATE", "ACTIVITY.DELETE",
                "SCHEDULE.READ", "SCHEDULE.UPDATE",
                "BASELINE.CREATE", "BASELINE.READ", "BASELINE.UPDATE", "BASELINE.DELETE",
                "RESOURCE.READ",
                "EVM.READ",
                "REPORT.READ", "REPORT.EXPORT",
                "AI.READ"
        ));

        // 7. PLANNING_ENGINEER — plan + schedule focus, slightly broader than SCHEDULER.
        m.put("PLANNING_ENGINEER", Set.of(
                "PROJECT.READ",
                "ACTIVITY.CREATE", "ACTIVITY.READ", "ACTIVITY.UPDATE",
                "SCHEDULE.READ", "SCHEDULE.UPDATE",
                "BASELINE.CREATE", "BASELINE.READ",
                "RESOURCE.READ",
                "RISK.READ",
                "EVM.READ",
                "DOCUMENT.READ",
                "REPORT.READ", "REPORT.EXPORT",
                "AI.READ"
        ));

        // 8. RESOURCE_MANAGER — existing RESOURCE_MANAGER profile + AI.READ.
        m.put("RESOURCE_MANAGER", Set.of(
                "PROJECT.READ", "ACTIVITY.READ",
                "RESOURCE.CREATE", "RESOURCE.READ", "RESOURCE.UPDATE", "RESOURCE.DELETE",
                "COST.READ",
                "ADMIN_MASTER.READ", "ADMIN_MASTER.UPDATE",
                "REPORT.READ", "REPORT.EXPORT",
                "AI.READ"
        ));

        // 9. STORE_MANAGER — store / inventory focus.
        m.put("STORE_MANAGER", Set.of(
                "PROJECT.READ",
                "RESOURCE.READ", "RESOURCE.UPDATE",
                "DOCUMENT.CREATE", "DOCUMENT.READ", "DOCUMENT.UPDATE",
                "REPORT.READ",
                "AI.READ"
        ));

        // 10. PROCUREMENT_OFFICER — buying / contract intake.
        m.put("PROCUREMENT_OFFICER", Set.of(
                "PROJECT.READ",
                "RESOURCE.READ", "RESOURCE.UPDATE",
                "CONTRACT.READ", "CONTRACT.UPDATE",
                "DOCUMENT.CREATE", "DOCUMENT.READ",
                "REPORT.READ",
                "AI.READ"
        ));

        // 11. SITE_MANAGER — existing SITE_MANAGER profile + DPR APPROVE + NCR + SAFETY + PERMIT read.
        m.put("SITE_MANAGER", Set.of(
                "PROJECT.READ",
                "ACTIVITY.READ", "ACTIVITY.UPDATE",
                "SCHEDULE.READ",
                "RESOURCE.READ", "RESOURCE.UPDATE",
                "COST.READ",
                "DOCUMENT.CREATE", "DOCUMENT.READ", "DOCUMENT.UPDATE",
                "DPR.READ", "DPR.CREATE", "DPR.UPDATE", "DPR.APPROVE",
                "NCR.READ",
                "SAFETY.READ",
                "PERMIT.READ", "PERMIT.CREATE",
                "REPORT.READ",
                "AI.READ"
        ));

        // 12. SITE_ENGINEER — existing SITE_ENGINEER profile + DPR write + NCR write + SAFETY + PERMIT.
        m.put("SITE_ENGINEER", Set.of(
                "PROJECT.READ",
                "ACTIVITY.READ", "ACTIVITY.UPDATE",
                "SCHEDULE.READ",
                "RESOURCE.READ", "RESOURCE.UPDATE",
                "DOCUMENT.CREATE", "DOCUMENT.READ", "DOCUMENT.UPDATE",
                "DPR.READ", "DPR.CREATE", "DPR.UPDATE",
                "NCR.READ", "NCR.CREATE",
                "SAFETY.READ", "SAFETY.INCIDENT_LOG",
                "PERMIT.READ", "PERMIT.CREATE",
                "REPORT.READ"
        ));

        // 13. PROJECT_ENGINEER — existing PROJECT_ENGINEER profile + DPR read/update + NCR read.
        m.put("PROJECT_ENGINEER", Set.of(
                "PROJECT.READ",
                "ACTIVITY.READ", "ACTIVITY.UPDATE",
                "SCHEDULE.READ",
                "RESOURCE.READ",
                "COST.READ",
                "EVM.READ",
                "DOCUMENT.READ",
                "DPR.READ", "DPR.UPDATE",
                "NCR.READ",
                "YIELD_VARIANCE.READ",
                "REPORT.READ",
                "AI.READ"
        ));

        // 14. SUPERVISOR — field supervision, DPR write.
        m.put("SUPERVISOR", Set.of(
                "PROJECT.READ", "ACTIVITY.READ",
                "RESOURCE.READ",
                "DOCUMENT.READ",
                "DPR.READ", "DPR.CREATE", "DPR.UPDATE",
                "NCR.READ",
                "SAFETY.READ", "SAFETY.INCIDENT_LOG",
                "PERMIT.READ",
                "REPORT.READ",
                "AI.READ"
        ));

        // 15. FOREMAN — crew level, narrowest field role.
        m.put("FOREMAN", Set.of(
                "PROJECT.READ", "ACTIVITY.READ",
                "RESOURCE.READ",
                "DPR.READ", "DPR.CREATE",
                "SAFETY.INCIDENT_LOG"
        ));

        // 16. QA_QC_ENGINEER — existing QA_QC_ENGINEER profile + DPR.READ.
        m.put("QA_QC_ENGINEER", Set.of(
                "PROJECT.READ",
                "ACTIVITY.READ",
                "RESOURCE.READ",
                "DOCUMENT.READ",
                "RISK.READ",
                "NCR.CREATE", "NCR.READ", "NCR.UPDATE", "NCR.APPROVE",
                "DPR.QC_ANNOTATE", "DPR.READ",
                "REPORT.READ",
                "AI.READ"
        ));

        // 17. SAFETY_OFFICER — safety / HSE focus.
        m.put("SAFETY_OFFICER", Set.of(
                "PROJECT.READ", "ACTIVITY.READ",
                "DOCUMENT.READ",
                "DPR.READ",
                "SAFETY.READ", "SAFETY.CREATE", "SAFETY.UPDATE", "SAFETY.INCIDENT_LOG",
                "PERMIT.READ", "PERMIT.APPROVE",
                "NCR.READ",
                "REPORT.READ",
                "AI.READ"
        ));

        // 18. BIM_DATA_COORDINATOR — existing BIM_DATA_COORDINATOR profile.
        m.put("BIM_DATA_COORDINATOR", Set.of(
                "PROJECT.READ",
                "ACTIVITY.READ",
                "RESOURCE.READ",
                "DOCUMENT.CREATE", "DOCUMENT.READ", "DOCUMENT.UPDATE",
                "ADMIN_MASTER.READ",
                "DATA_QUALITY.READ", "DATA_QUALITY.AUDIT",
                "REPORT.READ",
                "AI.READ"
        ));

        // 19. TEAM_MEMBER — existing DOCUMENT_CONTROLLER, narrowed.
        m.put("TEAM_MEMBER", Set.of(
                "PROJECT.READ", "ACTIVITY.READ",
                "DOCUMENT.CREATE", "DOCUMENT.READ", "DOCUMENT.UPDATE",
                "CONTRACT.READ",
                "REPORT.READ",
                "AI.READ"
        ));

        // 20. CONTRACTOR — external user, very narrow.
        m.put("CONTRACTOR", Set.of(
                "PROJECT.READ",
                "ACTIVITY.READ",
                "DPR.READ",
                "DOCUMENT.CREATE", "DOCUMENT.READ",
                "REPORT.READ"
        ));

        // 21. CLIENT — external read-only.
        m.put("CLIENT", Set.of(
                "PROJECT.READ",
                "ACTIVITY.READ",
                "SCHEDULE.READ",
                "COST.READ",
                "EVM.READ",
                "DOCUMENT.READ",
                "REPORT.READ", "REPORT.EXPORT"
        ));

        // 22. VIEWER — every *.READ code in the catalog + the two explicit export codes.
        m.put("VIEWER", Set.copyOf(
                Stream.concat(
                        PermissionCatalog.ALL_CODES.stream().filter(c -> c.endsWith(".READ")),
                        Stream.of("REPORT.EXPORT", "EVM.EXPORT")
                ).collect(Collectors.toSet())
        ));

        DEFAULTS = Collections.unmodifiableMap(m);
    }

    /**
     * @return the default permission set for the given canonical role name, or an empty set if
     *         the role is not in the matrix.
     */
    public static Set<String> permissionsFor(String roleName) {
        return DEFAULTS.getOrDefault(roleName, Set.of());
    }

    /**
     * @return the union of default permission sets for every canonical role name in the input.
     *         Unknown roles contribute nothing. The returned set is unmodifiable.
     */
    public static Set<String> permissionsForAll(Collection<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return Set.of();
        }
        Set<String> union = new HashSet<>();
        for (String role : roleNames) {
            union.addAll(permissionsFor(role));
        }
        return Collections.unmodifiableSet(union);
    }

    private RolePermissionMatrix() {}
}
