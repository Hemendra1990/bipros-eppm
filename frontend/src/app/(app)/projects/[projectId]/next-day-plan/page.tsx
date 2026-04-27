"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  nextDayPlanApi,
  type NextDayPlanResponse,
  type CreateNextDayPlanRequest,
} from "@/lib/api/nextDayPlanApi";
import { projectApi } from "@/lib/api/projectApi";
import { dailyActivityResourceOutputApi } from "@/lib/api/dailyActivityResourceOutputApi";
import { activityApi } from "@/lib/api/activityApi";
import { TabTip } from "@/components/common/TabTip";
import { chainageLabel, parseChainage } from "@/lib/format/chainage";
import { getErrorMessage } from "@/lib/utils/error";

interface PlanForm {
  reportDate: string;
  nextDayActivity: string;
  chainageFrom: string;
  chainageFromPreview: string;
  chainageTo: string;
  chainageToPreview: string;
  targetQty: string;
  unit: string;
  concerns: string;
  actionBy: string;
  dueDate: string;
}

const UNIT_OPTIONS = ["", "Cum", "MT", "Rm", "Each"];

const todayIso = () => new Date().toISOString().split("T")[0];

const initialFormState = (): PlanForm => ({
  reportDate: todayIso(),
  nextDayActivity: "",
  chainageFrom: "",
  chainageFromPreview: "",
  chainageTo: "",
  chainageToPreview: "",
  targetQty: "",
  unit: "",
  concerns: "",
  actionBy: "",
  dueDate: "",
});

