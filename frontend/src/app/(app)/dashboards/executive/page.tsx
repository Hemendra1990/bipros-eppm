"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { dashboardApi } from "@/lib/api/dashboardApi";
import { projectApi } from "@/lib/api/projectApi";
import { riskApi } from "@/lib/api/riskApi";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import type { ProjectResponse } from "@/lib/types";
import type { RiskResponse } from "@/lib/api/riskApi";

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

  const { data: risksData, isLoading: isLoadingRisks } = useQuery({
    queryKey: ["risks"],
    queryFn: () => riskApi.listRisks(undefined, 0, 5),
    retry: 1,
  });

  const projects = projectsData?.data?.content ?? [];
  const topRisks = risksData?.data ?? [];

  const getRiskColor = (level: string) => {
    const normalizedLevel = level?.toUpperCase() || "";
    switch (normalizedLevel) {
      case "HIGH":
        return "bg-red-500/10 text-red-300";
      case "MEDIUM":
        return "bg-amber-500/10 text-amber-300";
      case "LOW":
        return "bg-emerald-500/10 text-emerald-300";
      default:
        return "bg-slate-800/50 text-slate-100";
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case "GREEN":
        return "text-emerald-400";
      case "AMBER":
        return "text-amber-400";
      case "RED":
        return "text-red-400";
      default:
        return "text-slate-400";
    }
  };

  if (isLoadingConfig) {
    return (
      <div className="flex items-center justify-center p-6">
        <div className="text-slate-500">Loading dashboard...</div>
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center gap-4">
        <Link href="/dashboards">
          <button className="rounded p-1 hover:bg-slate-800">
            <ArrowLeft size={20} />
          </button>
        </Link>
        <div>
          <h1 className="text-3xl font-bold text-white">
            Executive Dashboard
          </h1>
          <p className="text-slate-400">
            Corridor-level overview and strategic metrics
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Projects Overview */}
        <div className="lg:col-span-2 space-y-6">
          <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
            <h2 className="mb-4 text-lg font-semibold text-white">
              Project Portfolio
            </h2>
            {isLoadingProjects ? (
              <div className="text-center text-slate-400">
                Loading projects...
              </div>
            ) : projects.length === 0 ? (
              <div className="rounded-lg border border-dashed border-slate-700 py-8 text-center">
                <p className="text-slate-400">No projects available</p>
              </div>
            ) : (
              <div className="space-y-4">
                {projects.map((project: ProjectResponse) => {
                  const proj = project as ProjectResponse & { budget?: number; spent?: number; progress?: number };
                  const budgetUtilization = proj.budget
                    ? ((proj.spent ?? 0) / proj.budget) * 100
                    : 0;
                  const progressPercent = proj.progress || 0;

                  return (
                    <div
                      key={project.id}
                      className="rounded-lg border border-slate-800 bg-slate-800/50 p-4 cursor-pointer hover:bg-slate-700/50 transition"
                      onClick={() => setSelectedProjectId(project.id)}
                    >
                      <div className="mb-3 flex items-start justify-between">
                        <div>
                          <h3 className="font-medium text-white">
                            {project.name}
                          </h3>
                          <p className="text-sm text-slate-400">
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
                            <span className="text-xs font-medium text-slate-400">
                              Progress
                            </span>
                            <span className="text-xs font-semibold text-white">
                              {progressPercent}%
                            </span>
                          </div>
                          <div className="h-2 w-full rounded-full bg-slate-700">
                            <div
                              className="h-2 rounded-full bg-blue-500 transition-all"
                              style={{ width: `${progressPercent}%` }}
                            />
                          </div>
                        </div>

                        {/* Budget */}
                        <div>
                          <div className="mb-1 flex items-center justify-between">
                            <span className="text-xs font-medium text-slate-400">
                              Budget Utilization
                            </span>
                            <span className="text-xs font-semibold text-white">
                              ${proj.spent?.toLocaleString()} / $
                              {proj.budget?.toLocaleString()}
                            </span>
                          </div>
                          <div className="h-2 w-full rounded-full bg-slate-700">
                            <div
                              className={`h-2 rounded-full transition-all ${
                                budgetUtilization > 90
                                  ? "bg-red-500"
                                  : budgetUtilization > 75
                                    ? "bg-amber-500"
                                    : "bg-emerald-500"
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
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
          <h2 className="mb-4 text-lg font-semibold text-white">
            Top 5 Risks
          </h2>
          {isLoadingRisks ? (
            <div className="text-center text-slate-400">Loading risks...</div>
          ) : !topRisks || topRisks.length === 0 ? (
            <div className="rounded-lg border border-dashed border-slate-700 py-8 text-center">
              <p className="text-slate-400">No risks available</p>
            </div>
          ) : (
            <div className="space-y-3">
              {topRisks.map((risk: RiskResponse) => (
                <div key={risk.id} className="rounded-lg border border-slate-800 bg-slate-800/50 p-3">
                  <div className="mb-2 flex items-start justify-between">
                    <span className="text-sm font-medium text-white">
                      {risk.title}
                    </span>
                    <span
                      className={`inline-block rounded-full px-2 py-0.5 text-xs font-semibold ${getRiskColor(
                        risk.status
                      )}`}
                    >
                      {risk.status || "OPEN"}
                    </span>
                  </div>
                  <p className="text-xs text-slate-400">
                    {risk.description || "No description"}
                  </p>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Project KPIs */}
      {selectedProjectId && (
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
          <h2 className="mb-4 text-lg font-semibold text-white">
            Project KPIs
          </h2>
          {isLoadingKpis ? (
            <div className="text-center text-slate-400">Loading KPIs...</div>
          ) : projectKpis && projectKpis.data && projectKpis.data.length > 0 ? (
            <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
              {projectKpis.data.map((kpi) => (
                <div
                  key={kpi.id}
                  className="rounded-lg border border-slate-800 bg-slate-800/50 p-4 text-center"
                >
                  <div className="mb-2 text-sm font-medium text-slate-400">
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
                        ? "bg-emerald-500/10 text-emerald-300"
                        : kpi.status === "AMBER"
                          ? "bg-amber-500/10 text-amber-300"
                          : "bg-red-500/10 text-red-300"
                    }`}
                  >
                    {kpi.status}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="rounded-lg border border-dashed border-slate-700 py-8 text-center">
              <p className="text-slate-400">No KPIs available for this project</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
