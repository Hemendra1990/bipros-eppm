"use client";

import { useMemo, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { X, Search } from "lucide-react";
import { riskApi } from "@/lib/api/riskApi";
import { apiClient } from "@/lib/api/client";
import { getErrorMessage } from "@/lib/utils/error";
import type { ApiResponse, PagedResponse } from "@/lib/types";

interface Activity {
  id: string;
  code: string;
  name: string;
  plannedStartDate?: string;
  plannedFinishDate?: string;
  status?: string;
}

type ActivitiesPayload = Activity[] | PagedResponse<Activity>;

interface Props {
  projectId: string;
  riskId: string;
  assignedActivityIds: string[];
  onClose: () => void;
}

function formatDate(dateStr?: string) {
  if (!dateStr) return "—";
  return new Date(dateStr).toLocaleDateString();
}

export function ActivityAssignmentModal({ projectId, riskId, assignedActivityIds, onClose }: Props) {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isAssigning, setIsAssigning] = useState(false);

  const { data: activitiesData, isLoading } = useQuery({
    queryKey: ["activities", projectId],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponse<ActivitiesPayload>>(
        `/v1/projects/${projectId}/activities?page=0&size=500`
      );
      return response.data.data;
    },
  });

  const assignMutation = useMutation({
    mutationFn: (activityId: string) => riskApi.addActivityToRisk(projectId, riskId, activityId),
  });

  // Backend returns either a flat Activity[] or a PagedResponse<Activity> with .content.
  const activities: Activity[] = Array.isArray(activitiesData)
    ? activitiesData
    : (activitiesData?.content ?? []);
  const filteredActivities = activities.filter(
    (a) =>
      !assignedActivityIds.includes(a.id) &&
      (a.code.toLowerCase().includes(search.toLowerCase()) ||
        a.name.toLowerCase().includes(search.toLowerCase()))
  );

  const toggleSelected = (id: string) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
    );
  };

  // Show what the new exposure window will look like after assignment.
  const exposurePreview = useMemo(() => {
    const selected = activities.filter((a) => selectedIds.includes(a.id));
    if (selected.length === 0) return null;
    const starts = selected.map((a) => a.plannedStartDate).filter(Boolean) as string[];
    const finishes = selected.map((a) => a.plannedFinishDate).filter(Boolean) as string[];
    if (starts.length === 0 && finishes.length === 0) return null;
    const minStart = starts.length ? starts.reduce((a, b) => (a < b ? a : b)) : undefined;
    const maxFinish = finishes.length ? finishes.reduce((a, b) => (a > b ? a : b)) : undefined;
    return { start: minStart, finish: maxFinish };
  }, [selectedIds, activities]);

  const handleAssign = async () => {
    if (selectedIds.length === 0) return;
    setErrorMessage(null);
    setIsAssigning(true);
    const succeeded: string[] = [];
    const failed: { id: string; reason: string }[] = [];
    for (const id of selectedIds) {
      try {
        await assignMutation.mutateAsync(id);
        succeeded.push(id);
      } catch (err) {
        failed.push({ id, reason: getErrorMessage(err, "Assign failed") });
      }
    }
    setIsAssigning(false);
    queryClient.invalidateQueries({ queryKey: ["risk-activities", projectId, riskId] });
    queryClient.invalidateQueries({ queryKey: ["risk", projectId, riskId] });
    queryClient.invalidateQueries({ queryKey: ["risks", projectId] });
    if (failed.length === 0) {
      toast.success(
        `Assigned ${succeeded.length} activit${succeeded.length === 1 ? "y" : "ies"}`
      );
      onClose();
      return;
    }
    if (succeeded.length === 0) {
      setErrorMessage(`Could not assign any activity: ${failed[0].reason}`);
      return;
    }
    setErrorMessage(
      `Assigned ${succeeded.length}, failed ${failed.length}. ${failed[0].reason}`
    );
    setSelectedIds(failed.map((f) => f.id));
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="assign-activities-title"
    >
      <div className="w-full max-w-4xl max-h-[85vh] overflow-hidden rounded-xl border border-border bg-surface shadow-xl flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-border p-4">
          <div>
            <h2 id="assign-activities-title" className="text-lg font-semibold text-text-primary">
              Assign Activities to Risk
            </h2>
            <p className="text-xs text-text-muted mt-0.5">
              Select activities to assign. Exposure dates auto-update from start/finish dates.
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-text-secondary hover:text-text-primary"
            aria-label="Close"
          >
            <X size={20} />
          </button>
        </div>

        {/* Search */}
        <div className="border-b border-border p-4">
          <div className="relative">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
            <input
              type="text"
              placeholder="Search activities by code or name..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-10 pr-4 py-2 bg-surface-hover border border-border rounded-md text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
            />
          </div>
          <div className="flex items-center justify-between mt-2">
            <span className="text-xs text-text-muted">{selectedIds.length} selected</span>
            <span className="text-xs text-text-muted">
              {filteredActivities.length} activities available
            </span>
          </div>
          {exposurePreview && (
            <div className="mt-2 rounded-md border border-accent/30 bg-accent/10 px-3 py-2 text-xs text-text-primary">
              <span className="font-semibold">Preview:</span> exposure window will be{" "}
              <span className="font-mono">{formatDate(exposurePreview.start)}</span> →{" "}
              <span className="font-mono">{formatDate(exposurePreview.finish)}</span>
            </div>
          )}
          {errorMessage && (
            <div
              className="mt-2 rounded-md border border-rose-500/40 bg-rose-500/10 px-3 py-2 text-xs text-rose-200"
              role="alert"
            >
              {errorMessage}
            </div>
          )}
        </div>

        {/* Activity List */}
        <div className="flex-1 overflow-y-auto p-4">
          {isLoading ? (
            <div className="text-center py-8 text-text-muted">Loading activities...</div>
          ) : filteredActivities.length === 0 ? (
            <div className="text-center py-8 text-text-muted">
              {search ? "No activities match your search" : "No activities available to assign"}
            </div>
          ) : (
            <div className="space-y-2">
              {filteredActivities.map((activity) => {
                const isSelected = selectedIds.includes(activity.id);
                return (
                  <div
                    key={activity.id}
                    className={`flex items-center gap-3 rounded-lg border p-3 cursor-pointer transition-colors ${
                      isSelected
                        ? "border-accent bg-accent/10"
                        : "border-border bg-surface/40 hover:bg-surface-hover/30"
                    }`}
                    onClick={() => toggleSelected(activity.id)}
                  >
                    <input
                      type="checkbox"
                      checked={isSelected}
                      onChange={() => toggleSelected(activity.id)}
                      onClick={(e) => e.stopPropagation()}
                      aria-label={`Select activity ${activity.code} ${activity.name}`}
                      className="shrink-0"
                    />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-xs text-text-muted">{activity.code}</span>
                        <span className="font-medium text-sm text-text-primary truncate">
                          {activity.name}
                        </span>
                      </div>
                      <div className="flex items-center gap-4 mt-1 text-xs text-text-secondary">
                        <span>Start: {formatDate(activity.plannedStartDate)}</span>
                        <span>Finish: {formatDate(activity.plannedFinishDate)}</span>
                        {activity.status && (
                          <span className="uppercase text-text-muted">{activity.status}</span>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-2 border-t border-border p-4">
          <button
            type="button"
            onClick={onClose}
            className="rounded border border-border bg-surface/50 px-4 py-2 text-sm text-text-secondary hover:bg-surface-hover"
            disabled={isAssigning}
          >
            Cancel
          </button>
          <button
            type="button"
            disabled={selectedIds.length === 0 || isAssigning}
            onClick={handleAssign}
            className="rounded bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
          >
            {isAssigning
              ? "Assigning..."
              : selectedIds.length === 0
                ? "Select activities to assign"
                : `Assign ${selectedIds.length} activit${selectedIds.length === 1 ? "y" : "ies"}`}
          </button>
        </div>
      </div>
    </div>
  );
}
