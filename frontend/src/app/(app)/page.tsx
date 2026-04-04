"use client";

import { FolderTree, Users, Calendar, BarChart3, TrendingUp, AlertCircle, Clock } from "lucide-react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";

const navigationCards = [
  {
    title: "Projects",
    description: "Manage enterprise project structure and schedules",
    href: "/projects",
    icon: FolderTree,
    color: "bg-blue-50 text-blue-700",
  },
  {
    title: "Resources",
    description: "Allocate and level resources across projects",
    href: "/resources",
    icon: Users,
    color: "bg-green-50 text-green-700",
  },
  {
    title: "Calendars",
    description: "Configure working time and holidays",
    href: "/admin/calendars",
    icon: Calendar,
    color: "bg-purple-50 text-purple-700",
  },
  {
    title: "Reports",
    description: "S-curves, EVM dashboards, and custom reports",
    href: "/reports",
    icon: BarChart3,
    color: "bg-amber-50 text-amber-700",
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
    const projectsResponse = await fetch("/v1/projects?page=0&size=100");
    const projectsData: ProjectsResponse = await projectsResponse.json();

    const projects = projectsData.data || [];

    const plannedCount = projects.filter((p) => p.status === "PLANNED").length;
    const activeCount = projects.filter((p) => p.status === "ACTIVE").length;
    const completedCount = projects.filter((p) => p.status === "COMPLETED").length;

    const recentProjects = projects
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
      .slice(0, 5);

    // Fetch resources count
    const resourcesResponse = await fetch("/v1/resources?page=0&size=1");
    const resourcesData = await resourcesResponse.json();
    const resourceCount = resourcesData.meta?.total || 0;

    // For now, use static activity data (would come from API in production)
    const activities: ActivityData = {
      totalActivities: 42,
      criticalActivities: 3,
      overdueCount: 1,
    };

    return {
      plannedCount,
      activeCount,
      completedCount,
      resourceCount,
      recentProjects,
      activities,
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

function MetricCard({ label, value, icon: Icon, color }: { label: string; value: number | string; icon: any; color: string }) {
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-gray-600">{label}</p>
          <p className={`mt-2 text-3xl font-bold ${color}`}>{value}</p>
        </div>
        <div className={`rounded-lg p-3 ${color.replace("text-", "bg-").replace("-700", "-50")}`}>
          <Icon size={24} className={color} />
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
      <h1 className="mb-8 text-2xl font-bold text-gray-900">Dashboard</h1>

      {/* Metrics Grid */}
      {isLoading ? (
        <div className="mb-8 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="h-32 animate-pulse rounded-lg bg-gray-200" />
          ))}
        </div>
      ) : (
        <>
          {/* Project Status Metrics */}
          <div className="mb-8">
            <h2 className="mb-4 text-lg font-semibold text-gray-900">Project Status</h2>
            <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
              <MetricCard label="Planned" value={metrics?.plannedCount || 0} icon={FolderTree} color="text-blue-700" />
              <MetricCard label="Active" value={metrics?.activeCount || 0} icon={TrendingUp} color="text-green-700" />
              <MetricCard label="Completed" value={metrics?.completedCount || 0} icon={BarChart3} color="text-purple-700" />
              <MetricCard label="Resources" value={metrics?.resourceCount || 0} icon={Users} color="text-amber-700" />
            </div>
          </div>

          {/* Activity Summary */}
          <div className="mb-8">
            <h2 className="mb-4 text-lg font-semibold text-gray-900">Activity Summary</h2>
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
                color="text-red-700"
              />
              <MetricCard
                label="Overdue"
                value={metrics?.activities.overdueCount || 0}
                icon={TrendingUp}
                color="text-orange-700"
              />
            </div>
          </div>

          {/* Recent Projects */}
          <div className="mb-8">
            <h2 className="mb-4 text-lg font-semibold text-gray-900">Recent Projects</h2>
            <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white shadow-sm">
              <table className="w-full">
                <thead className="border-b border-gray-200 bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Project Name</th>
                    <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Status</th>
                    <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Created</th>
                  </tr>
                </thead>
                <tbody>
                  {metrics?.recentProjects && metrics.recentProjects.length > 0 ? (
                    metrics.recentProjects.map((project) => (
                      <tr key={project.id} className="border-b border-gray-100 hover:bg-gray-50">
                        <td className="px-6 py-4 text-sm font-medium text-gray-900">
                          <Link href={`/projects/${project.id}`} className="text-blue-600 hover:underline">
                            {project.name}
                          </Link>
                        </td>
                        <td className="px-6 py-4 text-sm">
                          <span className={`inline-flex rounded-full px-3 py-1 text-xs font-medium ${
                            project.status === "ACTIVE"
                              ? "bg-green-50 text-green-700"
                              : project.status === "PLANNED"
                                ? "bg-blue-50 text-blue-700"
                                : "bg-gray-50 text-gray-700"
                          }`}>
                            {project.status}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-sm text-gray-600">
                          {new Date(project.createdAt).toLocaleDateString()}
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={3} className="px-6 py-4 text-center text-sm text-gray-600">
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
        <h2 className="mb-4 text-lg font-semibold text-gray-900">Quick Actions</h2>
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {navigationCards.map((card) => (
            <Link
              key={card.title}
              href={card.href}
              className="group rounded-lg border border-gray-200 bg-white p-6 shadow-sm transition-shadow hover:shadow-md"
            >
              <div className={`mb-4 inline-flex rounded-lg p-3 ${card.color}`}>
                <card.icon size={24} />
              </div>
              <h2 className="text-lg font-semibold text-gray-900 group-hover:text-blue-600">
                {card.title}
              </h2>
              <p className="mt-1 text-sm text-gray-500">{card.description}</p>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}
