"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import { materialCatalogueApi } from "@/lib/api/materialCatalogueApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
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

  const rows = data?.data ?? [];

  const columns: ColumnDef<MaterialResponse>[] = [
    { key: "code", label: "Code", sortable: true },
    { key: "name", label: "Name" },
    {
      key: "category",
      label: "Category",
      render: (v) => (v ? (v as string).replace("_", " ") : "—"),
    },
    { key: "unit", label: "Unit" },
    { key: "specificationGrade", label: "Specification" },
    { key: "minStockLevel", label: "Min Stock" },
    { key: "reorderQuantity", label: "Reorder Qty" },
    { key: "leadTimeDays", label: "Lead (days)" },
    {
      key: "status",
      label: "Status",
      render: (v) => {
        const s = v as MaterialStatus | null;
        if (!s) return "—";
        return (
          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLORS[s]}`}>
            {s}
          </span>
        );
      },
    },
    {
      key: "_actions",
      label: "",
      render: (_v, row) => (
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
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Material Catalogue"
        description="Register all project materials with specifications, units, reorder parameters, and approved sources."
        actions={
          <Link
            href={`/projects/${projectId}/materials/new`}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent/90"
          >
            New Material
          </Link>
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
        <DataTable
          columns={columns}
          data={rows}
          rowKey="id"
          onRowClick={(row) => router.push(`/projects/${projectId}/materials/${row.id}`)}
          searchable
          searchPlaceholder="Search by code, name, specification…"
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
