package com.bipros.api.config;

import com.bipros.security.domain.model.PermissionCatalog;
import com.bipros.security.domain.model.Profile;
import com.bipros.security.domain.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seeds the 10 system-default permission profiles. Idempotent: each profile is created only if a
 * row with the same {@code code} is absent. Existing profiles are NOT overwritten — admins may
 * have edited them.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProfileSeeder {

    private final ProfileRepository profileRepository;

    private static final String READ = "READ";
    private static final String CREATE = "CREATE";
    private static final String UPDATE = "UPDATE";
    private static final String DELETE = "DELETE";
    private static final String EXPORT = "EXPORT";
    private static final String APPROVE = "APPROVE";

    public void seed() {
        int created = 0;
        for (DefaultProfile dp : DEFAULTS) {
            if (profileRepository.existsByCode(dp.code)) continue;
            Profile p = new Profile(dp.code, dp.name, dp.description, dp.legacyRole,
                    true, dp.permissions);
            profileRepository.save(p);
            log.debug("Seeded profile {} ({} permissions)", dp.code, dp.permissions.size());
            created++;
        }
        if (created > 0) log.info("Seeded {} new default profiles", created);
    }

    private record DefaultProfile(String code, String name, String description,
                                  String legacyRole, Set<String> permissions) {}

    private static final List<DefaultProfile> DEFAULTS = List.of(
            new DefaultProfile(
                    "SYSTEM_ADMIN",
                    "System Administrator",
                    "Full platform control: all modules, user & profile management, settings.",
                    "ADMIN",
                    PermissionCatalog.ALL_CODES
            ),
            new DefaultProfile(
                    "PORTFOLIO_MANAGER",
                    "Portfolio Manager",
                    "Cross-project oversight, portfolio rollups, reports and analytics.",
                    "EXECUTIVE",
                    union(
                            allFor("PORTFOLIO"),
                            allFor("REPORT"),
                            of("PROJECT.READ", "ACTIVITY.READ", "SCHEDULE.READ", "COST.READ",
                                    "EVM.READ", "EVM.EXPORT", "RISK.READ", "ADMIN_ORG.READ")
                    )
            ),
            new DefaultProfile(
                    "PROJECT_MANAGER",
                    "Project Manager",
                    "End-to-end project delivery: schedule, cost, risk, resources, contracts.",
                    "PROJECT_MANAGER",
                    of(
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
                            "REPORT.READ", "REPORT.EXPORT",
                            "AI.READ"
                    )
            ),
            new DefaultProfile(
                    "SCHEDULER",
                    "Scheduler / Planner",
                    "Schedule development, baseline management, schedule analytics.",
                    "SCHEDULER",
                    of(
                            "PROJECT.READ",
                            "ACTIVITY.CREATE", "ACTIVITY.READ", "ACTIVITY.UPDATE", "ACTIVITY.DELETE",
                            "SCHEDULE.READ", "SCHEDULE.UPDATE",
                            "BASELINE.CREATE", "BASELINE.READ", "BASELINE.UPDATE", "BASELINE.DELETE",
                            "RESOURCE.READ",
                            "EVM.READ",
                            "REPORT.READ", "REPORT.EXPORT",
                            "AI.READ"
                    )
            ),
            new DefaultProfile(
                    "RESOURCE_MANAGER",
                    "Resource Manager",
                    "Labor, material and equipment pool management; deployment and rates.",
                    "RESOURCE_MANAGER",
                    of(
                            "PROJECT.READ",
                            "ACTIVITY.READ",
                            "RESOURCE.CREATE", "RESOURCE.READ", "RESOURCE.UPDATE", "RESOURCE.DELETE",
                            "COST.READ",
                            "ADMIN_MASTER.READ", "ADMIN_MASTER.UPDATE",
                            "REPORT.READ", "REPORT.EXPORT"
                    )
            ),
            new DefaultProfile(
                    "COST_CONTROLLER",
                    "Cost Controller",
                    "Budget, cost accounts, EVM, variance reporting, contract financials.",
                    "FINANCE",
                    of(
                            "PROJECT.READ",
                            "ACTIVITY.READ",
                            "COST.CREATE", "COST.READ", "COST.UPDATE", "COST.DELETE", "COST.EXPORT",
                            "EVM.READ", "EVM.UPDATE", "EVM.EXPORT",
                            "CONTRACT.READ", "CONTRACT.UPDATE",
                            "REPORT.READ", "REPORT.EXPORT",
                            "AI.READ"
                    )
            ),
            new DefaultProfile(
                    "RISK_MANAGER",
                    "Risk Manager",
                    "Risk register, scoring, Monte Carlo, mitigation tracking.",
                    "PMO",
                    of(
                            "PROJECT.READ",
                            "ACTIVITY.READ",
                            "RISK.CREATE", "RISK.READ", "RISK.UPDATE", "RISK.DELETE", "RISK.APPROVE",
                            "COST.READ",
                            "EVM.READ",
                            "REPORT.READ", "REPORT.EXPORT",
                            "AI.READ"
                    )
            ),
            new DefaultProfile(
                    "DOCUMENT_CONTROLLER",
                    "Document Controller",
                    "Document repository, RFIs, transmittals, drawing register, contracts.",
                    "TEAM_MEMBER",
                    of(
                            "PROJECT.READ",
                            "DOCUMENT.CREATE", "DOCUMENT.READ", "DOCUMENT.UPDATE", "DOCUMENT.DELETE",
                            "CONTRACT.CREATE", "CONTRACT.READ", "CONTRACT.UPDATE",
                            "REPORT.READ"
                    )
            ),
            new DefaultProfile(
                    "SITE_ENGINEER",
                    "Site Engineer",
                    "Field execution: activity progress, resource consumption, site documents.",
                    "SITE_ENGINEER",
                    of(
                            "PROJECT.READ",
                            "ACTIVITY.READ", "ACTIVITY.UPDATE",
                            "SCHEDULE.READ",
                            "RESOURCE.READ", "RESOURCE.UPDATE",
                            "DOCUMENT.CREATE", "DOCUMENT.READ", "DOCUMENT.UPDATE",
                            "REPORT.READ"
                    )
            ),
            new DefaultProfile(
                    "EXECUTIVE_VIEWER",
                    "Executive Viewer",
                    "Read-only across the platform with report export.",
                    "VIEWER",
                    union(
                            readOnly(),
                            of("REPORT.EXPORT", "EVM.EXPORT")
                    )
            )
    );

    private static Set<String> of(String... codes) {
        return Set.of(codes);
    }

    private static Set<String> allFor(String module) {
        String prefix = module + ".";
        return PermissionCatalog.ALL_CODES.stream()
                .filter(c -> c.startsWith(prefix))
                .collect(Collectors.toSet());
    }

    private static Set<String> readOnly() {
        return PermissionCatalog.ALL_CODES.stream()
                .filter(c -> c.endsWith(".READ"))
                .collect(Collectors.toSet());
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        return java.util.Arrays.stream(sets)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    static int defaultCount() {
        return DEFAULTS.size();
    }

    /** Lookup a default profile's code by its legacy role name; used when seeding demo users. */
    static String codeForLegacyRole(String legacyRole) {
        return DEFAULTS.stream()
                .filter(d -> d.legacyRole.equals(legacyRole))
                .map(DefaultProfile::code)
                .findFirst()
                .orElse(null);
    }

    static Map<String, String> defaultCodesByRole() {
        return DEFAULTS.stream().collect(Collectors.toMap(d -> d.legacyRole, DefaultProfile::code, (a, b) -> a));
    }
}
