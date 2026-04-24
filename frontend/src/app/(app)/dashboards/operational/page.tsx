"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { dashboardApi } from "@/lib/api/dashboardApi";
import { raBillApi } from "@/lib/api/raBillApi";
import { projectApi } from "@/lib/api/projectApi";
import {
  reportDataApi,
  type ResourceUtilRow,
  type WbsProgressRow,
} from "@/lib/api/reportDataApi";
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

interface ResourceUtilizationGroup {
  resourceType: string;
  allocated: number;
  utilized: number;
  percentage: number;
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

  const { data: resourceUtilizationRaw } = useQuery({
    queryKey: ["report-resource-utilization", selectedProjectId],
    queryFn: () =>
      selectedProjectId ? reportDataApi.getResourceUtilization(selectedProjectId) : null,
    enabled: !!selectedProjectId,
    retry: false,
  });

  const { data: wbsProgress } = useQuery({
    queryKey: ["project-wbs-progress", selectedProjectId],
    queryFn: () =>
      selectedProjectId ? reportDataApi.getWbsProgress(selectedProjectId) : null,
    enabled: !!selectedProjectId,
    retry: false,
  });

  const projects = projectsData?.data?.content ?? [];
  const raBills = Array.isArray(raBillsData?.data) ? raBillsData.data : [];

  // Aggregate per-resource rows into type-level totals — UI shows one bar per
  // resource category (Labor / Equipment / Material / Subcontractor).
  const resourceUtilization: ResourceUtilizationGroup[] = useMemo(() => {
    const rows = (resourceUtilizationRaw?.resources ?? []) as ResourceUtilRow[];
    const groups = new Map<string, { allocated: number; utilized: number }>();
    rows.forEach((r) => {
      const key = r.type || "Other";
      const g = groups.get(key) ?? { allocated: 0, utilized: 0 };
      g.allocated += r.plannedHours ?? 0;
      g.utilized += r.actualHours ?? 0;
      groups.set(key, g);
    });
    return Array.from(groups.entries()).map(([resourceType, g]) => ({
      resourceType,
      allocated: g.allocated,
      utilized: g.utilized,
      percentage: g.allocated > 0 ? (g.utilized / g.allocated) * 100 : 0,
    }));
  }, [resourceUtilizationRaw]);

  const wbsRows: WbsProgressRow[] = wbsProgress ?? [];

  const getStatusColor = (status: string) => {
    switch (status) {
      case "PAID":
        return "bg-success/10 text-success";
      case "APPROVED":
        return "bg-accent/10 text-blue-300";
      case "CERTIFIED":
        return "bg-purple-500/10 text-purple-400";
      case "SUBMITTED":
        return "bg-warning/10 text-warning";
      case "DRAFT":
        return "bg-surface-hover/50 text-text-primary";
      default:
        return "bg-surface-hover/50 text-text-primary";
    }
  };

