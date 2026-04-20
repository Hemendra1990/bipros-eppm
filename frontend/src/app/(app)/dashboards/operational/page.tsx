"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { dashboardApi } from "@/lib/api/dashboardApi";
import { raBillApi } from "@/lib/api/raBillApi";
import { projectApi } from "@/lib/api/projectApi";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import type { ProjectResponse } from "@/lib/types";
import { formatDefaultCurrency } from "@/lib/hooks/useCurrency";

interface RaBillRow {
  id: string;
  billNumber: string;
  billPeriodFrom: string;
  billPeriodTo: string;
  netAmount: number;
  status: string;
}

interface ResourceUtilization {
  resourceType: string;
  allocated: number;
  utilized: number;
  percentage: number;
}

interface ActivityProgress {
  wbsCode: string;
  description: string;
  plannedProgress: number;
  actualProgress: number;
  status: string;
}

export default function OperationalDashboardPage() {
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(
    null
  );

  const { data: dashboardConfig, isLoading: isLoadingConfig } = useQuery({
    queryKey: ["dashboard-config", "OPERATIONAL"],
    queryFn: () => dashboardApi.getDashboardByTier("OPERATIONAL"),
  });

  const { data: projectsData } = useQuery({
    queryKey: ["projects"],
    queryFn: () => projectApi.listProjects(),
  });

  const { data: raBillsData, isLoading: isLoadingRaBills } = useQuery({
    queryKey: ["ra-bills", selectedProjectId],
    queryFn: () =>
      selectedProjectId ? raBillApi.getRaBillsByProject(selectedProjectId) : null,
    enabled: !!selectedProjectId,
  });

  const projects = projectsData?.data?.content ?? [];
  const raBills = Array.isArray(raBillsData?.data) ? raBillsData.data : [];

  // Mock resource utilization data
  const mockResourceUtilization: ResourceUtilization[] = [
    {
      resourceType: "Labor",
      allocated: 150,
      utilized: 142,
      percentage: 94.7,
    },
    {
      resourceType: "Equipment",
      allocated: 45,
      utilized: 38,
      percentage: 84.4,
    },
    {
      resourceType: "Materials",
      allocated: 200,
      utilized: 175,
      percentage: 87.5,
    },
    {
      resourceType: "Subcontractors",
      allocated: 12,
      utilized: 10,
      percentage: 83.3,
    },
  ];

  // Mock activity progress by WBS
  const mockActivityProgress: ActivityProgress[] = [
    {
      wbsCode: "1.1.1",
      description: "Site Preparation",
      plannedProgress: 100,
      actualProgress: 100,
      status: "COMPLETED",
    },
    {
      wbsCode: "1.2.1",
      description: "Foundation",
      plannedProgress: 85,
      actualProgress: 82,
      status: "IN_PROGRESS",
    },
    {
      wbsCode: "1.3.1",
      description: "Structural Steel",
      plannedProgress: 60,
      actualProgress: 55,
      status: "IN_PROGRESS",
    },
    {
      wbsCode: "1.4.1",
      description: "Concrete Work",
      plannedProgress: 45,
      actualProgress: 40,
      status: "IN_PROGRESS",
    },
    {
      wbsCode: "1.5.1",
      description: "MEP Installation",
      plannedProgress: 20,
      actualProgress: 10,
      status: "PENDING",
    },
  ];

  const getStatusColor = (status: string) => {
    switch (status) {
      case "PAID":
        return "bg-emerald-500/10 text-emerald-300";
      case "APPROVED":
        return "bg-blue-500/10 text-blue-300";
      case "CERTIFIED":
        return "bg-purple-500/10 text-purple-300";
      case "SUBMITTED":
        return "bg-amber-500/10 text-amber-300";
      case "DRAFT":
        return "bg-slate-800/50 text-slate-100";
      default:
        return "bg-slate-800/50 text-slate-100";
    }
  };

  const getActivityStatusColor = (status: string) => {
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

  const getResourceColor = (percentage: number) => {
    if (percentage >= 90) return "bg-red-500";
    if (percentage >= 75) return "bg-amber-500";
    return "bg-emerald-500";
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
          <button className="rounded p-1 hover:bg-slate-800/50">
            <ArrowLeft size={20} />
          </button>
        </Link>
        <div>
          <h1 className="text-3xl font-bold text-white">
            Operational Dashboard
          </h1>
          <p className="text-slate-400">
            RA bills, resources, and WBS-level activity progress
          </p>
        </div>
      </div>

      {/* Project Selection */}
      <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
        <h2 className="mb-4 text-lg font-semibold text-white">
          Select Project
        </h2>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-4">
          {projects.map((project: ProjectResponse) => (
            <button
              key={project.id}
              onClick={() => setSelectedProjectId(project.id)}
              className={`rounded-lg border-2 p-3 text-left transition ${
                selectedProjectId === project.id
                  ? "border-blue-500 bg-blue-500/10"
                  : "border-slate-800 hover:border-blue-300"
              }`}
            >
              <div className="font-medium text-white">{project.name}</div>
              <div className="text-xs text-slate-500">
                {project.description}
              </div>
            </button>
          ))}
        </div>
      </div>

      {selectedProjectId && (
        <>
          {/* RA Bills Status */}
          <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
            <h2 className="mb-4 text-lg font-semibold text-white">
              RA Bills Status
            </h2>
            {isLoadingRaBills ? (
              <div className="text-center text-slate-500">
                Loading RA bills...
              </div>
            ) : raBills.length === 0 ? (
              <div className="rounded-lg border border-dashed border-slate-700 py-8 text-center">
                <p className="text-slate-500">No RA bills available</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead>
                    <tr className="border-b border-slate-800">
                      <th className="px-4 py-3 text-left text-sm font-semibold text-white">
                        Bill Number
                      </th>
                      <th className="px-4 py-3 text-left text-sm font-semibold text-white">
                        Period
                      </th>
                      <th className="px-4 py-3 text-left text-sm font-semibold text-white">
                        Amount
                      </th>
                      <th className="px-4 py-3 text-left text-sm font-semibold text-white">
                        Status
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {raBills.map((bill: RaBillRow) => (
                      <tr key={bill.id} className="border-b border-slate-800/50">
                        <td className="px-4 py-3 text-sm text-white">
                          {bill.billNumber}
                        </td>
                        <td className="px-4 py-3 text-sm text-slate-400">
                          {new Date(bill.billPeriodFrom).toLocaleDateString()} -{" "}
                          {new Date(bill.billPeriodTo).toLocaleDateString()}
                        </td>
                        <td className="px-4 py-3 text-sm font-medium text-white">
                          {formatDefaultCurrency(bill.netAmount)}
                        </td>
                        <td className="px-4 py-3 text-sm">
                          <span
                            className={`inline-block rounded-full px-3 py-1 text-xs font-semibold ${getStatusColor(
                              bill.status
                            )}`}
                          >
                            {bill.status}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Resource Utilization */}
          <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
            <h2 className="mb-4 text-lg font-semibold text-white">
              Resource Utilization
            </h2>
            <div className="space-y-4">
              {mockResourceUtilization.map((resource) => (
                <div key={resource.resourceType}>
                  <div className="mb-2 flex items-center justify-between">
                    <span className="font-medium text-white">
                      {resource.resourceType}
                    </span>
                    <span className="text-sm text-slate-400">
                      {resource.utilized} / {resource.allocated} (
                      {resource.percentage.toFixed(1)}%)
                    </span>
                  </div>
                  <div className="h-3 w-full rounded-full bg-slate-700/50">
                    <div
                      className={`h-3 rounded-full transition-all ${getResourceColor(
                        resource.percentage
                      )}`}
                      style={{ width: `${resource.percentage}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Activity Progress by WBS */}
          <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
            <h2 className="mb-4 text-lg font-semibold text-white">
              Activity Progress by WBS
            </h2>
            <div className="space-y-4">
              {mockActivityProgress.map((activity) => (
                <div
                  key={activity.wbsCode}
                  className="rounded-lg border border-slate-800 p-4"
                >
                  <div className="mb-3 flex items-start justify-between">
                    <div>
                      <h3 className="font-medium text-white">
                        {activity.wbsCode}
                      </h3>
                      <p className="text-sm text-slate-400">
                        {activity.description}
                      </p>
                    </div>
                    <span
                      className={`inline-block rounded-full px-3 py-1 text-xs font-semibold ${getActivityStatusColor(
                        activity.status
                      )}`}
                    >
                      {activity.status}
                    </span>
                  </div>

                  <div className="space-y-2">
                    <div>
                      <div className="mb-1 flex items-center justify-between">
                        <span className="text-xs font-medium text-slate-400">
                          Planned Progress
                        </span>
                        <span className="text-xs font-semibold text-white">
                          {activity.plannedProgress}%
                        </span>
                      </div>
                      <div className="h-2 w-full rounded-full bg-slate-700/50">
                        <div
                          className="h-2 rounded-full bg-blue-500"
                          style={{ width: `${activity.plannedProgress}%` }}
                        />
                      </div>
                    </div>

                    <div>
                      <div className="mb-1 flex items-center justify-between">
                        <span className="text-xs font-medium text-slate-400">
                          Actual Progress
                        </span>
                        <span className="text-xs font-semibold text-white">
                          {activity.actualProgress}%
                        </span>
                      </div>
                      <div className="h-2 w-full rounded-full bg-slate-700/50">
                        <div
                          className={`h-2 rounded-full ${
                            activity.actualProgress >= activity.plannedProgress
                              ? "bg-emerald-500"
                              : "bg-amber-500"
                          }`}
                          style={{ width: `${activity.actualProgress}%` }}
                        />
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
