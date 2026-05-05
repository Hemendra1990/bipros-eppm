"use client";

import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import Link from "next/link";
import { ArrowLeft, X } from "lucide-react";
import { baselineApi } from "@/lib/api/baselineApi";
import { projectApi } from "@/lib/api/projectApi";
import { getErrorMessage } from "@/lib/utils/error";

type Slot = "PRIMARY" | "SECONDARY" | "TERTIARY";

const SLOT_DESCRIPTIONS: Record<Slot, string> = {
  PRIMARY:
    "Drives the variance dashboard, Gantt overlay, and EVM by default. This is the baseline you compare actual progress against day to day.",
  SECONDARY:
    "A second comparison reference — useful for showing impact against a prior plan after a Variation Order or scope change.",
  TERTIARY:
    "A stable historical baseline kept alongside the live Primary. Often the original contract baseline, never refreshed.",
};

/**
 * Phase 3: P6-style Assign Baselines page. Three independent slot cards. Each card shows the
 * currently-assigned baseline (if any) plus a dropdown of all this project's baselines so the
 * planner can swap. A baseline can be assigned to multiple slots simultaneously (P6 allows it).
 */
export default function AssignBaselinesPage() {
  const params = useParams();
  const router = useRouter();
  const projectId = params.projectId as string;
  const qc = useQueryClient();

  const { data: projectData } = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => projectApi.getProject(projectId),
    enabled: !!projectId,
  });

  const { data: baselinesData, isLoading } = useQuery({
    queryKey: ["baselines", projectId],
    queryFn: () => baselineApi.listBaselines(projectId),
    enabled: !!projectId,
  });

  const project = projectData?.data;
  const baselines = baselinesData?.data ?? [];

  const assignMutation = useMutation({
    mutationFn: (vars: { baselineId: string; slot: Slot }) =>
      baselineApi.assignBaselineToSlot(projectId, vars.baselineId, vars.slot),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["project", projectId] });
      qc.invalidateQueries({ queryKey: ["baselines", projectId] });
      toast.success("Baseline slot updated");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Failed to assign baseline")),
  });

  const clearMutation = useMutation({
    mutationFn: (slot: Slot) => baselineApi.clearBaselineSlot(projectId, slot),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["project", projectId] });
      qc.invalidateQueries({ queryKey: ["baselines", projectId] });
      toast.success("Slot cleared");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Failed to clear slot")),
  });

  const slots: Array<{ slot: Slot; assigned: string | null | undefined }> = project
    ? [
        { slot: "PRIMARY", assigned: project.primaryBaselineId },
        { slot: "SECONDARY", assigned: project.secondaryBaselineId },
        { slot: "TERTIARY", assigned: project.tertiaryBaselineId },
      ]
    : [];

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Link
          href={`/projects/${projectId}?tab=baselines`}
          className="inline-flex items-center gap-1.5 rounded-md border border-border px-3 py-1.5 text-sm text-text-secondary hover:bg-surface-hover/50"
        >
          <ArrowLeft size={14} />
          Back to baselines
        </Link>
        <h1 className="text-xl font-semibold text-text-primary">Assign Baselines</h1>
      </div>

      <p className="max-w-2xl text-sm text-text-secondary">
        Each project has three slots — Primary, Secondary, Tertiary — that can hold a different
        baseline at the same time. Variance, Gantt overlay, and EVM use the <strong>Primary</strong>{" "}
        slot by default. Use Secondary and Tertiary to keep additional comparison points without
        losing the live one.
      </p>

      {isLoading || !project ? (
        <div className="text-center text-text-muted">Loading project and baselines…</div>
      ) : baselines.length === 0 ? (
        <div className="rounded-lg border border-border bg-surface/50 p-6 text-center">
          <p className="text-sm text-text-muted">
            This project has no baselines yet. Go back and create one before assigning slots.
          </p>
        </div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-3">
          {slots.map(({ slot, assigned }) => {
            const current = assigned ? baselines.find((b) => b.id === assigned) ?? null : null;
            const isPending = assignMutation.isPending || clearMutation.isPending;
            return (
              <div
                key={slot}
                className="rounded-lg border border-border bg-surface/50 p-5 shadow-sm"
              >
                <div className="mb-2 flex items-center justify-between gap-2">
                  <h2 className="text-sm font-semibold uppercase tracking-wider text-accent">
                    {slot}
                  </h2>
                  {current && (
                    <button
                      type="button"
                      onClick={() => clearMutation.mutate(slot)}
                      disabled={isPending}
                      className="inline-flex items-center gap-1 rounded-md p-1 text-text-secondary hover:bg-surface-hover/50 hover:text-text-primary disabled:opacity-60"
                      title="Detach the baseline from this slot. Other slots are unaffected."
                    >
                      <X size={14} />
                    </button>
                  )}
                </div>
                <p className="mb-3 text-xs text-text-muted">{SLOT_DESCRIPTIONS[slot]}</p>

                <div className="mb-3 rounded-md border border-border bg-surface-active/30 p-3">
                  {current ? (
                    <div>
                      <div className="text-sm font-medium text-text-primary">{current.name}</div>
                      <div className="mt-0.5 text-xs text-text-secondary">
                        {current.baselineType} · {current.baselineDate} ·{" "}
                        {current.totalActivities ?? "?"} activities
                      </div>
                      {current.description && (
                        <div className="mt-2 text-xs italic text-text-muted">
                          {current.description}
                        </div>
                      )}
                    </div>
                  ) : (
                    <div className="text-sm italic text-text-muted">No baseline assigned</div>
                  )}
                </div>

                <label className="block text-xs font-medium text-text-secondary">
                  Assign baseline
                </label>
                <select
                  value={current?.id ?? ""}
                  disabled={isPending}
                  onChange={(e) => {
                    const baselineId = e.target.value;
                    if (!baselineId) {
                      clearMutation.mutate(slot);
                    } else {
                      assignMutation.mutate({ baselineId, slot });
                    }
                  }}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none disabled:opacity-60"
                >
                  <option value="">— None —</option>
                  {baselines.map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.name} ({b.baselineType})
                    </option>
                  ))}
                </select>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