  const getActivityStatusColor = (status: string) => {
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

  const getResourceColor = (percentage: number) => {
    if (percentage >= 90) return "bg-red-500";
    if (percentage >= 75) return "bg-amber-500";
    return "bg-success";
  };

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
          <button className="rounded p-1 hover:bg-surface-hover/50">
            <ArrowLeft size={20} />
          </button>
        </Link>
        <div>
          <h1 className="text-3xl font-bold text-text-primary">
            Operational Dashboard
          </h1>
          <p className="text-text-secondary">
            RA bills, resources, and WBS-level activity progress
          </p>
        </div>
      </div>

      {/* Project Selection */}
      <div className="rounded-lg border border-border bg-surface/50 p-6">
        <h2 className="mb-4 text-lg font-semibold text-text-primary">
          Select Project
        </h2>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-4">
          {projects.map((project: ProjectResponse) => (
            <button
              key={project.id}
              onClick={() => setSelectedProjectId(project.id)}
              className={`rounded-lg border-2 p-3 text-left transition ${
                selectedProjectId === project.id
                  ? "border-accent bg-accent/10"
                  : "border-border hover:border-blue-300"
              }`}
            >
              <div className="font-medium text-text-primary">{project.name}</div>
              <div className="text-xs text-text-muted">
                {project.description}
              </div>
            </button>
          ))}
        </div>
      </div>

      {selectedProjectId && (
        <>
          {/* RA Bills Status */}
          <div className="rounded-lg border border-border bg-surface/50 p-6">
            <h2 className="mb-4 text-lg font-semibold text-text-primary">
              RA Bills Status
            </h2>
            {isLoadingRaBills ? (
              <div className="text-center text-text-muted">
                Loading RA bills...
              </div>
            ) : raBills.length === 0 ? (
              <div className="rounded-lg border border-dashed border-border py-8 text-center">
                <p className="text-text-muted">No RA bills available</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead>
                    <tr className="border-b border-border">
                      <th className="px-4 py-3 text-left text-sm font-semibold text-text-primary">
                        Bill Number
                      </th>
                      <th className="px-4 py-3 text-left text-sm font-semibold text-text-primary">
                        Period
                      </th>
                      <th className="px-4 py-3 text-left text-sm font-semibold text-text-primary">
                        Amount
                      </th>
                      <th className="px-4 py-3 text-left text-sm font-semibold text-text-primary">
                        Status
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {raBills.map((bill: RaBillRow) => (
                      <tr key={bill.id} className="border-b border-border/50">
                        <td className="px-4 py-3 text-sm text-text-primary">
                          {bill.billNumber}
                        </td>
                        <td className="px-4 py-3 text-sm text-text-secondary">
                          {new Date(bill.billPeriodFrom).toLocaleDateString()} -{" "}
                          {new Date(bill.billPeriodTo).toLocaleDateString()}
                        </td>
                        <td className="px-4 py-3 text-sm font-medium text-text-primary">
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
          <div className="rounded-lg border border-border bg-surface/50 p-6">
            <h2 className="mb-4 text-lg font-semibold text-text-primary">
              Resource Utilization
            </h2>
            {resourceUtilization.length === 0 ? (
              <div className="rounded-lg border border-dashed border-border py-8 text-center">
                <p className="text-text-muted">No resource data for this project</p>
              </div>
            ) : (
              <div className="space-y-4">
                {resourceUtilization.map((resource) => (
                  <div key={resource.resourceType}>
                    <div className="mb-2 flex items-center justify-between">
                      <span className="font-medium text-text-primary">
                        {resource.resourceType}
                      </span>
                      <span className="text-sm text-text-secondary">
                        {resource.utilized.toFixed(0)} / {resource.allocated.toFixed(0)} (
                        {resource.percentage.toFixed(1)}%)
                      </span>
                    </div>
                    <div className="h-3 w-full rounded-full bg-surface-active/50">
                      <div
                        className={`h-3 rounded-full transition-all ${getResourceColor(
                          resource.percentage
                        )}`}
                        style={{ width: `${Math.min(100, resource.percentage)}%` }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Activity Progress by WBS */}
          <div className="rounded-lg border border-border bg-surface/50 p-6">
            <h2 className="mb-4 text-lg font-semibold text-text-primary">
              Activity Progress by WBS
            </h2>
            {wbsRows.length === 0 ? (
              <div className="rounded-lg border border-dashed border-border py-8 text-center">
                <p className="text-text-muted">No WBS nodes for this project</p>
              </div>
            ) : (
              <div className="space-y-4">
                {wbsRows.map((row) => {
                  const status =
                    row.actualPct >= 100
                      ? "COMPLETED"
                      : row.actualPct > 0
                        ? "IN_PROGRESS"
                        : "PENDING";
                  return (
                    <div
                      key={row.wbsCode}
                      className="rounded-lg border border-border p-4"
                    >
                      <div className="mb-3 flex items-start justify-between">
                        <div>
                          <h3 className="font-medium text-text-primary">
                            {row.wbsCode}
                          </h3>
                          <p className="text-sm text-text-secondary">
                            {row.wbsName}
                          </p>
                        </div>
                        <span
                          className={`inline-block rounded-full px-3 py-1 text-xs font-semibold ${getActivityStatusColor(
                            status
                          )}`}
                        >
                          {status}
                        </span>
                      </div>

                      <div className="space-y-2">
                        <div>
                          <div className="mb-1 flex items-center justify-between">
                            <span className="text-xs font-medium text-text-secondary">
                              Planned Progress
                            </span>
                            <span className="text-xs font-semibold text-text-primary">
                              {row.plannedPct.toFixed(1)}%
                            </span>
                          </div>
                          <div className="h-2 w-full rounded-full bg-surface-active/50">
                            <div
                              className="h-2 rounded-full bg-blue-500"
                              style={{ width: `${Math.min(100, row.plannedPct)}%` }}
                            />
                          </div>
                        </div>

                        <div>
                          <div className="mb-1 flex items-center justify-between">
                            <span className="text-xs font-medium text-text-secondary">
                              Actual Progress
                            </span>
                            <span className="text-xs font-semibold text-text-primary">
                              {row.actualPct.toFixed(1)}%
                            </span>
                          </div>
                          <div className="h-2 w-full rounded-full bg-surface-active/50">
                            <div
                              className={`h-2 rounded-full ${
                                row.actualPct >= row.plannedPct
                                  ? "bg-success"
                                  : "bg-amber-500"
                              }`}
                              style={{ width: `${Math.min(100, row.actualPct)}%` }}
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
        </>
      )}
    </div>
  );
}
