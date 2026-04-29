"use client";

import { useState, useMemo } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { getErrorMessage } from "@/lib/utils/error";
import { Plus, Trash2, TrendingUp, DollarSign, BarChart3, Activity } from "lucide-react";
import {
  periodPerformanceApi,
  type StorePeriodPerformance,
  type FinancialPeriod,
  type CreateStorePeriodPerformanceRequest,
} from "@/lib/api/periodPerformanceApi";
import { activityApi, type ActivityResponse } from "@/lib/api/activityApi";
import { KpiTile } from "@/components/common/KpiTile";

const INR_PER_CRORE = 10_000_000;

function formatCrores(val: number | null | undefined): string {
  const v = (val ?? 0) / INR_PER_CRORE;
  return `₹${v.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}cr`;
}

function sumField(records: StorePeriodPerformance[], field: keyof StorePeriodPerformance): number {
  return records.reduce((acc, r) => acc + ((r[field] as number | null) ?? 0), 0);
}

const EMPTY_FORM: Omit<CreateStorePeriodPerformanceRequest, "projectId" | "financialPeriodId"> = {
  activityId: null,
  actualLaborCost: null,
  actualNonlaborCost: null,
  actualMaterialCost: null,
  actualExpenseCost: null,
  actualLaborUnits: null,
  actualNonlaborUnits: null,
  actualMaterialUnits: null,
  earnedValueCost: null,
  plannedValueCost: null,
};

