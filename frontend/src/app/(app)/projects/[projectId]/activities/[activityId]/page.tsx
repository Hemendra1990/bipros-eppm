"use client";

import { useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "@/components/common/PageHeader";
import { activityApi } from "@/lib/api/activityApi";
import { StatusBadge } from "@/components/common/StatusBadge";
import type { UpdateActivityRequest } from "@/lib/api/activityApi";

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
    },
    onError: (err) => {
      setError(err instanceof Error ? err.message : "Failed to update activity");
    },
  });

  const handleEditChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setEditData((prev) => ({
      ...prev,
      [name]:
        name === "percentComplete" || name === "remainingDuration" ? parseInt(value, 10) : value,
    }));
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
        actualStartDate: activity.actualStartDate || "",
        actualFinishDate: activity.actualFinishDate || "",
      });
      setIsEditing(true);
    }
  };

  if (isLoading) {
    return <div className="text-center text-gray-500">Loading activity...</div>;
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
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-gray-400"
          >
            {isEditing ? "Editing..." : "Edit"}
          </button>
        }
      />

      {error && (
        <div className="mb-6 rounded-md bg-red-50 p-4 text-sm text-red-700">{error}</div>
      )}

      {isEditing ? (
        <EditForm
          data={editData}
          onChange={handleEditChange}
          onSubmit={handleSaveEdit}
          onCancel={() => setIsEditing(false)}
          isSubmitting={updateMutation.isPending}
        />
      ) : (
        <ViewMode activity={activity} />
      )}
    </div>
  );
}

function ViewMode({ activity }: { activity: any }) {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-6">
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="text-sm font-medium text-gray-700">Status</h3>
          <div className="mt-2">
            <StatusBadge status={activity.status} />
          </div>
        </div>

        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="text-sm font-medium text-gray-700">% Complete</h3>
          <p className="mt-2 text-2xl font-bold text-gray-900">{activity.percentComplete}%</p>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="text-sm font-medium text-gray-700">Duration</h3>
          <p className="mt-2 text-2xl font-bold text-gray-900">{activity.duration} days</p>
        </div>

        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="text-sm font-medium text-gray-700">Remaining Duration</h3>
          <p className="mt-2 text-2xl font-bold text-gray-900">
            {activity.remainingDuration} days
          </p>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="text-sm font-medium text-gray-700">Total Float</h3>
          <p className="mt-2 text-2xl font-bold text-gray-900">{activity.totalFloat} days</p>
        </div>

        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="text-sm font-medium text-gray-700">Slack</h3>
          <p className="mt-2 text-2xl font-bold text-gray-900">{activity.slack} days</p>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="text-sm font-medium text-gray-700">Planned Start Date</h3>
          <p className="mt-2 text-lg text-gray-900">
            {activity.plannedStartDate || "Not set"}
          </p>
        </div>

        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="text-sm font-medium text-gray-700">Planned Finish Date</h3>
          <p className="mt-2 text-lg text-gray-900">
            {activity.plannedFinishDate || "Not set"}
          </p>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="text-sm font-medium text-gray-700">Actual Start Date</h3>
          <p className="mt-2 text-lg text-gray-900">
            {activity.actualStartDate || "Not started"}
          </p>
        </div>

        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="text-sm font-medium text-gray-700">Actual Finish Date</h3>
          <p className="mt-2 text-lg text-gray-900">
            {activity.actualFinishDate || "Not finished"}
          </p>
        </div>
      </div>
    </div>
  );
}

interface EditFormProps {
  data: UpdateActivityRequest;
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  onSubmit: (e: React.FormEvent) => void;
  onCancel: () => void;
  isSubmitting: boolean;
}

function EditForm({ data, onChange, onSubmit, onCancel, isSubmitting }: EditFormProps) {
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <form onSubmit={onSubmit} className="space-y-6">
        <div>
          <label className="block text-sm font-medium text-gray-700">Name *</label>
          <input
            type="text"
            name="name"
            value={data.name || ""}
            onChange={onChange}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 placeholder-gray-400 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            placeholder="Activity name"
          />
        </div>

        <div className="grid grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-medium text-gray-700">% Complete</label>
            <input
              type="number"
              name="percentComplete"
              value={data.percentComplete || 0}
              onChange={onChange}
              min="0"
              max="100"
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 placeholder-gray-400 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>

        </div>

        <div className="grid grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-medium text-gray-700">Actual Start Date</label>
            <input
              type="date"
              name="actualStartDate"
              value={data.actualStartDate || ""}
              onChange={onChange}
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700">
              Actual Finish Date
            </label>
            <input
              type="date"
              name="actualFinishDate"
              value={data.actualFinishDate || ""}
              onChange={onChange}
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
        </div>

        <div className="flex gap-3 pt-6">
          <button
            type="submit"
            disabled={isSubmitting}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-gray-400"
          >
            {isSubmitting ? "Saving..." : "Save Changes"}
          </button>
          <button
            type="button"
            onClick={onCancel}
            className="rounded-md bg-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-300"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
}
