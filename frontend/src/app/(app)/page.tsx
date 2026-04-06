"use client";

import { FolderTree, Users, Calendar, BarChart3, TrendingUp, AlertCircle, Clock } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { apiClient } from "@/lib/api/client";
import { TabTip } from "@/components/common/TabTip";

const navigationCards = [
  {
    title: "Projects",
    description: "Manage enterprise project structure and schedules",
    href: "/projects",
    icon: FolderTree,
    color: "bg-blue-500/10 text-blue-700",
  },
  {
    title: "Resources",
    description: "Allocate and level resources across projects",
    href: "/resources",
    icon: Users,
    color: "bg-emerald-500/10 text-emerald-400",
  },
  {
    title: "Calendars",
    description: "Configure working time and holidays",
    href: "/admin/calendars",
    icon: Calendar,
    color: "bg-purple-500/10 text-purple-400",
  },
  {
    title: "Reports",
    description: "S-curves, EVM dashboards, and custom reports",
    href: "/reports",
    icon: BarChart3,
    color: "bg-amber-500/10 text-amber-400",
  },
];

interface ProjectData {
  id: string;
  name: string;
  status: string;
  createdAt: string;
}

interface ProjectsResponse {
  data: ProjectData[];
}

interface ActivityData {
  totalActivities: number;
  criticalActivities: number;
  overdueCount: number;
}

interface MetricsData {
  plannedCount: number;
  activeCount: number;
  completedCount: number;
  resourceCount: number;
  recentProjects: ProjectData[];
  activities: ActivityData;
}

async function fetchMetrics(): Promise<MetricsData> {
  try {
    const projectsResponse = await apiClient.get("/v1/projects?page=0&size=100");
    const projectsData = projectsResponse.data.data;

    const projects: ProjectData[] = projectsData?.content || [];

    const plannedCount = projects.filter((p) => p.status === "PLANNED").length;
    const activeCount = projects.filter((p) => p.status === "ACTIVE").length;
    const completedCount = projects.filter((p) => p.status === "COMPLETED").length;

    const recentProjects = [...projects]
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
      .slice(0, 5);

    // Fetch resources count
    const resourcesResponse = await apiClient.get("/v1/resources");
    const resourcesList = resourcesResponse.data.data || [];
    const resourceCount = Array.isArray(resourcesList) ? resourcesList.length : 0;

    // Aggregate activity counts across all projects
    let totalActivities = 0;
    let criticalActivities = 0;
    for (const proj of projects.slice(0, 10)) {
      try {
        const actResp = await apiClient.get(`/v1/projects/${proj.id}/activities?page=0&size=1`);
        const actData = actResp.data.data;
        const total = actData?.pagination?.totalElements || 0;
        totalActivities += total;
      } catch { /* skip */ }
    }

    return {
      plannedCount,
      activeCount,
      completedCount,
      resourceCount,
      recentProjects,
      activities: { totalActivities, criticalActivities, overdueCount: 0 },
    };
  } catch (error) {
    console.error("Failed to fetch metrics:", error);
    return {
      plannedCount: 0,
      activeCount: 0,
      completedCount: 0,
      resourceCount: 0,
      recentProjects: [],
      activities: { totalActivities: 0, criticalActivities: 0, overdueCount: 0 },
    };
  }
}

