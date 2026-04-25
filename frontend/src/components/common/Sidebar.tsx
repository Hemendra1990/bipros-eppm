"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import type { LucideIcon } from "lucide-react";
import {
  BarChart3, Briefcase, Building2, Calendar, ChevronDown, ChevronLeft,
  ChevronRight, FileText, FolderTree, Gauge, LayoutDashboard, Layers,
  ListChecks, LogOut, Network, Plug, Settings, Shield, SlidersHorizontal,
  Sparkles, UserCog, Users,
} from "lucide-react";
import { cn } from "@/lib/utils/cn";
import { useAppStore, useAuthStore } from "@/lib/state/store";

type NavItem = { name: string; href: string; icon: LucideIcon };
type NavGroup = { label: string; items: NavItem[] };

const groups: NavGroup[] = [
  {
    label: "Plan",
    items: [
      { name: "Dashboard", href: "/", icon: LayoutDashboard },
      { name: "Portfolios", href: "/portfolios", icon: Briefcase },
      { name: "Projects", href: "/projects", icon: FolderTree },
      { name: "EPS", href: "/eps", icon: Layers },
      { name: "Dashboards", href: "/dashboards", icon: LayoutDashboard },
    ],
  },
  {
    label: "Execute",
    items: [
      { name: "Resources", href: "/resources", icon: Users },
      { name: "Calendars", href: "/admin/calendars", icon: Calendar },
    ],
  },
  {
    label: "Control",
    items: [
      { name: "Reports", href: "/reports", icon: BarChart3 },
      { name: "Risk", href: "/risk", icon: Shield },
      { name: "OBS", href: "/obs", icon: Network },
      { name: "Analytics", href: "/analytics", icon: Sparkles },
    ],
  },
  {
    label: "Admin",
    items: [
      { name: "Users", href: "/admin/users", icon: Users },
      { name: "Organisations", href: "/admin/organisations", icon: Building2 },
      { name: "User Access", href: "/admin/user-access", icon: UserCog },
      { name: "Resource Types", href: "/admin/resource-types", icon: ListChecks },
      { name: "Risk Library", href: "/admin/risk-library", icon: Shield },
      { name: "WBS Templates", href: "/admin/wbs-templates", icon: FileText },
      { name: "Productivity Norms", href: "/admin/productivity-norms", icon: Gauge },
      { name: "Unit Rate Master", href: "/admin/unit-rate-master", icon: Gauge },
      { name: "Resource Roles", href: "/admin/resource-roles", icon: Users },
      { name: "Integrations", href: "/admin/integrations", icon: Plug },
      { name: "User Defined Fields", href: "/admin/udf", icon: SlidersHorizontal },
      { name: "Settings", href: "/admin/settings", icon: Settings },
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
  const router = useRouter();

  const handleLogout = () => {
    document.cookie = "access_token=; path=/; max-age=0";
    clearAuth();
    router.push("/auth/login");
  };

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
        {groups.map((group) => (
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
                    size={16}
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
      {!sidebarCollapsed && (
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
                Programme lead
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
      )}
    </aside>
  );
}
