"use client";

import { useMemo } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { SimpleTable } from "@/components/common/SimpleTable";
import type { ScheduleComparisonRow } from "@/lib/api/baselineApi";

const statusColors: Record<ScheduleComparisonRow["status"], string> = {
  ADDED: "bg-success/10 text-success",
  DELETED: "bg-danger/10 text-danger",
  CHANGED: "bg-warning/10 text-warning",
  UNCHANGED: "bg-surface-active/50 text-text-secondary",
};

const statusLabels: Record<ScheduleComparisonRow["status"], string> = {
  ADDED: "Added",
  DELETED: "Deleted",
  CHANGED: "Changed",
  UNCHANGED: "Unchanged",
};

function formatDate(dateStr: string | null): string {
  if (!dateStr) return "\u2014";
  return new Date(dateStr).toLocaleDateString();
}

function getVarianceColor(variance: number): string {
  if (variance > 0) return "text-danger font-semibold";
  if (variance < 0) return "text-success font-semibold";
  return "text-text-secondary";
}

interface ScheduleComparisonTableProps {
  data: ScheduleComparisonRow[];
  filter?: ScheduleComparisonRow["status"] | "ALL";
}

export function ScheduleComparisonTable({
  data,
  filter = "ALL",
}: ScheduleComparisonTableProps) {
  const filtered = filter === "ALL" ? data : data.filter((r) => r.status === filter);

  // Summary counts
  const counts = {
    ALL: data.length,
    ADDED: data.filter((r) => r.status === "ADDED").length,
    DELETED: data.filter((r) => r.status === "DELETED").length,
    CHANGED: data.filter((r) => r.status === "CHANGED").length,
    UNCHANGED: data.filter((r) => r.status === "UNCHANGED").length,
  };

  const columns = useMemo<ColumnDef<ScheduleComparisonRow>[]>(
    () => [
      {
        header: "Activity",
        accessorKey: "activityName",
        cell: ({ getValue }) => (
          <p className="font-medium text-text-primary">{String(getValue())}</p>
        ),
      },
      {
        header: "Current Start",
        accessorKey: "currentStart",
        cell: ({ getValue }) => (
          <span className="text-sm text-text-secondary">
            {formatDate(getValue() as string | null)}
          </span>
        ),
      },
      {
        header: "Baseline Start",
        accessorKey: "baselineStart",
        cell: ({ getValue }) => (
          <span className="text-sm text-text-secondary">
            {formatDate(getValue() as string | null)}
          </span>
        ),
      },
      {
        header: "Start Var",
        accessorKey: "startVarianceDays",
        meta: { align: "right" },
        cell: ({ getValue }) => {
          const v = Number(getValue());
          return (
            <span className={`text-right text-sm ${getVarianceColor(v)}`}>
              {v > 0 ? "+" : ""}
              {v}d
            </span>
          );
        },
      },
      {
        header: "Current Finish",
        accessorKey: "currentFinish",
        cell: ({ getValue }) => (
          <span className="text-sm text-text-secondary">
            {formatDate(getValue() as string | null)}
          </span>
        ),
      },
      {
        header: "Baseline Finish",
        accessorKey: "baselineFinish",
        cell: ({ getValue }) => (
          <span className="text-sm text-text-secondary">
            {formatDate(getValue() as string | null)}
          </span>
        ),
      },
      {
        header: "Finish Var",
        accessorKey: "finishVarianceDays",
        meta: { align: "right" },
        cell: ({ getValue }) => {
          const v = Number(getValue());
          return (
            <span className={`text-right text-sm ${getVarianceColor(v)}`}>
              {v > 0 ? "+" : ""}
              {v}d
            </span>
          );
        },
      },
      {
        header: "Status",
        accessorKey: "status",
        cell: ({ getValue }) => {
          const status = getValue() as ScheduleComparisonRow["status"];
          return (
            <span
              className={`inline-block rounded px-2 py-1 text-xs font-medium ${statusColors[status]}`}
            >
              {statusLabels[status]}
            </span>
          );
        },
      },
    ],
    []
  );

  return (
    <div className="space-y-4">
      {/* Filter chips */}
      <div className="flex flex-wrap gap-2 text-xs">
        <span className="rounded-full bg-surface-hover px-3 py-1 text-text-secondary">
          Total: {counts.ALL}
        </span>
        {counts.CHANGED > 0 && (
          <span className="rounded-full bg-warning/10 px-3 py-1 text-warning">
            Changed: {counts.CHANGED}
          </span>
        )}
        {counts.ADDED > 0 && (
          <span className="rounded-full bg-success/10 px-3 py-1 text-success">
            Added: {counts.ADDED}
          </span>
        )}
        {counts.DELETED > 0 && (
          <span className="rounded-full bg-danger/10 px-3 py-1 text-danger">
            Deleted: {counts.DELETED}
          </span>
        )}
        {counts.UNCHANGED > 0 && (
          <span className="rounded-full bg-surface-active/50 px-3 py-1 text-text-secondary">
            Unchanged: {counts.UNCHANGED}
          </span>
        )}
      </div>

      <div className="rounded-lg border border-border bg-surface/50 overflow-hidden">
        <SimpleTable
          data={filtered}
          columns={columns}
          sortable={true}
          emptyMessage="No schedule data to compare"
          className="rounded-lg border-0"
        />
      </div>
    </div>
  );
}
