package com.bipros.security.domain.model;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Static catalog of every fine-grained permission an admin can assign through a Profile.
 * Lives in code (not DB) so the set ships with the application — admin only picks from this list,
 * never invents new codes. Codes follow the pattern {@code MODULE.ACTION}.
 */
public final class PermissionCatalog {

    public record Permission(String code, String module, String action, String label) {}

    private static final String CREATE = "CREATE";
    private static final String READ = "READ";
    private static final String UPDATE = "UPDATE";
    private static final String DELETE = "DELETE";
    private static final String EXPORT = "EXPORT";
    private static final String APPROVE = "APPROVE";

    public static final List<Permission> ALL = List.of(
            // Project
            new Permission("PROJECT.CREATE", "PROJECT", CREATE, "Create projects"),
            new Permission("PROJECT.READ",   "PROJECT", READ,   "View projects"),
            new Permission("PROJECT.UPDATE", "PROJECT", UPDATE, "Edit projects"),
            new Permission("PROJECT.DELETE", "PROJECT", DELETE, "Delete projects"),
            new Permission("PROJECT.EXPORT", "PROJECT", EXPORT, "Export project data"),

            // Activity / WBS
            new Permission("ACTIVITY.CREATE", "ACTIVITY", CREATE, "Create activities and WBS nodes"),
            new Permission("ACTIVITY.READ",   "ACTIVITY", READ,   "View activities and WBS"),
            new Permission("ACTIVITY.UPDATE", "ACTIVITY", UPDATE, "Update activities and progress"),
            new Permission("ACTIVITY.DELETE", "ACTIVITY", DELETE, "Delete activities"),

            // Schedule
            new Permission("SCHEDULE.READ",    "SCHEDULE", READ,    "View schedules"),
            new Permission("SCHEDULE.UPDATE",  "SCHEDULE", UPDATE,  "Edit and recalculate schedule"),
            new Permission("SCHEDULE.APPROVE", "SCHEDULE", APPROVE, "Approve schedule changes"),

            // Baseline
            new Permission("BASELINE.CREATE", "BASELINE", CREATE, "Create baselines"),
            new Permission("BASELINE.READ",   "BASELINE", READ,   "View baselines"),
            new Permission("BASELINE.UPDATE", "BASELINE", UPDATE, "Update / promote baselines"),
            new Permission("BASELINE.DELETE", "BASELINE", DELETE, "Delete baselines"),

            // Resource
            new Permission("RESOURCE.CREATE", "RESOURCE", CREATE, "Create resources"),
            new Permission("RESOURCE.READ",   "RESOURCE", READ,   "View resources"),
            new Permission("RESOURCE.UPDATE", "RESOURCE", UPDATE, "Edit and assign resources"),
            new Permission("RESOURCE.DELETE", "RESOURCE", DELETE, "Delete resources"),

            // Cost
            new Permission("COST.CREATE", "COST", CREATE, "Create cost / budget entries"),
            new Permission("COST.READ",   "COST", READ,   "View costs and budgets"),
            new Permission("COST.UPDATE", "COST", UPDATE, "Edit costs and budgets"),
            new Permission("COST.DELETE", "COST", DELETE, "Delete cost entries"),
            new Permission("COST.EXPORT", "COST", EXPORT, "Export cost data"),

            // EVM
            new Permission("EVM.READ",   "EVM", READ,   "View EVM analysis"),
            new Permission("EVM.UPDATE", "EVM", UPDATE, "Update EVM data date and run"),
            new Permission("EVM.EXPORT", "EVM", EXPORT, "Export EVM reports"),

            // Risk
            new Permission("RISK.CREATE",  "RISK", CREATE,  "Create risks"),
            new Permission("RISK.READ",    "RISK", READ,    "View risks"),
            new Permission("RISK.UPDATE",  "RISK", UPDATE,  "Edit risks"),
            new Permission("RISK.DELETE",  "RISK", DELETE,  "Delete risks"),
            new Permission("RISK.APPROVE", "RISK", APPROVE, "Approve risk responses"),

            // Document
            new Permission("DOCUMENT.CREATE", "DOCUMENT", CREATE, "Upload documents"),
            new Permission("DOCUMENT.READ",   "DOCUMENT", READ,   "View documents"),
            new Permission("DOCUMENT.UPDATE", "DOCUMENT", UPDATE, "Edit document metadata / replace"),
            new Permission("DOCUMENT.DELETE", "DOCUMENT", DELETE, "Delete documents"),

            // Contract
            new Permission("CONTRACT.CREATE",  "CONTRACT", CREATE,  "Create contracts"),
            new Permission("CONTRACT.READ",    "CONTRACT", READ,    "View contracts"),
            new Permission("CONTRACT.UPDATE",  "CONTRACT", UPDATE,  "Edit contracts and milestones"),
            new Permission("CONTRACT.DELETE",  "CONTRACT", DELETE,  "Delete contracts"),
            new Permission("CONTRACT.APPROVE", "CONTRACT", APPROVE, "Approve contract changes"),

            // Portfolio
            new Permission("PORTFOLIO.READ",   "PORTFOLIO", READ,   "View portfolio rollups"),
            new Permission("PORTFOLIO.UPDATE", "PORTFOLIO", UPDATE, "Edit portfolio hierarchy"),

            // Reports
            new Permission("REPORT.READ",   "REPORT", READ,   "View reports and dashboards"),
            new Permission("REPORT.EXPORT", "REPORT", EXPORT, "Export reports (Excel / PDF)"),

            // AI Assistant
            new Permission("AI.READ", "AI", READ, "Use AI chat and AI-generated insights"),

            // Admin areas
            new Permission("ADMIN_USER.CREATE", "ADMIN_USER", CREATE, "Create users"),
            new Permission("ADMIN_USER.READ",   "ADMIN_USER", READ,   "View users"),
            new Permission("ADMIN_USER.UPDATE", "ADMIN_USER", UPDATE, "Edit users / assign profiles"),
            new Permission("ADMIN_USER.DELETE", "ADMIN_USER", DELETE, "Disable users"),

            new Permission("ADMIN_PROFILE.CREATE", "ADMIN_PROFILE", CREATE, "Create profiles"),
            new Permission("ADMIN_PROFILE.READ",   "ADMIN_PROFILE", READ,   "View profiles"),
            new Permission("ADMIN_PROFILE.UPDATE", "ADMIN_PROFILE", UPDATE, "Edit profile permissions"),
            new Permission("ADMIN_PROFILE.DELETE", "ADMIN_PROFILE", DELETE, "Delete custom profiles"),

            new Permission("ADMIN_ORG.CREATE", "ADMIN_ORG", CREATE, "Create organisations"),
            new Permission("ADMIN_ORG.READ",   "ADMIN_ORG", READ,   "View organisations"),
            new Permission("ADMIN_ORG.UPDATE", "ADMIN_ORG", UPDATE, "Edit organisations"),
            new Permission("ADMIN_ORG.DELETE", "ADMIN_ORG", DELETE, "Delete organisations"),

            new Permission("ADMIN_MASTER.READ",   "ADMIN_MASTER", READ,   "View master data (calendars, codes, UDFs)"),
            new Permission("ADMIN_MASTER.UPDATE", "ADMIN_MASTER", UPDATE, "Edit master data"),

            new Permission("ADMIN_SETTINGS.READ",   "ADMIN_SETTINGS", READ,   "View global settings"),
            new Permission("ADMIN_SETTINGS.UPDATE", "ADMIN_SETTINGS", UPDATE, "Edit global settings and integrations")
    );

    public static final Set<String> ALL_CODES = ALL.stream()
            .map(Permission::code)
            .collect(Collectors.toUnmodifiableSet());

    public static boolean isValid(String code) {
        return ALL_CODES.contains(code);
    }

    private PermissionCatalog() {}
}
