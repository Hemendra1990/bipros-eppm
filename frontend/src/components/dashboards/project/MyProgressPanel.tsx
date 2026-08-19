"use client";

import { useQuery } from "@tanstack/react-query";
import { ClipboardList } from "lucide-react";
import {
  SectionCard,
  EmptyBlock,
} from "@/components/common/dashboard/primitives";
import { projectInsightsApi } from "@/lib/api/projectInsightsApi";
import { useAuthStore } from "@/lib/state/store";
import { useMounted } from "@/lib/hooks/useMounted";

/**
 * "My Progress" (client ask, 2026-08-20): the caller's supervised activities with
 * quantity done today / this week / this month / cumulative (approved DPRs) and
 * % complete. Permission-gated (MY_PROGRESS.READ), not role-hardcoded — the panel
 * renders for whichever profiles hold the code.
 */
export function MyProgressPanel({ projectId }: { projectId: string }) {
  const mounted = useMounted();
  const hasPermission = useAuthStore((s) => s.hasPermission);
  const canSee = mounted && hasPermission("MY_PROGRESS.READ");

  const { data: rows = [], isLoading } = useQuery({
    queryKey: ["my-progress", projectId],
    queryFn: () => projectInsightsApi.getMyProgress(projectId),
    enabled: canSee && !!projectId,
  });

  if (!canSee) return null;

  const fmt = (n: number) =>
    n === 0 ? "—" : n.toLocaleString(undefined, { maximumFractionDigits: 2 });

  return (
    <SectionCard
      title="My Progress"
      subtitle="Quantities done on the activities you supervise · approved DPRs"
      icon={<ClipboardList size={16} />}
      accent
    >
      {isLoading ? (
        <EmptyBlock label="Loading…" />
      ) : rows.length === 0 ? (
        <EmptyBlock label="No activities are assigned to you as supervisor" />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-hairline">
          <table className="w-full min-w-[640px] text-sm">
            <thead className="bg-ivory/60 text-[11px] font-semibold uppercase tracking-wide text-slate">
              <tr>
                <th className="px-3 py-2 text-left">Activity / BOQ</th>
                <th className="px-3 py-2 text-right">Today</th>
                <th className="px-3 py-2 text-right">This week</th>
                <th className="px-3 py-2 text-right">This month</th>
                <th className="px-3 py-2 text-right">Till date</th>
                <th className="px-3 py-2 text-left" style={{ width: 140 }}>
                  Complete
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => {
                const pct = Math.max(0, Math.min(100, r.percentComplete ?? 0));
                return (
                  <tr key={r.activityId} className="border-t border-hairline">
                    <td className="px-3 py-2">
                      <div className="font-medium text-charcoal">{r.activityName}</div>
                      <div className="text-[11px] text-slate">
                        {r.boqItemNo ? `BOQ ${r.boqItemNo}` : ""}
                        {r.boqItemNo && r.unit ? " · " : ""}
                        {r.unit}
                      </div>
                    </td>
                    <td className="px-3 py-2 text-right tabular-nums text-charcoal">
                      {fmt(r.todayQty)}
                    </td>
                    <td className="px-3 py-2 text-right tabular-nums text-charcoal">
                      {fmt(r.weekQty)}
                    </td>
                    <td className="px-3 py-2 text-right tabular-nums text-charcoal">
                      {fmt(r.monthQty)}
                    </td>
                    <td className="px-3 py-2 text-right tabular-nums font-semibold text-charcoal">
                      {fmt(r.cumulativeQty)}
                    </td>
                    <td className="px-3 py-2">
                      <div className="flex items-center gap-2">
                        <div className="h-1.5 w-20 overflow-hidden rounded-full bg-hairline">
                          <div
                            className={`h-full rounded-full ${pct >= 100 ? "bg-emerald" : "bg-gold-deep"}`}
                            style={{ width: `${pct}%` }}
                          />
                        </div>
                        <span className="text-[11px] tabular-nums text-slate">
                          {pct.toFixed(0)}%
                        </span>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </SectionCard>
  );
}
