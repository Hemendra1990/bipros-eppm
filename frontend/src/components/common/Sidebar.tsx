"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import type { LucideIcon } from "lucide-react";
import {
  Banknote, BarChart3, Briefcase, Building2, Calendar, ChevronDown,
  ChevronLeft, ChevronRight, CircleDollarSign, Contact, FileText, FolderTree, Gauge,
  Grid, HardHat, LayoutDashboard, LayoutGrid, Layers, Library, ListChecks, LogOut,
  Network, Plug, Settings, ShieldCheck, SlidersHorizontal, Sparkles, Tag,
  UserCog, Users, UsersRound, Workflow,
} from "lucide-react";
import { cn } from "@/lib/utils/cn";
import { useAppStore, useAuthStore } from "@/lib/state/store";
import type { IcpmsModule } from "@/lib/types";
import { useAccess } from "@/lib/auth/useAccess";
import { useAuth } from "@/lib/auth/useAuth";

/**
 * Optional gating fields:
 *   - {@code module} requires VIEW-or-better access to the named IC-PMS module
 *   - {@code adminOnly} is shorthand for "ROLE_ADMIN required"
 *   - {@code requireRoles} is an OR list of acceptable roles
 *
 * Items with no gate fields stay visible to every authenticated user.
 */
type NavItem = {
  name: string;
  href: string;
  icon: LucideIcon;
  module?: IcpmsModule;
  adminOnly?: boolean;
  requireRoles?: readonly string[];
};
type NavGroup = { label: string; items: NavItem[]; adminOnly?: boolean };

const groups: NavGroup[] = [
  {
    label: "Plan",
    items: [
      { name: "Dashboard", href: "/", icon: LayoutDashboard },
      { name: "Portfolios", href: "/portfolios", icon: Briefcase },
      { name: "Projects", href: "/projects", icon: FolderTree, module: "M1_WBS_GIS" },
      { name: "EPS", href: "/eps", icon: Layers, module: "M1_WBS_GIS" },
      { name: "Dashboards", href: "/dashboards", icon: LayoutGrid, module: "M9_REPORTS" },
    ],
  },
  {
    label: "Execute",
    items: [
      { name: "Resources", href: "/resources", icon: Users, module: "M8_RESOURCES" },
      { name: "Labour Master", href: "/labour-master", icon: HardHat, module: "M8_RESOURCES" },
      { name: "Calendars", href: "/admin/calendars", icon: Calendar, module: "M2_SCHEDULE_EVM" },
    ],
  },
  {
    label: "Control",
    items: [
      { name: "Reports", href: "/reports", icon: BarChart3, module: "M9_REPORTS" },
      { name: "OBS", href: "/obs", icon: Network, module: "M1_WBS_GIS" },
      { name: "Analytics", href: "/analytics", icon: Sparkles, module: "M9_REPORTS" },
    ],
  },
  {
    label: "HSE & Permits",
    items: [
      { name: "Permits", href: "/permits", icon: ShieldCheck,
        requireRoles: ["FOREMAN", "SITE_ENGINEER", "HSE_OFFICER", "PROJECT_MANAGER", "ADMIN"] },
      { name: "Workflow Reference", href: "/permits/workflow", icon: Workflow },
    ],
  },
  {
    label: "Admin",
    adminOnly: true,
    items: [
      { name: "Users", href: "/admin/users", icon: UsersRound, adminOnly: true },
      { name: "Organisations", href: "/admin/organisations", icon: Building2, adminOnly: true },
      { name: "User Access", href: "/admin/user-access", icon: UserCog, adminOnly: true },
      { name: "Resource Types", href: "/admin/resource-types", icon: ListChecks, adminOnly: true },
      { name: "Risk Library", href: "/admin/risk-library", icon: Library, adminOnly: true },
      { name: "Risk Categories", href: "/admin/risk-categories", icon: Layers, adminOnly: true },
      { name: "Risk Scoring Matrix", href: "/admin/risk-scoring-matrix", icon: Grid, adminOnly: true },
      { name: "WBS Templates", href: "/admin/wbs-templates", icon: FileText, adminOnly: true },
      { name: "Work Activities", href: "/admin/work-activities", icon: ListChecks, adminOnly: true },
      { name: "Productivity Norms", href: "/admin/productivity-norms", icon: Gauge, adminOnly: true },
      { name: "Unit Rate Master", href: "/admin/unit-rate-master", icon: Banknote, adminOnly: true },
      { name: "Cost Accounts", href: "/admin/cost-accounts", icon: CircleDollarSign, adminOnly: true },
      { name: "Resource Roles", href: "/admin/resource-roles", icon: Contact, adminOnly: true },
      { name: "Integrations", href: "/admin/integrations", icon: Plug, adminOnly: true },
      { name: "Project Categories", href: "/admin/project-categories", icon: Tag, adminOnly: true },
      { name: "User Defined Fields", href: "/admin/udf", icon: SlidersHorizontal, adminOnly: true },
      { name: "Settings", href: "/admin/settings", icon: Settings, adminOnly: true },
    ],
  },
];

