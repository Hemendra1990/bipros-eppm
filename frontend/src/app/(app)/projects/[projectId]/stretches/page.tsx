"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { Trash2 } from "lucide-react";
import { stretchApi, type StretchProgressResponse } from "@/lib/api/stretchApi";
import { VirtualDataTable } from "@/components/common/VirtualDataTable";
import type { ColumnDef } from "@tanstack/react-table";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import type { StretchResponse, StretchStatus } from "@/lib/types";

const STATUS_COLORS: Record<StretchStatus, string> = {
  NOT_STARTED: "bg-slate-500/20 text-slate-300",
  ACTIVE: "bg-success/20 text-success",
  COMPLETE: "bg-blue-500/20 text-blue-300",
  SNAGGING: "bg-amber-500/20 text-warning",
};

function formatChainage(metres: number | null | undefined): string {
  if (metres == null) return "—";
  const km = Math.floor(metres / 1000);
  const m = metres % 1000;
  return `${km}+${String(m).padStart(3, "0")}`;
}

export default function StretchesPage() {
  const params = useParams<{ projectId: string }>();
  const projectId = params.projectId;
  const router = useRouter();
  const queryClient = useQueryClient();
  const [confirmId, setConfirmId] = useState<string | null>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ["stretches", projectId],
    queryFn: () => stretchApi.listByProject(projectId),
    enabled: !!projectId,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => stretchApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["stretches", projectId] });
      setConfirmId(null);
    },
  });

  const stretches = data?.data ?? [];

  // Fetch progress for each stretch in parallel so the table renders % complete per row.
  const progressQueries = useQuery({
    queryKey: ["stretch-progress", projectId, stretches.map((s) => s.id).join(",")],
    queryFn: async () => {
      const results = await Promise.all(
        stretches.map((s) =>
          stretchApi
            .progress(s.id)
            .then((r) => r.data)
            .catch(() => null),
        ),
      );
      const map = new Map<string, StretchProgressResponse>();
      stretches.forEach((s, i) => {
        const r = results[i];
        if (r) map.set(s.id, r);
      });
      return map;
    },
    enabled: stretches.length > 0,
  });
  const progressMap = progressQueries.data ?? new Map<string, StretchProgressResponse>();

  const columns = useMemo<ColumnDef<StretchResponse>[]>(() => [
    { accessorKey: "stretchCode", header: "Stretch ID", enableSorting: true },
    { accessorKey: "name", header: "Name" },
    {
      accessorKey: "fromChainageM",
      header: "From",
      cell: (info) => formatChainage(info.getValue() as number | null),
    },
    {
      accessorKey: "toChainageM",
      header: "To",
      cell: (info) => formatChainage(info.getValue() as number | null),
    },
    {
      accessorKey: "lengthM",
      header: "Length (m)",
      cell: (info) => {
        const v = info.getValue();
        return v == null ? "—" : `${v}`;
      },
    },
    { accessorKey: "packageCode", header: "Package" },
    {
      accessorKey: "status",
      header: "Status",
      cell: (info) => {
        const val = info.getValue();
        const s = val as StretchStatus | null;
        if (!s) return "—";
        return (
          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLORS[s]}`}>
            {s.replace("_", " ")}
          </span>
        );
      },
    },
    {
      id: "_progress",
      header: "% Complete",
      cell: (info) => {
        const row = info.row.original;
        const p = progressMap.get(row.id);
        if (!p) return "—";
        return (
          <div className="flex items-center gap-2">
            <div className="h-2 w-24 rounded-full bg-surface-hover">
              <div
                className="h-full rounded-full bg-accent"
                style={{ width: `${Math.min(100, Number(p.percentComplete))}%` }}
              />
            </div>
            <span className="font-mono text-xs">
              {Number(p.percentComplete).toFixed(1)}%
            </span>
          </div>
        );
      },
    },
    { accessorKey: "milestoneName", header: "Milestone" },
    { accessorKey: "targetDate", header: "Target" },
    {
      id: "_actions",
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
            aria-label="Delete stretch"
          >
            <Trash2 className="h-4 w-4" />
          </button>
        );
      },
    },
  ], [progressMap]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Chainage & Stretch Master"
        description="Define project stretches, assign supervisors, set milestones, and track corridor progress."
        actions={
          <Link
            href={`/projects/${projectId}/stretches/new`}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent/90"
          >
            New Stretch
          </Link>
        }
      />

      {error ? (
        <EmptyState title="Failed to load stretches" description={(error as Error).message} />
      ) : isLoading ? (
        <div className="text-text-secondary">Loading…</div>
      ) : stretches.length === 0 ? (
        <EmptyState
          title="No stretches yet"
          description="Create a stretch to subdivide the corridor for supervisor assignment and milestone tracking."
        />
      ) : (
        <VirtualDataTable
          columns={columns}
          data={stretches}
          sortable
          resizable
          onRowClick={(row) => router.push(`/projects/${projectId}/stretches/${row.id}`)}
        />
      )}

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete stretch?"
        message="This removes the stretch and all its BOQ assignments. It cannot be undone."
        confirmLabel={deleteMutation.isPending ? "Deleting…" : "Delete"}
        onConfirm={() => confirmId && deleteMutation.mutate(confirmId)}
        onCancel={() => setConfirmId(null)}
      />
    </div>
  );
}
