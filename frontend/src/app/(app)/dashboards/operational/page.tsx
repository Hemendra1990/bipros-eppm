"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { dashboardApi } from "@/lib/api/dashboardApi";
import { raBillApi } from "@/lib/api/raBillApi";
import { projectApi } from "@/lib/api/projectApi";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";

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
  const raBills = raBillsData?.data ?? [];

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
        return "bg-green-100 text-green-800";
      case "APPROVED":
        return "bg-blue-100 text-blue-800";
      case "CERTIFIED":
        return "bg-purple-100 text-purple-800";
      case "SUBMITTED":
        return "bg-yellow-100 text-yellow-800";
      case "DRAFT":
        return "bg-gray-100 text-gray-800";
      default:
        return "bg-gray-100 text-gray-800";
    }
  };

  const getActivityStatusColor = (status: string) => {
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

  const getResourceColor = (percentage: number) => {
    if (percentage >= 90) return "bg-red-500";
    if (percentage >= 75) return "bg-yellow-500";
    return "bg-green-500";
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
            Operational Dashboard
          </h1>
          <p className="text-gray-600">
            RA bills, resources, and WBS-level activity progress
          </p>
        </div>
      </div>

      {/* Project Selection */}
      <div className="rounded-lg border border-gray-200 bg-white p-6">
        <h2 className="mb-4 text-lg font-semibold text-gray-900">
          Select Project
        </h2>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-4">
          {projects.map((project: any) => (
            <button
              key={project.id}
              onClick={() => setSelectedProjectId(project.id)}
              className={`rounded-lg border-2 p-3 text-left transition ${
                selectedProjectId === project.id
                  ? "border-blue-500 bg-blue-50"
                  : "border-gray-200 hover:border-blue-300"
              }`}
            >
              <div className="font-medium text-gray-900">{project.name}</div>
              <div className="text-xs text-gray-500">
                {project.description}
              </div>
            </button>
          ))}
        </div>
      </div>

      {selectedProjectId && (
        <>
          {/* RA Bills Status */}
          <div className="rounded-lg border border-gray-200 bg-white p-6">
            <h2 className="mb-4 text-lg font-semibold text-gray-900">
              RA Bills Status
            </h2>
            {isLoadingRaBills ? (
              <div className="text-center text-gray-500">
                Loading RA bills...
              </div>
            ) : raBills.length === 0 ? (
              <div className="rounded-lg border border-dashed border-gray-300 py-8 text-center">
                <p className="text-gray-500">No RA bills available</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead>
                    <tr className="border-b border-gray-200">
                      <th className="px-4 py-3 text-left text-sm font-semibold text-gray-900">
                        Bill Number
                      </th>
                      <th className="px-4 py-3 text-left text-sm font-semibold text-gray-900">
                        Period
                      </th>
                      <th className="px-4 py-3 text-left text-sm font-semibold text-gray-900">
                        Amount
                      </th>
                      <th className="px-4 py-3 text-left text-sm font-semibold text-gray-900">
                        Status
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {raBills.map((bill: RaBillRow) => (
                      <tr key={bill.id} className="border-b border-gray-100">
                        <td className="px-4 py-3 text-sm text-gray-900">
                          {bill.billNumber}
                        </td>
                        <td className="px-4 py-3 text-sm text-gray-600">
                          {new Date(bill.billPeriodFrom).toLocaleDateString()} -{" "}
                          {new Date(bill.billPeriodTo).toLocaleDateString()}
                        </td>
                        <td className="px-4 py-3 text-sm font-medium text-gray-900">
                          ${bill.netAmount.toLocaleString("en-US", {
                            minimumFractionDigits: 2,
                            maximumFractionDigits: 2,
                          })}
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
          <div className="rounded-lg border border-gray-200 bg-white p-6">
            <h2 className="mb-4 text-lg font-semibold text-gray-900">
              Resource Utilization
            </h2>
            <div className="space-y-4">
              {mockResourceUtilization.map((resource) => (
                <div key={resource.resourceType}>
                  <div className="mb-2 flex items-center justify-between">
                    <span className="font-medium text-gray-900">
                      {resource.resourceType}
                    </span>
                    <span className="text-sm text-gray-600">
                      {resource.utilized} / {resource.allocated} (
                      {resource.percentage.toFixed(1)}%)
                    </span>
                  </div>
                  <div className="h-3 w-full rounded-full bg-gray-200">
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
          <div className="rounded-lg border border-gray-200 bg-white p-6">
            <h2 className="mb-4 text-lg font-semibold text-gray-900">
              Activity Progress by WBS
            </h2>
            <div className="space-y-4">
              {mockActivityProgress.map((activity) => (
                <div
                  key={activity.wbsCode}
                  className="rounded-lg border border-gray-200 p-4"
                >
                  <div className="mb-3 flex items-start justify-between">
                    <div>
                      <h3 className="font-medium text-gray-900">
                        {activity.wbsCode}
                      </h3>
                      <p className="text-sm text-gray-600">
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
                        <span className="text-xs font-medium text-gray-600">
                          Planned Progress
                        </span>
                        <span className="text-xs font-semibold text-gray-900">
                          {activity.plannedProgress}%
                        </span>
                      </div>
                      <div className="h-2 w-full rounded-full bg-gray-200">
                        <div
                          className="h-2 rounded-full bg-blue-500"
                          style={{ width: `${activity.plannedProgress}%` }}
                        />
                      </div>
                    </div>

                    <div>
                      <div className="mb-1 flex items-center justify-between">
                        <span className="text-xs font-medium text-gray-600">
                          Actual Progress
                        </span>
                        <span className="text-xs font-semibold text-gray-900">
                          {activity.actualProgress}%
                        </span>
                      </div>
                      <div className="h-2 w-full rounded-full bg-gray-200">
                        <div
                          className={`h-2 rounded-full ${
                            activity.actualProgress >= activity.plannedProgress
                              ? "bg-green-500"
                              : "bg-yellow-500"
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
