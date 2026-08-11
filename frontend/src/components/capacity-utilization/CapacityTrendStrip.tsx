"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronDown, ChevronRight, TrendingUp } from "lucide-react";
import {
  capacityUtilizationApi,
  type CapacitySection,
} from "@/lib/api/capacityUtilizationApi";
import { utilBand } from "@/components/capacity/PeriodCell";
import { useProjectCurrency } from "@/lib/currency/ProjectCurrencyProvider";

/**
 * Utilization trend strip (AI Agent sheet, Capacity Utilization row: "Analysis of utilization
 * performance to be shown on Capacity Utilization dash board"). Monthly buckets from the
 * existing aggregate endpoint — last 6 calendar months ending at the page's To date; each
 * bucket's figures are its own cumulative totals (same engine as the tables below).
 */
export function CapacityTrendStrip({
  projectId,
  toDate,
}: {
  projectId: string;
  toDate: string;
}) {
  const [open, setOpen] = useState(false);
  const { money } = useProjectCurrency();

  // Window: first day of the month 5 months before the To date's month → To date.
  const to = toDate || new Date().toISOString().split("T")[0];
  const toD = new Date(`${to}T00:00:00`);
  const fromD = new Date(toD.getFullYear(), toD.getMonth() - 5, 1);
  const from = `${fromD.getFullYear()}-${String(fromD.getMonth() + 1).padStart(2, "0")}-01`;

  const { data, isLoading } = useQuery({
    queryKey: ["capacity-trend", projectId, from, to],
    queryFn: () =>
      capacityUtilizationApi.getAggregate({
        projectId,
        periodType: "MONTHLY",
        from,
        to,
        groupBy: "RESOURCE_TYPE",
      }),
    enabled: !!projectId && open,
  });

  const buckets = data?.data?.buckets ?? [];

  return (
    <div className="mb-4 rounded-lg border border-border bg-surface/50">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center gap-2 px-4 py-2.5 text-left"
      >
        {open ? (
          <ChevronDown size={16} className="text-text-secondary" />
        ) : (
          <ChevronRight size={16} className="text-text-secondary" />
        )}
        <TrendingUp size={16} className="text-accent" />
        <span className="text-sm font-semibold text-text-primary">Utilization Trend</span>
        <span className="text-xs text-text-muted">
          monthly efficiency &amp; cost impact — last 6 months to the To date
        </span>
      </button>

      {open && (
        <div className="border-t border-border px-4 py-4">
          {isLoading ? (
            <p className="text-sm text-text-muted">Loading trend…</p>
          ) : buckets.length === 0 ? (
            <p className="text-sm text-text-muted">No data in this period.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-[560px] text-sm">
                <thead>
                  <tr className="text-left text-xs uppercase tracking-wide text-text-muted">
                    <th className="py-1 pr-6 font-semibold">Month</th>
                    <th className="py-1 pr-6 font-semibold">Manpower eff.</th>
                    <th className="py-1 pr-6 font-semibold">Equipment eff.</th>
                    <th className="py-1 text-right font-semibold">Cost impact</th>
                  </tr>
                </thead>
                <tbody>
                  {buckets.map((b) => {
                    const cost = costImpact(b.manpower) + costImpact(b.equipment);
                    return (
                      <tr key={b.label} className="border-t border-border/60">
                        <td className="py-1.5 pr-6 text-text-primary">{b.label}</td>
                        <td className="py-1.5 pr-6">
                          <EffCell section={b.manpower} />
                        </td>
                        <td className="py-1.5 pr-6">
                          <EffCell section={b.equipment} />
                        </td>
                        <td
                          className={`py-1.5 text-right ${
                            cost > 0 ? "text-danger" : cost < 0 ? "text-success" : "text-text-muted"
                          }`}
                        >
                          {cost === 0
                            ? "—"
                            : `${cost > 0 ? "overrun" : "saved"} ${money(Math.abs(cost), { decimals: 0 })}`}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
              <p className="mt-2 text-xs text-text-muted">
                Efficiency = each month&apos;s budget days ÷ counted days from the same engine as the
                tables below; cost impact = manpower + equipment implication for the month.
              </p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function costImpact(section: CapacitySection | null): number {
  return section?.totalCumulative?.costImplication ?? 0;
}

function EffCell({ section }: { section: CapacitySection | null }) {
  const pct = section?.totalCumulative?.utilizationPct ?? null;
  return (
    <span
      className={`inline-block rounded px-2 py-0.5 text-xs font-semibold ${utilBand(pct)}`}
    >
      {pct === null ? "—" : `${pct.toLocaleString("en-IN", { maximumFractionDigits: 1 })} %`}
    </span>
  );
}
