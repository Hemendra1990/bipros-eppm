"use client";

import { useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { getErrorMessage } from "@/lib/utils/error";
import { activityNotifications, notificationHelpers } from "@/lib/notificationHelpers";
import { PageHeader } from "@/components/common/PageHeader";
import { activityApi } from "@/lib/api/activityApi";
import type { ActivityResponse, UpdateActivityRequest } from "@/lib/api/activityApi";
import { StatusBadge } from "@/components/common/StatusBadge";
import { ActivityDependencies } from "@/components/activity/ActivityDependencies";
import { UdfSection } from "@/components/udf/UdfSection";

export default function ActivityDetailPage() {
  const router = useRouter();
  const params = useParams();
  const queryClient = useQueryClient();
  const projectId = params.projectId as string;
  const activityId = params.activityId as string;

  const [isEditing, setIsEditing] = useState(false);
  const [error, setError] = useState("");

  const [editData, setEditData] = useState<UpdateActivityRequest>({
    name: "",
    percentComplete: 0,
    actualStartDate: "",
    actualFinishDate: "",
  });

  const [usePert, setUsePert] = useState(false);
  const [pertData, setPertData] = useState({
    optimisticDuration: 0,
    mostLikelyDuration: 0,
    pessimisticDuration: 0,
    expectedDuration: 0,
    standardDeviation: 0,
  });

  const { data: activityData, isLoading } = useQuery({
    queryKey: ["activity", projectId, activityId],
    queryFn: () => activityApi.getActivity(projectId, activityId),
  });

  const activity = activityData?.data;

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
          ? parseFloat(value) || 0
          : value,
    }));
  };

  const handlePertChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    const numValue = parseFloat(value) || 0;
    const updated = {
      ...pertData,
      [name]: numValue,
    };
    setPertData(updated);

    // Auto-calculate expected duration and std deviation
    if (updated.optimisticDuration && updated.mostLikelyDuration && updated.pessimisticDuration) {
      const o = updated.optimisticDuration;
      const m = updated.mostLikelyDuration;
      const p = updated.pessimisticDuration;
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

    updateMutation.mutate(editData);
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
      });
      setIsEditing(true);
    }
  };

  if (isLoading) {
    return <div className="text-center text-slate-500">Loading activity...</div>;
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
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:bg-slate-600"
          >
            {isEditing ? "Editing..." : "Edit"}
          </button>
        }
      />

      {error && (
        <div className="mb-6 rounded-md bg-red-500/10 p-4 text-sm text-red-400">{error}</div>
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
        />
      ) : (
        <ViewMode activity={activity} projectId={projectId} />
      )}
    </div>
  );
}

function ViewMode({ activity, projectId }: { activity: ActivityResponse; projectId: string }) {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-6">
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
          <h3 className="text-sm font-medium text-slate-300">Status</h3>
          <div className="mt-2">
            <StatusBadge status={activity.status} />
          </div>
        </div>

        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
          <h3 className="text-sm font-medium text-slate-300">% Complete</h3>
          <p className="mt-2 text-2xl font-bold text-white">{activity.percentComplete}%</p>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
          <h3 className="text-sm font-medium text-slate-300">Duration</h3>
          <p className="mt-2 text-2xl font-bold text-white">{activity.duration} days</p>
        </div>

        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
          <h3 className="text-sm font-medium text-slate-300">Remaining Duration</h3>
          <p className="mt-2 text-2xl font-bold text-white">
            {activity.remainingDuration} days
          </p>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
          <h3 className="text-sm font-medium text-slate-300">Total Float</h3>
          <p className="mt-2 text-2xl font-bold text-white">{activity.totalFloat} days</p>
        </div>

        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
          <h3 className="text-sm font-medium text-slate-300">Slack</h3>
          <p className="mt-2 text-2xl font-bold text-white">{activity.slack} days</p>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
          <h3 className="text-sm font-medium text-slate-300">Planned Start Date</h3>
          <p className="mt-2 text-lg text-white">
            {activity.plannedStartDate || "Not set"}
          </p>
        </div>

        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
          <h3 className="text-sm font-medium text-slate-300">Planned Finish Date</h3>
          <p className="mt-2 text-lg text-white">
            {activity.plannedFinishDate || "Not set"}
          </p>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
          <h3 className="text-sm font-medium text-slate-300">Actual Start Date</h3>
          <p className="mt-2 text-lg text-white">
            {activity.actualStartDate || "Not started"}
          </p>
        </div>

        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
          <h3 className="text-sm font-medium text-slate-300">Actual Finish Date</h3>
          <p className="mt-2 text-lg text-white">
            {activity.actualFinishDate || "Not finished"}
          </p>
        </div>
      </div>

      {/* Dependencies Section */}
      <ActivityDependencies
        projectId={projectId}
        activityId={activity.id}
        activityName={activity.name}
      />

      <UdfSection entityId={activity.id} subject="ACTIVITY" projectId={projectId} />
    </div>
  );
}