function isActive(pathname: string, href: string) {
  if (href === "/") return pathname === "/";
  return pathname === href || pathname.startsWith(href + "/");
}

export function Sidebar() {
  const pathname = usePathname();
  const { sidebarCollapsed, toggleSidebar } = useAppStore();
  const { user, clearAuth } = useAuthStore();
  const { isAdmin, hasAnyRole } = useAuth();
  const { canAccessModule } = useAccess();
  const router = useRouter();

  const handleLogout = () => {
    document.cookie = "access_token=; path=/; max-age=0";
    clearAuth();
    router.push("/auth/login");
  };

  // Filter the static groups by the current user's roles + module access. ADMIN sees everything.
  const visibleGroups = groups
    .map((group) => {
      if (group.adminOnly && !isAdmin) return null;
      const visibleItems = group.items.filter((item) => {
        if (item.adminOnly && !isAdmin) return false;
        if (item.requireRoles && !hasAnyRole(item.requireRoles)) return false;
        if (item.module && !canAccessModule(item.module)) return false;
        return true;
      });
      if (visibleItems.length === 0) return null;
      return { ...group, items: visibleItems };
    })
    .filter((g): g is NavGroup => g !== null);

  // Pick the most "senior" role to show beneath the user's name in the chip. Falls back to
  // the raw first role string if none of the well-known roles match.
  const roleHierarchy = [
    "ROLE_ADMIN", "ROLE_EXECUTIVE", "ROLE_PMO", "ROLE_FINANCE",
    "ROLE_PROJECT_MANAGER", "ROLE_SCHEDULER", "ROLE_RESOURCE_MANAGER",
    "ROLE_TEAM_MEMBER", "ROLE_CLIENT", "ROLE_VIEWER",
  ];
  const norm = (r: string) => (r.startsWith("ROLE_") ? r : `ROLE_${r}`);
  const userRoles = (user?.roles ?? []).map(norm);
  const seniorRole = roleHierarchy.find((r) => userRoles.includes(r)) ?? userRoles[0];
  const roleLabel = seniorRole ? seniorRole.replace(/^ROLE_/, "").replace(/_/g, " ").toLowerCase() : "user";

  const displayName = user?.firstName ?? user?.username ?? "User";
  const initials = displayName.slice(0, 2).toUpperCase();

  return (
    <aside
      className={cn(
        "flex flex-col bg-paper border-r border-hairline transition-[width] duration-200",
        sidebarCollapsed ? "w-16" : "w-[260px]"
      )}
    >
      {/* Brand + collapse */}
      <div className="flex items-center justify-between border-b border-hairline px-4 py-5">
        {!sidebarCollapsed && (
          <div className="flex items-center gap-2.5">
            <img
              src="/bipros-logo.png"
              alt="Bipros"
              width={32}
              height={32}
              className="h-8 w-8 rounded-lg object-contain"
            />
            <div className="flex flex-col leading-none">
              <span className="font-display font-semibold text-lg text-charcoal tracking-tight">
                Bipros
              </span>
              <span className="text-[9px] font-semibold uppercase tracking-[0.2em] text-gold-deep mt-0.5">
                EPPM
              </span>
            </div>
          </div>
        )}
        {sidebarCollapsed && (
          <img
            src="/bipros-logo.png"
            alt="Bipros"
            width={32}
            height={32}
            className="mx-auto h-8 w-8 rounded-lg object-contain"
          />
        )}
        {!sidebarCollapsed && (
          <button
            onClick={toggleSidebar}
            aria-label="Collapse sidebar"
            className="rounded-md p-1 text-slate hover:bg-ivory hover:text-gold-deep"
          >
            <ChevronLeft size={16} />
          </button>
        )}
      </div>

      {sidebarCollapsed && (
        <button
          onClick={toggleSidebar}
          aria-label="Expand sidebar"
          className="mx-auto my-2 rounded-md p-1 text-slate hover:bg-ivory hover:text-gold-deep"
        >
          <ChevronRight size={16} />
        </button>
      )}

      {/* Workspace picker */}
      {!sidebarCollapsed && (
        <div className="px-4 pt-4 pb-2">
          <div className="mb-1.5 text-[10px] font-semibold uppercase tracking-[0.12em] text-slate">
            Workspace
          </div>
          <button
            type="button"
            className="flex w-full items-center justify-between rounded-[10px] border border-hairline bg-ivory px-3 py-2.5 text-sm font-semibold text-charcoal hover:bg-paper"
          >
            <span className="truncate">Acme Infrastructure</span>
            <ChevronDown size={14} className="text-ash" />
          </button>
        </div>
      )}

      {/* Nav */}
      <nav className="flex-1 overflow-y-auto px-3 pb-3 pt-2">
        {visibleGroups.map((group) => (
          <div key={group.label} className="mb-1">
            {!sidebarCollapsed && (
              <div className="px-2.5 pt-4 pb-1.5 text-[9px] font-semibold uppercase tracking-[0.14em] text-ash">
                {group.label}
              </div>
            )}
            {group.items.map((item) => {
              const active = isActive(pathname, item.href);
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  title={sidebarCollapsed ? item.name : undefined}
                  className={cn(
                    "relative flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-[13px] font-medium transition-colors",
                    active
                      ? "text-charcoal font-semibold bg-[linear-gradient(90deg,rgba(212,175,55,0.09),rgba(212,175,55,0)_90%)]"
                      : "text-charcoal hover:bg-ivory",
                    sidebarCollapsed && "justify-center"
                  )}
                >
                  {active && (
                    <span
                      aria-hidden
                      className="absolute left-[-13px] top-1.5 bottom-1.5 w-[3px] rounded-r-[3px] bg-gold"
                    />
                  )}
                  <item.icon
                    size={sidebarCollapsed ? 20 : 16}
                    className={cn("shrink-0", active ? "text-gold-deep" : "text-slate")}
                    strokeWidth={1.5}
                  />
                  {!sidebarCollapsed && <span className="truncate">{item.name}</span>}
                </Link>
              );
            })}
          </div>
        ))}
      </nav>

      {/* User chip */}
      {!sidebarCollapsed ? (
        <div className="border-t border-hairline p-3">
          <div className="flex items-center gap-2.5 rounded-[10px] px-2.5 py-2 hover:bg-ivory">
            <div
              className="flex h-8 w-8 items-center justify-center rounded-full bg-parchment text-gold-deep font-display font-semibold text-xs"
              style={{ border: "2px solid #D4AF37" }}
            >
              {initials}
            </div>
            <div className="min-w-0 flex-1 leading-tight">
              <div className="truncate text-[13px] font-semibold text-charcoal">
                {displayName}
              </div>
              <div className="truncate text-[10px] font-semibold uppercase tracking-[0.08em] text-gold-deep">
                {roleLabel}
              </div>
            </div>
            <button
              onClick={handleLogout}
              aria-label="Sign out"
              className="rounded-md p-1.5 text-slate hover:bg-paper hover:text-burgundy"
            >
              <LogOut size={14} />
            </button>
          </div>
        </div>
      ) : (
        <div className="flex flex-col items-center gap-2 border-t border-hairline px-2 py-3">
          <div
            title={`${displayName} · ${roleLabel}`}
            className="flex h-9 w-9 items-center justify-center rounded-full bg-parchment text-gold-deep font-display font-semibold text-xs"
            style={{ border: "2px solid #D4AF37" }}
          >
            {initials}
          </div>
          <button
            onClick={handleLogout}
            aria-label="Sign out"
            title="Sign out"
            className="flex h-9 w-9 items-center justify-center rounded-md text-slate hover:bg-ivory hover:text-burgundy"
          >
            <LogOut size={18} strokeWidth={1.5} />
          </button>
        </div>
      )}
    </aside>
  );
}
