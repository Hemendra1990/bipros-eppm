"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { equipmentKpiApi } from "@/lib/api/equipmentKpiApi";

interface Props {
  projectId: string;
  from?: string;
  to?: string;
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

export function EquipmentKpiSection({ projectId, from, to, density = "compact" }: Props) {
  const range = useMemo(() => {
    if (from && to) return { from, to };
    return defaultRange();
  }, [from, to]);

  const { data, isLoading, error } = useQuery({
    queryKey: ["equipment-kpis", projectId, range.from, range.to],
    queryFn: () => equipmentKpiApi.getKpis(projectId, range.from, range.to),
    enabled: !!projectId,
  });

  if (!projectId) return null;
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border bg-surface/40 p-4 text-sm text-text-muted">
        Loading equipment KPIs…
      </div>
    );
  }
  if (error || !data?.data) {
    return (
      <div className="rounded-lg border border-border bg-surface/40 p-4 text-sm text-danger">
        Failed to load equipment KPIs.
      </div>
    );
  }
  const kpis = data.data;
  const totalCost = kpis.ownedVsRented.reduce((s, x) => s + x.cost, 0);
  const avgUtilisation = kpis.utilization.length === 0
    ? null
    : kpis.utilization.reduce((s, u) => s + u.utilizationPct, 0) / kpis.utilization.length;

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-lg font-semibold text-text-primary">
          Equipment KPIs <span className="text-xs font-normal text-text-muted">({range.from} → {range.to})</span>
        </h2>
        <div className="text-[11px] text-text-muted">
          {kpis.utilization.length} machines tracked
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="rounded-lg border border-border bg-surface/50 p-4">
          <div className="text-xs uppercase tracking-wide text-text-muted">Idle-Time Alerts (latest day)</div>
          <div
            className={`mt-1 text-2xl font-semibold ${kpis.idleAlerts.length > 0 ? "text-warning" : "text-text-primary"}`}
          >
            {kpis.idleAlerts.length}
          </div>
          <div
            className="mt-1 text-xs text-text-secondary"
            title="Machines whose idle hours > 2 on the latest log date. Site Manager should triage these for redeployment or breakdown investigation."
          >
            machines idle &gt; 2h
          </div>
        </div>
        <div className="rounded-lg border border-border bg-surface/50 p-4">
          <div className="text-xs uppercase tracking-wide text-text-muted">Avg Utilisation %</div>
          <div
            className={`mt-1 text-2xl font-semibold ${
              avgUtilisation != null && avgUtilisation < 0.6
                ? "text-warning"
                : avgUtilisation != null && avgUtilisation > 0.85
                  ? "text-success"
                  : "text-text-primary"
            }`}
          >
            {avgUtilisation != null ? formatPct(avgUtilisation) : "—"}
          </div>
          <div
            className="mt-1 text-xs text-text-secondary"
            title="Average of operatingHours ÷ (operatingHours + idleHours + breakdownHours) across all tracked machines. >85% = strong; <60% = under-utilised, consider redeployment or off-hire."
          >
            operating ÷ tracked hrs
          </div>
        </div>
        <div className="rounded-lg border border-border bg-surface/50 p-4">
          <div className="text-xs uppercase tracking-wide text-text-muted">Owned/Rented Cost</div>
          <div className="mt-1 text-2xl font-semibold text-text-primary">₹{formatNumber(totalCost, 0)}</div>
          <div
            className="mt-1 text-xs text-text-secondary"
            title="Σ (operatingHours × Resource.costPerUnit), grouped by ownership. Owned uses depreciation + fuel rate; Rented uses hire rate. Comparison is approximate — see donut for split."
          >
            mixed-source — see tooltip
          </div>
        </div>
        <div className="rounded-lg border border-border bg-surface/50 p-4">
          <div className="text-xs uppercase tracking-wide text-text-muted">Service Due (next 7 days)</div>
          <div
            className={`mt-1 text-2xl font-semibold ${kpis.serviceDue.length > 0 ? "text-warning" : "text-text-primary"}`}
          >
            {kpis.serviceDue.length}
          </div>
          <div
            className="mt-1 text-xs text-text-secondary"
            title="Equipment whose nextServiceDate falls within the next 7 days. Schedule preventive maintenance to avoid breakdowns on the critical path."
          >
            machines
          </div>
        </div>
      </div>

      {/* New NH-48 KPIs */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="rounded-lg border border-border bg-surface/50 p-4">
          <div className="text-xs uppercase tracking-wide text-text-muted">Mechanical Availability</div>
          <div
            className={`mt-1 text-2xl font-semibold ${
              kpis.mechanicalAvailabilityPct < 0.85
                ? "text-warning"
                : "text-success"
            }`}
          >
            {formatPct(kpis.mechanicalAvailabilityPct)}
          </div>
          <div className="mt-1 text-xs text-text-secondary" title="KPI 4.1 — (Avail Hrs − Breakdown Hrs) ÷ Avail Hrs × 100. Target ≥ 85%.">
            (op + idle) ÷ (op + idle + breakdown)
          </div>
        </div>
        <div className="rounded-lg border border-border bg-surface/50 p-4">
          <div className="text-xs uppercase tracking-wide text-text-muted">Equipment Productivity Index</div>
          <div
            className={`mt-1 text-2xl font-semibold ${
              kpis.equipmentProductivityIndexPct < 0.8
                ? "text-danger"
                : kpis.equipmentProductivityIndexPct > 1.0
                  ? "text-success"
                  : "text-text-primary"
            }`}
          >
            {kpis.equipmentProductivityIndexPct > 0
              ? formatPct(kpis.equipmentProductivityIndexPct)
              : "—"}
          </div>
          <div className="mt-1 text-xs text-text-secondary" title="KPI 6.1 — Actual Output ÷ IRC Norm Output × 100, averaged across machines.">
            actual ÷ standard output
          </div>
        </div>
      </div>

      {density === "full" && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <div className="rounded-lg border border-border bg-surface/40 p-4">
            <h3 className="text-sm font-semibold text-text-primary mb-2">Utilisation %</h3>
            <table className="w-full text-xs">
              <thead className="text-text-muted">
                <tr>
                  <th className="text-left pb-1">Machine</th>
                  <th className="text-right pb-1">Op</th>
                  <th className="text-right pb-1">Idle</th>
                  <th className="text-right pb-1">%</th>
                </tr>
              </thead>
              <tbody>
                {kpis.utilization.length === 0 && (
                  <tr><td colSpan={4} className="py-2 text-center text-text-muted">No data</td></tr>
                )}
                {kpis.utilization.slice(0, 8).map((u) => (
                  <tr key={u.resourceId} className="text-text-primary">
                    <td className="py-1 truncate max-w-[160px]">{u.resourceCode}</td>
                    <td className="py-1 text-right">{formatNumber(u.operatingHours, 1)}</td>
                    <td className="py-1 text-right">{formatNumber(u.idleHours, 1)}</td>
                    <td className="py-1 text-right">{formatPct(u.utilizationPct)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="rounded-lg border border-border bg-surface/40 p-4">
            <h3 className="text-sm font-semibold text-text-primary mb-2">Availability vs Performance</h3>
            <table className="w-full text-xs">
              <thead className="text-text-muted">
                <tr>
                  <th className="text-left pb-1">Machine</th>
                  <th className="text-right pb-1">Avail</th>
                  <th className="text-right pb-1">Perf</th>
                </tr>
              </thead>
              <tbody>
                {kpis.availabilityPerformance.length === 0 && (
                  <tr><td colSpan={3} className="py-2 text-center text-text-muted">No data</td></tr>
                )}
                {kpis.availabilityPerformance.slice(0, 8).map((r) => (
                  <tr key={r.resourceId} className="text-text-primary">
                    <td className="py-1 truncate max-w-[160px]">{r.resourceCode}</td>
                    <td className="py-1 text-right">{formatPct(r.availability)}</td>
                    <td
                      className={`py-1 text-right ${r.performance < 0.8 ? "text-danger" : r.performance > 1.0 ? "text-success" : ""}`}
                    >
                      {formatPct(r.performance)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="rounded-lg border border-border bg-surface/40 p-4">
            <h3 className="text-sm font-semibold text-text-primary mb-2">Owned vs Rented</h3>
            <table className="w-full text-xs">
              <thead className="text-text-muted">
                <tr>
                  <th className="text-left pb-1">Ownership</th>
                  <th className="text-right pb-1">Op Hours</th>
                  <th className="text-right pb-1">Cost (₹)</th>
                </tr>
              </thead>
              <tbody>
                {kpis.ownedVsRented.length === 0 && (
                  <tr><td colSpan={3} className="py-2 text-center text-text-muted">No data</td></tr>
                )}
                {kpis.ownedVsRented.map((s) => (
                  <tr key={s.ownershipType} className="text-text-primary">
                    <td className="py-1">{s.ownershipType}</td>
                    <td className="py-1 text-right">{formatNumber(s.operatingHours, 1)}</td>
                    <td className="py-1 text-right">{formatNumber(s.cost, 0)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="mt-2 text-[11px] text-text-muted">
              Owned uses depreciation + fuel rate; Rented uses hire rate (both via{" "}
              <code>Resource.costPerUnit</code>). Comparison is approximate.
            </p>
          </div>
        </div>
      )}

      {kpis.idleAlerts.length > 0 && (
        <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-xs text-amber-200">
          <strong>Idle alerts ({kpis.idleAlerts[0].logDate}):</strong>{" "}
          {kpis.idleAlerts.slice(0, 5).map((a) => a.resourceCode).join(", ")}
          {kpis.idleAlerts.length > 5 && ` +${kpis.idleAlerts.length - 5} more`}
        </div>
      )}
    </section>
  );
}
