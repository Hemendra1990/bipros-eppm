"use client";

import { useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { labourMasterApi } from "@/lib/api/labourMasterApi";
import {
  KpiTiles,
  WorkforceCategoryBarChart,
  WorkforceSummaryTable,
} from "@/components/labour-master";

export default function LabourMasterDashboardPage() {
  const search = useSearchParams();
  const projectId = search?.get("projectId") || undefined;

  const dashboardQuery = useQuery({
    queryKey: ["labour-deployments-dashboard", projectId],
    queryFn: () => labourMasterApi.deployments.getDashboard(projectId!),
    enabled: !!projectId,
  });

  if (!projectId) {
    return (
      <p className="text-sm text-muted-foreground">
        Select a project to view the labour dashboard. Append <code>?projectId=...</code> to the URL.
      </p>
    );
  }
  if (dashboardQuery.isLoading) return <p>Loading…</p>;
  if (dashboardQuery.isError) {
    return <p className="text-red-700">Failed to load dashboard.</p>;
  }
  const summary = dashboardQuery.data!.data!;
  return (
    <div className="space-y-6">
      <KpiTiles summary={summary} />
      <WorkforceCategoryBarChart rows={summary.byCategory} />
      <WorkforceSummaryTable rows={summary.byCategory} />
    </div>
  );
}
