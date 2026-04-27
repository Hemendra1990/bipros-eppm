"use client";

import { useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { getErrorMessage } from "@/lib/utils/error";
import { activityNotifications, notificationHelpers } from "@/lib/notificationHelpers";
import { PageHeader } from "@/components/common/PageHeader";
import { activityApi } from "@/lib/api/activityApi";
import type { ActivityResponse, UpdateActivityRequest } from "@/lib/api/activityApi";
import { workActivityApi } from "@/lib/api/workActivityApi";
import type { WorkActivityResponse } from "@/lib/api/workActivityApi";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { StatusBadge } from "@/components/common/StatusBadge";
import { ActivityDependencies } from "@/components/activity/ActivityDependencies";
import { UdfSection } from "@/components/udf/UdfSection";

type EditData = Omit<UpdateActivityRequest, "originalDuration" | "percentComplete"> & {
  originalDuration?: number | "";
  percentComplete?: number | "";
};

export default function ActivityDetailPage() {
  const router = useRouter();
  const params = useParams();
  const queryClient = useQueryClient();
  const projectId = params.projectId as string;
  const activityId = params.activityId as string;

  const [isEditing, setIsEditing] = useState(false);
  const [error, setError] = useState("");

  const [editData, setEditData] = useState<EditData>({
    name: "",
    percentComplete: 0,
    actualStartDate: "",
    actualFinishDate: "",
    workActivityId: "",
  });

  const [usePert, setUsePert] = useState(false);
  const [pertData, setPertData] = useState({
    optimisticDuration: 0 as number | "",
    mostLikelyDuration: 0 as number | "",
    pessimisticDuration: 0 as number | "",
    expectedDuration: 0,
    standardDeviation: 0,
  });

  const { data: activityData, isLoading } = useQuery({
    queryKey: ["activity", projectId, activityId],
    queryFn: () => activityApi.getActivity(projectId, activityId),
  });

  const activity = activityData?.data;

  const { data: workActivitiesData } = useQuery({
    queryKey: ["work-activities", "active"],
    queryFn: () => workActivityApi.list(true),
  });
  const workActivities: WorkActivityResponse[] = workActivitiesData?.data ?? [];
  const linkedWorkActivity = activity?.workActivityId
    ? workActivities.find((w) => w.id === activity.workActivityId) ?? null
    : null;

  const updateMutation = useMutation({
    mutationFn: (data: UpdateActivityRequest) =>
      activityApi.updateActivity(projectId, activityId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["activity", projectId, activityId] });
      queryClient.invalidateQueries({ queryKey: ["activities", projectId] });
      setIsEditing(false);
      setError("");
      activityNotifications.updated();
    },
    onError: (err: unknown) => {
      const msg = getErrorMessage(err, "Failed to update activity");
      setError(msg);
      notificationHelpers.handleApiError(err, "Failed to update activity");
    },
  });

  const handleEditChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    if (error) setError("");
    setEditData((prev) => ({
      ...prev,
      [name]:
        name === "percentComplete" || name === "originalDuration" || name === "remainingDuration"
          ? (value === "" ? "" : parseFloat(value))
          : value,
    }));
  };

  const handlePertChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    const numValue = value === "" ? "" : parseFloat(value);
    const updated = {
      ...pertData,
      [name]: numValue,
    };

    // Auto-calculate expected duration and std deviation
    const o = updated.optimisticDuration === "" ? NaN : updated.optimisticDuration;
    const m = updated.mostLikelyDuration === "" ? NaN : updated.mostLikelyDuration;
    const p = updated.pessimisticDuration === "" ? NaN : updated.pessimisticDuration;
    if (!Number.isNaN(o) && !Number.isNaN(m) && !Number.isNaN(p)) {
      updated.expectedDuration = (o + 4 * m + p) / 6;
      updated.standardDeviation = (p - o) / 6;
    }
    setPertData(updated);
  };

  const handleSaveEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (!editData.name) {
      setError("Name is required");
      return;
    }

    const sanitizedData: UpdateActivityRequest = {
      ...editData,
      originalDuration: editData.originalDuration === "" ? 0 : editData.originalDuration,
      percentComplete: editData.percentComplete === "" ? 0 : editData.percentComplete,
    };
    updateMutation.mutate(sanitizedData);
  };

  const handleStartEdit = () => {
    if (activity) {
      setEditData({
        name: activity.name,
        percentComplete: activity.percentComplete,
        originalDuration: activity.duration,
        plannedStartDate: activity.plannedStartDate || "",
        plannedFinishDate: activity.plannedFinishDate || "",
        actualStartDate: activity.actualStartDate || "",
        actualFinishDate: activity.actualFinishDate || "",
        workActivityId: activity.workActivityId || "",
      });
      setIsEditing(true);
    }
  };

  const handleWorkActivityChange = (value: string) => {
    setEditData((prev) => ({ ...prev, workActivityId: value }));
  };

  if (isLoading) {
    return <div className="text-center text-text-muted">Loading activity...</div>;
  }

  if (!activity) {
    return <div className="text-center text-red-500">Activity not found</div>;
  }

  return (
    <div>
      <PageHeader
        title={activity.code}
        description={activity.name}
        actions={
          <button
            onClick={handleStartEdit}
            disabled={isEditing}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:bg-border"
          >
            {isEditing ? "Editing..." : "Edit"}
          </button>
        }
      />

      {error && (
        <div className="mb-6 rounded-md bg-danger/10 p-4 text-sm text-danger">{error}</div>
      )}

      {isEditing ? (
        <EditForm
          data={editData}
          onChange={handleEditChange}
          onSubmit={handleSaveEdit}
          onCancel={() => setIsEditing(false)}
          isSubmitting={updateMutation.isPending}
          usePert={usePert}
          onTogglePert={() => setUsePert(!usePert)}
          pertData={pertData}
          onPertChange={handlePertChange}
          workActivities={workActivities}
          onWorkActivityChange={handleWorkActivityChange}
        />
      ) : (
        <ViewMode activity={activity} projectId={projectId} workActivity={linkedWorkActivity} />
      )}
    </div>
  );
}

