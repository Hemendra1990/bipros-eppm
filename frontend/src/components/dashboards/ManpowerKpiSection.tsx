"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { manpowerKpiApi } from "@/lib/api/manpowerKpiApi";

interface Props {
  projectId: string;
  /**
   * Period in ISO yyyy-MM-dd format. If omitted, defaults to last 30 days ending today.
   */
  from?: string;
  to?: string;
  /**
   * Visual density. "compact" hides per-activity tables and shows only the headline cards.
   */
  density?: "compact" | "full";
}

function defaultRange(): { from: string; to: string } {
  const to = new Date();
  const from = new Date(to.getTime() - 30 * 24 * 60 * 60 * 1000);
  const fmt = (d: Date) => d.toISOString().split("T")[0];
  return { from: fmt(from), to: fmt(to) };
}

function formatPct(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return "—";
  return `${(value * 100).toFixed(1)}%`;
}

function formatNumber(value: number | null | undefined, fractionDigits = 2): string {
  if (value == null || Number.isNaN(value)) return "—";
  return value.toLocaleString("en-IN", {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  });
}

function formatRupees(value: number | null | undefined, fractionDigits = 0): string {
  if (value == null || Number.isNaN(value)) return "—";
  return `₹${value.toLocaleString("en-IN", {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  })}`;
}

