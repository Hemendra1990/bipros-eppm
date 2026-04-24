"use client";

import { useParams } from "next/navigation";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { stockApi } from "@/lib/api/materialCatalogueApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import type { MaterialStockRow, StockStatusTag } from "@/lib/types";

const TAG_COLORS: Record<StockStatusTag, string> = {
  OK: "bg-success/20 text-success",
  LOW: "bg-amber-500/20 text-warning",
  CRITICAL: "bg-danger/20 text-danger",
};

export default function StockRegisterPage() {
  const params = useParams<{ projectId: string }>();
  const projectId = params.projectId;

  const { data, isLoading } = useQuery({
    queryKey: ["stock-register", projectId],
    queryFn: () => stockApi.listByProject(projectId),
    enabled: !!projectId,
  });

  const rows = data?.data ?? [];

  const columns: ColumnDef<MaterialStockRow>[] = [
    { key: "materialCode", label: "Code", sortable: true },
    { key: "materialName", label: "Material" },
    { key: "openingStock", label: "Opening" },
    { key: "receivedMonth", label: "Received (Month)" },
    { key: "issuedMonth", label: "Issued (Month)" },
    {
      key: "currentStock",
      label: "Current Stock",
      render: (v, row) => {
        const tag = row.stockStatusTag;
        const colour =
          tag === "CRITICAL"
            ? "text-danger"
            : tag === "LOW"
              ? "text-warning"
              : "text-text-primary";
        return <span className={`font-semibold ${colour}`}>{String(v ?? 0)}</span>;
      },
    },
    { key: "minStockLevel", label: "Min Stock" },
    { key: "reorderQuantity", label: "Reorder Qty" },
    {
      key: "stockValue",
      label: "Stock Value (₹)",
      render: (v) => (v == null ? "—" : `₹${Number(v).toLocaleString("en-IN")}`),
    },
    {
      key: "stockStatusTag",
      label: "Status",
      render: (v) => {
        const s = v as StockStatusTag | null;
        if (!s) return "—";
        return (
          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${TAG_COLORS[s]}`}>
            {s}
          </span>
        );
      },
    },
    {
      key: "wastagePercent",
      label: "Wastage %",
      render: (v) => (v == null ? "—" : `${v}%`),
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Stock & Inventory Register"
        description="Real-time stock balances with automated OK / LOW / CRITICAL status tags."
        actions={
          <div className="flex gap-2">
            <Link
              href={`/projects/${projectId}/grns/new`}
              className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent/90"
            >
              Log GRN
            </Link>
            <Link
              href={`/projects/${projectId}/issues/new`}
              className="rounded-md border border-border bg-surface-hover px-4 py-2 text-sm font-medium text-text-primary hover:bg-surface"
            >
              Log Issue
            </Link>
          </div>
        }
      />

      {isLoading ? (
        <div className="text-text-secondary">Loading…</div>
      ) : rows.length === 0 ? (
        <EmptyState
          title="No stock transactions yet"
          description="Log a GRN for any material in the catalogue to start tracking stock."
        />
      ) : (
        <DataTable columns={columns} data={rows} rowKey="id" searchable searchPlaceholder="Search stock…" />
      )}
    </div>
  );
}
