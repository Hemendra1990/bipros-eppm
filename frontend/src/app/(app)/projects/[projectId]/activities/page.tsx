"use client";

import { useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { List, FolderTree } from "lucide-react";
import { PageHeader } from "@/components/common/PageHeader";
import { activityApi } from "@/lib/api/activityApi";
import type { ActivityResponse } from "@/lib/api/activityApi";
import { projectApi } from "@/lib/api/projectApi";
import { StatusBadge } from "@/components/common/StatusBadge";
import { ActivityWbsTreeView } from "@/components/activity/ActivityWbsTreeView";
import { getErrorMessage } from "@/lib/utils/error";
import { notificationHelpers } from "@/lib/notificationHelpers";
import Link from "next/link";

const todayIso = () => new Date().toISOString().slice(0, 10);

export default function ActivitiesPage() {
  const router = useRouter();
  const params = useParams();
  const projectId = params.projectId as string;
  const qc = useQueryClient();

  const [lookAheadWeeks, setLookAheadWeeks] = useState<4 | 13 | null>(null);
  const [viewMode, setViewMode] = useState<"list" | "tree">("list");
  // Row-specific inline editor state. Keyed by activity id; value = string
  // currently typed into the % input.
  const [progressEdit, setProgressEdit] = useState<Record<string, string>>({});
  const [pendingId, setPendingId] = useState<string | null>(null);

  const { data: activitiesData, isLoading: isLoadingActivities } = useQuery({
    queryKey: ["activities", projectId],
    queryFn: () => activityApi.listActivities(projectId, 0, 500),
    enabled: !!projectId,
  });

  const { data: wbsData, isLoading: isLoadingWbs } = useQuery({
    queryKey: ["wbs", projectId],
    queryFn: () => projectApi.getWbsTree(projectId),
    enabled: !!projectId,
  });

  const activities = (activitiesData?.data?.content || []) as ActivityResponse[];
  const wbsNodes = wbsData?.data ?? [];

  const progressMutation = useMutation({
    mutationFn: async (vars: {
      id: string;
      percentComplete: number;
      actualStartDate?: string;
      actualFinishDate?: string;
    }) => {
      setPendingId(vars.id);
      return activityApi.updateProgress(
        projectId,
        vars.id,
        vars.percentComplete,
        vars.actualStartDate,
        vars.actualFinishDate
      );
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["activities", projectId] });
    },
    onError: (err: unknown) => {
      const msg = getErrorMessage(err, "Failed to update progress");
      notificationHelpers.handleApiError(err, msg);
    },
    onSettled: () => setPendingId(null),
  });

  const getFilteredActivities = () => {
    if (!lookAheadWeeks) return activities;
    const today = new Date();
    const endDate = new Date(today);
    endDate.setDate(endDate.getDate() + lookAheadWeeks * 7);
    return activities.filter((activity: ActivityResponse) => {
      const startDate = activity.earlyStartDate || activity.plannedStartDate;
      if (!startDate) return false;
      const activityStart = new Date(startDate);
      return activityStart >= today && activityStart <= endDate;
    });
  };

  const filteredActivities = getFilteredActivities();

  const canStart = (a: ActivityResponse) =>
    a.status === "NOT_STARTED" || (a.percentComplete ?? 0) === 0;
  const canComplete = (a: ActivityResponse) => (a.percentComplete ?? 0) < 100;

  const start = (a: ActivityResponse) =>
    progressMutation.mutate({
      id: a.id,
      percentComplete: Math.max(a.percentComplete ?? 0, 1),
      actualStartDate: a.actualStartDate || todayIso(),
      actualFinishDate: a.actualFinishDate ?? undefined,
    });

  const complete = (a: ActivityResponse) =>
    progressMutation.mutate({
      id: a.id,
      percentComplete: 100,
      actualStartDate: a.actualStartDate || a.plannedStartDate || todayIso(),
      actualFinishDate: todayIso(),
    });

  const saveProgress = (a: ActivityResponse) => {
    const raw = progressEdit[a.id];
    const pct = Math.max(0, Math.min(100, parseFloat(raw ?? "") || 0));
    const actualStart =
      pct > 0 && !a.actualStartDate ? todayIso() : (a.actualStartDate ?? undefined);
    const actualFinish = pct >= 100 ? todayIso() : (a.actualFinishDate ?? undefined);
    progressMutation.mutate({
      id: a.id,
      percentComplete: pct,
      actualStartDate: actualStart,
      actualFinishDate: actualFinish,
    });
    setProgressEdit((s) => {
      const next = { ...s };
      delete next[a.id];
      return next;
    });
  };

  const isLoading = isLoadingActivities || (viewMode === "tree" && isLoadingWbs);

  return (
    <div>
      <PageHeader
        title="Activities"
        description="View and manage project activities"
        actions={
          <button
            onClick={() => router.push(`/projects/${projectId}/activities/new`)}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
          >
            New Activity
          </button>
        }
      />

      {/* Controls */}
      <div className="mb-6 flex flex-wrap items-center gap-4">
        {/* Look-Ahead Filter */}
        <div className="flex gap-2">
          <button
            onClick={() => setLookAheadWeeks(null)}
            className={`rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
              lookAheadWeeks === null
                ? "bg-accent text-text-primary"
                : "bg-surface-active/50 text-text-secondary hover:bg-surface-active"
            }`}
          >
            All Activities
          </button>
          <button
            onClick={() => setLookAheadWeeks(4)}
            className={`rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
              lookAheadWeeks === 4
                ? "bg-accent text-text-primary"
                : "bg-surface-active/50 text-text-secondary hover:bg-surface-active"
            }`}
          >
            4-Week Look-Ahead
          </button>
          <button
            onClick={() => setLookAheadWeeks(13)}
            className={`rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
              lookAheadWeeks === 13
                ? "bg-accent text-text-primary"
                : "bg-surface-active/50 text-text-secondary hover:bg-surface-active"
            }`}
          >
            13-Week Look-Ahead
          </button>
        </div>

        {/* View Mode Toggle */}
        <div className="inline-flex rounded-lg border border-border bg-surface/60 p-0.5">
          <button
            onClick={() => setViewMode("list")}
            className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
              viewMode === "list"
                ? "bg-accent text-text-primary"
                : "text-text-secondary hover:bg-surface-hover/50 hover:text-text-primary"
            }`}
          >
            <List size={14} />
            List
          </button>
          <button
            onClick={() => setViewMode("tree")}
            className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
              viewMode === "tree"
                ? "bg-accent text-text-primary"
                : "text-text-secondary hover:bg-surface-hover/50 hover:text-text-primary"
            }`}
          >
            <FolderTree size={14} />
            WBS Tree
          </button>
        </div>
      </div>

      {progressMutation.isError && (
        <div className="mb-4 rounded-md bg-danger/10 border border-danger/30 p-3 text-sm text-danger">
          {(progressMutation.error as Error)?.message ?? "Failed to update progress"}
        </div>
      )}

      {isLoading ? (
        <div className="text-center text-text-muted">
          {viewMode === "tree" ? "Loading activities and WBS..." : "Loading activities..."}
        </div>
      ) : filteredActivities.length === 0 ? (
        <div className="rounded-lg border border-border bg-surface/80 p-8 text-center">
          <p className="text-text-muted">
            {lookAheadWeeks
              ? `No activities scheduled in the next ${lookAheadWeeks} weeks`
              : "No activities found"}
          </p>
        </div>
      ) : viewMode === "tree" ? (
        <ActivityWbsTreeView
          wbsNodes={wbsNodes}
          activities={filteredActivities}
          projectId={projectId}
          progressEdit={progressEdit}
          setProgressEdit={setProgressEdit}
          pendingId={pendingId}
          progressMutationIsPending={progressMutation.isPending}
          onSaveProgress={saveProgress}
          onStartActivity={start}
          onCompleteActivity={complete}
        />
      ) : (
        <div className="rounded-lg border border-border bg-surface/50 shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="border-b border-border bg-surface/80">
                <tr>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary">Code</th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary">Name</th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary">Status</th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary">% Complete</th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary">Start Date</th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary">Finish Date</th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary">Duration</th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary">Float</th>
                  <th className="px-4 py-3 text-right text-sm font-semibold text-text-secondary">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/50">
                {filteredActivities.map((activity: ActivityResponse) => {
                  const editing = progressEdit[activity.id] !== undefined;
                  const busy = pendingId === activity.id && progressMutation.isPending;
                  return (
                    <tr key={activity.id} className="hover:bg-surface/80">
                      <td className="px-4 py-4 text-sm font-medium text-text-primary">{activity.code}</td>
                      <td className="px-4 py-4 text-sm text-text-primary">{activity.name}</td>
                      <td className="px-4 py-4 text-sm">
                        <StatusBadge status={activity.status} />
                      </td>
                      <td className="px-4 py-4 text-sm text-text-secondary">
                        {editing ? (
                          <div className="flex items-center gap-1">
                            <input
                              type="number"
                              min={0}
                              max={100}
                              step={1}
                              autoFocus
                              className="w-16 rounded-md border border-border bg-surface-hover px-2 py-1 text-sm text-text-primary"
                              value={progressEdit[activity.id]}
                              onChange={(e) =>
                                setProgressEdit({ ...progressEdit, [activity.id]: e.target.value })
                              }
                              onKeyDown={(e) => {
                                if (e.key === "Enter") saveProgress(activity);
                                if (e.key === "Escape")
                                  setProgressEdit((s) => {
                                    const n = { ...s };
                                    delete n[activity.id];
                                    return n;
                                  });
                              }}
                              disabled={busy}
                            />
                            <button
                              type="button"
                              onClick={() => saveProgress(activity)}
                              disabled={busy}
                              className="rounded-md bg-success px-2 py-1 text-xs text-text-primary hover:bg-success/80 disabled:opacity-60"
                            >
                              Save
                            </button>
                            <button
                              type="button"
                              onClick={() =>
                                setProgressEdit((s) => {
                                  const n = { ...s };
                                  delete n[activity.id];
                                  return n;
                                })
                              }
                              className="text-xs text-text-secondary hover:text-text-primary"
                            >
                              Cancel
                            </button>
                          </div>
                        ) : (
                          <button
                            type="button"
                            onClick={() =>
                              setProgressEdit({
                                ...progressEdit,
                                [activity.id]: String(activity.percentComplete ?? 0),
                              })
                            }
                            className="rounded-md border border-border px-2 py-0.5 text-xs hover:bg-surface-hover"
                            title="Click to edit % complete"
                          >
                            {activity.percentComplete ?? 0}%
                          </button>
                        )}
                      </td>
                      <td className="px-4 py-4 text-sm text-text-secondary">
                        {activity.actualStartDate ||
                          activity.earlyStartDate ||
                          activity.plannedStartDate ||
                          "-"}
                      </td>
                      <td className="px-4 py-4 text-sm text-text-secondary">
                        {activity.actualFinishDate ||
                          activity.earlyFinishDate ||
                          activity.plannedFinishDate ||
                          "-"}
                      </td>
                      <td className="px-4 py-4 text-sm text-text-secondary">
                        {activity.remainingDuration || activity.duration || "-"} days
                      </td>
                      <td className="px-4 py-4 text-sm">
                        {activity.totalFloat !== undefined ? (
                          <span
                            className={`inline-block rounded-full px-3 py-1 text-xs font-semibold ${
                              activity.totalFloat === 0
                                ? "bg-danger/10 text-danger"
                                : activity.totalFloat <= 5
                                  ? "bg-warning/10 text-warning"
                                  : "bg-success/10 text-success"
                            }`}
                          >
                            {activity.totalFloat.toFixed(1)} days
                          </span>
                        ) : (
                          "-"
                        )}
                      </td>
                      <td className="px-4 py-4 text-right text-sm">
                        <div className="flex justify-end gap-2">
                          {canStart(activity) && (
                            <button
                              type="button"
                              onClick={() => start(activity)}
                              disabled={busy}
                              className="rounded-md bg-accent px-2 py-1 text-xs font-medium text-text-primary hover:bg-accent-hover disabled:opacity-60"
                              title="Record actual start date as today"
                            >
                              Start
                            </button>
                          )}
                          {canComplete(activity) && (
                            <button
                              type="button"
                              onClick={() => complete(activity)}
                              disabled={busy}
                              className="rounded-md bg-success px-2 py-1 text-xs font-medium text-text-primary hover:bg-success/80 disabled:opacity-60"
                              title="Mark 100% complete and set actual finish to today"
                            >
                              Complete
                            </button>
                          )}
                          <Link
                            href={`/projects/${projectId}/activities/${activity.id}`}
                            className="rounded-md border border-border px-2 py-1 text-xs text-text-secondary hover:bg-surface-hover"
                          >
                            View
                          </Link>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {lookAheadWeeks && (
        <div className="mt-6 rounded-lg bg-accent/10 p-4">
          <p className="text-sm text-blue-300">
            Showing {filteredActivities.length} of {activities.length} activities scheduled in the
            next {lookAheadWeeks} weeks.
          </p>
        </div>
      )}
    </div>
  );
}
