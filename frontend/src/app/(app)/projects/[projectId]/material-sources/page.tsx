"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import { materialSourceApi } from "@/lib/api/materialSourceApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import type { LabTestStatus, MaterialSourceResponse, MaterialSourceType } from "@/lib/types";

const TABS: { key: MaterialSourceType | "ALL"; label: string }[] = [
  { key: "ALL", label: "All" },
  { key: "BORROW_AREA", label: "Borrow Areas" },
  { key: "QUARRY", label: "Quarries" },
  { key: "BITUMEN_DEPOT", label: "Bitumen Depots" },
  { key: "CEMENT_SOURCE", label: "Cement Sources" },
];

const LAB_STATUS_COLORS: Record<LabTestStatus, string> = {
  ALL_PASS: "bg-success/20 text-success",
  TESTS_PENDING: "bg-amber-500/20 text-warning",
  ONE_OR_MORE_FAIL: "bg-danger/20 text-danger",
};

export default function MaterialSourcesPage() {
  const params = useParams<{ projectId: string }>();
  const projectId = params.projectId;
  const router = useRouter();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<MaterialSourceType | "ALL">("ALL");
  const [confirmId, setConfirmId] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ["material-sources", projectId, tab],
    queryFn: () =>
      materialSourceApi.listByProject(projectId, tab === "ALL" ? undefined : tab),
    enabled: !!projectId,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => materialSourceApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["material-sources", projectId] });
      setConfirmId(null);
    },
  });

  const rows = data?.data ?? [];

  const columns: ColumnDef<MaterialSourceResponse>[] = [
    { key: "sourceCode", label: "Source ID", sortable: true },
    { key: "name", label: "Name" },
    {
      key: "sourceType",
      label: "Type",
      render: (v) => (v as string).replace("_", " "),
    },
    { key: "village", label: "Village / Location" },
    { key: "district", label: "District" },
    {
      key: "distanceKm",
      label: "Distance (km)",
      render: (v) => (v == null ? "—" : `${v}`),
    },
    {
      key: "approvedQuantity",
      label: "Approved Qty",
      render: (v, row) =>
        v == null ? "—" : `${v} ${row.approvedQuantityUnit ?? ""}`.trim(),
    },
    { key: "cbrAveragePercent", label: "CBR %" },
    { key: "mddGcc", label: "MDD (g/cc)" },
    {
      key: "labTestStatus",
      label: "Lab Status",
      render: (v) => {
        const s = v as LabTestStatus | null;
        if (!s) return "—";
        return (
          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${LAB_STATUS_COLORS[s]}`}>
            {s.replace(/_/g, " ")}
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
        title="Borrow Area & Source Master"
        description="Register approved borrow areas, quarries, and material sources with lab-test results."
        actions={
          <Link
            href={`/projects/${projectId}/material-sources/new`}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent/90"
          >
            New Source
          </Link>
        }
      />

      <div className="flex gap-2 border-b border-border">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setTab(t.key)}
            className={`px-4 py-2 text-sm font-medium transition-colors ${
              tab === t.key
                ? "border-b-2 border-accent text-accent"
                : "text-text-secondary hover:text-text-primary"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="text-text-secondary">Loading…</div>
      ) : rows.length === 0 ? (
        <EmptyState
          title="No sources yet"
          description="Register approved borrow areas, quarries and depots here so they're available for daily material receipts."
        />
      ) : (
        <DataTable
          columns={columns}
          data={rows}
          rowKey="id"
          onRowClick={(row) => router.push(`/projects/${projectId}/material-sources/${row.id}`)}
          searchable
          searchPlaceholder="Search by code, name, village…"
        />
      )}

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete source?"
        message="This removes the source and all linked lab-test records."
        confirmLabel={deleteMutation.isPending ? "Deleting…" : "Delete"}
        onConfirm={() => confirmId && deleteMutation.mutate(confirmId)}
        onCancel={() => setConfirmId(null)}
      />
    </div>
  );
}
