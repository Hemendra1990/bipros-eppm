"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";

import {
  dbsApi,
  type BoqSupervisorPerformanceRow,
  type DbsPeriodType,
} from "@/lib/api/dbsApi";
import { formatCurrency, formatPercent } from "@/lib/utils/format";

/**
 * "BOQ performance by supervisor — Cost" (AI Agent sheet, DBS row: "Comparison between
 * the supervisor for the BOQ"). One row per (BOQ item × supervisor) for the page's
 * period window, grouped visually by item. Income follows the DBS billable convention
 * (measurement operations, minus sub-contractor share); cost is the supervisor's
 * DPR-attributable manpower + machinery + fuel + material on that item — admin
 * (Section B) is not item-attributable and is excluded, so a supervisor's costs here
 * reconcile to their DBS expense minus Section B.
 */
export function BoqSupervisorComparison({
  projectId,
  date,
  periodType,
  currency,
  onSupervisorClick,
}: {
  projectId: string;
  date: string;
  periodType: DbsPeriodType;
  currency?: string | null;
  onSupervisorClick?: (supervisorUserId: string) => void;
}) {
  const { data, isLoading } = useQuery({
    queryKey: ["dbs-boq-supervisor-comparison", projectId, date, periodType],
    queryFn: () => dbsApi.getBoqSupervisorComparison(projectId, date, periodType),
    enabled: !!projectId && !!date,
  });
  const rows = useMemo(() => data?.data ?? [], [data]);

  // Group by item, preserving backend order (item_no asc).
  const groups = useMemo(() => {
    const byItem = new Map<string, BoqSupervisorPerformanceRow[]>();
    for (const r of rows) {
      const key = r.itemNo;
      const bucket = byItem.get(key) ?? [];
      bucket.push(r);
      byItem.set(key, bucket);
    }
    return Array.from(byItem.entries());
  }, [rows]);

  if (isLoading || rows.length === 0) return null;

  return (
    <section className="rounded-lg border border-border bg-surface/50 shadow-sm">
      <header className="border-b border-border px-4 py-3">
        <h3 className="text-sm font-semibold text-text-primary">
          BOQ performance by supervisor — Cost
          {periodType !== "DAY" ? ` — this ${periodType.toLowerCase()}` : ""}
        </h3>
        <p className="mt-0.5 text-xs text-text-muted">
          Per BOQ item: each supervisor&apos;s billable qty, income (qty × BOQ rate) and
          DPR-attributable cost (manpower + machinery + fuel + material — admin is not
          item-attributable). Click a supervisor to open them above.
        </p>
      </header>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-surface/40 text-left text-xs uppercase tracking-wide text-text-muted">
            <tr>
              <th className="px-4 py-2">Supervisor</th>
              <th className="px-4 py-2 text-right">Qty</th>
              <th className="px-4 py-2 text-right">Income</th>
              <th className="px-4 py-2 text-right">Cost</th>
              <th className="px-4 py-2 text-right">Contribution</th>
              <th className="px-4 py-2 text-right">Contribution %</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {groups.map(([itemNo, itemRows]) => {
              const first = itemRows[0];
              return [
                <tr key={`hdr-${itemNo}`} className="bg-accent/10">
                  <td colSpan={6} className="px-4 py-2 font-semibold text-text-primary">
                    {itemNo}
                    {first.description ? ` — ${first.description}` : ""}
                    <span className="ml-2 text-xs font-normal text-text-muted">
                      {first.unit ?? ""} · BOQ rate {formatCurrency(first.boqRate, currency)}
                    </span>
                  </td>
                </tr>,
                ...itemRows.map((r, i) => (
                  <tr
                    key={`${itemNo}-${r.supervisorUserId ?? r.supervisorName}-${i}`}
                    onClick={
                      r.supervisorUserId && onSupervisorClick
                        ? () => onSupervisorClick(r.supervisorUserId as string)
                        : undefined
                    }
                    className={r.supervisorUserId && onSupervisorClick ? "cursor-pointer hover:bg-surface-hover" : ""}
                  >
                    <td className="px-4 py-2 text-text-primary">{r.supervisorName}</td>
                    <td className="px-4 py-2 text-right font-mono text-text-secondary">
                      {r.qty.toLocaleString()}
                    </td>
                    <td className="px-4 py-2 text-right font-mono text-text-secondary">
                      {formatCurrency(r.income, currency)}
                    </td>
                    <td
                      className="px-4 py-2 text-right font-mono text-text-secondary"
                      title={`Manpower ${formatCurrency(r.manpowerCost, currency)} · Machinery ${formatCurrency(r.machineryCost, currency)} · Fuel ${formatCurrency(r.fuelCost, currency)} · Material ${formatCurrency(r.materialCost, currency)}`}
                    >
                      {formatCurrency(r.totalCost, currency)}
                    </td>
                    <td
                      className={`px-4 py-2 text-right font-mono ${
                        r.contribution > 0
                          ? "text-emerald-700"
                          : r.contribution < 0
                            ? "text-red-700"
                            : "text-text-secondary"
                      }`}
                    >
                      {formatCurrency(r.contribution, currency)}
                    </td>
                    <td className="px-4 py-2 text-right font-mono text-text-secondary">
                      {r.income > 0 ? formatPercent(r.contributionPct * 100) : "—"}
                    </td>
                  </tr>
                )),
              ];
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}
