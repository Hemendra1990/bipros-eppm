"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { dashboardApi } from "@/lib/api/dashboardApi";
import { projectApi } from "@/lib/api/projectApi";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";

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

  const projects = projectsData?.data?.content ?? [];

  // Mock EVM metrics
  const mockEvmMetrics: EvmMetrics[] = projects.map((p: any) => ({
    projectId: p.id,
    spi: 0.92 + Math.random() * 0.16,
    cpi: 0.88 + Math.random() * 0.2,
    currentPv: Math.random() * 1000000,
    currentEv: Math.random() * 950000,
  }));

  // Mock milestones
  const mockMilestones: Milestone[] = [
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
        return "bg-green-100 text-green-800";
      case "IN_PROGRESS":
        return "bg-blue-100 text-blue-800";
      case "PENDING":
        return "bg-gray-100 text-gray-800";
      default:
        return "bg-gray-100 text-gray-800";
    }
  };

  const getScoreColor = (score: number) => {
    if (score >= 90) return "text-green-600";
    if (score >= 80) return "text-blue-600";
    if (score >= 70) return "text-yellow-600";
    return "text-red-600";
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
            Programme Dashboard
          </h1>
          <p className="text-gray-600">
            Earned Value Management and contractor performance
          </p>
        </div>
      </div>

      {/* EVM Metrics */}
      <div className="rounded-lg border border-gray-200 bg-white p-6">
        <h2 className="mb-4 text-lg font-semibold text-gray-900">
          Earned Value Metrics
        </h2>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
          {projects.map((project: any) => {
            const evm = mockEvmMetrics.find((m) => m.projectId === project.id);
            return (
              <div
                key={project.id}
                className="rounded-lg border border-gray-200 p-4 cursor-pointer hover:bg-gray-50 transition"
                onClick={() => setSelectedProjectId(project.id)}
              >
                <h3 className="mb-4 font-medium text-gray-900">
                  {project.name}
                </h3>
                <div className="space-y-3">
                  <div>
                    <div className="text-xs font-medium text-gray-600">
                      Schedule Performance Index (SPI)
                    </div>
                    <div
                      className={`text-2xl font-bold ${
                        evm && evm.spi >= 0.95
                          ? "text-green-600"
                          : evm && evm.spi >= 0.9
                            ? "text-blue-600"
                            : "text-red-600"
                      }`}
                    >
                      {evm ? evm.spi.toFixed(2) : "N/A"}
                    </div>
                  </div>
                  <div>
                    <div className="text-xs font-medium text-gray-600">
                      Cost Performance Index (CPI)
                    </div>
                    <div
                      className={`text-2xl font-bold ${
                        evm && evm.cpi >= 0.95
                          ? "text-green-600"
                          : evm && evm.cpi >= 0.9
                            ? "text-blue-600"
                            : "text-red-600"
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
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h2 className="mb-4 text-lg font-semibold text-gray-900">
            Milestone Tracker
          </h2>
          <div className="space-y-3">
            {mockMilestones.map((milestone) => (
              <div
                key={milestone.id}
                className="rounded-lg border border-gray-200 p-4"
              >
                <div className="mb-2 flex items-start justify-between">
                  <h3 className="font-medium text-gray-900">
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
                <div className="text-sm text-gray-600">
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
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h2 className="mb-4 text-lg font-semibold text-gray-900">
            Contractor Performance
          </h2>
          <div className="space-y-4">
            {mockContractors.map((contractor) => (
              <div
                key={contractor.id}
                className="rounded-lg border border-gray-200 p-4"
              >
                <h3 className="mb-3 font-medium text-gray-900">
                  {contractor.name}
                </h3>
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-gray-600">
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
                  <div className="h-1.5 w-full rounded-full bg-gray-200">
                    <div
                      className="h-1.5 rounded-full bg-blue-500"
                      style={{
                        width: `${contractor.performanceScore}%`,
                      }}
                    />
                  </div>

                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-gray-600">
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
                  <div className="h-1.5 w-full rounded-full bg-gray-200">
                    <div
                      className="h-1.5 rounded-full bg-green-500"
                      style={{
                        width: `${contractor.safetyScore}%`,
                      }}
                    />
                  </div>

                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-gray-600">
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
                  <div className="h-1.5 w-full rounded-full bg-gray-200">
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
