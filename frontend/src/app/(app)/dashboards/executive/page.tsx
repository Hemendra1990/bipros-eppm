"use client";

import { useState } from "react";
import { useQueries, useQuery } from "@tanstack/react-query";
import { dashboardApi } from "@/lib/api/dashboardApi";
import { projectApi } from "@/lib/api/projectApi";
import { riskApi } from "@/lib/api/riskApi";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import type { ProjectResponse, WbsNodeResponse } from "@/lib/types";
import type { RiskRag, RiskResponse } from "@/lib/api/riskApi";

// IC-PMS budgets are in INR crores on WBS nodes. This dashboard only shows
// crore values directly, so no INR→crore conversion helper is needed here.
function formatCrores(crores: number): string {
  const rounded = Number.isFinite(crores) ? Math.round(crores * 100) / 100 : 0;
  return `₹${rounded.toLocaleString("en-IN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}cr`;
}

function pickTopLevelBudgetCrores(nodes: WbsNodeResponse[]): number {
  const roots = nodes.filter((n) => n.budgetCrores != null);
  if (roots.length > 0) return roots.reduce((s, n) => s + Number(n.budgetCrores ?? 0), 0);
  // Walk full tree if roots have no budget.
  let total = 0;
  for (const n of nodes) {
    if (n.budgetCrores != null) total += Number(n.budgetCrores);
    if (n.children?.length) total += pickTopLevelBudgetCrores(n.children);
  }
  return total;
}

function pickRootPercentComplete(nodes: WbsNodeResponse[]): number {
  // Prefer the first root's summary percent complete — it's the whole-project roll-up.
  for (const n of nodes) {
    if (n.summaryPercentComplete != null) return Number(n.summaryPercentComplete);
  }
  return 0;
}

// Ordering of RAG severity — CRIMSON is most severe, OPPORTUNITY is positive.
const RAG_SEVERITY: Record<RiskRag, number> = {
  CRIMSON: 5,
  RED: 4,
  AMBER: 3,
  GREEN: 2,
  OPPORTUNITY: 1,
};

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

  // Pull WBS trees per project so we can show real progress + budget utilisation.
  const wbsQueries = useQueries({
    queries: projects.map((p: ProjectResponse) => ({
      queryKey: ["wbs", p.id],
      queryFn: () => projectApi.getWbsTree(p.id),
      staleTime: 60_000,
    })),
  });

  const wbsByProjectId = new Map<string, WbsNodeResponse[]>();
  projects.forEach((p: ProjectResponse, i: number) => {
    const resp = wbsQueries[i]?.data;
    if (resp?.data) wbsByProjectId.set(p.id, resp.data);
  });

  // Aggregate risks across all projects: each project has its own /risks endpoint.
  const riskQueries = useQueries({
    queries: projects.map((p: ProjectResponse) => ({
      queryKey: ["risks", p.id],
      queryFn: () => riskApi.getRisksByProject(p.id),
      staleTime: 60_000,
    })),
  });

  const isLoadingRisks = riskQueries.some((q) => q.isLoading);
  const allRisks: RiskResponse[] = riskQueries.flatMap((q) => q.data?.data ?? []);

  // Top 5 risks: sort by RAG severity then by residual risk score descending.
  const topRisks = [...allRisks]
    .sort((a, b) => {
      const ragDiff = (RAG_SEVERITY[b.rag] ?? 0) - (RAG_SEVERITY[a.rag] ?? 0);
      if (ragDiff !== 0) return ragDiff;
      return (b.residualRiskScore ?? b.riskScore ?? 0) - (a.residualRiskScore ?? a.riskScore ?? 0);
    })
    .slice(0, 5);

  const getRagColor = (rag: RiskRag | undefined) => {
    switch (rag) {
      case "CRIMSON":
        return "bg-rose-700/20 text-rose-200";
      case "RED":
        return "bg-red-500/10 text-red-300";
      case "AMBER":
        return "bg-amber-500/10 text-amber-300";
      case "GREEN":
        return "bg-emerald-500/10 text-emerald-300";
      case "OPPORTUNITY":
        return "bg-sky-500/10 text-sky-300";
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
                  const wbs = wbsByProjectId.get(project.id) ?? [];
                  const budgetCrores = pickTopLevelBudgetCrores(wbs);
                  const progressPercent = Math.round(pickRootPercentComplete(wbs));
                  // Spent = budget * percent complete, a planned-value approximation
                  // when no expense ledger exists. Good enough for executive view.
                  const spentCrores = (budgetCrores * progressPercent) / 100;
                  const budgetUtilization = budgetCrores > 0 ? (spentCrores / budgetCrores) * 100 : 0;

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
                              {formatCrores(spentCrores)} / {formatCrores(budgetCrores)}
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
                  <div className="mb-2 flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <div className="text-[10px] uppercase tracking-wide text-slate-500">
                        {risk.code}
                      </div>
                      <span className="text-sm font-medium text-white">
                        {risk.title}
                      </span>
                    </div>
                    <span
                      className={`inline-block shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold ${getRagColor(
                        risk.rag
                      )}`}
                    >
                      {risk.rag || "—"}
                    </span>
                  </div>
                  <p className="text-xs text-slate-400 line-clamp-2">
                    {risk.description || "No description"}
                  </p>
                  {risk.residualRiskScore != null && (
                    <div className="mt-2 text-[11px] text-slate-500">
                      Residual score: {Number(risk.residualRiskScore).toFixed(1)}
                    </div>
                  )}
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
