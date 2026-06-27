"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { ListTodo, Plus, Trash2, Eye } from "lucide-react";
import { baselineApi } from "@/lib/api/baselineApi";
import { projectApi } from "@/lib/api/projectApi";
import { getErrorMessage } from "@/lib/utils/error";
import { useProjectCurrency } from "@/lib/currency/ProjectCurrencyProvider";
import { EmptyState } from "@/components/common/EmptyState";
import { VarianceDashboard } from "@/components/baseline/VarianceDashboard";
import { ScheduleComparisonTable } from "@/components/baseline/ScheduleComparisonTable";
import { ScheduleVarianceSection } from "@/components/reports/ScheduleVarianceSection";
import { CostVarianceSection } from "@/components/reports/CostVarianceSection";
import type { BaselineResponse, BaselineVarianceRow } from "@/lib/types";

/**
 * Self-contained Baselines view: owns its data wiring (baselines list, project, and the
 * create/activate/delete/restore/update mutations) and renders the presentational BaselinesTab.
 *
 * Used in two places so the same UI is reachable both as the `?tab=baselines` query tab on the
 * project page and as the dedicated `/projects/[projectId]/baselines` route (linked from the
 * "More" menu). Keep all baseline logic here — the project page no longer carries it.
 */
export function BaselinesPanel({ projectId }: { projectId: string }) {
  const queryClient = useQueryClient();

  const { data: projectData } = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => projectApi.getProject(projectId),
  });

  const { data: baselinesData, isLoading: isLoadingBaselines, refetch: refetchBaselines } = useQuery({
    queryKey: ["baselines", projectId],
    queryFn: () => baselineApi.listBaselines(projectId),
  });

  const createBaselineMutation = useMutation({
    mutationFn: (data: { name: string; baselineType: string }) =>
      baselineApi.createBaseline(projectId, data as { name: string; baselineType: "PROJECT" | "PRIMARY" | "SECONDARY" | "TERTIARY"; description?: string }),
    onSuccess: () => {
      refetchBaselines();
      toast.success("Baseline created successfully");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to create baseline"));
    },
  });

  const setActiveBaselineMutation = useMutation({
    mutationFn: (baselineId: string) => baselineApi.setActiveBaseline(projectId, baselineId),
    onSuccess: () => {
      refetchBaselines();
      queryClient.invalidateQueries({ queryKey: ["project", projectId] });
      toast.success("Baseline set as active");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to activate baseline"));
    },
  });

  const deleteBaselineMutation = useMutation({
    mutationFn: (baselineId: string) => baselineApi.deleteBaseline(projectId, baselineId),
    onSuccess: () => {
      refetchBaselines();
      toast.success("Baseline deleted");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to delete baseline"));
    },
  });

  // Phase 4.1: Restore Baseline. Destructive — overwrites planned dates / durations /
  // relationships on the live project from the snapshot. Actuals are preserved.
  const restoreBaselineMutation = useMutation({
    mutationFn: (baselineId: string) => baselineApi.restoreBaseline(projectId, baselineId),
    onSuccess: () => {
      refetchBaselines();
      queryClient.invalidateQueries({ queryKey: ["activities", projectId] });
      queryClient.invalidateQueries({ queryKey: ["relationships", projectId] });
      queryClient.invalidateQueries({ queryKey: ["critical-path", projectId] });
      toast.success("Project restored from baseline");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to restore baseline"));
    },
  });

  // Phase 4.2: Selective Update Baseline. The button below runs with empty filters which
  // refreshes every activity + relationship — equivalent to P6's "Update" with all defaults.
  // The endpoint supports narrower scopes via UpdateBaselineRequest; a richer filter dialog
  // can be wired in a follow-up round.
  const updateBaselineMutation = useMutation({
    mutationFn: (baselineId: string) => baselineApi.updateBaseline(projectId, baselineId, {}),
    onSuccess: () => {
      refetchBaselines();
      queryClient.invalidateQueries({ queryKey: ["baseline-detail", projectId] });
      toast.success("Baseline updated from current schedule");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to update baseline"));
    },
  });

  return (
    <BaselinesTab
      projectId={projectId}
      baselines={baselinesData?.data ?? []}
      activeBaselineId={projectData?.data?.activeBaselineId ?? null}
      isLoading={isLoadingBaselines}
      onCreateBaseline={(data) => createBaselineMutation.mutate(data)}
      isCreating={createBaselineMutation.isPending}
      onDeleteBaseline={(baselineId) => deleteBaselineMutation.mutate(baselineId)}
      isDeleting={deleteBaselineMutation.isPending}
      onSetActiveBaseline={(baselineId) => setActiveBaselineMutation.mutate(baselineId)}
      isActivating={setActiveBaselineMutation.isPending}
      activatingBaselineId={setActiveBaselineMutation.variables ?? null}
      onRestoreBaseline={(baselineId) => restoreBaselineMutation.mutate(baselineId)}
      isRestoring={restoreBaselineMutation.isPending}
      restoringBaselineId={restoreBaselineMutation.variables ?? null}
      onUpdateBaseline={(baselineId) => updateBaselineMutation.mutate(baselineId)}
      isUpdatingBaseline={updateBaselineMutation.isPending}
      updatingBaselineId={updateBaselineMutation.variables ?? null}
    />
  );
}

