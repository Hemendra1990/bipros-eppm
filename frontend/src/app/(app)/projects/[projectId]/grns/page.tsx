"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { grnApi, materialCatalogueApi } from "@/lib/api/materialCatalogueApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import type { GoodsReceiptResponse } from "@/lib/types";

export default function GrnsPage() {
  const params = useParams<{ projectId: string }>();
  const projectId = params.projectId;

  const { data, isLoading } = useQuery({
    queryKey: ["grns", projectId],
    queryFn: () => grnApi.listByProject(projectId),
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

  const columns: ColumnDef<GoodsReceiptResponse>[] = [
    { key: "grnNumber", label: "GRN #", sortable: true },
    { key: "receivedDate", label: "Date" },
    {
      key: "materialId",
      label: "Material",
      render: (v) => matName(v as string),
    },
    { key: "quantity", label: "Qty" },
    {
      key: "unitRate",
      label: "Unit Rate",
      render: (v) => (v == null ? "—" : `₹${Number(v).toLocaleString("en-IN")}`),
    },
    {
      key: "amount",
      label: "Amount",
      render: (v) => (v == null ? "—" : `₹${Number(v).toLocaleString("en-IN")}`),
    },
    { key: "poNumber", label: "PO #" },
    { key: "vehicleNumber", label: "Vehicle" },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Goods Receipt Notes (GRN)"
        description="Inward receipt entries for material stock."
        actions={
          <Link
            href={`/projects/${projectId}/grns/new`}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent/90"
          >
            New GRN
          </Link>
        }
      />

      {isLoading ? (
        <div className="text-text-secondary">Loading…</div>
      ) : rows.length === 0 ? (
        <EmptyState title="No GRNs logged yet" description="Record a GRN when material arrives on site." />
      ) : (
        <DataTable columns={columns} data={rows} rowKey="id" searchable searchPlaceholder="Search GRNs…" />
      )}
    </div>
  );
}