export default function NextDayPlanPage() {
  const params = useParams();
  const projectId = params.projectId as string;

  const queryClient = useQueryClient();

  const { data: projectData } = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => projectApi.getProject(projectId),
    enabled: !!projectId,
  });
  const project = projectData?.data;

  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [appliedFrom, setAppliedFrom] = useState("");
  const [appliedTo, setAppliedTo] = useState("");

  useEffect(() => {
    if (!project) return;
    if (appliedFrom === "" && project.plannedStartDate) {
      setFromDate(project.plannedStartDate);
      setAppliedFrom(project.plannedStartDate);
    }
    if (appliedTo === "" && project.plannedFinishDate) {
      setToDate(project.plannedFinishDate);
      setAppliedTo(project.plannedFinishDate);
    }
  }, [project, appliedFrom, appliedTo]);

  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState<PlanForm>(initialFormState());
  const [error, setError] = useState<string | null>(null);

  const { data, isLoading, isError, error: queryError } = useQuery({
    queryKey: ["next-day-plan", projectId, appliedFrom, appliedTo],
    queryFn: () =>
      nextDayPlanApi.list(projectId, { from: appliedFrom, to: appliedTo }),
    enabled: !!projectId && !!appliedFrom && !!appliedTo,
  });

  const plans: NextDayPlanResponse[] = data?.data ?? [];

  // Pull yesterday's daily outputs + the project's activities (for code↔name resolution).
  // This powers the "Carry forward from yesterday" button: clicking it pre-populates the form
  // with the most recent daily output, so the planner doesn't re-key activity / unit / qty.
  const { data: yesterdayOutputsData } = useQuery({
    queryKey: ["daily-outputs", projectId, "carry-forward"],
    queryFn: () => dailyActivityResourceOutputApi.list(projectId),
    enabled: !!projectId,
  });
  const recentOutputs = yesterdayOutputsData?.data ?? [];

  const { data: activitiesPageData } = useQuery({
    queryKey: ["activities", projectId, "for-carry-forward"],
    queryFn: () => activityApi.listActivities(projectId, 0, 500),
    enabled: !!projectId,
  });
  const activitiesByIdLookup: Record<string, string> = (() => {
    const map: Record<string, string> = {};
    for (const a of activitiesPageData?.data?.content ?? []) {
      map[a.id] = a.name;
    }
    return map;
  })();

  const handleCarryForward = () => {
    if (recentOutputs.length === 0) {
      setError("No prior daily outputs to carry forward from.");
      return;
    }
    // Pick the most recent output strictly before today.
    const today = todayIso();
    const candidates = [...recentOutputs]
      .filter((o) => o.outputDate < today)
      .sort((a, b) => (a.outputDate > b.outputDate ? -1 : 1));
    const source = candidates[0] ?? recentOutputs[0];
    const activityName =
      activitiesByIdLookup[source.activityId] ?? `Activity ${source.activityId.slice(0, 8)}`;
    setFormData({
      ...initialFormState(),
      reportDate: today,
      nextDayActivity: activityName,
      targetQty: source.qtyExecuted.toString(),
      unit: source.unit,
      concerns: `Carried forward from ${source.outputDate} (qty ${source.qtyExecuted} ${source.unit})`,
    });
    setShowForm(true);
    setError(null);
  };

  const handleApply = () => {
    setAppliedFrom(fromDate);
    setAppliedTo(toDate);
  };

  const handleChainageBlur = (field: "chainageFrom" | "chainageTo") => {
    const raw = formData[field];
    const metres = parseChainage(raw);
    const previewKey = field === "chainageFrom" ? "chainageFromPreview" : "chainageToPreview";
    setFormData({
      ...formData,
      [previewKey]: metres != null ? chainageLabel(metres) : "",
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      if (!formData.nextDayActivity.trim()) {
        setError("Next Day Activity is required");
        return;
      }
      const payload: CreateNextDayPlanRequest = {
        reportDate: formData.reportDate,
        nextDayActivity: formData.nextDayActivity.trim(),
        chainageFromM: parseChainage(formData.chainageFrom),
        chainageToM: parseChainage(formData.chainageTo),
        targetQty:
          formData.targetQty === "" ? null : Number(formData.targetQty),
        unit: formData.unit || null,
        concerns: formData.concerns.trim() || null,
        actionBy: formData.actionBy.trim() || null,
        dueDate: formData.dueDate || null,
      };
      await nextDayPlanApi.create(projectId, payload);
      setFormData(initialFormState());
      setShowForm(false);
      queryClient.invalidateQueries({ queryKey: ["next-day-plan", projectId] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to create next day plan"));
    }
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm("Delete this plan entry?")) return;
    try {
      await nextDayPlanApi.delete(projectId, id);
      queryClient.invalidateQueries({ queryKey: ["next-day-plan", projectId] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to delete plan"));
    }
  };

  return (
    <div className="p-6">
      <TabTip
        title="Next Day Plan"
        description="Supervisor's look-ahead — activity, target, concerns, action owner and due date."
      />

      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-4 text-text-primary">Next Day Plan</h1>

        <div className="flex flex-wrap items-end gap-3 mb-6">
          <div>
            <label className="block text-sm font-medium mb-1 text-text-secondary">From</label>
            <input
              type="date"
              value={fromDate}
              onChange={(e) => setFromDate(e.target.value)}
              className="px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1 text-text-secondary">To</label>
            <input
              type="date"
              value={toDate}
              onChange={(e) => setToDate(e.target.value)}
              className="px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
            />
          </div>
          <button
            onClick={handleApply}
            className="px-4 py-2 bg-accent text-text-primary rounded-lg hover:bg-accent-hover"
          >
            Apply
          </button>
          <button
            onClick={handleCarryForward}
            className="px-4 py-2 bg-info/10 text-info ring-1 ring-info/30 rounded-lg hover:bg-info/20 ml-auto"
            title="Pre-fill the form from the most recent Daily Output before today"
          >
            ↻ Carry forward from yesterday
          </button>
          <button
            onClick={() => {
              setShowForm(!showForm);
              setError(null);
            }}
            className="px-4 py-2 bg-accent text-text-primary rounded-lg hover:bg-accent-hover"
          >
            {showForm ? "Cancel" : "Add Plan"}
          </button>
        </div>

        {error && <div className="text-danger mb-4">{error}</div>}
        {isError && (
          <div className="text-danger mb-4">
            {getErrorMessage(queryError, "Failed to load next day plans")}
          </div>
        )}

        {showForm && (
          <form
            onSubmit={handleSubmit}
            className="bg-surface/50 p-4 rounded-lg border border-border mb-6 shadow-xl"
          >
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Report Date
                </label>
                <input
                  type="date"
                  value={formData.reportDate}
                  onChange={(e) =>
                    setFormData({ ...formData, reportDate: e.target.value })
                  }
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Next Day Activity
                </label>
                <input
                  type="text"
                  value={formData.nextDayActivity}
                  onChange={(e) =>
                    setFormData({ ...formData, nextDayActivity: e.target.value })
                  }
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Chainage From
                </label>
                <input
                  type="text"
                  value={formData.chainageFrom}
                  onChange={(e) =>
                    setFormData({ ...formData, chainageFrom: e.target.value })
                  }
                  onBlur={() => handleChainageBlur("chainageFrom")}
                  placeholder="e.g. 145+000 or 145000"
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
                {formData.chainageFromPreview && (
                  <p className="text-xs text-text-muted mt-1">
                    Preview: {formData.chainageFromPreview}
                  </p>
                )}
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Chainage To
                </label>
                <input
                  type="text"
                  value={formData.chainageTo}
                  onChange={(e) =>
                    setFormData({ ...formData, chainageTo: e.target.value })
                  }
                  onBlur={() => handleChainageBlur("chainageTo")}
                  placeholder="e.g. 145+200"
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
                {formData.chainageToPreview && (
                  <p className="text-xs text-text-muted mt-1">
                    Preview: {formData.chainageToPreview}
                  </p>
                )}
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Target Qty
                </label>
                <input
                  type="number"
                  step="0.01"
                  value={formData.targetQty}
                  onChange={(e) =>
                    setFormData({ ...formData, targetQty: e.target.value })
                  }
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Unit
                </label>
                <select
                  value={formData.unit}
                  onChange={(e) => setFormData({ ...formData, unit: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                >
                  {UNIT_OPTIONS.map((u) => (
                    <option key={u || "blank"} value={u}>
                      {u || "—"}
                    </option>
                  ))}
                </select>
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Concerns
                </label>
                <textarea
                  value={formData.concerns}
                  onChange={(e) =>
                    setFormData({ ...formData, concerns: e.target.value })
                  }
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  rows={3}
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Action By
                </label>
                <input
                  type="text"
                  value={formData.actionBy}
                  onChange={(e) =>
                    setFormData({ ...formData, actionBy: e.target.value })
                  }
                  placeholder="e.g. Store Keeper"
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Due Date
                </label>
                <input
                  type="date"
                  value={formData.dueDate}
                  onChange={(e) =>
                    setFormData({ ...formData, dueDate: e.target.value })
                  }
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
            </div>
            <div className="flex gap-2 mt-4">
              <button
                type="submit"
                className="px-4 py-2 bg-green-600 text-text-primary rounded-lg hover:bg-green-600"
              >
                Save Plan
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="px-4 py-2 bg-surface-active/50 text-text-secondary rounded-lg hover:bg-border"
              >
                Cancel
              </button>
            </div>
          </form>
        )}

        <div className="overflow-x-auto">
          <table className="w-full border-collapse border border-border">
            <thead>
              <tr className="bg-surface/80">
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Report Date</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Next Day Activity</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Chainage From</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Chainage To</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Target Qty</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Unit</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Concerns</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Action By</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Due Date</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Actions</th>
              </tr>
            </thead>
            <tbody>
              {isLoading && (
                <tr>
                  <td colSpan={10} className="border border-border px-4 py-6 text-center text-text-muted">
                    Loading plans…
                  </td>
                </tr>
              )}
              {!isLoading && plans.length === 0 && (
                <tr>
                  <td colSpan={10} className="border border-border px-4 py-6 text-center text-text-muted">
                    No plans in this date range.
                  </td>
                </tr>
              )}
              {plans.map((plan) => (
                <tr key={plan.id} className="hover:bg-surface-hover/30 text-text-primary">
                  <td className="border border-border px-4 py-2">{plan.reportDate}</td>
                  <td className="border border-border px-4 py-2">{plan.nextDayActivity}</td>
                  <td className="border border-border px-4 py-2">{chainageLabel(plan.chainageFromM)}</td>
                  <td className="border border-border px-4 py-2">{chainageLabel(plan.chainageToM)}</td>
                  <td className="border border-border px-4 py-2 text-right">
                    {plan.targetQty != null ? plan.targetQty.toLocaleString() : "—"}
                  </td>
                  <td className="border border-border px-4 py-2">{plan.unit ?? "—"}</td>
                  <td className="border border-border px-4 py-2 max-w-xs truncate">
                    {plan.concerns ?? "—"}
                  </td>
                  <td className="border border-border px-4 py-2">{plan.actionBy ?? "—"}</td>
                  <td className="border border-border px-4 py-2">{plan.dueDate ?? "—"}</td>
                  <td className="border border-border px-4 py-2">
                    <button
                      onClick={() => handleDelete(plan.id)}
                      className="text-danger hover:underline text-sm"
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