interface EditFormProps {
  data: UpdateActivityRequest;
  onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => void;
  onSubmit: (e: React.FormEvent) => void;
  onCancel: () => void;
  isSubmitting: boolean;
  usePert: boolean;
  onTogglePert: () => void;
  pertData: {
    optimisticDuration: number;
    mostLikelyDuration: number;
    pessimisticDuration: number;
    expectedDuration: number;
    standardDeviation: number;
  };
  onPertChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
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
}: EditFormProps) {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6 shadow-sm">
      <form onSubmit={onSubmit} className="space-y-6">
        <div>
          <label className="block text-sm font-medium text-slate-300">Name *</label>
          <input
            type="text"
            name="name"
            value={data.name || ""}
            onChange={onChange}
            className="mt-1 block w-full rounded-md border border-slate-700 px-3 py-2 text-white placeholder-gray-400 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            placeholder="Activity name"
          />
        </div>

        <div className="grid grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-medium text-slate-300">Duration (days)</label>
            <input
              type="number"
              name="originalDuration"
              value={data.originalDuration || 0}
              onChange={onChange}
              min="0"
              className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-300">% Complete</label>
            <input
              type="number"
              name="percentComplete"
              value={data.percentComplete || 0}
              onChange={onChange}
              min="0"
              max="100"
              className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-medium text-slate-300">Planned Start Date</label>
            <input
              type="date"
              name="plannedStartDate"
              value={data.plannedStartDate || ""}
              onChange={onChange}
              className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-300">Planned Finish Date</label>
            <input
              type="date"
              name="plannedFinishDate"
              value={data.plannedFinishDate || ""}
              onChange={onChange}
              className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-medium text-slate-300">Actual Start Date</label>
            <input
              type="date"
              name="actualStartDate"
              value={data.actualStartDate || ""}
              onChange={onChange}
              className="mt-1 block w-full rounded-md border border-slate-700 px-3 py-2 text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-300">
              Actual Finish Date
            </label>
            <input
              type="date"
              name="actualFinishDate"
              value={data.actualFinishDate || ""}
              onChange={onChange}
              className="mt-1 block w-full rounded-md border border-slate-700 px-3 py-2 text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
        </div>

        <div className="border-t border-slate-800 pt-6">
          <div className="flex items-center gap-3">
            <input
              type="checkbox"
              id="usePert"
              checked={usePert}
              onChange={onTogglePert}
              className="rounded border-slate-700"
            />
            <label htmlFor="usePert" className="text-sm font-medium text-slate-300">
              Use PERT Estimation
            </label>
          </div>
        </div>

        {usePert && (
          <div className="space-y-6 rounded-lg border border-amber-200 bg-amber-500/10 p-4">
            <div className="grid grid-cols-3 gap-4">
              <div>
                <label className="block text-sm font-medium text-slate-300">
                  Optimistic (days)
                </label>
                <input
                  type="number"
                  name="optimisticDuration"
                  value={pertData.optimisticDuration || ""}
                  onChange={onPertChange}
                  min="0"
                  step="0.5"
                  className="mt-1 block w-full rounded-md border border-slate-700 px-3 py-2 text-white placeholder-gray-400 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  placeholder="Optimistic"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-300">
                  Most Likely (days)
                </label>
                <input
                  type="number"
                  name="mostLikelyDuration"
                  value={pertData.mostLikelyDuration || ""}
                  onChange={onPertChange}
                  min="0"
                  step="0.5"
                  className="mt-1 block w-full rounded-md border border-slate-700 px-3 py-2 text-white placeholder-gray-400 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  placeholder="Most Likely"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-300">
                  Pessimistic (days)
                </label>
                <input
                  type="number"
                  name="pessimisticDuration"
                  value={pertData.pessimisticDuration || ""}
                  onChange={onPertChange}
                  min="0"
                  step="0.5"
                  className="mt-1 block w-full rounded-md border border-slate-700 px-3 py-2 text-white placeholder-gray-400 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  placeholder="Pessimistic"
                />
              </div>
            </div>

            <div className="rounded-lg bg-slate-900/50 p-4">
              <div className="text-sm font-medium text-slate-300">Calculated Values</div>
              <div className="mt-3 grid grid-cols-2 gap-4 text-sm">
                <div>
                  <span className="text-slate-400">Expected Duration:</span>
                  <span className="ml-2 font-semibold text-white">
                    {pertData.expectedDuration.toFixed(2)} days
                  </span>
                </div>
                <div>
                  <span className="text-slate-400">Standard Deviation:</span>
                  <span className="ml-2 font-semibold text-white">
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
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:bg-slate-600"
          >
            {isSubmitting ? "Saving..." : "Save Changes"}
          </button>
          <button
            type="button"
            onClick={onCancel}
            className="rounded-md bg-slate-700/50 px-4 py-2 text-sm font-medium text-slate-300 hover:bg-slate-700"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
}
