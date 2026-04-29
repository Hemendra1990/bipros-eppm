"use client";

import { useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import {
  labourMasterApi,
  type LabourDesignationResponse,
} from "@/lib/api/labourMasterApi";
import { WorkerTable, WorkerDetailModal } from "@/components/labour-master";

export default function TablePage() {
  const search = useSearchParams();
  const projectId = search?.get("projectId") || undefined;
  const [open, setOpen] = useState<LabourDesignationResponse | null>(null);

  const deployments = useQuery({
    queryKey: ["labour-deployments", projectId],
    queryFn: () => labourMasterApi.deployments.listForProject(projectId!),
    enabled: !!projectId,
  });

  const rows: LabourDesignationResponse[] = useMemo(
    () =>
      (deployments.data?.data ?? []).map((d) => ({
        ...d.designation,
        deployment: {
          id: d.id,
          workerCount: d.workerCount,
          actualDailyRate: d.actualDailyRate,
          effectiveRate: d.effectiveRate,
          dailyCost: d.dailyCost,
          notes: d.notes,
        },
      })),
    [deployments.data]
  );

  if (!projectId) {
    return <p className="text-sm text-muted-foreground">Select a project (append <code>?projectId=...</code>).</p>;
  }
  if (deployments.isLoading) return <p>Loading…</p>;
  if (deployments.isError) return <p className="text-red-700">Failed to load labour deployments.</p>;
  return (
    <>
      <WorkerTable rows={rows} onRowClick={setOpen} />
      <WorkerDetailModal designation={open} onClose={() => setOpen(null)} />
    </>
  );
}
