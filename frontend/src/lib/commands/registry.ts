import type { LucideIcon } from "lucide-react";
import {
  Award, Banknote, BarChart3, Briefcase, Building2, Calendar,
  CircleDollarSign, Contact, FileText, FolderTree, Gauge,
  Grid, HardHat, Home, LayoutGrid, Layers, Library, ListChecks,
  Network, Plug, Settings, ShieldCheck, SlidersHorizontal, Sparkles,
  Tag, UserCog, Users, UsersRound, Workflow, Bot, PanelLeft, SunMoon,
} from "lucide-react";
import type { IcpmsModule } from "@/lib/types";

export interface Command {
  id: string;
  title: string;
  keywords: string[];
  icon: LucideIcon;
  group: string;
  href?: string;
  action?: () => void;
  adminOnly?: boolean;
  requireRoles?: readonly string[];
  module?: IcpmsModule;
}

export const COMMAND_GROUPS = [
  "Recent",
  "Plan",
  "Execute",
  "Control",
  "HSE & Permits",
  "Resources",
  "Master Data",
  "Admin",
  "Current Project",
  "Actions",
] as const;

export type CommandGroup = (typeof COMMAND_GROUPS)[number];

const GROUP_ORDER: Record<string, number> = {
  Recent: 0,
  Plan: 1,
  Execute: 2,
  Control: 3,
  "HSE & Permits": 4,
  Resources: 5,
  "Master Data": 6,
  Admin: 7,
  "Current Project": 8,
  Actions: 9,
};

export function groupRank(group: string): number {
  return GROUP_ORDER[group] ?? 99;
}

function nav(
  id: string,
  title: string,
  href: string,
  icon: LucideIcon,
  group: CommandGroup,
  opts?: Omit<Partial<Command>, "id" | "title" | "href" | "icon" | "group">
): Command {
  return { id, title, href, icon, group, keywords: [], ...opts };
}

function action(
  id: string,
  title: string,
  icon: LucideIcon,
  group: CommandGroup,
  action: () => void,
  opts?: Omit<Partial<Command>, "id" | "title" | "action" | "icon" | "group">
): Command {
  return { id, title, icon, group, action, keywords: [], ...opts };
}

/**
 * Static registry of all commands. Navigation commands mirror the sidebar;
 * action commands provide quick toggles.
 */