export function PeriodPerformanceTab({ projectId }: { projectId: string }) {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [selectedPeriodId, setSelectedPeriodId] = useState<string>("");
  const [form, setForm] = useState(EMPTY_FORM);

  const { data: periodsData, isLoading: isLoadingPeriods } = useQuery({
    queryKey: ["financial-periods"],
    queryFn: () => periodPerformanceApi.getAllFinancialPeriods(),
  });

  const { data: sppData, isLoading: isLoadingSpp } = useQuery({
    queryKey: ["spp", projectId],
    queryFn: () => periodPerformanceApi.getProjectPeriodPerformance(projectId),
  });

  const { data: activitiesData } = useQuery({
    queryKey: ["activities", projectId],
    queryFn: () => activityApi.listActivities(projectId, 0, 200),
  });

  const periods: FinancialPeriod[] = periodsData?.data ?? [];
  const records: StorePeriodPerformance[] = sppData?.data ?? [];
  const activities: ActivityResponse[] = activitiesData?.data?.content ?? [];

  const activityMap = useMemo(() => {
    const m = new Map<string, ActivityResponse>();
    for (const a of (activitiesData?.data?.content ?? [])) m.set(a.id, a);
    return m;
  }, [activitiesData]);

  const periodMap = useMemo(() => {
    const m = new Map<string, FinancialPeriod>();
    for (const p of (periodsData?.data ?? [])) m.set(p.id, p);
    return m;
  }, [periodsData]);

  const createMutation = useMutation({
    mutationFn: (data: CreateStorePeriodPerformanceRequest) =>
      periodPerformanceApi.createStorePeriodPerformance(projectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["spp", projectId] });
      toast.success("Period performance recorded");
      setShowForm(false);
      setForm(EMPTY_FORM);
      setSelectedPeriodId("");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to save period performance"));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (sppId: string) =>
      periodPerformanceApi.deleteStorePeriodPerformance(projectId, sppId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["spp", projectId] });
      toast.success("Record deleted");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to delete record"));
    },
  });

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedPeriodId) {
      toast.error("Please select a financial period");
      return;
    }
    createMutation.mutate({
      projectId,
      financialPeriodId: selectedPeriodId,
      ...form,
    });
  }

  function parseNum(val: string): number | null {
    const n = parseFloat(val);
    return isNaN(n) ? null : n;
  }

  const totalActualCost =
    sumField(records, "actualLaborCost") +
    sumField(records, "actualNonlaborCost") +
    sumField(records, "actualMaterialCost") +
    sumField(records, "actualExpenseCost");

  const totalEv = sumField(records, "earnedValueCost");
  const totalPv = sumField(records, "plannedValueCost");
  const totalLaborUnits = sumField(records, "actualLaborUnits");

  return (
    <div className="space-y-6 px-6 pb-8">
      {/* Summary KPIs */}
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <KpiTile
          label="Total Actual Cost"
          value={formatCrores(totalActualCost)}
          hint="Sum across all periods"
          tone="danger"
          icon={<DollarSign size={14} />}
        />
        <KpiTile
          label="Earned Value"
          value={formatCrores(totalEv)}
          hint="Cumulative EV (SPP)"
          tone="success"
          icon={<TrendingUp size={14} />}
        />
        <KpiTile
          label="Planned Value"
          value={formatCrores(totalPv)}
          hint="Cumulative PV (SPP)"
          tone="accent"
          icon={<BarChart3 size={14} />}
        />
        <KpiTile
          label="Labor Units"
          value={totalLaborUnits.toLocaleString("en-IN", { maximumFractionDigits: 1 })}
          hint="Actual labour units (all periods)"
          tone="default"
          icon={<Activity size={14} />}
        />
      </div>

      {/* Toolbar */}
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-text-secondary">
          Period Performance Records
        </h2>
        <button
          onClick={() => setShowForm(!showForm)}
          className="flex items-center gap-1.5 rounded-md bg-accent px-3 py-1.5 text-xs font-semibold text-white hover:bg-accent/90"
        >
          <Plus size={14} />
          Record Period
        </button>
      </div>

      {/* Entry Form */}
      {showForm && (
        <form
          onSubmit={handleSubmit}
          className="rounded-xl border border-border bg-surface/60 p-5 shadow-sm space-y-4"
        >
          <h3 className="text-sm font-semibold text-text-primary">New Period Performance Entry</h3>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs font-medium text-text-secondary">
                Financial Period <span className="text-danger">*</span>
              </label>
              {isLoadingPeriods ? (
                <div className="h-9 animate-pulse rounded-md bg-surface-hover/50" />
              ) : (
                <select
                  value={selectedPeriodId}
                  onChange={(e) => setSelectedPeriodId(e.target.value)}
                  required
                  className="w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none"
                >
                  <option value="">— Select period —</option>
                  {periods.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} ({p.startDate} → {p.endDate}){p.isClosed ? " [Closed]" : ""}
                    </option>
                  ))}
                </select>
              )}
            </div>

            <div>
              <label className="mb-1 block text-xs font-medium text-text-secondary">
                Activity (optional)
              </label>
              <select
                value={form.activityId ?? ""}
                onChange={(e) =>
                  setForm((f) => ({ ...f, activityId: e.target.value || null }))
                }
                className="w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none"
              >
                <option value="">— Project-level (no activity) —</option>
                {activities.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.code} — {a.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            {(
              [
                { field: "actualLaborCost", label: "Actual Labor Cost (₹)" },
                { field: "actualNonlaborCost", label: "Actual Non-Labor Cost (₹)" },
                { field: "actualMaterialCost", label: "Actual Material Cost (₹)" },
                { field: "actualExpenseCost", label: "Actual Expense Cost (₹)" },
              ] as const
            ).map(({ field, label }) => (
              <div key={field}>
                <label className="mb-1 block text-xs font-medium text-text-secondary">{label}</label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  value={form[field] ?? ""}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, [field]: parseNum(e.target.value) }))
                  }
                  placeholder="0.00"
                  className="w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-accent focus:outline-none"
                />
              </div>
            ))}
          </div>

          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
            {(
              [
                { field: "actualLaborUnits", label: "Labor Units" },
                { field: "actualNonlaborUnits", label: "Non-Labor Units" },
                { field: "actualMaterialUnits", label: "Material Units" },
              ] as const
            ).map(({ field, label }) => (
              <div key={field}>
                <label className="mb-1 block text-xs font-medium text-text-secondary">{label}</label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  value={form[field] ?? ""}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, [field]: parseNum(e.target.value) }))
                  }
                  placeholder="0.00"
                  className="w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-accent focus:outline-none"
                />
              </div>
            ))}
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {(
              [
                { field: "earnedValueCost", label: "Earned Value Cost (₹)" },
                { field: "plannedValueCost", label: "Planned Value Cost (₹)" },
              ] as const
            ).map(({ field, label }) => (
              <div key={field}>
                <label className="mb-1 block text-xs font-medium text-text-secondary">{label}</label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  value={form[field] ?? ""}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, [field]: parseNum(e.target.value) }))
                  }
                  placeholder="0.00"
                  className="w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-accent focus:outline-none"
                />
              </div>
            ))}
          </div>

          <div className="flex gap-3 pt-1">
            <button
              type="submit"
              disabled={createMutation.isPending}
              className="rounded-md bg-accent px-4 py-1.5 text-xs font-semibold text-white hover:bg-accent/90 disabled:opacity-60"
            >
              {createMutation.isPending ? "Saving…" : "Save"}
            </button>
            <button
              type="button"
              onClick={() => {
                setShowForm(false);
                setForm(EMPTY_FORM);
                setSelectedPeriodId("");
              }}
              className="rounded-md border border-border px-4 py-1.5 text-xs font-medium text-text-secondary hover:bg-surface-hover/50"
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      {/* Records Table */}
      {isLoadingSpp ? (
        <div className="space-y-2">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-10 animate-pulse rounded-md bg-surface-hover/50" />
          ))}
        </div>
      ) : records.length === 0 ? (
        <div className="rounded-xl border border-dashed border-border bg-surface-hover/20 py-12 text-center">
          <p className="text-sm text-text-muted">No period performance records yet.</p>
          <p className="mt-1 text-xs text-text-muted">
            Click &ldquo;Record Period&rdquo; to add the first entry.
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border bg-surface-hover/60">
                <th className="px-3 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  Period
                </th>
                <th className="px-3 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  Activity
                </th>
                <th className="px-3 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  Labor Cost
                </th>
                <th className="px-3 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  Non-Labor
                </th>
                <th className="px-3 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  Material
                </th>
                <th className="px-3 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  Expense
                </th>
                <th className="px-3 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  EV Cost
                </th>
                <th className="px-3 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  PV Cost
                </th>
                <th className="px-3 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  Labor Units
                </th>
                <th className="px-3 py-2.5 text-xs font-semibold uppercase tracking-wide text-text-secondary" />
              </tr>
            </thead>
            <tbody>
              {records.map((r) => {
                const period = periodMap.get(r.financialPeriodId);
                const activity = r.activityId ? activityMap.get(r.activityId) : null;
                return (
                  <tr key={r.id} className="border-b border-border hover:bg-surface-hover/40">
                    <td className="px-3 py-2 text-text-primary">
                      {period ? (
                        <span>
                          <span className="font-medium">{period.name}</span>
                          <span className="ml-1 text-xs text-text-muted">
                            ({period.startDate} → {period.endDate})
                          </span>
                        </span>
                      ) : (
                        <span className="text-text-muted text-xs font-mono">{r.financialPeriodId.slice(0, 8)}…</span>
                      )}
                    </td>
                    <td className="px-3 py-2 text-text-secondary">
                      {activity ? (
                        <span>
                          <span className="font-mono text-xs text-accent">{activity.code}</span>
                          {" "}{activity.name}
                        </span>
                      ) : (
                        <span className="italic text-text-muted">Project-level</span>
                      )}
                    </td>
                    <td className="px-3 py-2 text-right text-text-primary">
                      {r.actualLaborCost != null ? formatCrores(r.actualLaborCost) : "—"}
                    </td>
                    <td className="px-3 py-2 text-right text-text-secondary">
                      {r.actualNonlaborCost != null ? formatCrores(r.actualNonlaborCost) : "—"}
                    </td>
                    <td className="px-3 py-2 text-right text-text-secondary">
                      {r.actualMaterialCost != null ? formatCrores(r.actualMaterialCost) : "—"}
                    </td>
                    <td className="px-3 py-2 text-right text-text-secondary">
                      {r.actualExpenseCost != null ? formatCrores(r.actualExpenseCost) : "—"}
                    </td>
                    <td className="px-3 py-2 text-right text-success">
                      {r.earnedValueCost != null ? formatCrores(r.earnedValueCost) : "—"}
                    </td>
                    <td className="px-3 py-2 text-right text-accent">
                      {r.plannedValueCost != null ? formatCrores(r.plannedValueCost) : "—"}
                    </td>
                    <td className="px-3 py-2 text-right text-text-secondary">
                      {r.actualLaborUnits != null
                        ? r.actualLaborUnits.toLocaleString("en-IN", { maximumFractionDigits: 2 })
                        : "—"}
                    </td>
                    <td className="px-3 py-2 text-right">
                      <button
                        onClick={() => {
                          if (window.confirm("Delete this record?")) {
                            deleteMutation.mutate(r.id);
                          }
                        }}
                        disabled={deleteMutation.isPending}
                        className="text-danger hover:text-danger/80 disabled:opacity-50"
                        title="Delete"
                      >
                        <Trash2 size={14} />
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