function ViewMode({
  activity,
  projectId,
  workActivity,
}: {
  activity: ActivityResponse;
  projectId: string;
  workActivity: WorkActivityResponse | null;
}) {
  const stat = (label: string, value: React.ReactNode, tone?: "neutral" | "accent" | "success" | "warning" | "danger") => {
    const toneCls = {
      neutral: "text-text-primary",
      accent: "text-accent",
      success: "text-success",
      warning: "text-warning",
      danger: "text-danger",
    };
    return (
      <div className="rounded-lg border border-border bg-surface/50 p-3">
        <p className="text-xs text-text-secondary">{label}</p>
        <p className={`mt-0.5 text-base font-semibold ${tone ? toneCls[tone] : toneCls.neutral}`}>
          {value}
        </p>
      </div>
    );
  };

  const datePair = (label: string, value: string | null | undefined, fallback: string) => (
    <div className="flex items-center justify-between py-2 border-b border-border/50 last:border-0">
      <span className="text-sm text-text-secondary">{label}</span>
      <span className="text-sm font-medium text-text-primary">{value || fallback}</span>
    </div>
  );

  function getStatusTone(status: string): "neutral" | "accent" | "success" | "warning" | "danger" {
    switch (status) {
      case "IN_PROGRESS":
        return "accent";
      case "ACTIVE":
      case "COMPLETED":
        return "success";
      case "INACTIVE":
      case "ON_HOLD":
        return "warning";
      case "SUSPENDED":
      case "DELAYED":
      case "CANCELLED":
        return "danger";
      case "NOT_STARTED":
      case "PLANNED":
      default:
        return "neutral";
    }
  }

  return (
    <div className="space-y-5">
      {/* Key Metrics */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {stat("Status", <StatusBadge status={activity.status} />, getStatusTone(activity.status))}
        {stat("% Complete", `${activity.percentComplete}%`, activity.percentComplete === 100 ? "success" : "neutral")}
        {stat("Duration", `${activity.duration ?? activity.originalDuration ?? 0} days`)}
        {stat("Remaining", `${activity.remainingDuration ?? 0} days`)}
      </div>

      {/* Schedule Metrics */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {stat("Total Float", `${activity.totalFloat ?? 0} days`, (activity.totalFloat ?? 0) === 0 ? "danger" : "neutral")}
        {stat("Slack", `${activity.slack ?? 0} days`)}
        {stat("Free Float", `${activity.freeFloat ?? 0} days`)}
        {stat("Critical", activity.isCritical ? "Yes" : "No", activity.isCritical ? "danger" : "success")}
      </div>

      {/* Dates Panel */}
      <div className="rounded-lg border border-border bg-surface/50 p-4">
        <h3 className="text-sm font-semibold text-text-primary mb-2">Dates</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-6">
          {datePair("Planned Start", activity.plannedStartDate, "Not set")}
          {datePair("Planned Finish", activity.plannedFinishDate, "Not set")}
          {datePair("Early Start", activity.earlyStartDate, "—")}
          {datePair("Early Finish", activity.earlyFinishDate, "—")}
          {datePair("Late Start", activity.lateStartDate, "—")}
          {datePair("Late Finish", activity.lateFinishDate, "—")}
          {datePair("Actual Start", activity.actualStartDate, "Not started")}
          {datePair("Actual Finish", activity.actualFinishDate, "Not finished")}
        </div>
      </div>

      {/* Master Work Activity link */}
      <div className="rounded-lg border border-border bg-surface/50 p-4">
        <h3 className="text-sm font-semibold text-text-primary mb-2">Work Activity (master)</h3>
        {workActivity ? (
          <div className="flex flex-wrap items-center gap-3 text-sm text-text-primary">
            <span className="font-medium">{workActivity.name}</span>
            <span className="font-mono text-xs text-text-muted">{workActivity.code}</span>
            {workActivity.defaultUnit && (
              <span className="px-2 py-0.5 rounded bg-info/10 text-info ring-1 ring-info/20 text-xs">
                {workActivity.defaultUnit}
              </span>
            )}
            {workActivity.discipline && (
              <span className="text-xs text-text-secondary">· {workActivity.discipline}</span>
            )}
          </div>
        ) : (
          <p className="text-sm text-text-muted">
            Not linked. Edit the activity and pick a master entry to enable
            productivity-norm-driven capacity utilisation reports.
          </p>
        )}
      </div>

      {/* Dependencies */}
      <ActivityDependencies
        projectId={projectId}
        activityId={activity.id}
        activityName={activity.name}
      />

      {/* Custom Fields */}
      <UdfSection entityId={activity.id} subject="ACTIVITY" projectId={projectId} />
    </div>
  );
}

interface EditFormProps {
  data: EditData;
  onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => void;
  onSubmit: (e: React.FormEvent) => void;
  onCancel: () => void;
  isSubmitting: boolean;
  usePert: boolean;
  onTogglePert: () => void;
  pertData: {
    optimisticDuration: number | "";
    mostLikelyDuration: number | "";
    pessimisticDuration: number | "";
    expectedDuration: number;
    standardDeviation: number;
  };
  onPertChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  workActivities: WorkActivityResponse[];
  onWorkActivityChange: (value: string) => void;
}

function EditForm({
  data,
  onChange,
  onSubmit,
  onCancel,
  isSubmitting,
  usePert,
  onTogglePert,
  pertData,
  onPertChange,
  workActivities,
  onWorkActivityChange,
}: EditFormProps) {
  return (
    <div className="rounded-lg border border-border bg-surface/50 p-6 shadow-sm">
      <form onSubmit={onSubmit} className="space-y-6">
        <div>
          <label className="block text-sm font-medium text-text-secondary">Name *</label>
          <input
            type="text"
            name="name"
            value={data.name || ""}
            onChange={onChange}
            className="mt-1 block w-full rounded-md border border-border px-3 py-2 text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
            placeholder="Activity name"
          />
        </div>

        <div className="grid grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Duration (days)</label>
            <input
              type="number"
              name="originalDuration"
              value={data.originalDuration ?? ""}
              onChange={onChange}
              min="0"
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">% Complete</label>
            <input
              type="number"
              name="percentComplete"
              value={data.percentComplete ?? ""}
              onChange={onChange}
              min="0"
              max="100"
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
            />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Planned Start Date</label>
            <input
              type="date"
              name="plannedStartDate"
              value={data.plannedStartDate || ""}
              onChange={onChange}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Planned Finish Date</label>
            <input
              type="date"
              name="plannedFinishDate"
              value={data.plannedFinishDate || ""}
              onChange={onChange}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
            />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Actual Start Date</label>
            <input
              type="date"
              name="actualStartDate"
              value={data.actualStartDate || ""}
              onChange={onChange}
              className="mt-1 block w-full rounded-md border border-border px-3 py-2 text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-text-secondary">
              Actual Finish Date
            </label>
            <input
              type="date"
              name="actualFinishDate"
              value={data.actualFinishDate || ""}
              onChange={onChange}
              className="mt-1 block w-full rounded-md border border-border px-3 py-2 text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
            />
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-text-secondary">
            Work Activity (master)
          </label>
          <SearchableSelect
            value={data.workActivityId ?? ""}
            onChange={onWorkActivityChange}
            placeholder="Search master library (optional)..."
            options={[
              { value: "", label: "— none —" },
              ...workActivities.map((wa) => ({
                value: wa.id,
                label: wa.defaultUnit ? `${wa.name} (${wa.defaultUnit})` : wa.name,
              })),
            ]}
          />
          <p className="mt-1 text-xs text-text-muted">
            Links this project activity to its master library entry — required for productivity-norm
            lookups when computing budgeted vs actual resource-days.
          </p>
        </div>

        <div className="border-t border-border pt-6">
          <div className="flex items-center gap-3">
            <input
              type="checkbox"
              id="usePert"
              checked={usePert}
              onChange={onTogglePert}
              className="rounded border-border"
            />
            <label htmlFor="usePert" className="text-sm font-medium text-text-secondary">
              Use PERT Estimation
            </label>
          </div>
        </div>

        {usePert && (
          <div className="space-y-6 rounded-lg border border-warning/30 bg-warning/10 p-4">
            <div className="grid grid-cols-3 gap-4">
              <div>
                <label className="block text-sm font-medium text-text-secondary">
                  Optimistic (days)
                </label>
                <input
                  type="number"
                  name="optimisticDuration"
                  value={pertData.optimisticDuration}
                  onChange={onPertChange}
                  min="0"
                  step="0.5"
                  className="mt-1 block w-full rounded-md border border-border px-3 py-2 text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                  placeholder="Optimistic"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-text-secondary">
                  Most Likely (days)
                </label>
                <input
                  type="number"
                  name="mostLikelyDuration"
                  value={pertData.mostLikelyDuration}
                  onChange={onPertChange}
                  min="0"
                  step="0.5"
                  className="mt-1 block w-full rounded-md border border-border px-3 py-2 text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                  placeholder="Most Likely"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-text-secondary">
                  Pessimistic (days)
                </label>
                <input
                  type="number"
                  name="pessimisticDuration"
                  value={pertData.pessimisticDuration}
                  onChange={onPertChange}
                  min="0"
                  step="0.5"
                  className="mt-1 block w-full rounded-md border border-border px-3 py-2 text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                  placeholder="Pessimistic"
                />
              </div>
            </div>

            <div className="rounded-lg bg-surface/50 p-4">
              <div className="text-sm font-medium text-text-secondary">Calculated Values</div>
              <div className="mt-3 grid grid-cols-2 gap-4 text-sm">
                <div>
                  <span className="text-text-secondary">Expected Duration:</span>
                  <span className="ml-2 font-semibold text-text-primary">
                    {pertData.expectedDuration.toFixed(2)} days
                  </span>
                </div>
                <div>
                  <span className="text-text-secondary">Standard Deviation:</span>
                  <span className="ml-2 font-semibold text-text-primary">
                    {pertData.standardDeviation.toFixed(2)} days
                  </span>
                </div>
              </div>
            </div>
          </div>
        )}

        <div className="flex gap-3 pt-6">
          <button
            type="submit"
            disabled={isSubmitting}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:bg-border"
          >
            {isSubmitting ? "Saving..." : "Save Changes"}
          </button>
          <button
            type="button"
            onClick={onCancel}
            className="rounded-md bg-surface-active/50 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-active"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
}
