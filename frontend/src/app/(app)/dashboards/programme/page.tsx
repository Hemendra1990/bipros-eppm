"use client";

import { useState, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { dashboardApi } from "@/lib/api/dashboardApi";
import { projectApi } from "@/lib/api/projectApi";
import { activityApi } from "@/lib/api/activityApi";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import type { ProjectResponse, ActivityResponse } from "@/lib/types";

interface EvmMetrics {
  projectId: string;
  spi: number;
  cpi: number;
  currentPv: number;
  currentEv: number;
}

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
  performanceScore: number;
  safetyScore: number;
  complianceScore: number;
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

  const projects = projectsData?.data?.content ?? [];
  const activities = activitiesData?.data?.content ?? [];

  // Mock EVM metrics — seeded by project index to avoid re-render instability
  const mockEvmMetrics: EvmMetrics[] = useMemo(
    () =>
      projects.map((p: ProjectResponse, i: number) => ({
        projectId: p.id,
        spi: 0.92 + ((i * 7 + 3) % 16) / 100,
        cpi: 0.88 + ((i * 11 + 5) % 20) / 100,
        currentPv: ((i * 13 + 7) % 100) * 10000,
        currentEv: ((i * 17 + 3) % 95) * 10000,
      })),
    [projects]
  );

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

  // Mock contractor scorecards
  const mockContractors: ContractorScorecard[] = [
    {
      id: "1",
      name: "ABC Construction",
      performanceScore: 92,
      safetyScore: 88,
      complianceScore: 95,
    },
    {
      id: "2",
      name: "XYZ Engineering",
      performanceScore: 85,
      safetyScore: 90,
      complianceScore: 82,
    },
    {
      id: "3",
      name: "BuildRight Solutions",
      performanceScore: 78,
      safetyScore: 75,
      complianceScore: 80,
    },
  ];

  const getMilestoneStatusColor = (status: string) => {
    switch (status) {
      case "COMPLETED":
        return "bg-emerald-500/10 text-emerald-300";
      case "IN_PROGRESS":
        return "bg-blue-500/10 text-blue-300";
      case "PENDING":
        return "bg-slate-800/50 text-slate-100";
      default:
        return "bg-slate-800/50 text-slate-100";
    }
  };

  const getScoreColor = (score: number) => {
    if (score >= 90) return "text-emerald-400";
    if (score >= 80) return "text-blue-400";
    if (score >= 70) return "text-amber-400";
    return "text-red-400";
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
            Programme Dashboard
          </h1>
          <p className="text-slate-400">
            Earned Value Management and contractor performance
          </p>
        </div>
      </div>

      {/* EVM Metrics */}
      <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
        <h2 className="mb-4 text-lg font-semibold text-white">
          Earned Value Metrics
        </h2>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
          {projects.map((project: ProjectResponse) => {
            const evm = mockEvmMetrics.find((m) => m.projectId === project.id);
            return (
              <div
                key={project.id}
                className="rounded-lg border border-slate-800 bg-slate-800/50 p-4 cursor-pointer hover:bg-slate-700/50 transition"
                onClick={() => setSelectedProjectId(project.id)}
              >
                <h3 className="mb-4 font-medium text-white">
                  {project.name}
                </h3>
                <div className="space-y-3">
                  <div>
                    <div className="text-xs font-medium text-slate-400">
                      Schedule Performance Index (SPI)
                    </div>
                    <div
                      className={`text-2xl font-bold ${
                        evm && evm.spi >= 0.95
                          ? "text-emerald-400"
                          : evm && evm.spi >= 0.9
                            ? "text-blue-400"
                            : "text-red-400"
                      }`}
                    >
                      {evm ? evm.spi.toFixed(2) : "N/A"}
                    </div>
                  </div>
                  <div>
                    <div className="text-xs font-medium text-slate-400">
                      Cost Performance Index (CPI)
                    </div>
                    <div
                      className={`text-2xl font-bold ${
                        evm && evm.cpi >= 0.95
                          ? "text-emerald-400"
                          : evm && evm.cpi >= 0.9
                            ? "text-blue-400"
                            : "text-red-400"
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
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
          <h2 className="mb-4 text-lg font-semibold text-white">
            Milestone Tracker
          </h2>
          <div className="space-y-3">
            {milestonesToDisplay.map((milestone) => (
              <div
                key={milestone.id}
                className="rounded-lg border border-slate-800 bg-slate-800/50 p-4"
              >
                <div className="mb-2 flex items-start justify-between">
                  <h3 className="font-medium text-white">
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
                <div className="text-sm text-slate-400">
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
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
          <h2 className="mb-4 text-lg font-semibold text-white">
            Contractor Performance
          </h2>
          <div className="space-y-4">
            {mockContractors.map((contractor) => (
              <div
                key={contractor.id}
                className="rounded-lg border border-slate-800 bg-slate-800/50 p-4"
              >
                <h3 className="mb-3 font-medium text-white">
                  {contractor.name}
                </h3>
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-slate-400">
                      Performance
                    </span>
                    <span
                      className={`text-sm font-semibold ${getScoreColor(
                        contractor.performanceScore
                      )}`}
                    >
                      {contractor.performanceScore}%
                    </span>
                  </div>
                  <div className="h-1.5 w-full rounded-full bg-slate-700">
                    <div
                      className="h-1.5 rounded-full bg-blue-500"
                      style={{
                        width: `${contractor.performanceScore}%`,
                      }}
                    />
                  </div>

                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-slate-400">
                      Safety
                    </span>
                    <span
                      className={`text-sm font-semibold ${getScoreColor(
                        contractor.safetyScore
                      )}`}
                    >
                      {contractor.safetyScore}%
                    </span>
                  </div>
                  <div className="h-1.5 w-full rounded-full bg-slate-700">
                    <div
                      className="h-1.5 rounded-full bg-emerald-500"
                      style={{
                        width: `${contractor.safetyScore}%`,
                      }}
                    />
                  </div>

                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-slate-400">
                      Compliance
                    </span>
                    <span
                      className={`text-sm font-semibold ${getScoreColor(
                        contractor.complianceScore
                      )}`}
                    >
                      {contractor.complianceScore}%
                    </span>
                  </div>
                  <div className="h-1.5 w-full rounded-full bg-slate-700">
                    <div
                      className="h-1.5 rounded-full bg-purple-500"
                      style={{
                        width: `${contractor.complianceScore}%`,
                      }}
                    />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
