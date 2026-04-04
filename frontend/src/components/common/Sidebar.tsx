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
  { name: "WBS Templates", href: "/admin/wbs-templates", icon: FileText },
  { name: "Integrations", href: "/admin/integrations", icon: Plug },
];

export function Sidebar() {
  const pathname = usePathname();
  const { sidebarCollapsed, toggleSidebar } = useAppStore();

  return (
    <aside
      className={cn(
        "flex flex-col border-r border-gray-200 bg-white transition-all duration-200",
        sidebarCollapsed ? "w-16" : "w-64"
      )}
    >
      <div className="flex h-14 items-center justify-between border-b px-4">
        {!sidebarCollapsed && (
          <span className="text-lg font-bold text-blue-600">Bipros</span>
        )}
        <button
          onClick={toggleSidebar}
          className="rounded p-1 hover:bg-gray-100"
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
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-blue-50 text-blue-700"
                    : "text-gray-700 hover:bg-gray-50 hover:text-gray-900"
                )}
              >
                <item.icon size={20} />
                {!sidebarCollapsed && <span>{item.name}</span>}
              </Link>
            );
          })}
        </div>

        {/* Admin Section Divider */}
        {!sidebarCollapsed && (
          <div className="my-2 border-t border-gray-200" />
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
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-blue-50 text-blue-700"
                    : "text-gray-700 hover:bg-gray-50 hover:text-gray-900"
                )}
              >
                <item.icon size={20} />
                {!sidebarCollapsed && <span>{item.name}</span>}
              </Link>
            );
          })}
        </div>
      </nav>
    </aside>
  );
}
