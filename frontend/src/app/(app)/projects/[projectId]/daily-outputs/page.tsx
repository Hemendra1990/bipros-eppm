"use client";

import { useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  dailyActivityResourceOutputApi,
  type CreateDailyActivityResourceOutputRequest,
  type DailyActivityResourceOutputResponse,
} from "@/lib/api/dailyActivityResourceOutputApi";
import { activityApi } from "@/lib/api/activityApi";
import { resourceApi } from "@/lib/api/resourceApi";
// import { AiInsightsPanel } from "@/components/ai/AiInsightsPanel";
import { TabTip } from "@/components/common/TabTip";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { getErrorMessage } from "@/lib/utils/error";
import { VirtualDataTable } from "@/components/common/VirtualDataTable";
import type { ColumnDef } from "@tanstack/react-table";

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

const daysAgo = (days: number) => {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().split("T")[0];
};

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

const DEFAULT_FROM_DAYS = 30;

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

  const [fromDate, setFromDate] = useState<string>(() => daysAgo(DEFAULT_FROM_DAYS));
  const [toDate, setToDate] = useState<string>(() => today());

  const { data: outputsData, isLoading, isFetching } = useQuery({
    queryKey: ["daily-outputs", projectId, fromDate, toDate],
    queryFn: () =>
      dailyActivityResourceOutputApi.list(projectId, {
        from: fromDate || undefined,
        to: toDate || undefined,
      }),
    placeholderData: keepPreviousData,
  });
  const outputs: DailyActivityResourceOutputResponse[] = outputsData?.data ?? [];

  const { data: activitiesData } = useQuery({
    queryKey: ["activities", projectId],
    queryFn: () => activityApi.listActivities(projectId, 0, 500),
    placeholderData: keepPreviousData,
  });
  const activities = activitiesData?.data?.content ?? [];

  const { data: resourcesData } = useQuery({
    queryKey: ["resources", "all"],
    queryFn: () => resourceApi.listResources(),
    placeholderData: keepPreviousData,
  });
  const allResources = resourcesData?.data ?? [];

  const { data: activityResourcesData } = useQuery({
    queryKey: ["resources", "by-activity", projectId, formData.activityId],
    queryFn: () =>
      resourceApi.getAssignmentsByActivity(projectId, formData.activityId),
    enabled: !!formData.activityId,
  });
  const activityResources = activityResourcesData?.data ?? [];

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

  const columns = useMemo<ColumnDef<DailyActivityResourceOutputResponse>[]>(() => [
    {
      accessorKey: "outputDate",
      header: "Date",
      cell: ({ row }) => (
        <span className="font-mono text-sm">{row.original.outputDate}</span>
      ),
    },
    {
      accessorKey: "activityId",
      header: "Activity",
      cell: ({ row }) => {
        const activity = activityById[row.original.activityId];
        return activity
          ? `${activity.code} — ${activity.name}`
          : row.original.activityId.slice(0, 8) + "…";
      },
    },
    {
      accessorKey: "resourceId",
      header: "Resource",
      cell: ({ row }) => {
        const resource = resourceById[row.original.resourceId];
        return resource
          ? `${resource.code} — ${resource.name}`
          : row.original.resourceId.slice(0, 8) + "…";
      },
    },
    {
      accessorKey: "qtyExecuted",
      header: "Qty",
      cell: ({ row }) => row.original.qtyExecuted.toLocaleString("en-IN"),
    },
    { accessorKey: "unit", header: "Unit" },
    {
      accessorKey: "hoursWorked",
      header: "Hrs",
      cell: ({ row }) => row.original.hoursWorked ?? "—",
    },
    {
      accessorKey: "daysWorked",
      header: "Days",
      cell: ({ row }) => {
        const days =
          row.original.daysWorked ??
          (row.original.hoursWorked != null ? row.original.hoursWorked / 8 : null);
        return days != null ? days.toFixed(2) : "—";
      },
    },
    {
      id: "actualPerDay",
      header: "Actual / Day",
      cell: ({ row }) => {
        const days =
          row.original.daysWorked ??
          (row.original.hoursWorked != null ? row.original.hoursWorked / 8 : null);
        const actualPerDay = days && days > 0 ? row.original.qtyExecuted / days : null;
        return actualPerDay != null
          ? actualPerDay.toLocaleString("en-IN", { maximumFractionDigits: 1 })
          : "—";
      },
    },
    {
      id: "actions",
      header: "Actions",
      cell: ({ row }) => (
        <button
          onClick={() => handleDelete(row.original.id)}
          className="px-3 py-1 bg-danger/10 text-danger ring-1 ring-red-500/20 rounded hover:bg-danger/20"
        >
          Delete
        </button>
      ),
    },
  ], [activityById, resourceById]);

  if (isLoading && outputs.length === 0) {
    return <div className="p-6 text-text-muted">Loading daily outputs...</div>;
  }

  return (
    <div className="p-6">
      {/* <AiInsightsPanel projectId={projectId} endpoint={`/v1/projects/${projectId}/daily-outputs/ai/insights`} /> */}
      <TabTip
        title="Daily Outputs"
        description="One row per (date × activity × resource): how much work the resource did on that activity that day. Feeds the Capacity Utilization report — actual productivity is computed from these rows against the planned norm."
      />
      <div className="mb-8">
        <div className="sticky top-[var(--tab-nav-h,53px)] z-20 -mx-6 px-6 pt-2 pb-3 bg-ivory border-b border-border">
          <h1 className="text-3xl font-bold mb-4 text-text-primary">Daily Activity-Resource Outputs</h1>

          <button
            onClick={() => (showForm ? resetForm() : setShowForm(true))}
            className="px-4 py-2 bg-accent text-accent-foreground rounded-lg hover:bg-accent-hover"
          >
            {showForm ? "Cancel" : "Add Output"}
          </button>
        </div>

        {error && <div className="text-danger mt-4 mb-4">{error}</div>}

        {showForm && (
          <form
            onSubmit={handleSubmit}
            className="bg-surface/50 p-4 rounded-lg border border-border mt-4 mb-6 shadow-xl"
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
                  onChange={(v) =>
                    setFormData({ ...formData, activityId: v, resourceId: "" })
                  }
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
                  placeholder={
                    formData.activityId
                      ? "Search resources..."
                      : "Select an activity first"
                  }
                  options={activityResources
                    .filter((ra) => !!ra.resourceId)
                    .map((ra) => {
                      const r = allResources.find((x) => x.id === ra.resourceId);
                      return {
                        value: ra.resourceId!,
                        label: r
                          ? `${r.code} — ${r.name}`
                          : ra.resourceName ?? ra.resourceId!,
                      };
                    })}
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

        <div className="mb-3 flex flex-wrap items-end gap-3 rounded-lg border border-border bg-surface/50 p-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-text-secondary">From</label>
            <input
              type="date"
              value={fromDate}
              max={toDate || undefined}
              onChange={(e) => setFromDate(e.target.value)}
              className="rounded-md border border-border bg-surface-hover px-2 py-1.5 text-sm text-text-primary"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-text-secondary">To</label>
            <input
              type="date"
              value={toDate}
              min={fromDate || undefined}
              onChange={(e) => setToDate(e.target.value)}
              className="rounded-md border border-border bg-surface-hover px-2 py-1.5 text-sm text-text-primary"
            />
          </div>
          <div className="flex flex-wrap gap-1">
            {[7, 30, 90].map((d) => (
              <button
                key={d}
                type="button"
                onClick={() => {
                  setFromDate(daysAgo(d));
                  setToDate(today());
                }}
                className="rounded-md border border-border bg-surface px-2.5 py-1.5 text-xs text-text-secondary hover:bg-surface-hover"
              >
                Last {d}d
              </button>
            ))}
            <button
              type="button"
              onClick={() => {
                setFromDate("");
                setToDate("");
              }}
              className="rounded-md border border-border bg-surface px-2.5 py-1.5 text-xs text-text-secondary hover:bg-surface-hover"
              title="Load all rows — may be slow for large projects"
            >
              All time
            </button>
          </div>
          <div className="ml-auto text-xs text-text-muted">
            {isFetching
              ? "Loading…"
              : `${outputs.length.toLocaleString("en-IN")} ${outputs.length === 1 ? "row" : "rows"}`}
          </div>
        </div>

        <VirtualDataTable
          columns={columns}
          data={outputs}
          sortable
          resizable
          isLoading={isLoading}
          emptyMessage="No outputs in the selected date range."
        />
      </div>
    </div>
  );
}