function BaselinesTab({
  projectId,
  baselines,
  activeBaselineId,
  isLoading,
  onCreateBaseline,
  isCreating,
  onDeleteBaseline,
  isDeleting,
  onSetActiveBaseline,
  isActivating,
  activatingBaselineId,
  onRestoreBaseline,
  isRestoring,
  restoringBaselineId,
  onUpdateBaseline,
  isUpdatingBaseline,
  updatingBaselineId,
}: {
  projectId: string;
  baselines: BaselineResponse[];
  activeBaselineId: string | null;
  isLoading: boolean;
  onCreateBaseline: (data: { name: string; baselineType: string; description?: string }) => void;
  isCreating: boolean;
  onDeleteBaseline: (baselineId: string) => void;
  isDeleting: boolean;
  onSetActiveBaseline: (baselineId: string) => void;
  onRestoreBaseline: (baselineId: string) => void;
  isRestoring: boolean;
  restoringBaselineId: string | null;
  onUpdateBaseline: (baselineId: string) => void;
  isUpdatingBaseline: boolean;
  updatingBaselineId: string | null;
  isActivating: boolean;
  activatingBaselineId: string | null;
}) {
  const { money } = useProjectCurrency();
  const [varianceTab, setVarianceTab] = useState<"schedule" | "cost">("schedule");
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({ name: "", description: "", baselineType: "PROJECT" });
  const [expandedBaselineId, setExpandedBaselineId] = useState<string | null>(null);
  const [varianceData, setVarianceData] = useState<Record<string, BaselineVarianceRow[]>>({});
  const [loadingVarianceId, setLoadingVarianceId] = useState<string | null>(null);
  const [comparisonBaselineId, setComparisonBaselineId] = useState<string | null>(null);
  const [comparisonData, setComparisonData] = useState<Record<string, any[]>>({});
  const [loadingComparisonId, setLoadingComparisonId] = useState<string | null>(null);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (formData.name.trim()) {
      onCreateBaseline({
        name: formData.name,
        baselineType: formData.baselineType,
        description: formData.description.trim() || undefined,
      });
      setFormData({ name: "", description: "", baselineType: "PROJECT" });
      setShowForm(false);
    }
  };

  const handleViewVariance = async (baselineId: string) => {
    if (expandedBaselineId === baselineId) {
      setExpandedBaselineId(null);
      return;
    }

    if (varianceData[baselineId]) {
      setExpandedBaselineId(baselineId);
      return;
    }

    setLoadingVarianceId(baselineId);
    try {
      const response = await baselineApi.getVariance(projectId, baselineId);
      if (response?.data) {
        setVarianceData((prev) => ({
          ...prev,
          [baselineId]: response.data!,
        }));
        setExpandedBaselineId(baselineId);
      }
    } catch (error) {
      console.error("Failed to load variance data:", error);
    } finally {
      setLoadingVarianceId(null);
    }
  };

  const handleCompareSchedule = async (baselineId: string) => {
    if (comparisonBaselineId === baselineId) {
      setComparisonBaselineId(null);
      return;
    }

    if (comparisonData[baselineId]) {
      setComparisonBaselineId(baselineId);
      return;
    }

    setLoadingComparisonId(baselineId);
    try {
      const response = await baselineApi.getScheduleComparison(projectId, baselineId);
      if (response?.data) {
        setComparisonData((prev) => ({
          ...prev,
          [baselineId]: response.data!,
        }));
        setComparisonBaselineId(baselineId);
      }
    } catch (error) {
      console.error("Failed to load schedule comparison data:", error);
    } finally {
      setLoadingComparisonId(null);
    }
  };

  if (isLoading) {
    return <div className="text-center text-text-muted">Loading baselines...</div>;
  }

  return (
    <div className="space-y-6">
      {!showForm && (
        <div className="flex flex-wrap items-center gap-3">
          <button
            onClick={() => setShowForm(true)}
            className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-accent-foreground hover:bg-accent-hover"
          >
            <Plus size={16} />
            Create Baseline
          </button>
          <Link
            href={`/projects/${projectId}/baselines/assign`}
            className="inline-flex items-center gap-2 rounded-md border border-border bg-surface/60 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50 hover:text-text-primary"
            title="Assign baselines to PRIMARY / SECONDARY / TERTIARY slots (P6-style)"
          >
            Assign Baselines
          </Link>
        </div>
      )}

      {showForm && (
        <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
          <h3 className="mb-4 text-lg font-semibold text-text-primary">Create New Baseline</h3>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-text-secondary">Name</label>
              <input
                type="text"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary placeholder-text-muted focus:border-accent focus:outline-none"
                placeholder="Baseline name"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-secondary">Description</label>
              <textarea
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                rows={3}
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary placeholder-text-muted focus:border-accent focus:outline-none"
                placeholder="Optional notes — e.g. 'after VO-12 approved', 'pre-monsoon plan'"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-secondary">Type</label>
              <select
                value={formData.baselineType}
                onChange={(e) => setFormData({ ...formData, baselineType: e.target.value })}
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
              >
                <option value="PROJECT">PROJECT</option>
                <option value="PRIMARY">PRIMARY</option>
                <option value="SECONDARY">SECONDARY</option>
                <option value="TERTIARY">TERTIARY</option>
              </select>
            </div>
            <div className="flex gap-3">
              <button
                type="submit"
                disabled={isCreating}
                className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-accent-foreground hover:bg-accent-hover disabled:opacity-50"
              >
                {isCreating ? "Creating..." : "Create"}
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="rounded-md border border-border px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {baselines.length === 0 ? (
        <EmptyState
          icon={ListTodo}
          title="No baselines"
          description="This project has no baselines yet. Create a baseline to start tracking project variance."
        />
      ) : (
        <div className="space-y-4">
          {baselines.map((baseline) => {
            const isActive = activeBaselineId === baseline.id;
            return (
            <div key={baseline.id} className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <h3 className="text-lg font-semibold text-text-primary">{baseline.name}</h3>
                    {isActive && (
                      <span className="inline-flex items-center gap-1 rounded-md border border-gold/40 bg-gold-tint px-2 py-0.5 text-[11px] font-semibold uppercase tracking-wide text-gold-ink">
                        <span className="h-1.5 w-1.5 rounded-full bg-gold" />
                        Active
                      </span>
                    )}
                  </div>
                  <div className="mt-2 space-y-1 text-sm text-text-secondary">
                    <p>Type: {baseline.baselineType}</p>
                    <p>Date: {new Date(baseline.baselineDate).toLocaleDateString()}</p>
                    <p>Activities: {baseline.totalActivities}</p>
                    {baseline.totalCost > 0 && <p>Total Cost: {money(baseline.totalCost)}</p>}
                  </div>
                </div>
                <div className="flex flex-wrap justify-end gap-2">
                  {!isActive && (
                    <button
                      onClick={() => onSetActiveBaseline(baseline.id)}
                      disabled={isActivating && activatingBaselineId === baseline.id}
                      className="inline-flex items-center gap-2 rounded-md border border-gold/40 bg-gold-tint px-3 py-2 text-sm font-medium text-gold-ink hover:bg-gold/20 disabled:opacity-50"
                    >
                      {isActivating && activatingBaselineId === baseline.id ? "Setting…" : "Set as active"}
                    </button>
                  )}
                  <button
                    onClick={() => handleViewVariance(baseline.id)}
                    disabled={loadingVarianceId === baseline.id}
                    className="inline-flex items-center gap-2 rounded-md bg-surface-hover/50 px-3 py-2 text-sm font-medium text-text-secondary hover:bg-surface-active/50 disabled:opacity-50"
                  >
                    <Eye size={16} />
                    {expandedBaselineId === baseline.id ? "Hide Variance" : "View Variance"}
                  </button>
                  <button
                    onClick={() => handleCompareSchedule(baseline.id)}
                    disabled={loadingComparisonId === baseline.id}
                    className="inline-flex items-center gap-2 rounded-md bg-accent/10 px-3 py-2 text-sm font-medium text-accent hover:bg-accent-hover/20 disabled:opacity-50"
                  >
                    <Eye size={16} />
                    {comparisonBaselineId === baseline.id ? "Hide Compare" : "Compare"}
                  </button>
                  <button
                    onClick={() => {
                      if (
                        window.confirm(
                          `Refresh "${baseline.name}" with the current project's dates, durations, costs, and relationships?\n\nThis is the P6 "Update Baseline" action. Existing snapshot entries are overwritten; new activities are inserted.`
                        )
                      ) {
                        onUpdateBaseline(baseline.id);
                      }
                    }}
                    disabled={isUpdatingBaseline && updatingBaselineId === baseline.id}
                    className="inline-flex items-center gap-2 rounded-md bg-accent/10 px-3 py-2 text-sm font-medium text-accent hover:bg-accent-hover/20 disabled:opacity-50"
                    title="Update Baseline: re-snapshot the current project state into this baseline. Use the API directly for selective filters."
                  >
                    {isUpdatingBaseline && updatingBaselineId === baseline.id ? "Updating…" : "Update"}
                  </button>
                  <button
                    onClick={() => {
                      if (
                        window.confirm(
                          `Restore the project's planned dates, durations, and relationships from "${baseline.name}"?\n\nActual progress (start/finish dates, % complete) will be preserved. This action is audit-logged but not undoable.`
                        )
                      ) {
                        onRestoreBaseline(baseline.id);
                      }
                    }}
                    disabled={isRestoring && restoringBaselineId === baseline.id}
                    className="inline-flex items-center gap-2 rounded-md bg-warning/10 px-3 py-2 text-sm font-medium text-warning hover:bg-warning/20 disabled:opacity-50"
                    title="Restore: overwrite planned dates / durations / relationships from this baseline. Actuals preserved."
                  >
                    {isRestoring && restoringBaselineId === baseline.id ? "Restoring…" : "Restore"}
                  </button>
                  <button
                    onClick={() => {
                      if (window.confirm("Are you sure you want to delete this baseline?")) {
                        onDeleteBaseline(baseline.id);
                      }
                    }}
                    disabled={isDeleting}
                    className="inline-flex items-center gap-2 rounded-md bg-danger/10 px-3 py-2 text-sm font-medium text-danger hover:bg-danger/20 disabled:opacity-50"
                  >
                    <Trash2 size={16} />
                    Delete
                  </button>
                </div>
              </div>

              {expandedBaselineId === baseline.id && varianceData[baseline.id] && (
                <div className="mt-6 border-t border-border pt-6">
                  <h4 className="mb-4 font-semibold text-text-primary">Variance Dashboard</h4>
                  <VarianceDashboard data={varianceData[baseline.id]} />
                </div>
              )}

              {comparisonBaselineId === baseline.id && comparisonData[baseline.id] && (
                <div className="mt-6 border-t border-border pt-6">
                  <h4 className="mb-4 font-semibold text-text-primary">Schedule Comparison</h4>
                  <ScheduleComparisonTable data={comparisonData[baseline.id]} />
                </div>
              )}
            </div>
            );
          })}
        </div>
      )}

      {activeBaselineId && (
        <div className="mt-8 space-y-4">
          <div className="flex flex-wrap items-end justify-between gap-3 border-t border-hairline pt-6">
            <div>
              <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep mb-1">
                Variance · vs active baseline
              </div>
              <h2 className="font-display text-2xl font-semibold tracking-tight text-charcoal">
                Schedule &amp; cost variance
              </h2>
            </div>
            <div className="inline-flex rounded-lg border border-hairline bg-ivory p-0.5">
              <button
                type="button"
                onClick={() => setVarianceTab("schedule")}
                className={`rounded-md px-4 py-2 text-sm font-semibold transition-colors ${
                  varianceTab === "schedule"
                    ? "bg-paper text-charcoal shadow-sm"
                    : "text-slate hover:text-charcoal"
                }`}
              >
                Schedule
              </button>
              <button
                type="button"
                onClick={() => setVarianceTab("cost")}
                className={`rounded-md px-4 py-2 text-sm font-semibold transition-colors ${
                  varianceTab === "cost"
                    ? "bg-paper text-charcoal shadow-sm"
                    : "text-slate hover:text-charcoal"
                }`}
              >
                Cost
              </button>
            </div>
          </div>
          {varianceTab === "schedule" ? (
            <ScheduleVarianceSection projectId={projectId} baselineId={activeBaselineId} />
          ) : (
            <CostVarianceSection projectId={projectId} baselineId={activeBaselineId} />
          )}
        </div>
      )}
    </div>
  );
}