function MetricCard({ label, value, icon: Icon, color }: { label: string; value: number | string; icon: LucideIcon; color: string }) {
  // Map color values to dark theme variants
  const colorAccent: Record<string, { bg: string; text: string; border: string; accentBg: string }> = {
    "text-blue-700": { bg: "bg-slate-900/60 border-l-4 border-blue-500", text: "text-blue-400", border: "border border-slate-800", accentBg: "bg-blue-500/10" },
    "text-emerald-400": { bg: "bg-slate-900/60 border-l-4 border-emerald-500", text: "text-emerald-400", border: "border border-slate-800", accentBg: "bg-emerald-500/10" },
    "text-purple-400": { bg: "bg-slate-900/60 border-l-4 border-cyan-500", text: "text-cyan-400", border: "border border-slate-800", accentBg: "bg-cyan-500/10" },
    "text-amber-400": { bg: "bg-slate-900/60 border-l-4 border-purple-500", text: "text-purple-400", border: "border border-slate-800", accentBg: "bg-purple-500/10" },
    "text-red-400": { bg: "bg-slate-900/60 border-l-4 border-red-500", text: "text-red-400", border: "border border-slate-800", accentBg: "bg-red-500/10" },
  };

  const colorConfig = colorAccent[color] || { bg: "bg-slate-900/60", text: "text-slate-300", border: "border border-slate-800", accentBg: "bg-slate-700/30" };

  return (
    <div className={`rounded-xl p-6 shadow-lg ${colorConfig.bg} ${colorConfig.border}`}>
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm text-slate-400 uppercase tracking-wide">{label}</p>
          <p className={`mt-2 text-3xl font-bold ${colorConfig.text}`}>{value}</p>
        </div>
        <div className={`rounded-lg p-3 ${colorConfig.accentBg}`}>
          <Icon size={24} className={colorConfig.text} />
        </div>
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const [isClient, setIsClient] = useState(false);
  const { data: metrics, isLoading } = useQuery<MetricsData>({
    queryKey: ["dashboard-metrics"],
    queryFn: fetchMetrics,
    staleTime: 60000, // 1 minute
  });

  useEffect(() => {
    setIsClient(true);
  }, []);

  if (!isClient) return null;

  return (
    <div>
      <h1 className="mb-8 text-2xl font-bold text-white">Dashboard</h1>

      <TabTip
        title="Dashboard"
        description="Your command center. See project counts by status, total activities, resource utilization, and recent projects at a glance. Click any card to navigate to the details."
      />

      {/* Metrics Grid */}
      {isLoading ? (
        <div className="mb-8 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="h-32 animate-pulse rounded-xl bg-slate-800/50" />
          ))}
        </div>
      ) : (
        <>
          {/* Project Status Metrics */}
          <div className="mb-8">
            <h2 className="mb-4 text-lg font-semibold text-white">Project Status</h2>
            <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
              <MetricCard label="Planned" value={metrics?.plannedCount || 0} icon={FolderTree} color="text-blue-700" />
              <MetricCard label="Active" value={metrics?.activeCount || 0} icon={TrendingUp} color="text-emerald-400" />
              <MetricCard label="Completed" value={metrics?.completedCount || 0} icon={BarChart3} color="text-purple-400" />
              <MetricCard label="Resources" value={metrics?.resourceCount || 0} icon={Users} color="text-amber-400" />
            </div>
          </div>

          {/* Activity Summary */}
          <div className="mb-8">
            <h2 className="mb-4 text-lg font-semibold text-white">Activity Summary</h2>
            <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
              <MetricCard
                label="Total Activities"
                value={metrics?.activities.totalActivities || 0}
                icon={Clock}
                color="text-blue-700"
              />
              <MetricCard
                label="Critical Activities"
                value={metrics?.activities.criticalActivities || 0}
                icon={AlertCircle}
                color="text-red-400"
              />
              <MetricCard
                label="Overdue"
                value={metrics?.activities.overdueCount || 0}
                icon={TrendingUp}
                color="text-orange-400"
              />
            </div>
          </div>

          {/* Recent Projects */}
          <div className="mb-8">
            <h2 className="mb-4 text-lg font-semibold text-white">Recent Projects</h2>
            <div className="overflow-x-auto rounded-xl border border-slate-800 bg-slate-900/50 shadow-xl">
              <table className="w-full">
                <thead className="border-b border-slate-700/50 bg-slate-900/80">
                  <tr>
                    <th className="px-6 py-3 text-left text-sm font-semibold text-slate-400">Project Name</th>
                    <th className="px-6 py-3 text-left text-sm font-semibold text-slate-400">Status</th>
                    <th className="px-6 py-3 text-left text-sm font-semibold text-slate-400">Created</th>
                  </tr>
                </thead>
                <tbody>
                  {metrics?.recentProjects && metrics.recentProjects.length > 0 ? (
                    metrics.recentProjects.map((project) => (
                      <tr key={project.id} className="border-b border-slate-800/50 hover:bg-slate-800/30 transition-colors">
                        <td className="px-6 py-4 text-sm font-medium text-slate-300">
                          <Link href={`/projects/${project.id}`} className="text-blue-400 hover:text-blue-300">
                            {project.name}
                          </Link>
                        </td>
                        <td className="px-6 py-4 text-sm">
                          <span className={`inline-flex rounded-md px-2.5 py-1 text-xs font-medium ring-1 ${
                            project.status === "ACTIVE"
                              ? "bg-emerald-500/10 text-emerald-400 ring-emerald-500/20"
                              : project.status === "PLANNED"
                                ? "bg-blue-500/10 text-blue-400 ring-blue-500/20"
                                : "bg-slate-700/50 text-slate-300 ring-slate-600/50"
                          }`}>
                            {project.status}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-sm text-slate-400">
                          {new Date(project.createdAt).toLocaleDateString()}
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={3} className="px-6 py-4 text-center text-sm text-slate-500">
                        No recent projects
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}

      {/* Navigation Cards */}
      <div className="mt-8">
        <h2 className="mb-4 text-lg font-semibold text-white">Quick Actions</h2>
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {navigationCards.map((card) => (
            <Link
              key={card.title}
              href={card.href}
              className="group rounded-xl border border-slate-800 bg-slate-900/40 p-6 shadow-lg hover:border-slate-700 hover:bg-slate-800/50 transition-all"
            >
              <div className={`mb-4 inline-flex rounded-lg p-3 bg-blue-500/10`}>
                <card.icon size={24} className="text-blue-400" />
              </div>
              <h2 className="text-lg font-semibold text-white group-hover:text-blue-300">
                {card.title}
              </h2>
              <p className="mt-1 text-sm text-slate-500">{card.description}</p>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}
