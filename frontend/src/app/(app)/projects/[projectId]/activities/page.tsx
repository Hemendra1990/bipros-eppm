"use client";

import { useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "@/components/common/PageHeader";
import { activityApi } from "@/lib/api/activityApi";
import type { ActivityResponse } from "@/lib/api/activityApi";
import { StatusBadge } from "@/components/common/StatusBadge";
import Link from "next/link";

const todayIso = () => new Date().toISOString().slice(0, 10);

export default function ActivitiesPage() {
  const router = useRouter();
  const params = useParams();
  const projectId = params.projectId as string;
  const qc = useQueryClient();

  const [lookAheadWeeks, setLookAheadWeeks] = useState<4 | 13 | null>(null);
  // Row-specific inline editor state. Keyed by activity id; value = string
  // currently typed into the % input.
  const [progressEdit, setProgressEdit] = useState<Record<string, string>>({});
  const [pendingId, setPendingId] = useState<string | null>(null);

  const { data: activitiesData, isLoading } = useQuery({
    queryKey: ["activities", projectId],
    queryFn: () => activityApi.listActivities(projectId, 0, 500),
    enabled: !!projectId,
  });

  const activities = (activitiesData?.data?.content || []) as ActivityResponse[];

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

  return (
    <div>
      <PageHeader
        title="Activities"
        description="View and manage project activities"
        actions={
          <button
            onClick={() => router.push(`/projects/${projectId}/activities/new`)}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500"
          >
            New Activity
          </button>
        }
      />

      {/* Look-Ahead Filter */}
      <div className="mb-6 flex gap-2">
        <button
          onClick={() => setLookAheadWeeks(null)}
          className={`rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
            lookAheadWeeks === null
              ? "bg-blue-600 text-white"
              : "bg-slate-700/50 text-slate-300 hover:bg-slate-700"
          }`}
        >
          All Activities
        </button>
        <button
          onClick={() => setLookAheadWeeks(4)}
          className={`rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
            lookAheadWeeks === 4
              ? "bg-blue-600 text-white"
              : "bg-slate-700/50 text-slate-300 hover:bg-slate-700"
          }`}
        >
          4-Week Look-Ahead
        </button>
        <button
          onClick={() => setLookAheadWeeks(13)}
          className={`rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
            lookAheadWeeks === 13
              ? "bg-blue-600 text-white"
              : "bg-slate-700/50 text-slate-300 hover:bg-slate-700"
          }`}
        >
          13-Week Look-Ahead
        </button>
      </div>

      {progressMutation.isError && (
        <div className="mb-4 rounded-md bg-red-500/10 border border-red-500/30 p-3 text-sm text-red-300">
          {(progressMutation.error as Error)?.message ?? "Failed to update progress"}
        </div>
      )}

      {isLoading ? (
        <div className="text-center text-slate-500">Loading activities...</div>
      ) : filteredActivities.length === 0 ? (
        <div className="rounded-lg border border-slate-800 bg-slate-900/80 p-8 text-center">
          <p className="text-slate-500">
            {lookAheadWeeks
              ? `No activities scheduled in the next ${lookAheadWeeks} weeks`
              : "No activities found"}
          </p>
        </div>
      ) : (
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="border-b border-slate-800 bg-slate-900/80">
                <tr>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-slate-300">Code</th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-slate-300">Name</th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-slate-300">Status</th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-slate-300">% Complete</th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-slate-300">Start Date</th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-slate-300">Finish Date</th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-slate-300">Duration</th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-slate-300">Float</th>
                  <th className="px-4 py-3 text-right text-sm font-semibold text-slate-300">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/50">
                {filteredActivities.map((activity: ActivityResponse) => {
                  const editing = progressEdit[activity.id] !== undefined;
                  const busy = pendingId === activity.id && progressMutation.isPending;
                  return (
                    <tr key={activity.id} className="hover:bg-slate-900/80">
                      <td className="px-4 py-4 text-sm font-medium text-white">{activity.code}</td>
                      <td className="px-4 py-4 text-sm text-white">{activity.name}</td>
                      <td className="px-4 py-4 text-sm">
                        <StatusBadge status={activity.status} />
                      </td>
                      <td className="px-4 py-4 text-sm text-slate-400">
                        {editing ? (
                          <div className="flex items-center gap-1">
                            <input
                              type="number"
                              min={0}
                              max={100}
                              step={1}
                              autoFocus
                              className="w-16 rounded-md border border-slate-700 bg-slate-800 px-2 py-1 text-sm text-white"
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
                              className="rounded-md bg-emerald-600 px-2 py-1 text-xs text-white hover:bg-emerald-500 disabled:opacity-60"
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
                              className="text-xs text-slate-400 hover:text-slate-200"
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
                            className="rounded-md border border-slate-700 px-2 py-0.5 text-xs hover:bg-slate-800"
                            title="Click to edit % complete"
                          >
                            {activity.percentComplete ?? 0}%
                          </button>
                        )}
                      </td>
                      <td className="px-4 py-4 text-sm text-slate-400">
                        {activity.actualStartDate ||
                          activity.earlyStartDate ||
                          activity.plannedStartDate ||
                          "-"}
                      </td>
                      <td className="px-4 py-4 text-sm text-slate-400">
                        {activity.actualFinishDate ||
                          activity.earlyFinishDate ||
                          activity.plannedFinishDate ||
                          "-"}
                      </td>
                      <td className="px-4 py-4 text-sm text-slate-400">
                        {activity.remainingDuration || activity.duration || "-"} days
                      </td>
                      <td className="px-4 py-4 text-sm">
                        {activity.totalFloat !== undefined ? (
                          <span
                            className={`inline-block rounded-full px-3 py-1 text-xs font-semibold ${
                              activity.totalFloat === 0
                                ? "bg-red-500/10 text-red-300"
                                : activity.totalFloat <= 5
                                  ? "bg-amber-500/10 text-amber-300"
                                  : "bg-emerald-500/10 text-emerald-300"
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
                              className="rounded-md bg-blue-600 px-2 py-1 text-xs font-medium text-white hover:bg-blue-500 disabled:opacity-60"
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
                              className="rounded-md bg-emerald-600 px-2 py-1 text-xs font-medium text-white hover:bg-emerald-500 disabled:opacity-60"
                              title="Mark 100% complete and set actual finish to today"
                            >
                              Complete
                            </button>
                          )}
                          <Link
                            href={`/projects/${projectId}/activities/${activity.id}`}
                            className="rounded-md border border-slate-700 px-2 py-1 text-xs text-slate-300 hover:bg-slate-800"
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
        <div className="mt-6 rounded-lg bg-blue-500/10 p-4">
          <p className="text-sm text-blue-300">
            Showing {filteredActivities.length} of {activities.length} activities scheduled in the
            next {lookAheadWeeks} weeks.
          </p>
        </div>
      )}
    </div>
  );
}