export const commands: Command[] = [
  // Plan
  nav("home", "Home", "/", Home, "Plan"),
  nav("portfolios", "Portfolios", "/portfolios", Briefcase, "Plan"),
  nav("projects", "Projects", "/projects", FolderTree, "Plan", { module: "M1_WBS_GIS" }),
  nav("eps", "EPS", "/eps", Layers, "Plan", { module: "M1_WBS_GIS" }),
  nav("dashboards", "Dashboards", "/dashboards", LayoutGrid, "Plan", { module: "M9_REPORTS" }),

  // Execute
  nav("calendars", "Calendars", "/admin/calendars", Calendar, "Execute", { module: "M2_SCHEDULE_EVM" }),

  // Control
  nav("reports", "Reports", "/reports", BarChart3, "Control", { module: "M9_REPORTS" }),
  nav("obs", "OBS", "/obs", Network, "Control", { module: "M1_WBS_GIS" }),
  nav("analytics", "Analytics", "/analytics", Sparkles, "Control", { module: "M9_REPORTS" }),

  // HSE & Permits
  nav("permits", "Permits", "/permits", ShieldCheck, "HSE & Permits", {
    requireRoles: ["FOREMAN", "SITE_ENGINEER", "HSE_OFFICER", "PROJECT_MANAGER", "ADMIN"],
  }),
  nav("permits-workflow", "Workflow Reference", "/permits/workflow", Workflow, "HSE & Permits"),

  // Resources
  nav("resource-types", "Resource Types", "/admin/resource-types", ListChecks, "Resources", { adminOnly: true }),
  nav("resource-roles", "Resource Roles", "/admin/resource-roles", Contact, "Resources", { adminOnly: true }),
  nav("resources", "Resources", "/resources", Users, "Resources", { adminOnly: true }),
  nav("labour-master", "Labour Master", "/labour-master", HardHat, "Resources", { adminOnly: true }),

  // Master Data
  nav("manpower-categories", "Categories", "/admin/manpower-categories", FolderTree, "Master Data", { adminOnly: true }),
  nav("employment-types", "Employment Types", "/admin/employment-types", Briefcase, "Master Data", { adminOnly: true }),
  nav("skills", "Skills", "/admin/skills", Sparkles, "Master Data", { adminOnly: true }),
  nav("skill-levels", "Skill Levels", "/admin/skill-levels", Award, "Master Data", { adminOnly: true }),
  nav("risk-library", "Risk Library", "/admin/risk-library", Library, "Master Data", { adminOnly: true }),
  nav("risk-categories", "Risk Categories", "/admin/risk-categories", Layers, "Master Data", { adminOnly: true }),
  nav("work-activities", "Work Activities", "/admin/work-activities", ListChecks, "Master Data", { adminOnly: true }),
  nav("productivity-norms", "Productivity Norms", "/admin/productivity-norms", Gauge, "Master Data", { adminOnly: true }),
  nav("project-categories", "Project Categories", "/admin/project-categories", Tag, "Master Data", { adminOnly: true }),

  // Admin
  nav("users", "Users", "/admin/users", UsersRound, "Admin", { adminOnly: true }),
  nav("profiles", "Profiles", "/admin/profiles", ShieldCheck, "Admin", { adminOnly: true }),
  nav("organisations", "Organisations", "/admin/organisations", Building2, "Admin", { adminOnly: true }),
  nav("user-access", "User Access", "/admin/user-access", UserCog, "Admin", { adminOnly: true }),
  nav("risk-scoring-matrix", "Risk Scoring Matrix", "/admin/risk-scoring-matrix", Grid, "Admin", { adminOnly: true }),
  nav("wbs-templates", "WBS Templates", "/admin/wbs-templates", FileText, "Admin", { adminOnly: true }),
  nav("unit-rate-master", "Unit Rate Master", "/admin/unit-rate-master", Banknote, "Admin", { adminOnly: true }),
  nav("cost-accounts", "Cost Accounts", "/admin/cost-accounts", CircleDollarSign, "Admin", { adminOnly: true }),
  nav("integrations", "Integrations", "/admin/integrations", Plug, "Admin", { adminOnly: true }),
  nav("udf", "User Defined Fields", "/admin/udf", SlidersHorizontal, "Admin", { adminOnly: true }),
  nav("settings", "Settings", "/admin/settings", Settings, "Admin", { adminOnly: true }),

  // Actions (placeholders — actual functions injected at runtime)
  action("toggle-sidebar", "Toggle Sidebar", PanelLeft, "Actions", () => {}, { keywords: ["collapse", "expand", "nav"] }),
  action("toggle-ai", "Open AI Chat", Bot, "Actions", () => {}, { keywords: ["ask", "assistant", "chat"] }),
  action("toggle-theme", "Toggle Theme", SunMoon, "Actions", () => {}, { keywords: ["dark", "light", "mode"] }),
];

/**
 * Project-scoped commands. These are instantiated dynamically when a projectId is known.
 */
