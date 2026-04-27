"use client";

import { useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  dailyActivityResourceOutputApi,
  type CreateDailyActivityResourceOutputRequest,
  type DailyActivityResourceOutputResponse,
} from "@/lib/api/dailyActivityResourceOutputApi";
import { activityApi } from "@/lib/api/activityApi";
import { resourceApi } from "@/lib/api/resourceApi";
import { TabTip } from "@/components/common/TabTip";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { getErrorMessage } from "@/lib/utils/error";

interface OutputForm {
  outputDate: string;
  activityId: string;
  resourceId: string;
  qtyExecuted: string;
  unit: string;
  hoursWorked: string;
  daysWorked: string;
  remarks: string;
}

const today = () => new Date().toISOString().split("T")[0];

const initialFormState: OutputForm = {
  outputDate: today(),
  activityId: "",
  resourceId: "",
  qtyExecuted: "",
  unit: "",
  hoursWorked: "8",
  daysWorked: "",
  remarks: "",
};

const toNumberOrUndefined = (value: string): number | undefined => {
  if (value === "" || value === null || value === undefined) return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
};

export default function DailyOutputsPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const queryClient = useQueryClient();

  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState<OutputForm>(initialFormState);
  const [error, setError] = useState<string | null>(null);

  const { data: outputsData, isLoading } = useQuery({
    queryKey: ["daily-outputs", projectId],
    queryFn: () => dailyActivityResourceOutputApi.list(projectId),
  });
  const outputs: DailyActivityResourceOutputResponse[] = outputsData?.data ?? [];

  const { data: activitiesData } = useQuery({
    queryKey: ["activities", projectId],
    queryFn: () => activityApi.listActivities(projectId, 0, 500),
  });
  const activities = activitiesData?.data?.content ?? [];

  const { data: resourcesData } = useQuery({
    queryKey: ["resources", "all"],
    queryFn: () => resourceApi.listResources(),
  });
  const allResources = resourcesData?.data ?? [];

  const activityById = useMemo(() => {
    const map: Record<string, { code: string; name: string }> = {};
    for (const a of activities) {
      map[a.id] = { code: a.code, name: a.name };
    }
    return map;
  }, [activities]);

  const resourceById = useMemo(() => {
    const map: Record<string, { code: string; name: string }> = {};
    for (const r of allResources) {
      map[r.id] = { code: r.code, name: r.name };
    }
    return map;
  }, [allResources]);

  const resetForm = () => {
    setFormData(initialFormState);
    setShowForm(false);
    setError(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!formData.activityId || !formData.resourceId || !formData.qtyExecuted) {
      setError("Activity, Resource, and Qty Executed are required");
      return;
    }
    const qty = Number(formData.qtyExecuted);
    if (!Number.isFinite(qty) || qty <= 0) {
      setError("Qty Executed must be a positive number");
      return;
    }
    try {
      const request: CreateDailyActivityResourceOutputRequest = {
        outputDate: formData.outputDate,
        activityId: formData.activityId,
        resourceId: formData.resourceId,
        qtyExecuted: qty,
        unit: formData.unit || undefined,
        hoursWorked: toNumberOrUndefined(formData.hoursWorked) ?? null,
        daysWorked: toNumberOrUndefined(formData.daysWorked) ?? null,
        remarks: formData.remarks || null,
      };
      await dailyActivityResourceOutputApi.create(projectId, request);
      resetForm();
      queryClient.invalidateQueries({ queryKey: ["daily-outputs", projectId] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to record daily output"));
    }
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm("Delete this daily output?")) return;
    try {
      await dailyActivityResourceOutputApi.delete(projectId, id);
      queryClient.invalidateQueries({ queryKey: ["daily-outputs", projectId] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to delete daily output"));
    }
  };

  if (isLoading && outputs.length === 0) {
    return <div className="p-6 text-text-muted">Loading daily outputs...</div>;
  }

  return (
    <div className="p-6">
      <TabTip
        title="Daily Outputs"
        description="One row per (date × activity × resource): how much work the resource did on that activity that day. Feeds the Capacity Utilization report — actual productivity is computed from these rows against the planned norm."
      />
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-4 text-text-primary">Daily Activity-Resource Outputs</h1>

        <button
          onClick={() => (showForm ? resetForm() : setShowForm(true))}
          className="mb-6 px-4 py-2 bg-accent text-text-primary rounded-lg hover:bg-accent-hover"
        >
          {showForm ? "Cancel" : "Add Output"}
        </button>

        {error && <div className="text-danger mb-4">{error}</div>}

        {showForm && (
          <form
            onSubmit={handleSubmit}
            className="bg-surface/50 p-4 rounded-lg border border-border mb-6 shadow-xl"
          >
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Date <span className="text-danger">*</span>
                </label>
                <input
                  type="date"
                  value={formData.outputDate}
                  onChange={(e) => setFormData({ ...formData, outputDate: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Unit <span className="text-text-muted">(auto-fills from activity)</span>
                </label>
                <input
                  type="text"
                  value={formData.unit}
                  onChange={(e) => setFormData({ ...formData, unit: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  placeholder="Sqm / Cum / MT"
                />
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Activity <span className="text-danger">*</span>
                </label>
                <SearchableSelect
                  value={formData.activityId}
                  onChange={(v) => setFormData({ ...formData, activityId: v })}
                  placeholder="Search activities..."
                  options={activities.map((a) => ({
                    value: a.id,
                    label: `${a.code} — ${a.name}`,
                  }))}
                />
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Resource <span className="text-danger">*</span>
                </label>
                <SearchableSelect
                  value={formData.resourceId}
                  onChange={(v) => setFormData({ ...formData, resourceId: v })}
                  placeholder="Search resources..."
                  options={allResources.map((r) => ({
                    value: r.id,
                    label: `${r.code} — ${r.name}`,
                  }))}
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Qty Executed <span className="text-danger">*</span>
                </label>
                <input
                  type="number"
                  step="0.001"
                  min="0"
                  value={formData.qtyExecuted}
                  onChange={(e) => setFormData({ ...formData, qtyExecuted: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block text-sm font-medium mb-1 text-text-secondary">
                    Hours Worked
                  </label>
                  <input
                    type="number"
                    step="0.1"
                    min="0"
                    value={formData.hoursWorked}
                    onChange={(e) => setFormData({ ...formData, hoursWorked: e.target.value })}
                    className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1 text-text-secondary">
                    Days <span className="text-text-muted">(auto)</span>
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={formData.daysWorked}
                    onChange={(e) => setFormData({ ...formData, daysWorked: e.target.value })}
                    placeholder="hours / 8"
                    className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  />
                </div>
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Remarks
                </label>
                <textarea
                  value={formData.remarks}
                  onChange={(e) => setFormData({ ...formData, remarks: e.target.value })}
                  rows={2}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
            </div>
            <div className="flex gap-2 mt-4">
              <button
                type="submit"
                className="px-4 py-2 bg-green-600 text-text-primary rounded-lg hover:bg-green-600"
              >
                Save Output
              </button>
              <button
                type="button"
                onClick={resetForm}
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
                <th className="border border-border px-3 py-2 text-left text-text-secondary">Date</th>
                <th className="border border-border px-3 py-2 text-left text-text-secondary">Activity</th>
                <th className="border border-border px-3 py-2 text-left text-text-secondary">Resource</th>
                <th className="border border-border px-3 py-2 text-right text-text-secondary">Qty</th>
                <th className="border border-border px-3 py-2 text-left text-text-secondary">Unit</th>
                <th className="border border-border px-3 py-2 text-right text-text-secondary">Hrs</th>
                <th className="border border-border px-3 py-2 text-right text-text-secondary">Days</th>
                <th className="border border-border px-3 py-2 text-right text-text-secondary">Actual / Day</th>
                <th className="border border-border px-3 py-2 text-left text-text-secondary">Actions</th>
              </tr>
            </thead>
            <tbody>
              {outputs.map((o) => {
                const days =
                  o.daysWorked ??
                  (o.hoursWorked != null ? o.hoursWorked / 8 : null);
                const actualPerDay =
                  days && days > 0 ? o.qtyExecuted / days : null;
                const activity = activityById[o.activityId];
                const resource = resourceById[o.resourceId];
                return (
                  <tr key={o.id} className="hover:bg-surface-hover/30 text-text-primary">
                    <td className="border border-border px-3 py-2 font-mono text-sm">{o.outputDate}</td>
                    <td className="border border-border px-3 py-2">
                      {activity ? `${activity.code} — ${activity.name}` : o.activityId.slice(0, 8) + "…"}
                    </td>
                    <td className="border border-border px-3 py-2">
                      {resource ? `${resource.code} — ${resource.name}` : o.resourceId.slice(0, 8) + "…"}
                    </td>
                    <td className="border border-border px-3 py-2 text-right">{o.qtyExecuted.toLocaleString("en-IN")}</td>
                    <td className="border border-border px-3 py-2">{o.unit}</td>
                    <td className="border border-border px-3 py-2 text-right">{o.hoursWorked ?? "—"}</td>
                    <td className="border border-border px-3 py-2 text-right">{days != null ? days.toFixed(2) : "—"}</td>
                    <td className="border border-border px-3 py-2 text-right">
                      {actualPerDay != null ? actualPerDay.toLocaleString("en-IN", { maximumFractionDigits: 1 }) : "—"}
                    </td>
                    <td className="border border-border px-3 py-2">
                      <button
                        onClick={() => handleDelete(o.id)}
                        className="px-3 py-1 bg-danger/10 text-danger ring-1 ring-red-500/20 rounded hover:bg-danger/20"
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                );
              })}
              {outputs.length === 0 && (
                <tr>
                  <td colSpan={9} className="border border-border px-4 py-8 text-center text-text-muted">
                    No outputs recorded yet — add one to start populating the Capacity Utilization report.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
