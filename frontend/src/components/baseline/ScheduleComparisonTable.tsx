"use client";

import type { ScheduleComparisonRow } from "@/lib/api/baselineApi";

const statusColors: Record<ScheduleComparisonRow["status"], string> = {
  ADDED: "bg-emerald-500/10 text-emerald-400",
  DELETED: "bg-red-500/10 text-red-400",
  CHANGED: "bg-amber-500/10 text-amber-400",
  UNCHANGED: "bg-slate-700/50 text-slate-300",
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
  if (variance > 0) return "text-red-400 font-semibold";
  if (variance < 0) return "text-emerald-400 font-semibold";
  return "text-slate-400";
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
        <span className="rounded-full bg-slate-800 px-3 py-1 text-slate-300">
          Total: {counts.ALL}
        </span>
        {counts.CHANGED > 0 && (
          <span className="rounded-full bg-amber-500/10 px-3 py-1 text-amber-400">
            Changed: {counts.CHANGED}
          </span>
        )}
        {counts.ADDED > 0 && (
          <span className="rounded-full bg-emerald-500/10 px-3 py-1 text-emerald-400">
            Added: {counts.ADDED}
          </span>
        )}
        {counts.DELETED > 0 && (
          <span className="rounded-full bg-red-500/10 px-3 py-1 text-red-400">
            Deleted: {counts.DELETED}
          </span>
        )}
        {counts.UNCHANGED > 0 && (
          <span className="rounded-full bg-slate-700/50 px-3 py-1 text-slate-400">
            Unchanged: {counts.UNCHANGED}
          </span>
        )}
      </div>

      <div className="rounded-lg border border-slate-800 bg-slate-900/50 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-900/80">
                <th className="px-4 py-3 text-left text-sm font-semibold text-white">
                  Activity
                </th>
                <th className="px-4 py-3 text-left text-sm font-semibold text-white">
                  Current Start
                </th>
                <th className="px-4 py-3 text-left text-sm font-semibold text-white">
                  Baseline Start
                </th>
                <th className="px-4 py-3 text-right text-sm font-semibold text-white">
                  Start Var
                </th>
                <th className="px-4 py-3 text-left text-sm font-semibold text-white">
                  Current Finish
                </th>
                <th className="px-4 py-3 text-left text-sm font-semibold text-white">
                  Baseline Finish
                </th>
                <th className="px-4 py-3 text-right text-sm font-semibold text-white">
                  Finish Var
                </th>
                <th className="px-4 py-3 text-left text-sm font-semibold text-white">
                  Status
                </th>
              </tr>
            </thead>
            <tbody>
              {filtered.length === 0 ? (
                <tr>
                  <td
                    colSpan={8}
                    className="px-6 py-8 text-center text-slate-400"
                  >
                    No schedule data to compare
                  </td>
                </tr>
              ) : (
                filtered.map((row) => (
                  <tr
                    key={row.activityId}
                    className="border-b border-slate-800 hover:bg-slate-800/30"
                  >
                    <td className="px-4 py-3">
                      <p className="font-medium text-white">
                        {row.activityName}
                      </p>
                    </td>
                    <td className="px-4 py-3 text-sm text-slate-300">
                      {formatDate(row.currentStart)}
                    </td>
                    <td className="px-4 py-3 text-sm text-slate-400">
                      {formatDate(row.baselineStart)}
                    </td>
                    <td
                      className={`px-4 py-3 text-right text-sm ${getVarianceColor(row.startVarianceDays)}`}
                    >
                      {row.startVarianceDays > 0 ? "+" : ""}
                      {row.startVarianceDays}d
                    </td>
                    <td className="px-4 py-3 text-sm text-slate-300">
                      {formatDate(row.currentFinish)}
                    </td>
                    <td className="px-4 py-3 text-sm text-slate-400">
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