export function buildProjectCommands(projectId: string): Command[] {
  return [
    nav("project-dashboard", "Project Dashboard", `/projects/${projectId}`, Home, "Current Project", { module: "M1_WBS_GIS" }),
    nav("project-activities", "Activities", `/projects/${projectId}/activities`, ListChecks, "Current Project", { module: "M1_WBS_GIS" }),
    nav("project-activity-codes", "Activity Codes", `/projects/${projectId}/activity-codes`, Tag, "Current Project", { module: "M1_WBS_GIS" }),
    nav("project-activity-correlations", "Activity Correlations", `/projects/${projectId}/activity-correlations`, Network, "Current Project", { module: "M1_WBS_GIS" }),
    nav("project-budget-changes", "Budget Changes", `/projects/${projectId}/budget-changes`, Banknote, "Current Project", { module: "M4_COST_RA_BILLS" }),
    nav("project-contracts", "Contracts", `/projects/${projectId}/contracts`, FileText, "Current Project", { module: "M5_CONTRACTS" }),
    nav("project-documents", "Documents", `/projects/${projectId}/documents`, FileText, "Current Project", { module: "M6_DOCUMENTS" }),
    nav("project-dpr", "DPR", `/projects/${projectId}/dpr`, BarChart3, "Current Project", { module: "M9_REPORTS" }),
    nav("project-drawings", "Drawings", `/projects/${projectId}/drawings`, FileText, "Current Project", { module: "M6_DOCUMENTS" }),
    nav("project-equipment-logs", "Equipment Logs", `/projects/${projectId}/equipment-logs`, Settings, "Current Project", { module: "M8_RESOURCES" }),
    nav("project-evm", "EVM", `/projects/${projectId}/evm`, BarChart3, "Current Project", { module: "M2_SCHEDULE_EVM" }),
    nav("project-gis-viewer", "GIS Viewer", `/projects/${projectId}/gis-viewer`, LayoutGrid, "Current Project", { module: "M1_WBS_GIS" }),
    nav("project-global-change", "Global Change", `/projects/${projectId}/global-change`, SlidersHorizontal, "Current Project", { module: "M1_WBS_GIS" }),
    nav("project-issues", "Issues", `/projects/${projectId}/issues`, ShieldCheck, "Current Project", { module: "M7_RISKS" }),
    nav("project-labour-returns", "Labour Returns", `/projects/${projectId}/labour-returns`, HardHat, "Current Project", { module: "M8_RESOURCES" }),
    nav("project-material-consumption", "Material Consumption", `/projects/${projectId}/material-consumption`, Grid, "Current Project", { module: "M8_RESOURCES" }),
    nav("project-material-reconciliation", "Material Reconciliation", `/projects/${projectId}/material-reconciliation`, Grid, "Current Project", { module: "M8_RESOURCES" }),
    nav("project-material-sources", "Material Sources", `/projects/${projectId}/material-sources`, Grid, "Current Project", { module: "M8_RESOURCES" }),
    nav("project-next-day-plan", "Next Day Plan", `/projects/${projectId}/next-day-plan`, Calendar, "Current Project", { module: "M2_SCHEDULE_EVM" }),
    nav("project-predictions", "Predictions", `/projects/${projectId}/predictions`, Sparkles, "Current Project", { module: "M9_REPORTS" }),
    nav("project-relationships", "Relationships", `/projects/${projectId}/relationships`, Network, "Current Project", { module: "M1_WBS_GIS" }),
    nav("project-resource-deployment", "Resource Deployment", `/projects/${projectId}/resource-deployment`, Users, "Current Project", { module: "M8_RESOURCES" }),
    nav("project-rfis", "RFIs", `/projects/${projectId}/rfis`, FileText, "Current Project", { module: "M6_DOCUMENTS" }),
    nav("project-risks", "Risks", `/projects/${projectId}/risks`, ShieldCheck, "Current Project", { module: "M7_RISKS" }),
    nav("project-risk-analysis", "Risk Analysis", `/projects/${projectId}/risk-analysis`, BarChart3, "Current Project", { module: "M7_RISKS" }),
    nav("project-schedule-compression", "Schedule Compression", `/projects/${projectId}/schedule-compression`, Calendar, "Current Project", { module: "M2_SCHEDULE_EVM" }),
    nav("project-schedule-health", "Schedule Health", `/projects/${projectId}/schedule-health`, Gauge, "Current Project", { module: "M2_SCHEDULE_EVM" }),
    nav("project-stretches", "Stretches", `/projects/${projectId}/stretches`, LayoutGrid, "Current Project", { module: "M1_WBS_GIS" }),
    nav("project-stock-register", "Stock Register", `/projects/${projectId}/stock-register`, Grid, "Current Project", { module: "M8_RESOURCES" }),
    nav("project-weather-log", "Weather Log", `/projects/${projectId}/weather-log`, SunMoon, "Current Project", { module: "M2_SCHEDULE_EVM" }),
  ];
}
