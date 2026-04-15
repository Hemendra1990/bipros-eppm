"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  BarChart3,
  Briefcase,
  Calendar,
  ChevronLeft,
  ChevronRight,
  FolderTree,
  LayoutDashboard,
  Network,
  Settings,
  Shield,
  Users,
  Layers,
  Sparkles,
  FileText,
  Plug,
  SlidersHorizontal,
  Building2,
  UserCog,
} from "lucide-react";
import { cn } from "@/lib/utils/cn";
import { useAppStore } from "@/lib/state/store";

const mainNavigation = [
  { name: "Dashboard", href: "/", icon: LayoutDashboard },
  { name: "Projects", href: "/projects", icon: FolderTree },
  { name: "Portfolios", href: "/portfolios", icon: Briefcase },
  { name: "EPS", href: "/eps", icon: Layers },
  { name: "Resources", href: "/resources", icon: Users },
  { name: "Calendars", href: "/admin/calendars", icon: Calendar },
  { name: "Reports", href: "/reports", icon: BarChart3 },
  { name: "Risk", href: "/risk", icon: Shield },
  { name: "OBS", href: "/obs", icon: Network },
  { name: "Analytics", href: "/analytics", icon: Sparkles },
  { name: "Dashboards", href: "/dashboards", icon: LayoutDashboard },
];

const adminNavigation = [
  { name: "Settings", href: "/admin/settings", icon: Settings },
  { name: "Organisations", href: "/admin/organisations", icon: Building2 },
  { name: "User Access", href: "/admin/user-access", icon: UserCog },
  { name: "WBS Templates", href: "/admin/wbs-templates", icon: FileText },
  { name: "Integrations", href: "/admin/integrations", icon: Plug },
  { name: "User Defined Fields", href: "/admin/udf", icon: SlidersHorizontal },
];

export function Sidebar() {
  const pathname = usePathname();
  const { sidebarCollapsed, toggleSidebar } = useAppStore();

  return (
    <aside
      className={cn(
        "flex flex-col border-r border-border-subtle bg-surface shadow-lg transition-all duration-200",
        "relative before:absolute before:left-0 before:top-0 before:bottom-0 before:w-px before:bg-gradient-to-b before:from-accent-glow before:to-transparent before:opacity-50",
        sidebarCollapsed ? "w-16" : "w-64"
      )}
    >
      <div className="flex h-14 items-center justify-between border-b border-border-subtle px-4 relative z-10">
        {!sidebarCollapsed && (
          <span className="text-lg font-bold gradient-text">Bipros</span>
        )}
        <button
          onClick={toggleSidebar}
          className="rounded p-1 text-text-secondary hover:text-text-primary hover:bg-surface-hover transition-colors"
        >
          {sidebarCollapsed ? <ChevronRight size={18} /> : <ChevronLeft size={18} />}
        </button>
      </div>
      <nav className="flex-1 space-y-1 overflow-y-auto px-2 py-4">
        {/* Main Navigation */}
        <div className="space-y-1">
          {mainNavigation.map((item) => {
            const isActive =
              item.href === "/"
                ? pathname === "/"
                : pathname.startsWith(item.href);
            return (
              <Link
                key={item.name}
                href={item.href}
                className={cn(
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors relative",
                  isActive
                    ? "text-accent hover:text-accent-hover border-l-2 border-accent"
                    : "text-text-secondary hover:text-text-primary hover:bg-surface-hover"
                )}
              >
                {isActive && (
                  <div className="absolute inset-0 rounded-md bg-accent-glow pointer-events-none -z-10"></div>
                )}
                <item.icon size={20} />
                {!sidebarCollapsed && <span>{item.name}</span>}
              </Link>
            );
          })}
        </div>

        {/* Admin Section Divider */}
        {!sidebarCollapsed && (
          <div className="my-2 border-t border-border-subtle" />
        )}

        {/* Admin Navigation */}
        <div className="space-y-1">
          {adminNavigation.map((item) => {
            const isActive = pathname.startsWith(item.href);
            return (
              <Link
                key={item.name}
                href={item.href}
                className={cn(
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors relative",
                  isActive
                    ? "text-accent hover:text-accent-hover border-l-2 border-accent"
                    : "text-text-muted hover:text-text-secondary hover:bg-surface-hover"
                )}
              >
                {isActive && (
                  <div className="absolute inset-0 rounded-md bg-accent-glow pointer-events-none -z-10"></div>
                )}
                <item.icon size={20} />
                {!sidebarCollapsed && <span>{item.name}</span>}
              </Link>
            );
          })}
        </div>
      </nav>

      {/* Footer - Version */}
      {!sidebarCollapsed && (
        <div className="border-t border-border-subtle px-4 py-3">
          <p className="text-xs text-text-muted">v1.0.0</p>
        </div>
      )}
    </aside>
  );
}
