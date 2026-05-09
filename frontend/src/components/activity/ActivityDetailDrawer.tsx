"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { X, ExternalLink, Plus } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { activityApi } from "@/lib/api/activityApi";
import { resourceApi, type ResourceAssignmentResponse } from "@/lib/api/resourceApi";
import { StatusBadge } from "@/components/common/StatusBadge";
import { ActivityAssignmentsByRole } from "@/components/activity/ActivityAssignmentsByRole";
import { ResourceAssignmentForm } from "@/components/resource/ResourceAssignmentForm";

interface Props {
  open: boolean;
  onClose: () => void;
  projectId: string;
  activityId: string | null;
}

const formatDate = (value: string | null | undefined) => {
  if (!value) return "—";
  const d = new Date(value);
  return d.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
};

export function ActivityDetailDrawer({ open, onClose, projectId, activityId }: Props) {
  // Close on Escape.
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  const { data: activityData, isLoading: isLoadingActivity } = useQuery({
    queryKey: ["activity", projectId, activityId],
    queryFn: () => activityApi.getActivity(projectId, activityId!),
    enabled: open && !!activityId,
  });

  const { data: assignmentsData } = useQuery({
    queryKey: ["activity-assignments", projectId, activityId],
    queryFn: () => resourceApi.getAssignmentsByActivity(projectId, activityId!),
    enabled: open && !!activityId,
  });

  const activity = activityData?.data ?? null;
  const assignments = useMemo<ResourceAssignmentResponse[]>(() => {
    const raw = assignmentsData?.data;
    return Array.isArray(raw) ? raw : [];
  }, [assignmentsData]);

  if (!open || !activityId) return null;

  return (
    <aside
      className="fixed right-0 top-0 z-40 flex h-screen w-full flex-col border-l border-border bg-surface shadow-xl md:w-[640px] lg:w-[760px]"
      role="dialog"
      aria-modal="false"
      aria-label="Activity detail"
    >
      <DrawerInner
        // Re-mount on activity change so any local state (e.g. inline form open/closed)
        // resets cleanly without an effect.
        key={activityId}
        activity={activity}
        isLoadingActivity={isLoadingActivity}
        assignments={assignments}
        projectId={projectId}
        activityId={activityId}
        onClose={onClose}
      />
    </aside>
  );
}

function DrawerInner({
  activity,
  isLoadingActivity,
  assignments,
  projectId,
  activityId,
  onClose,
}: {
  activity: Awaited<ReturnType<typeof activityApi.getActivity>>["data"] | null;
  isLoadingActivity: boolean;
  assignments: ResourceAssignmentResponse[];
  projectId: string;
  activityId: string;
  onClose: () => void;
}) {
  const [showForm, setShowForm] = useState(false);

  return (
    <>
      <header className="flex items-start justify-between gap-3 border-b border-border px-5 py-4">
        <div className="min-w-0">
          {isLoadingActivity || !activity ? (
            <p className="text-sm text-text-muted">Loading…</p>
          ) : (
            <>
              <div className="flex items-center gap-2 text-sm font-medium text-text-secondary">
                <span>{activity.code}</span>
                <StatusBadge status={activity.status} />
              </div>
              <h2 className="mt-1 truncate text-lg font-semibold text-text-primary">
                {activity.name}
              </h2>
            </>
          )}
        </div>
        <button
          type="button"
          onClick={onClose}
          className="rounded-md p-1 text-text-secondary hover:bg-surface-hover hover:text-text-primary"
          aria-label="Close drawer"
        >
          <X size={18} />
        </button>
      </header>

      <div className="flex-1 overflow-y-auto px-5 py-4">
        {isLoadingActivity || !activity ? (
          <p className="text-sm text-text-muted">Loading activity…</p>
        ) : (
          <div className="space-y-6">
            <section>
              <h3 className="mb-2 text-xs font-semibold uppercase tracking-wider text-text-muted">
                Schedule
              </h3>
              <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                <div>
                  <dt className="text-text-muted">% Complete</dt>
                  <dd className="text-text-primary">{activity.percentComplete ?? 0}%</dd>
                </div>
                <div>
                  <dt className="text-text-muted">Duration</dt>
                  <dd className="text-text-primary">
                    {activity.originalDuration ?? activity.duration ?? "—"} d
                  </dd>
                </div>
                <div>
                  <dt className="text-text-muted">Planned Start</dt>
                  <dd className="text-text-primary">{formatDate(activity.plannedStartDate)}</dd>
                </div>
                <div>
                  <dt className="text-text-muted">Planned Finish</dt>
                  <dd className="text-text-primary">{formatDate(activity.plannedFinishDate)}</dd>
                </div>
                <div>
                  <dt className="text-text-muted">Actual Start</dt>
                  <dd className="text-text-primary">{formatDate(activity.actualStartDate)}</dd>
                </div>
                <div>
                  <dt className="text-text-muted">Actual Finish</dt>
                  <dd className="text-text-primary">{formatDate(activity.actualFinishDate)}</dd>
                </div>
                <div>
                  <dt className="text-text-muted">Total Float</dt>
                  <dd className="text-text-primary">
                    {activity.totalFloat != null ? `${activity.totalFloat.toFixed(1)} d` : "—"}
                  </dd>
                </div>
                <div>
                  <dt className="text-text-muted">Supervisor</dt>
                  <dd className="text-text-primary">
                    {activity.responsibleResourceName ?? (
                      <span className="text-text-muted">—</span>
                    )}
                  </dd>
                </div>
              </dl>
            </section>

            <section>
              <div className="mb-2 flex items-center justify-between gap-2">
                <h3 className="text-xs font-semibold uppercase tracking-wider text-text-muted">
                  Resource Assignments
                </h3>
                <button
                  type="button"
                  onClick={() => setShowForm((v) => !v)}
                  className="inline-flex items-center gap-1.5 rounded-md bg-accent px-2.5 py-1 text-xs font-medium text-accent-foreground hover:bg-accent-hover"
                >
                  <Plus size={14} />
                  Assign resource
                </button>
              </div>

              {showForm && (
                <div className="mb-3">
                  <ResourceAssignmentForm
                    projectId={projectId}
                    activityId={activityId}
                    onSuccess={() => setShowForm(false)}
                    onCancel={() => setShowForm(false)}
                  />
                </div>
              )}

              {assignments.length === 0 ? (
                <p className="text-sm text-text-muted">
                  No resources assigned yet. Click <span className="font-medium">Assign resource</span> above to add one.
                </p>
              ) : (
                <ActivityAssignmentsByRole
                  assignments={assignments}
                  // The drawer is read-only-ish for staff/swap — those flows live on the full
                  // detail page where StaffSwapDialog is wired up. Send the user there.
                  onStaff={() => {
                    window.location.href = `/projects/${projectId}/activities/${activityId}`;
                  }}
                  onSwap={() => {
                    window.location.href = `/projects/${projectId}/activities/${activityId}`;
                  }}
                />
              )}
            </section>
          </div>
        )}
      </div>

      <footer className="border-t border-border bg-surface/80 px-5 py-3">
        <Link
          href={`/projects/${projectId}/activities/${activityId}`}
          className="inline-flex items-center gap-1.5 text-sm font-medium text-accent hover:underline"
        >
          Open full detail page
          <ExternalLink size={14} />
        </Link>
      </footer>
    </>
  );
}
