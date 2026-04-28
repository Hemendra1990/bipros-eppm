"use client";

import { useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { List, FolderTree, Play } from "lucide-react";
import toast from "react-hot-toast";
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
  const [viewMode, setViewMode] = useState<"list" | "tree">("tree");
  const [scheduleError, setScheduleError] = useState("");
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

  const { data: relationshipsData } = useQuery({
    queryKey: ["relationships", projectId],
    queryFn: () => activityApi.getRelationships(projectId),
    enabled: !!projectId,
  });

  const activities = (activitiesData?.data?.content || []) as ActivityResponse[];
  const wbsNodes = wbsData?.data ?? [];
  const relationships = relationshipsData?.data ?? [];

  const scheduleMutation = useMutation({
    mutationFn: () => activityApi.triggerSchedule(projectId, "RETAINED_LOGIC"),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["activities", projectId] });
      qc.invalidateQueries({ queryKey: ["critical-path", projectId] });
      setScheduleError("");
      toast.success("Schedule calculated successfully");
    },
    onError: (err: unknown) => {
      const msg = getErrorMessage(err, "Failed to trigger schedule");
      setScheduleError(msg);
      toast.error(msg);
    },
  });

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
        {/* Run Schedule */}
        <button
          onClick={() => scheduleMutation.mutate()}
          disabled={scheduleMutation.isPending}
          className="inline-flex items-center gap-2 rounded-md bg-success px-4 py-2 text-sm font-medium text-text-primary hover:bg-success/80 disabled:opacity-50"
        >
          <Play size={16} />
          {scheduleMutation.isPending ? "Running..." : "Run Schedule"}
        </button>

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

      {scheduleError && (
        <div className="mb-4 rounded-md bg-danger/10 border border-danger/30 p-3 text-sm text-danger">
          {scheduleError}
        </div>
      )}

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
          relationships={relationships}
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
        <ActivitiesListTable
          activities={filteredActivities}
          relationships={relationships}
          projectId={projectId}
          progressEdit={progressEdit}
          setProgressEdit={setProgressEdit}
          pendingId={pendingId}
          progressIsPending={progressMutation.isPending}
          saveProgress={saveProgress}
          start={start}
          complete={complete}
          canStart={canStart}
          canComplete={canComplete}
        />
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

function ActivitiesListTable({
  activities,
  relationships,
  projectId,
  progressEdit,
  setProgressEdit,
  pendingId,
  progressIsPending,
  saveProgress,
  start,
  complete,
  canStart,
  canComplete,
}: {
  activities: ActivityResponse[];
  relationships: Array<{ id?: string; predecessorActivityId: string; successorActivityId: string; relationshipType: string }>;
  projectId: string;
  progressEdit: Record<string, string>;
  setProgressEdit: React.Dispatch<React.SetStateAction<Record<string, string>>>;
  pendingId: string | null;
  progressIsPending: boolean;
  saveProgress: (a: ActivityResponse) => void;
  start: (a: ActivityResponse) => void;
  complete: (a: ActivityResponse) => void;
  canStart: (a: ActivityResponse) => boolean;
  canComplete: (a: ActivityResponse) => boolean;
}) {
  // Build dependency count map
  const predCountMap = new Map<string, number>();
  const succCountMap = new Map<string, number>();
  for (const rel of relationships) {
    predCountMap.set(rel.successorActivityId, (predCountMap.get(rel.successorActivityId) ?? 0) + 1);
    succCountMap.set(rel.predecessorActivityId, (succCountMap.get(rel.predecessorActivityId) ?? 0) + 1);
  }

  const formatDate = (value: string | null | undefined) => {
    if (!value) return "—";
    const d = new Date(value);
    return d.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
  };

  return (
    <div className="rounded-lg border border-border bg-surface/50 shadow-sm">
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead className="border-b border-border bg-surface/80">
            <tr>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary whitespace-nowrap">Code</th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary whitespace-nowrap">Name</th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary whitespace-nowrap">Duration (days)</th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary whitespace-nowrap">% Complete</th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary whitespace-nowrap">Status</th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary whitespace-nowrap">Float (days)</th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary whitespace-nowrap">Planned Start</th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary whitespace-nowrap">Planned Finish</th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary whitespace-nowrap">ES</th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary whitespace-nowrap">EF</th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary whitespace-nowrap">LS</th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary whitespace-nowrap">LF</th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary whitespace-nowrap">Actions</th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary whitespace-nowrap">Deps</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border/50">
            {activities.map((activity: ActivityResponse) => {
              const editing = progressEdit[activity.id] !== undefined;
              const busy = pendingId === activity.id && progressIsPending;
              const predCount = predCountMap.get(activity.id) ?? 0;
              const succCount = succCountMap.get(activity.id) ?? 0;
              return (
                <tr key={activity.id} className="hover:bg-surface/80">
                  <td className="px-4 py-4 text-sm font-medium text-text-primary whitespace-nowrap">{activity.code}</td>
                  <td className="px-4 py-4 text-sm text-text-primary whitespace-nowrap">{activity.name}</td>
                  <td className="px-4 py-4 text-sm text-text-secondary whitespace-nowrap">{activity.originalDuration ?? activity.duration ?? "—"}</td>
                  <td className="px-4 py-4 text-sm text-text-secondary whitespace-nowrap">
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
                  <td className="px-4 py-4 text-sm whitespace-nowrap">
                    <StatusBadge status={activity.status} />
                  </td>
                  <td className="px-4 py-4 text-sm whitespace-nowrap">
                    {activity.totalFloat != null ? (
                      <span
                        className={`inline-block rounded-full px-3 py-1 text-xs font-semibold ${
                          activity.totalFloat === 0
                            ? "bg-danger/10 text-danger"
                            : activity.totalFloat <= 5
                              ? "bg-warning/10 text-warning"
                              : "bg-success/10 text-success"
                        }`}
                      >
                        {activity.totalFloat.toFixed(1)}
                      </span>
                    ) : (
                      "—"
                    )}
                  </td>
                  <td className="px-4 py-4 text-sm text-text-secondary whitespace-nowrap">{formatDate(activity.plannedStartDate)}</td>
                  <td className="px-4 py-4 text-sm text-text-secondary whitespace-nowrap">{formatDate(activity.plannedFinishDate)}</td>
                  <td className="px-4 py-4 text-sm text-text-secondary whitespace-nowrap">{formatDate(activity.earlyStartDate)}</td>
                  <td className="px-4 py-4 text-sm text-text-secondary whitespace-nowrap">{formatDate(activity.earlyFinishDate)}</td>
                  <td className="px-4 py-4 text-sm text-text-secondary whitespace-nowrap">{formatDate(activity.lateStartDate)}</td>
                  <td className="px-4 py-4 text-sm text-text-secondary whitespace-nowrap">{formatDate(activity.lateFinishDate)}</td>
                  <td className="px-4 py-4 text-sm whitespace-nowrap">
                    <div className="flex gap-2">
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
                  <td className="px-4 py-4 text-sm whitespace-nowrap">
                    {predCount === 0 && succCount === 0 ? (
                      <span className="text-text-muted">—</span>
                    ) : (
                      <span className="text-xs text-text-secondary">
                        {predCount > 0 && <span className="text-accent">{predCount}P</span>}
                        {predCount > 0 && succCount > 0 && " / "}
                        {succCount > 0 && <span className="text-success">{succCount}S</span>}
                      </span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
