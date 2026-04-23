"use client";

import { useState, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { dashboardApi } from "@/lib/api/dashboardApi";
import { projectApi } from "@/lib/api/projectApi";
import { activityApi } from "@/lib/api/activityApi";
import { organisationApi } from "@/lib/api/organisationApi";
import { analyticsApi, type ContractorPerformance } from "@/lib/api/analyticsApi";
import { portfolioReportApi, type PortfolioEvmRow } from "@/lib/api/portfolioReportApi";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import type { ProjectResponse, ActivityResponse, OrganisationResponse } from "@/lib/types";

interface Milestone {
  id: string;
  name: string;
  plannedDate: string;
  actualDate?: string;
  status: string;
}

interface ContractorScorecard {
  id: string;
  name: string;
  // Scores are `null` until contractor-scorecard ingestion is wired — the UI
  // renders "n/a" badges in that case instead of fabricating numbers.
  performanceScore: number | null;
  safetyScore: number | null;
  complianceScore: number | null;
}

export default function ProgrammeDashboardPage() {
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(
    null
  );

  const { data: dashboardConfig, isLoading: isLoadingConfig } = useQuery({
    queryKey: ["dashboard-config", "PROGRAMME"],
    queryFn: () => dashboardApi.getDashboardByTier("PROGRAMME"),
  });

  const { data: projectsData } = useQuery({
    queryKey: ["projects"],
    queryFn: () => projectApi.listProjects(),
  });

  const { data: activitiesData } = useQuery({
    queryKey: ["activities", selectedProjectId],
    queryFn: () =>
      selectedProjectId ? activityApi.listActivities(selectedProjectId) : null,
    enabled: !!selectedProjectId,
    retry: 1,
  });

  const { data: epcContractorsData } = useQuery({
    queryKey: ["organisations", "EPC_CONTRACTOR"],
    queryFn: () => organisationApi.listByType("EPC_CONTRACTOR"),
    staleTime: 5 * 60_000,
  });

  // Real per-contractor performance/compliance from the new analytics endpoint.
  // Derived server-side from RA-bill satellite-gate PASS % + BG-validity %.
  const { data: contractorPerfData } = useQuery({
    queryKey: ["analytics", "contractor-performance"],
    queryFn: () => analyticsApi.getContractorPerformance(),
    staleTime: 60_000,
  });

  // Portfolio-level EVM rollup — one round-trip for every visible project, backed
  // by the latest EvmCalculation per project.
  const { data: evmRollupResponse } = useQuery({
    queryKey: ["portfolio", "evm-rollup"],
    queryFn: () => portfolioReportApi.getEvmRollup(),
    staleTime: 60_000,
  });
  const evmByProjectId = useMemo(() => {
    const map = new Map<string, PortfolioEvmRow>();
    (evmRollupResponse?.data ?? []).forEach((row) => map.set(row.projectId, row));
    return map;
  }, [evmRollupResponse]);

  const projects = projectsData?.data?.content ?? [];
  const activities = activitiesData?.data?.content ?? [];
  const epcContractors: OrganisationResponse[] = epcContractorsData?.data ?? [];
  const contractorPerfByCode = useMemo(() => {
    const map = new Map<string, ContractorPerformance>();
    (contractorPerfData ?? []).forEach((p) => map.set(p.orgCode, p));
    return map;
  }, [contractorPerfData]);

  // Convert activities to milestone format
  const mockMilestones: Milestone[] = activities
    .filter((a: ActivityResponse) => a.name?.toLowerCase()?.includes("milestone"))
    .slice(0, 4)
    .map((a: ActivityResponse) => ({
      id: a.id,
      name: a.name,
      plannedDate: a.plannedStartDate || "",
      actualDate: a.actualStartDate ?? undefined,
      status: a.status || "PENDING",
    }));

  // Fallback to mock milestones if no activities match
  const milestonesToDisplay: Milestone[] =
    mockMilestones.length > 0
      ? mockMilestones
      : [
          {
            id: "1",
            name: "Foundation Work Complete",
            plannedDate: "2025-03-31",
            actualDate: "2025-03-28",
            status: "COMPLETED",
          },
          {
            id: "2",
            name: "Structural Work 50%",
            plannedDate: "2025-06-30",
            actualDate: undefined,
            status: "IN_PROGRESS",
          },
          {
            id: "3",
            name: "MEP Installation Start",
            plannedDate: "2025-07-15",
            actualDate: undefined,
            status: "PENDING",
          },
          {
            id: "4",
            name: "Internal Finishes",
            plannedDate: "2025-09-30",
            actualDate: undefined,
            status: "PENDING",
          },
        ];

  // Real EPC contractors from /v1/organisations?type=EPC_CONTRACTOR, joined to
  // /v1/analytics/contractor-performance for derived scores. Performance comes
  // from satellite-gate PASS% on RA bills, compliance from BG validity%. Safety
  // stays null because safety-incident ingestion is not yet wired.
  const contractors: ContractorScorecard[] = epcContractors.map((o) => {
    const perf = contractorPerfByCode.get(o.code);
    return {
      id: o.id,
      name: o.shortName || o.name,
      performanceScore: perf?.performanceScore ?? null,
      safetyScore: perf?.safetyScore ?? null,
      complianceScore: perf?.complianceScore ?? null,
    };
  });

  const getMilestoneStatusColor = (status: string) => {
    switch (status) {
      case "COMPLETED":
        return "bg-success/10 text-success";
      case "IN_PROGRESS":
        return "bg-accent/10 text-blue-300";
      case "PENDING":
        return "bg-surface-hover/50 text-text-primary";
      default:
        return "bg-surface-hover/50 text-text-primary";
    }
  };

  const getScoreColor = (score: number | null) => {
    if (score == null) return "text-text-muted";
    if (score >= 90) return "text-success";
    if (score >= 80) return "text-accent";
    if (score >= 70) return "text-warning";
    return "text-danger";
  };

  const formatScore = (score: number | null) =>
    score == null ? "n/a" : `${score}%`;

  if (isLoadingConfig) {
    return (
      <div className="flex items-center justify-center p-6">
        <div className="text-text-muted">Loading dashboard...</div>
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center gap-4">
        <Link href="/dashboards">
          <button className="rounded p-1 hover:bg-surface-hover">
            <ArrowLeft size={20} />
          </button>
        </Link>
        <div>
          <h1 className="text-3xl font-bold text-text-primary">
            Programme Dashboard
          </h1>
          <p className="text-text-secondary">
            Earned Value Management and contractor performance
          </p>
        </div>
      </div>

      {/* EVM Metrics */}
      <div className="rounded-lg border border-border bg-surface/50 p-6">
        <h2 className="mb-4 text-lg font-semibold text-text-primary">
          Earned Value Metrics
        </h2>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
          {projects.map((project: ProjectResponse) => {
            const evm = evmByProjectId.get(project.id);
            return (
              <div
                key={project.id}
                className="rounded-lg border border-border bg-surface-hover/50 p-4 cursor-pointer hover:bg-surface-active/50 transition"
                onClick={() => setSelectedProjectId(project.id)}
              >
                <h3 className="mb-4 font-medium text-text-primary">
                  {project.name}
                </h3>
                <div className="space-y-3">
                  <div>
                    <div className="text-xs font-medium text-text-secondary">
                      Schedule Performance Index (SPI)
                    </div>
                    <div
                      className={`text-2xl font-bold ${
                        evm && evm.spi >= 0.95
                          ? "text-success"
                          : evm && evm.spi >= 0.9
                            ? "text-accent"
                            : "text-danger"
                      }`}
                    >
                      {evm ? evm.spi.toFixed(2) : "N/A"}
                    </div>
                  </div>
                  <div>
                    <div className="text-xs font-medium text-text-secondary">
                      Cost Performance Index (CPI)
                    </div>
                    <div
                      className={`text-2xl font-bold ${
                        evm && evm.cpi >= 0.95
                          ? "text-success"
                          : evm && evm.cpi >= 0.9
                            ? "text-accent"
                            : "text-danger"
                      }`}
                    >
                      {evm ? evm.cpi.toFixed(2) : "N/A"}
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Milestones */}
        <div className="rounded-lg border border-border bg-surface/50 p-6">
          <h2 className="mb-4 text-lg font-semibold text-text-primary">
            Milestone Tracker
          </h2>
          <div className="space-y-3">
            {milestonesToDisplay.map((milestone) => (
              <div
                key={milestone.id}
                className="rounded-lg border border-border bg-surface-hover/50 p-4"
              >
                <div className="mb-2 flex items-start justify-between">
                  <h3 className="font-medium text-text-primary">
                    {milestone.name}
                  </h3>
                  <span
                    className={`inline-block rounded-full px-2 py-1 text-xs font-semibold ${getMilestoneStatusColor(
                      milestone.status
                    )}`}
                  >
                    {milestone.status}
                  </span>
                </div>
                <div className="text-sm text-text-secondary">
                  Planned:{" "}
                  {new Date(milestone.plannedDate).toLocaleDateString()}
                  {milestone.actualDate &&
                    ` | Actual: ${new Date(milestone.actualDate).toLocaleDateString()}`}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Contractor Scorecards */}
        <div className="rounded-lg border border-border bg-surface/50 p-6">
          <h2 className="mb-4 text-lg font-semibold text-text-primary">
            Contractor Performance
          </h2>
          {contractors.length === 0 ? (
            <div className="rounded-lg border border-dashed border-border py-8 text-center">
              <p className="text-text-secondary">No EPC contractors registered</p>
            </div>
          ) : (
            <div className="space-y-4">
              {contractors.map((contractor) => (
                <div
                  key={contractor.id}
                  className="rounded-lg border border-border bg-surface-hover/50 p-4"
                >
                  <h3 className="mb-3 font-medium text-text-primary">{contractor.name}</h3>
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-medium text-text-secondary">
                        Performance
                      </span>
                      <span
                        className={`text-sm font-semibold ${getScoreColor(contractor.performanceScore)}`}
                      >
                        {formatScore(contractor.performanceScore)}
                      </span>
                    </div>
                    <div className="h-1.5 w-full rounded-full bg-surface-active">
                      <div
                        className="h-1.5 rounded-full bg-blue-500"
                        style={{ width: `${contractor.performanceScore ?? 0}%` }}
                      />
                    </div>

                    <div className="flex items-center justify-between">
                      <span className="text-xs font-medium text-text-secondary">
                        Safety
                      </span>
                      <span
                        className={`text-sm font-semibold ${getScoreColor(contractor.safetyScore)}`}
                      >
                        {formatScore(contractor.safetyScore)}
                      </span>
                    </div>
                    <div className="h-1.5 w-full rounded-full bg-surface-active">
                      <div
                        className="h-1.5 rounded-full bg-success"
                        style={{ width: `${contractor.safetyScore ?? 0}%` }}
                      />
                    </div>

                    <div className="flex items-center justify-between">
                      <span className="text-xs font-medium text-text-secondary">
                        Compliance
                      </span>
                      <span
                        className={`text-sm font-semibold ${getScoreColor(contractor.complianceScore)}`}
                      >
                        {formatScore(contractor.complianceScore)}
                      </span>
                    </div>
                    <div className="h-1.5 w-full rounded-full bg-surface-active">
                      <div
                        className="h-1.5 rounded-full bg-purple-500"
                        style={{ width: `${contractor.complianceScore ?? 0}%` }}
                      />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
