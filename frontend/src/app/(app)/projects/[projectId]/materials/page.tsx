"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import { materialCatalogueApi } from "@/lib/api/materialCatalogueApi";
import { VirtualDataTable } from "@/components/common/VirtualDataTable";
import type { ColumnDef } from "@tanstack/react-table";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { useAuthStore } from "@/lib/state/store";
import { useMounted } from "@/lib/hooks/useMounted";
import type { MaterialCategory, MaterialResponse, MaterialStatus } from "@/lib/types";

const CATEGORY_OPTIONS: { value: MaterialCategory | "ALL"; label: string }[] = [
  { value: "ALL", label: "All Categories" },
  { value: "BITUMINOUS", label: "Bituminous" },
  { value: "AGGREGATE", label: "Aggregate" },
  { value: "CEMENT", label: "Cement" },
  { value: "STEEL", label: "Steel" },
  { value: "GRANULAR", label: "Granular" },
  { value: "SAND", label: "Sand" },
  { value: "PRECAST", label: "Precast" },
  { value: "ROAD_MARKING", label: "Road Marking" },
];

const STATUS_COLORS: Record<MaterialStatus, string> = {
  ACTIVE: "bg-success/20 text-success",
  INACTIVE: "bg-slate-500/20 text-slate-300",
  DISCONTINUED: "bg-danger/20 text-danger",
};

export default function MaterialsPage() {
  const params = useParams<{ projectId: string }>();
  const projectId = params.projectId;
  const router = useRouter();
  const queryClient = useQueryClient();
  const [category, setCategory] = useState<MaterialCategory | "ALL">("ALL");
  const [confirmId, setConfirmId] = useState<string | null>(null);
  // Storekeeper round (2026-08-19): writes are STORE.UPDATE, catalogue delete
  // STORE.DELETE — hide (never disable) controls the backend would 403.
  const mounted = useMounted();
  const hasPermission = useAuthStore((s) => s.hasPermission);
  const canWrite = mounted && hasPermission("STORE.UPDATE");
  const canDelete = mounted && hasPermission("STORE.DELETE");

  const { data, isLoading } = useQuery({
    queryKey: ["materials", projectId, category],
    queryFn: () =>
      materialCatalogueApi.listByProject(projectId, category === "ALL" ? undefined : category),
    enabled: !!projectId,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => materialCatalogueApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["materials", projectId] });
      setConfirmId(null);
    },
  });

  const rows = useMemo(() => data?.data ?? [], [data]);

  // Catalogue delete is STORE.DELETE (admin-tier) — the column exists only for holders.
  const deleteColumn = useMemo<ColumnDef<MaterialResponse>[]>(() => canDelete
    ? [{
        accessorKey: "_actions",
        header: "",
        cell: (info) => {
          const row = info.row.original;
          return (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                setConfirmId(row.id);
              }}
              className="rounded p-1 text-text-secondary hover:bg-surface-hover hover:text-danger"
              aria-label="Delete"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          );
        },
      }]
    : [], [canDelete]);

  const columns = useMemo<ColumnDef<MaterialResponse>[]>(() => [
    { accessorKey: "code", header: "Code", enableSorting: true },
    { accessorKey: "name", header: "Name" },
    {
      accessorKey: "category",
      header: "Category",
      cell: (info) => {
        const v = info.getValue();
        return v ? (v as string).replace("_", " ") : "—";
      },
    },
    { accessorKey: "unit", header: "Unit" },
    { accessorKey: "specificationGrade", header: "Specification" },
    { accessorKey: "minStockLevel", header: "Min Stock" },
    { accessorKey: "reorderQuantity", header: "Reorder Qty" },
    { accessorKey: "leadTimeDays", header: "Lead (days)" },
    {
      accessorKey: "status",
      header: "Status",
      cell: (info) => {
        const s = info.getValue() as MaterialStatus | null;
        if (!s) return "—";
        return (
          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLORS[s]}`}>
            {s}
          </span>
        );
      },
    },
    ...deleteColumn,
  ], [deleteColumn]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Material Catalogue"
        description="Register all project materials with specifications, units, reorder parameters, and approved sources."
        actions={
          canWrite ? (
            <Link
              href={`/projects/${projectId}/materials/new`}
              className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent/90"
            >
              New Material
            </Link>
          ) : undefined
        }
      />

      <div className="flex items-center gap-3">
        <label className="text-sm text-text-secondary">Category</label>
        <select
          value={category}
          onChange={(e) => setCategory(e.target.value as MaterialCategory | "ALL")}
          className="rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none"
        >
          {CATEGORY_OPTIONS.map((c) => (
            <option key={c.value} value={c.value}>
              {c.label}
            </option>
          ))}
        </select>
      </div>

      {isLoading ? (
        <div className="text-text-secondary">Loading…</div>
      ) : rows.length === 0 ? (
        <EmptyState
          title="No materials in catalogue"
          description="Add materials to the catalogue to enable stock tracking, GRN and issue workflows."
        />
      ) : (
        <VirtualDataTable
          columns={columns}
          data={rows}
          sortable
          resizable
          onRowClick={(row) => router.push(`/projects/${projectId}/materials/${row.id}`)}
        />
      )}

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete material?"
        message="This removes the material from the catalogue. Existing GRN / issue records remain but will reference a missing material."
        confirmLabel={deleteMutation.isPending ? "Deleting…" : "Delete"}
        onConfirm={() => confirmId && deleteMutation.mutate(confirmId)}
        onCancel={() => setConfirmId(null)}
      />
    </div>
  );
}
