"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { materialCatalogueApi, materialIssueApi } from "@/lib/api/materialCatalogueApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import type { MaterialIssueResponse } from "@/lib/types";

export default function IssuesPage() {
  const params = useParams<{ projectId: string }>();
  const projectId = params.projectId;

  const { data, isLoading } = useQuery({
    queryKey: ["issues", projectId],
    queryFn: () => materialIssueApi.listByProject(projectId),
    enabled: !!projectId,
  });
  const { data: materials } = useQuery({
    queryKey: ["materials", projectId, "ALL"],
    queryFn: () => materialCatalogueApi.listByProject(projectId),
    enabled: !!projectId,
  });

  const matName = (id: string) =>
    materials?.data?.find((m) => m.id === id)?.name ?? id.slice(0, 8);

  const rows = data?.data ?? [];

  const columns: ColumnDef<MaterialIssueResponse>[] = [
    { key: "challanNumber", label: "Challan #", sortable: true },
    { key: "issueDate", label: "Date" },
    {
      key: "materialId",
      label: "Material",
      render: (v) => matName(v as string),
    },
    { key: "quantity", label: "Qty Issued" },
    { key: "wastageQuantity", label: "Wastage" },
    { key: "remarks", label: "Remarks" },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Material Issues"
        description="Issue challans — material released from stores to supervisors/activities."
        actions={
          <Link
            href={`/projects/${projectId}/issues/new`}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent/90"
          >
            New Issue
          </Link>
        }
      />

      {isLoading ? (
        <div className="text-text-secondary">Loading…</div>
      ) : rows.length === 0 ? (
        <EmptyState title="No issues yet" description="Log an issue when material leaves the store." />
      ) : (
        <DataTable columns={columns} data={rows} rowKey="id" searchable searchPlaceholder="Search issues…" />
      )}
    </div>
  );
}
