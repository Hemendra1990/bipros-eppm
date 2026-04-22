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
    color: "bg-accent/10 text-accent",
  },
  {
    title: "Resources",
    description: "Allocate and level resources across projects",
    href: "/resources",
    icon: Users,
    color: "bg-success/10 text-success",
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
    color: "bg-warning/10 text-warning",
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
        const actResp = await apiClient.get(`/v1/projects/${proj.id}/activities?page=0&size=500`);
        const actData = actResp.data.data;
        const total = actData?.pagination?.totalElements || 0;
        totalActivities += total;
        const activitiesList: Array<{ isCritical?: boolean; totalFloat?: number }> = actData?.content || [];
        criticalActivities += activitiesList.filter((a) => a.isCritical === true || a.totalFloat === 0).length;
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
    "text-accent": { bg: "bg-surface/60 border-l-4 border-accent", text: "text-accent", border: "border border-border", accentBg: "bg-accent/10" },
    "text-success": { bg: "bg-surface/60 border-l-4 border-success", text: "text-success", border: "border border-border", accentBg: "bg-success/10" },
    "text-purple-400": { bg: "bg-surface/60 border-l-4 border-info", text: "text-info", border: "border border-border", accentBg: "bg-cyan-500/10" },
    "text-warning": { bg: "bg-surface/60 border-l-4 border-purple-400", text: "text-purple-400", border: "border border-border", accentBg: "bg-purple-500/10" },
    "text-danger": { bg: "bg-surface/60 border-l-4 border-danger", text: "text-danger", border: "border border-border", accentBg: "bg-danger/10" },
  };

  const colorConfig = colorAccent[color] || { bg: "bg-surface/60", text: "text-text-secondary", border: "border border-border", accentBg: "bg-surface-active/30" };

  return (
    <div className={`rounded-xl p-6 shadow-lg ${colorConfig.bg} ${colorConfig.border}`}>
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm text-text-secondary uppercase tracking-wide">{label}</p>
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
      <h1 className="mb-8 text-2xl font-bold text-text-primary">Dashboard</h1>

      <TabTip
        title="Dashboard"
        description="Your command center. See project counts by status, total activities, resource utilization, and recent projects at a glance. Click any card to navigate to the details."
      />

      {/* Metrics Grid */}
      {isLoading ? (
        <div className="mb-8 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="h-32 animate-pulse rounded-xl bg-surface-hover/50" />
          ))}
        </div>
      ) : (
        <>
          {/* Project Status Metrics */}
          <div className="mb-8">
            <h2 className="mb-4 text-lg font-semibold text-text-primary">Project Status</h2>
            <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
              <MetricCard label="Planned" value={metrics?.plannedCount || 0} icon={FolderTree} color="text-accent" />
              <MetricCard label="Active" value={metrics?.activeCount || 0} icon={TrendingUp} color="text-success" />
              <MetricCard label="Completed" value={metrics?.completedCount || 0} icon={BarChart3} color="text-purple-400" />
              <MetricCard label="Resources" value={metrics?.resourceCount || 0} icon={Users} color="text-warning" />
            </div>
          </div>

          {/* Activity Summary */}
          <div className="mb-8">
            <h2 className="mb-4 text-lg font-semibold text-text-primary">Activity Summary</h2>
            <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
              <MetricCard
                label="Total Activities"
                value={metrics?.activities.totalActivities || 0}
                icon={Clock}
                color="text-accent"
              />
              <MetricCard
                label="Critical Activities"
                value={metrics?.activities.criticalActivities || 0}
                icon={AlertCircle}
                color="text-danger"
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
            <h2 className="mb-4 text-lg font-semibold text-text-primary">Recent Projects</h2>
            <div className="overflow-x-auto rounded-xl border border-border bg-surface/50 shadow-xl">
              <table className="w-full">
                <thead className="border-b border-border/50 bg-surface/80">
                  <tr>
                    <th className="px-6 py-3 text-left text-sm font-semibold text-text-secondary">Project Name</th>
                    <th className="px-6 py-3 text-left text-sm font-semibold text-text-secondary">Status</th>
                    <th className="px-6 py-3 text-left text-sm font-semibold text-text-secondary">Created</th>
                  </tr>
                </thead>
                <tbody>
                  {metrics?.recentProjects && metrics.recentProjects.length > 0 ? (
                    metrics.recentProjects.map((project) => (
                      <tr key={project.id} className="border-b border-border/50 hover:bg-surface-hover/30 transition-colors">
                        <td className="px-6 py-4 text-sm font-medium text-text-secondary">
                          <Link href={`/projects/${project.id}`} className="text-accent hover:text-blue-300">
                            {project.name}
                          </Link>
                        </td>
                        <td className="px-6 py-4 text-sm">
                          <span className={`inline-flex rounded-md px-2.5 py-1 text-xs font-medium ring-1 ${
                            project.status === "ACTIVE"
                              ? "bg-success/10 text-success ring-success/20"
                              : project.status === "PLANNED"
                                ? "bg-accent/10 text-accent ring-accent/20"
                                : "bg-surface-active/50 text-text-secondary ring-border/50"
                          }`}>
                            {project.status}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-sm text-text-secondary">
                          {new Date(project.createdAt).toLocaleDateString()}
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={3} className="px-6 py-4 text-center text-sm text-text-muted">
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
        <h2 className="mb-4 text-lg font-semibold text-text-primary">Quick Actions</h2>
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {navigationCards.map((card) => (
            <Link
              key={card.title}
              href={card.href}
              className="group rounded-xl border border-border bg-surface/40 p-6 shadow-lg hover:border-border hover:bg-surface-hover/50 transition-all"
            >
              <div className={`mb-4 inline-flex rounded-lg p-3 bg-accent/10`}>
                <card.icon size={24} className="text-accent" />
              </div>
              <h2 className="text-lg font-semibold text-text-primary group-hover:text-blue-300">
                {card.title}
              </h2>
              <p className="mt-1 text-sm text-text-muted">{card.description}</p>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}
