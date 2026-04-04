"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { dashboardApi } from "@/lib/api/dashboardApi";
import { projectApi } from "@/lib/api/projectApi";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";

interface Project {
  id: string;
  name: string;
  status: string;
  progress: number;
  budget: number;
  spent: number;
  risk?: {
    level: "LOW" | "MEDIUM" | "HIGH";
    description: string;
  };
}

export default function ExecutiveDashboardPage() {
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(
    null
  );

  const { data: dashboardConfig, isLoading: isLoadingConfig } = useQuery({
    queryKey: ["dashboard-config", "EXECUTIVE"],
    queryFn: () => dashboardApi.getDashboardByTier("EXECUTIVE"),
  });

  const { data: projectsData, isLoading: isLoadingProjects } = useQuery({
    queryKey: ["projects"],
    queryFn: () => projectApi.listProjects(),
  });

  const { data: projectKpis, isLoading: isLoadingKpis } = useQuery({
    queryKey: ["project-kpis", selectedProjectId],
    queryFn: () =>
      selectedProjectId
        ? dashboardApi.getProjectKpiSnapshots(selectedProjectId)
        : null,
    enabled: !!selectedProjectId,
  });

  const projects = projectsData?.data?.content ?? [];

  // Mock data for risks (would come from riskApi)
  const topRisks = [
    {
      id: "1",
      title: "Material supply delays",
      level: "HIGH",
      impact: "Schedule",
    },
    {
      id: "2",
      title: "Labor shortage",
      level: "HIGH",
      impact: "Resources",
    },
    {
      id: "3",
      title: "Weather impact",
      level: "MEDIUM",
      impact: "Schedule",
    },
    {
      id: "4",
      title: "Design changes",
      level: "MEDIUM",
      impact: "Cost",
    },
    {
      id: "5",
      title: "Equipment availability",
      level: "LOW",
      impact: "Schedule",
    },
  ];

  const getRiskColor = (level: string) => {
    switch (level) {
      case "HIGH":
        return "bg-red-100 text-red-800";
      case "MEDIUM":
        return "bg-yellow-100 text-yellow-800";
      case "LOW":
        return "bg-green-100 text-green-800";
      default:
        return "bg-gray-100 text-gray-800";
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case "GREEN":
        return "text-green-600";
      case "AMBER":
        return "text-yellow-600";
      case "RED":
        return "text-red-600";
      default:
        return "text-gray-600";
    }
  };

  if (isLoadingConfig) {
    return (
      <div className="flex items-center justify-center p-6">
        <div className="text-gray-500">Loading dashboard...</div>
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center gap-4">
        <Link href="/dashboards">
          <button className="rounded p-1 hover:bg-gray-100">
            <ArrowLeft size={20} />
          </button>
        </Link>
        <div>
          <h1 className="text-3xl font-bold text-gray-900">
            Executive Dashboard
          </h1>
          <p className="text-gray-600">
            Corridor-level overview and strategic metrics
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Projects Overview */}
        <div className="lg:col-span-2 space-y-6">
          <div className="rounded-lg border border-gray-200 bg-white p-6">
            <h2 className="mb-4 text-lg font-semibold text-gray-900">
              Project Portfolio
            </h2>
            {isLoadingProjects ? (
              <div className="text-center text-gray-500">
                Loading projects...
              </div>
            ) : projects.length === 0 ? (
              <div className="rounded-lg border border-dashed border-gray-300 py-8 text-center">
                <p className="text-gray-500">No projects available</p>
              </div>
            ) : (
              <div className="space-y-4">
                {projects.map((project: any) => {
                  const budgetUtilization = project.budget
                    ? (project.spent / project.budget) * 100
                    : 0;
                  const progressPercent = project.progress || 0;

                  return (
                    <div
                      key={project.id}
                      className="rounded-lg border border-gray-200 p-4 cursor-pointer hover:bg-gray-50 transition"
                      onClick={() => setSelectedProjectId(project.id)}
                    >
                      <div className="mb-3 flex items-start justify-between">
                        <div>
                          <h3 className="font-medium text-gray-900">
                            {project.name}
                          </h3>
                          <p className="text-sm text-gray-500">
                            {project.description}
                          </p>
                        </div>
                        <span
                          className={`inline-block rounded-full px-3 py-1 text-xs font-semibold ${getStatusColor(
                            project.status
                          )}`}
                        >
                          {project.status || "ACTIVE"}
                        </span>
                      </div>

                      <div className="space-y-3">
                        {/* Progress */}
                        <div>
                          <div className="mb-1 flex items-center justify-between">
                            <span className="text-xs font-medium text-gray-600">
                              Progress
                            </span>
                            <span className="text-xs font-semibold text-gray-900">
                              {progressPercent}%
                            </span>
                          </div>
                          <div className="h-2 w-full rounded-full bg-gray-200">
                            <div
                              className="h-2 rounded-full bg-blue-500 transition-all"
                              style={{ width: `${progressPercent}%` }}
                            />
                          </div>
                        </div>

                        {/* Budget */}
                        <div>
                          <div className="mb-1 flex items-center justify-between">
                            <span className="text-xs font-medium text-gray-600">
                              Budget Utilization
                            </span>
                            <span className="text-xs font-semibold text-gray-900">
                              ${project.spent?.toLocaleString()} / $
                              {project.budget?.toLocaleString()}
                            </span>
                          </div>
                          <div className="h-2 w-full rounded-full bg-gray-200">
                            <div
                              className={`h-2 rounded-full transition-all ${
                                budgetUtilization > 90
                                  ? "bg-red-500"
                                  : budgetUtilization > 75
                                    ? "bg-yellow-500"
                                    : "bg-green-500"
                              }`}
                              style={{ width: `${Math.min(budgetUtilization, 100)}%` }}
                            />
                          </div>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* Top Risks */}
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h2 className="mb-4 text-lg font-semibold text-gray-900">
            Top 5 Risks
          </h2>
          <div className="space-y-3">
            {topRisks.map((risk) => (
              <div key={risk.id} className="rounded-lg border border-gray-100 p-3">
                <div className="mb-2 flex items-start justify-between">
                  <span className="text-sm font-medium text-gray-900">
                    {risk.title}
                  </span>
                  <span
                    className={`inline-block rounded-full px-2 py-0.5 text-xs font-semibold ${getRiskColor(
                      risk.level
                    )}`}
                  >
                    {risk.level}
                  </span>
                </div>
                <p className="text-xs text-gray-500">Impact: {risk.impact}</p>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Project KPIs */}
      {selectedProjectId && (
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h2 className="mb-4 text-lg font-semibold text-gray-900">
            Project KPIs
          </h2>
          {isLoadingKpis ? (
            <div className="text-center text-gray-500">Loading KPIs...</div>
          ) : projectKpis && projectKpis.data && projectKpis.data.length > 0 ? (
            <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
              {projectKpis.data.map((kpi) => (
                <div
                  key={kpi.id}
                  className="rounded-lg border border-gray-200 p-4 text-center"
                >
                  <div className="mb-2 text-sm font-medium text-gray-600">
                    KPI {kpi.kpiDefinitionId}
                  </div>
                  <div
                    className={`text-2xl font-bold ${getStatusColor(
                      kpi.status
                    )}`}
                  >
                    {kpi.value.toFixed(2)}
                  </div>
                  <div
                    className={`mt-2 inline-block rounded-full px-2 py-1 text-xs font-semibold ${
                      kpi.status === "GREEN"
                        ? "bg-green-100 text-green-800"
                        : kpi.status === "AMBER"
                          ? "bg-yellow-100 text-yellow-800"
                          : "bg-red-100 text-red-800"
                    }`}
                  >
                    {kpi.status}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="rounded-lg border border-dashed border-gray-300 py-8 text-center">
              <p className="text-gray-500">No KPIs available for this project</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