export function ManpowerKpiSection({ projectId, from, to, density = "compact" }: Props) {
  const range = useMemo(() => {
    if (from && to) return { from, to };
    return defaultRange();
  }, [from, to]);

  const { data, isLoading, error } = useQuery({
    queryKey: ["manpower-kpis", projectId, range.from, range.to],
    queryFn: () => manpowerKpiApi.getKpis(projectId, range.from, range.to),
    enabled: !!projectId,
  });

  if (!projectId) return null;
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border bg-surface/40 p-4 text-sm text-text-muted">
        Loading manpower KPIs…
      </div>
    );
  }
  if (error || !data?.data) {
    return (
      <div className="rounded-lg border border-border bg-surface/40 p-4 text-sm text-danger">
        Failed to load manpower KPIs.
      </div>
    );
  }
  const kpis = data.data;
  const wu = kpis.workforceUtilization;

  // Derived KPIs (computed in-memory from the response shape — keeps backend stable).
  const totalLabourCost = kpis.labourCostPerUnit.reduce((sum, r) => sum + r.labourCost, 0);
  const productivityRows = kpis.productivityFactor.filter((p) => p.normOutputPerManPerDay > 0);
  const avgProductivityFactor = productivityRows.length === 0
    ? null
    : productivityRows.reduce((s, p) => s + p.factor, 0) / productivityRows.length;
  const underPerformingCount = productivityRows.filter((p) => p.factor < 0.8).length;

  const worstProductivity = kpis.productivityFactor.slice(0, 5);
  const topCpu = kpis.labourCostPerUnit.slice(0, 5);
  const worstCrews = kpis.crewOutput.slice(0, 5);

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-lg font-semibold text-text-primary">
          Manpower KPIs <span className="text-xs font-normal text-text-muted">({range.from} → {range.to})</span>
        </h2>
        <div className="text-[11px] text-text-muted">
          {wu.laborResourceCount} labour resources active · {kpis.productivityFactor.length} activities tracked · {kpis.labourCostPerUnit.length} BOQ items costed
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="rounded-lg border border-border bg-surface/50 p-4">
          <div className="text-xs uppercase tracking-wide text-text-muted">Workforce Utilisation</div>
          <div className="mt-1 text-2xl font-semibold text-text-primary">{formatPct(wu.utilizationPct)}</div>
          <div
            className="mt-1 text-xs text-text-secondary"
            title="Σ logged hours ÷ Σ available hours (workingHoursPerDay × workingDays × headcount). 100% = fully utilised; >100% indicates overtime or data unit mismatch."
          >
            {formatNumber(wu.actualHours)} of {formatNumber(wu.availableHours)} hrs
          </div>
        </div>
        <div className="rounded-lg border border-border bg-surface/50 p-4">
          <div className="text-xs uppercase tracking-wide text-text-muted">Total Labour Cost</div>
          <div className="mt-1 text-2xl font-semibold text-text-primary">{formatRupees(totalLabourCost)}</div>
          <div
            className="mt-1 text-xs text-text-secondary"
            title="Σ (hours worked × hourly rate) across all labour resources, normalised by salary type (PERMANENT=base/30/8, CONTRACT=base/26/8, DAILY=base/8, HOURLY=direct)."
          >
            across all labour activities
          </div>
        </div>
        <div className="rounded-lg border border-border bg-surface/50 p-4">
          <div className="text-xs uppercase tracking-wide text-text-muted">Avg Productivity Factor</div>
          <div
            className={`mt-1 text-2xl font-semibold ${
              avgProductivityFactor != null && avgProductivityFactor < 0.8
                ? "text-danger"
                : avgProductivityFactor != null && avgProductivityFactor > 1.1
                  ? "text-success"
                  : "text-text-primary"
            }`}
          >
            {avgProductivityFactor != null ? formatNumber(avgProductivityFactor, 2) : "—"}
          </div>
          <div
            className="mt-1 text-xs text-text-secondary"
            title="Average of (actual output per man-day ÷ ProductivityNorm.outputPerManPerDay) across activities with a defined norm. 1.0 = on norm; <0.8 = under-performing."
          >
            actual ÷ norm · {productivityRows.length} activities
          </div>
        </div>
        <div className="rounded-lg border border-border bg-surface/50 p-4">
          <div className="text-xs uppercase tracking-wide text-text-muted">Under-Performing Activities</div>
          <div
            className={`mt-1 text-2xl font-semibold ${underPerformingCount > 0 ? "text-warning" : "text-text-primary"}`}
          >
            {underPerformingCount}
          </div>
          <div
            className="mt-1 text-xs text-text-secondary"
            title="Activities with productivity factor < 0.8 (i.e. delivering <80% of norm output per man-day)."
          >
            factor &lt; 0.8 of norm
          </div>
        </div>
      </div>

      {density === "full" && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <div className="rounded-lg border border-border bg-surface/40 p-4">
            <h3 className="text-sm font-semibold text-text-primary mb-2">
              Productivity Factor — bottom 5 activities
            </h3>
            <table className="w-full text-xs">
              <thead className="text-text-muted">
                <tr>
                  <th className="text-left pb-1">Activity</th>
                  <th className="text-right pb-1">Actual</th>
                  <th className="text-right pb-1">Norm</th>
                  <th className="text-right pb-1">Factor</th>
                </tr>
              </thead>
              <tbody>
                {worstProductivity.length === 0 && (
                  <tr>
                    <td colSpan={4} className="py-2 text-center text-text-muted">No data</td>
                  </tr>
                )}
                {worstProductivity.map((p) => (
                  <tr key={p.activityId} className="text-text-primary">
                    <td className="py-1 truncate max-w-[200px]">{p.activityName}</td>
                    <td className="py-1 text-right">{formatNumber(p.actualOutputPerManPerDay, 2)}</td>
                    <td className="py-1 text-right">{formatNumber(p.normOutputPerManPerDay, 2)}</td>
                    <td className={`py-1 text-right ${p.factor < 0.8 ? "text-danger" : p.factor > 1.1 ? "text-success" : ""}`}>
                      {formatNumber(p.factor, 2)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="rounded-lg border border-border bg-surface/40 p-4">
            <h3 className="text-sm font-semibold text-text-primary mb-2">
              Labour Cost / Unit — top 5 BOQ items
            </h3>
            <table className="w-full text-xs">
              <thead className="text-text-muted">
                <tr>
                  <th className="text-left pb-1">BOQ Item</th>
                  <th className="text-right pb-1">Qty</th>
                  <th className="text-right pb-1">₹ / unit</th>
                </tr>
              </thead>
              <tbody>
                {topCpu.length === 0 && (
                  <tr>
                    <td colSpan={3} className="py-2 text-center text-text-muted">No data</td>
                  </tr>
                )}
                {topCpu.map((row) => (
                  <tr key={row.boqItemId} className="text-text-primary">
                    <td className="py-1 truncate max-w-[200px]">{row.itemNo}</td>
                    <td className="py-1 text-right">{formatNumber(row.qtyExecuted, 3)}</td>
                    <td className="py-1 text-right">₹{formatNumber(row.costPerUnit, 2)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="rounded-lg border border-border bg-surface/40 p-4">
            <h3 className="text-sm font-semibold text-text-primary mb-2">
              Crew Output vs Norm — bottom 5 by deviation
            </h3>
            <table className="w-full text-xs">
              <thead className="text-text-muted">
                <tr>
                  <th className="text-left pb-1">Activity</th>
                  <th className="text-right pb-1">Crew</th>
                  <th className="text-right pb-1">Actual / day</th>
                  <th className="text-right pb-1">Δ</th>
                </tr>
              </thead>
              <tbody>
                {worstCrews.length === 0 && (
                  <tr>
                    <td colSpan={4} className="py-2 text-center text-text-muted">No data</td>
                  </tr>
                )}
                {worstCrews.map((c) => (
                  <tr key={c.activityId} className="text-text-primary">
                    <td className="py-1 truncate max-w-[160px]">{c.activityName}</td>
                    <td className="py-1 text-right">{c.crewSize ?? "—"}</td>
                    <td className="py-1 text-right">{formatNumber(c.actualOutputPerDay, 2)}</td>
                    <td
                      className={`py-1 text-right ${c.deviationPct < -0.1 ? "text-danger" : c.deviationPct > 0.1 ? "text-success" : ""}`}
                    >
                      {formatPct(c.deviationPct)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}
