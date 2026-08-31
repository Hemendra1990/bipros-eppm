"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { materialConsumptionReportApi } from "@/lib/api/materialConsumptionReportApi";
import { MaterialKpiSection } from "@/components/dashboards/MaterialKpiSection";

/**
 * Project-overview material availability analysis (AI Agent sheet Material row, MAT-06).
 * Store-tracked projects get closing-balance / days-of-cover chips + the compact material KPI
 * strip; untracked projects get an honest one-liner instead of fabricated zeros.
 */
export function MaterialAvailabilityCard({ projectId }: { projectId: string }) {
  const { data } = useQuery({
    queryKey: ["material-availability", projectId, "overview"],
    queryFn: () => materialConsumptionReportApi.availability(projectId),
    enabled: !!projectId,
  });
  const availability = data?.data;
  if (!availability) return null;

  const reportHref = `/projects/${projectId}/reports/material-consumption`;

  if (!availability.tracked) {
    return (
      <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-medium uppercase tracking-wider text-text-secondary">
            Material Availability
          </h3>
          <Link href={reportHref} className="text-xs text-primary hover:underline">
            Open report
          </Link>
        </div>
        <p className="mt-2 text-sm text-text-muted">
          Stock not tracked — the store has no GRN / issue-slip entries yet, so closing balance
          cannot be computed. Material consumption is still recorded through DPRs.
        </p>
      </div>
    );
  }

  const shortages = availability.rows.filter(
    (r) => r.alerts.includes("BELOW_MIN_STOCK") || r.alerts.includes("LOW_COVER"),
  );
  const lowestCover = availability.rows
    .filter((r) => r.daysOfCover !== null)
    .sort((a, b) => (a.daysOfCover ?? 0) - (b.daysOfCover ?? 0))
    .slice(0, 3);

  return (
    <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium uppercase tracking-wider text-text-secondary">
          Material Availability
        </h3>
        <Link href={reportHref} className="text-xs text-primary hover:underline">
          Open report
        </Link>
      </div>
      <div className="mt-3 flex flex-wrap gap-2 text-xs">
        <span className="rounded-full border border-border bg-surface px-2 py-0.5">
          {availability.rows.length} material{availability.rows.length === 1 ? "" : "s"} tracked
        </span>
        <span
          className={`rounded-full border px-2 py-0.5 font-semibold ${
            shortages.length > 0
              ? "border-rose-500/30 bg-rose-500/10 text-rose-600"
              : "border-emerald-500/30 bg-emerald-500/10 text-emerald-600"
          }`}
        >
          {shortages.length > 0 ? `${shortages.length} in short supply` : "No short supply"}
        </span>
      </div>
      {lowestCover.length > 0 && (
        <ul className="mt-3 space-y-1 text-sm">
          {lowestCover.map((r) => (
            <li key={r.materialKey} className="flex items-baseline justify-between gap-3">
              <span className="truncate">{r.materialName}</span>
              <span className="whitespace-nowrap text-text-muted">
                {r.storeClosing?.toLocaleString()}
                {r.unit ? ` ${r.unit}` : ""} · {r.daysOfCover} day
                {r.daysOfCover === 1 ? "" : "s"} cover
              </span>
            </li>
          ))}
        </ul>
      )}
      <div className="mt-4 border-t border-border/60 pt-4">
        <MaterialKpiSection projectId={projectId} density="compact" />
      </div>
    </div>
  );
}
