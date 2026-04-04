"use client";

import type { ScheduleComparisonRow } from "@/lib/api/baselineApi";

const statusColors = {
  ADDED: "bg-green-100 text-green-800",
  DELETED: "bg-red-100 text-red-800",
  CHANGED: "bg-yellow-100 text-yellow-800",
  UNCHANGED: "bg-gray-100 text-gray-800",
};

const statusLabels = {
  ADDED: "Added",
  DELETED: "Deleted",
  CHANGED: "Changed",
  UNCHANGED: "Unchanged",
};

function formatDate(dateStr: string | null): string {
  if (!dateStr) return "—";
  return new Date(dateStr).toLocaleDateString();
}

function getVarianceColor(variance: number): string {
  if (variance > 0) return "text-red-600";
  if (variance < 0) return "text-green-600";
  return "text-gray-600";
}

interface ScheduleComparisonTableProps {
  data: ScheduleComparisonRow[];
}

export function ScheduleComparisonTable({ data }: ScheduleComparisonTableProps) {
  return (
    <div className="rounded-lg border border-gray-200 bg-white overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50">
              <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
                Activity
              </th>
              <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
                Start Date
              </th>
              <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
                Variance
              </th>
              <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
                Finish Date
              </th>
              <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
                Variance
              </th>
              <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
                Status
              </th>
            </tr>
          </thead>
          <tbody>
            {data.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-6 py-8 text-center text-gray-500">
                  No schedule data to compare
                </td>
              </tr>
            ) : (
              data.map((row) => (
                <tr
                  key={row.activityId}
                  className="border-b border-gray-200 hover:bg-gray-50"
                >
                  <td className="px-6 py-4">
                    <div>
                      <p className="font-medium text-gray-900">{row.activityName}</p>
                      <p className="text-xs text-gray-500">{row.activityId}</p>
                    </div>
                  </td>
                  <td className="px-6 py-4 text-sm">
                    <div className="flex flex-col gap-1">
                      <span className="text-gray-900">
                        {formatDate(row.currentStart)}
                      </span>
                      <span className="text-xs text-gray-500">
                        Base: {formatDate(row.baselineStart)}
                      </span>
                    </div>
                  </td>
                  <td className="px-6 py-4 text-sm">
                    <span className={getVarianceColor(row.startVarianceDays)}>
                      {row.startVarianceDays > 0 ? "+" : ""}
                      {row.startVarianceDays} days
                    </span>
                  </td>
                  <td className="px-6 py-4 text-sm">
                    <div className="flex flex-col gap-1">
                      <span className="text-gray-900">
                        {formatDate(row.currentFinish)}
                      </span>
                      <span className="text-xs text-gray-500">
                        Base: {formatDate(row.baselineFinish)}
                      </span>
                    </div>
                  </td>
                  <td className="px-6 py-4 text-sm">
                    <span className={getVarianceColor(row.finishVarianceDays)}>
                      {row.finishVarianceDays > 0 ? "+" : ""}
                      {row.finishVarianceDays} days
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    <span
                      className={`inline-block px-2 py-1 rounded text-xs font-medium ${
                        statusColors[row.status]
                      }`}
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
  );
}
