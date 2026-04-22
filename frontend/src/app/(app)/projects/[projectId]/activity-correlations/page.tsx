"use client";

import { useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import { activityApi } from "@/lib/api/activityApi";
import {
  activityCorrelationApi,
  type ActivityCorrelation,
} from "@/lib/api/activityCorrelationApi";
import { PageHeader } from "@/components/common/PageHeader";
import { TabTip } from "@/components/common/TabTip";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/common/EmptyState";

export default function ActivityCorrelationsPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const qc = useQueryClient();

  const [a, setA] = useState<string>("");
  const [b, setB] = useState<string>("");
  const [coef, setCoef] = useState<number>(0.5);

  // Fetch all activities (up to 1000) so users can pick from a dropdown.
  const { data: activitiesPage } = useQuery({
    queryKey: ["activities-for-correlation", projectId],
    queryFn: () => activityApi.listActivities(projectId, 0, 1000),
  });
  const activities = activitiesPage?.data?.content ?? [];
  const labelOf = useMemo(() => {
    const m = new Map<string, string>();
    for (const act of activities) m.set(act.id, `${act.code} — ${act.name}`);
    return (id: string) => m.get(id) ?? id.slice(0, 8);
  }, [activities]);

  const { data: correlations, isLoading } = useQuery({
    queryKey: ["activity-correlations", projectId],
    queryFn: () => activityCorrelationApi.list(projectId),
    retry: false,
  });

  const upsertMutation = useMutation({
    mutationFn: (body: ActivityCorrelation) => activityCorrelationApi.upsert(projectId, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["activity-correlations", projectId] });
      setA("");
      setB("");
      setCoef(0.5);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (pair: { aId: string; bId: string }) =>
      activityCorrelationApi.remove(projectId, pair.aId, pair.bId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["activity-correlations", projectId] }),
  });

  const canAdd = a && b && a !== b && Math.abs(coef) < 1 && !upsertMutation.isPending;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Activity Correlations"
        description="Pairwise duration correlations applied via Iman-Conover rank reshuffling in Monte Carlo"
      />

      <TabTip
        title="Activity duration correlations"
        description="When two activities' durations tend to move together (shared labour, common weather exposure, same vendor) their variance compounds on the critical path. Set a positive coefficient for activities that run long together, negative for ones where slowdown in one frees up resource for the other. Coefficients must be in (-1, 1); large values near ±1 may be auto-regularised if the matrix stops being positive semi-definite."
      />

      <div className="bg-slate-900/50 rounded-lg border border-slate-800 p-6">
        <h2 className="text-lg font-semibold text-white mb-4">Add correlation</h2>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
          <label className="block text-sm md:col-span-1">
            <span className="text-white">Activity A</span>
            <select
              value={a}
              onChange={(e) => setA(e.target.value)}
              className="mt-1 w-full px-2 py-2 border border-slate-700 rounded-md text-sm bg-slate-900 text-white"
            >
              <option value="">Select…</option>
              {activities.map((act) => (
                <option key={act.id} value={act.id}>
                  {act.code} — {act.name}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-sm md:col-span-1">
            <span className="text-white">Activity B</span>
            <select
              value={b}
              onChange={(e) => setB(e.target.value)}
              className="mt-1 w-full px-2 py-2 border border-slate-700 rounded-md text-sm bg-slate-900 text-white"
            >
              <option value="">Select…</option>
              {activities.map((act) => (
                <option key={act.id} value={act.id}>
                  {act.code} — {act.name}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-sm md:col-span-1">
            <span className="text-white">Coefficient ({coef.toFixed(2)})</span>
            <input
              type="range"
              min={-0.95}
              max={0.95}
              step={0.05}
              value={coef}
              onChange={(e) => setCoef(parseFloat(e.target.value))}
              className="w-full accent-blue-500 mt-2"
            />
          </label>
          <div className="flex items-end">
            <Button
              onClick={() =>
                upsertMutation.mutate({ activityAId: a, activityBId: b, coefficient: coef })
              }
              disabled={!canAdd}
            >
              {upsertMutation.isPending ? "Saving…" : "Add / update"}
            </Button>
          </div>
          {upsertMutation.isError && (
            <div className="md:col-span-4 text-sm text-red-400">
              {(upsertMutation.error as Error)?.message ?? "Save failed"}
            </div>
          )}
        </div>
      </div>

      <div className="bg-slate-900/50 rounded-lg border border-slate-800 p-6">
        <h2 className="text-lg font-semibold text-white mb-4">Configured correlations</h2>

        {isLoading && <p className="text-sm text-slate-400">Loading…</p>}

        {!isLoading && !correlations?.data?.length && (
          <EmptyState
            icon={Trash2 /* reuse any icon; visual filler */}
            title="No correlations configured"
            description="Add a pair above. With none configured, every activity duration is sampled independently."
          />
        )}

        {correlations?.data?.length ? (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-800 text-slate-300">
                  <th className="text-left py-2 px-3">Activity A</th>
                  <th className="text-left py-2 px-3">Activity B</th>
                  <th className="text-right py-2 px-3">Coefficient</th>
                  <th className="py-2 px-3"></th>
                </tr>
              </thead>
              <tbody>
                {correlations.data.map((c) => (
                  <tr key={c.id ?? `${c.activityAId}-${c.activityBId}`} className="border-b border-slate-800/50">
                    <td className="py-2 px-3 text-white">{labelOf(c.activityAId)}</td>
                    <td className="py-2 px-3 text-white">{labelOf(c.activityBId)}</td>
                    <td className="py-2 px-3 text-right text-white">{c.coefficient.toFixed(2)}</td>
                    <td className="py-2 px-3 text-right">
                      <button
                        className="text-red-400 hover:text-red-300 cursor-pointer"
                        onClick={() =>
                          deleteMutation.mutate({ aId: c.activityAId, bId: c.activityBId })
                        }
                        disabled={deleteMutation.isPending}
                        aria-label="Remove correlation"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </div>
    </div>
  );
}
