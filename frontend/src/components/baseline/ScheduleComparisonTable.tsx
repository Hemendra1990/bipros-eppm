"use client";

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
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-border bg-surface/80">
                <th className="px-4 py-3 text-left text-sm font-semibold text-text-primary">
                  Activity
                </th>
                <th className="px-4 py-3 text-left text-sm font-semibold text-text-primary">
                  Current Start
                </th>
                <th className="px-4 py-3 text-left text-sm font-semibold text-text-primary">
                  Baseline Start
                </th>
                <th className="px-4 py-3 text-right text-sm font-semibold text-text-primary">
                  Start Var
                </th>
                <th className="px-4 py-3 text-left text-sm font-semibold text-text-primary">
                  Current Finish
                </th>
                <th className="px-4 py-3 text-left text-sm font-semibold text-text-primary">
                  Baseline Finish
                </th>
                <th className="px-4 py-3 text-right text-sm font-semibold text-text-primary">
                  Finish Var
                </th>
                <th className="px-4 py-3 text-left text-sm font-semibold text-text-primary">
                  Status
                </th>
              </tr>
            </thead>
            <tbody>
              {filtered.length === 0 ? (
                <tr>
                  <td
                    colSpan={8}
                    className="px-6 py-8 text-center text-text-secondary"
                  >
                    No schedule data to compare
                  </td>
                </tr>
              ) : (
                filtered.map((row) => (
                  <tr
                    key={row.activityId}
                    className="border-b border-border hover:bg-surface-hover/30"
                  >
                    <td className="px-4 py-3">
                      <p className="font-medium text-text-primary">
                        {row.activityName}
                      </p>
                    </td>
                    <td className="px-4 py-3 text-sm text-text-secondary">
                      {formatDate(row.currentStart)}
                    </td>
                    <td className="px-4 py-3 text-sm text-text-secondary">
                      {formatDate(row.baselineStart)}
                    </td>
                    <td
                      className={`px-4 py-3 text-right text-sm ${getVarianceColor(row.startVarianceDays)}`}
                    >
                      {row.startVarianceDays > 0 ? "+" : ""}
                      {row.startVarianceDays}d
                    </td>
                    <td className="px-4 py-3 text-sm text-text-secondary">
                      {formatDate(row.currentFinish)}
                    </td>
                    <td className="px-4 py-3 text-sm text-text-secondary">
                      {formatDate(row.baselineFinish)}
                    </td>
                    <td
                      className={`px-4 py-3 text-right text-sm ${getVarianceColor(row.finishVarianceDays)}`}
                    >
                      {row.finishVarianceDays > 0 ? "+" : ""}
                      {row.finishVarianceDays}d
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-block rounded px-2 py-1 text-xs font-medium ${statusColors[row.status]}`}
                      >
                        {statusLabels[row.status]}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
