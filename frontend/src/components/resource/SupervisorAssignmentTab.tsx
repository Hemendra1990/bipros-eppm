"use client";

import { useState, useMemo } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { UserCheck, AlertTriangle, Search } from "lucide-react";
import { activityApi, type ActivityResponse } from "@/lib/api/activityApi";
import { projectResourceApi } from "@/lib/api/projectResourceApi";
import { StatusBadge } from "@/components/common/StatusBadge";
import { getErrorMessage } from "@/lib/utils/error";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogBody,
  DialogFooter,
} from "@/components/ui/dialog";

/**
 * Bulk-assign one supervisor (Manpower / Labor) across many activities. Sister to the
 * per-activity Supervisor field on the activity create / edit form — both write to the
 * same column ({@code Activity.responsibleResourceId}).
 *
 * <p>Two-panel layout:
 * <ul>
 *   <li>LEFT: scrollable list of all LABOR resources in the project pool. Radio-select.
 *       Below the list: a summary card with selection counts + Save action.</li>
 *   <li>RIGHT: full activities table with Code, Name, Status, planned dates, % Complete,
 *       and Current Supervisor. Multi-select via checkbox. Rows where another supervisor
 *       is already set show "(will replace)" with a warning tint when ticked.</li>
 * </ul>
 */
export function SupervisorAssignmentTab({ projectId }: { projectId: string }) {
  const queryClient = useQueryClient();
  const [supervisorId, setSupervisorId] = useState<string>("");
  const [supervisorSearch, setSupervisorSearch] = useState<string>("");
  const [checkedActivityIds, setCheckedActivityIds] = useState<Set<string>>(new Set());
  // Once the user manually changes checkboxes, we stop auto-resetting on supervisor change.
  const [userTouched, setUserTouched] = useState(false);
  const [confirmReplaceOpen, setConfirmReplaceOpen] = useState(false);

  const { data: poolData, isLoading: isLoadingPool } = useQuery({
    queryKey: ["resource-pool", projectId],
    queryFn: () => projectResourceApi.listPool(projectId),
    enabled: !!projectId,
  });

  const { data: activitiesData, isLoading: isLoadingActivities } = useQuery({
    queryKey: ["activities", projectId],
    queryFn: () => activityApi.listActivities(projectId, 0, 1000),
    enabled: !!projectId,
  });

  const supervisorOptions = useMemo(() => {
    return (poolData?.data ?? [])
      .filter((p) => {
        const t = (p.resourceTypeName ?? "").toLowerCase();
        return t.includes("labor") || t.includes("labour") || t.includes("manpower");
      })
      .map((p) => ({
        value: p.resourceId,
        code: p.resourceCode ?? "",
        name: p.resourceName ?? p.resourceId,
        role: p.roleName ?? null,
      }))
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [poolData]);

  const filteredSupervisorOptions = useMemo(() => {
    const q = supervisorSearch.trim().toLowerCase();
    if (!q) return supervisorOptions;
    return supervisorOptions.filter(
      (o) =>
        o.name.toLowerCase().includes(q) ||
        o.code.toLowerCase().includes(q) ||
        (o.role ?? "").toLowerCase().includes(q)
    );
  }, [supervisorOptions, supervisorSearch]);

  const activities: ActivityResponse[] = activitiesData?.data?.content ?? [];
  const sortedActivities = useMemo(
    () => [...activities].sort((a, b) => a.code.localeCompare(b.code, undefined, { numeric: true })),
    [activities]
  );

  const handleSupervisorChange = (val: string) => {
    setSupervisorId(val);
    if (!userTouched) {
      // Auto-pre-check activities where this is already the supervisor.
      const next = new Set<string>();
      for (const a of activities) {
        if (a.responsibleResourceId === val) next.add(a.id);
      }
      setCheckedActivityIds(next);
    }
  };

  const toggleActivity = (activityId: string) => {
    setUserTouched(true);
    setCheckedActivityIds((prev) => {
      const next = new Set(prev);
      if (next.has(activityId)) next.delete(activityId);
      else next.add(activityId);
      return next;
    });
  };

  const toggleAll = () => {
    setUserTouched(true);
    setCheckedActivityIds((prev) =>
      prev.size === sortedActivities.length
        ? new Set()
        : new Set(sortedActivities.map((a) => a.id))
    );
  };

  const supervisorName =
    supervisorOptions.find((o) => o.value === supervisorId)?.name ?? null;

  // Counts for the summary + warning banner.
  const replaceConflicts = useMemo(() => {
    if (!supervisorId) return [] as string[];
    return sortedActivities
      .filter(
        (a) =>
          checkedActivityIds.has(a.id) &&
          a.responsibleResourceId &&
          a.responsibleResourceId !== supervisorId
      )
      .map((a) => a.id);
  }, [sortedActivities, checkedActivityIds, supervisorId]);

  const noOpCount = sortedActivities.filter(
    (a) => checkedActivityIds.has(a.id) && a.responsibleResourceId === supervisorId
  ).length;
  const newAssignmentCount =
    checkedActivityIds.size - replaceConflicts.length - noOpCount;

  const saveMutation = useMutation({
    mutationFn: () =>
      activityApi.bulkSetSupervisor(projectId, {
        supervisorResourceId: supervisorId,
        supervisorResourceName: supervisorName,
        activityIds: Array.from(checkedActivityIds),
      }),
    onSuccess: (resp) => {
      const updated = resp.data?.updated ?? checkedActivityIds.size;
      toast.success(
        `Supervisor set on ${updated} ${updated === 1 ? "activity" : "activities"}`
      );
      queryClient.invalidateQueries({ queryKey: ["activities", projectId] });
      queryClient.invalidateQueries({ queryKey: ["resource-assignments", projectId] });
      setUserTouched(false);
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to assign supervisor"));
    },
  });

  const handleSave = () => {
    if (replaceConflicts.length > 0) {
      setConfirmReplaceOpen(true);
      return;
    }
    saveMutation.mutate();
  };

  const canSave =
    !!supervisorId && checkedActivityIds.size > 0 && !saveMutation.isPending;
  const allChecked =
    sortedActivities.length > 0 && checkedActivityIds.size === sortedActivities.length;

  const formatDate = (value: string | null | undefined) => {
    if (!value) return "—";
    const d = new Date(value);
    return d.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[360px_1fr] gap-4 items-start">
      {/* LEFT panel — supervisor list + summary */}
      <div className="space-y-3 lg:sticky lg:top-4">
        {/* Picker card */}
        <div className="rounded-lg border border-border bg-surface/50 shadow-sm">
          <div className="px-4 py-3 border-b border-border">
            <div className="flex items-start gap-2">
              <UserCheck size={16} className="text-accent mt-0.5 shrink-0" />
              <div>
                <h3 className="text-sm font-semibold text-text-primary">
                  Bulk Supervisor Assignment
                </h3>
                <p className="text-xs text-text-secondary mt-0.5">
                  Pick one Labor resource, then check the activities to supervise.
                </p>
              </div>
            </div>
          </div>

          <div className="px-4 pt-3 pb-2">
            <div className="flex items-center gap-2 rounded-md border border-border bg-surface-hover px-2 py-1.5">
              <Search size={13} className="text-text-muted shrink-0" />
              <input
                type="text"
                placeholder="Filter labor resources..."
                value={supervisorSearch}
                onChange={(e) => setSupervisorSearch(e.target.value)}
                className="w-full bg-transparent text-sm text-text-primary placeholder-text-muted focus:outline-none"
              />
            </div>
            <p className="mt-1 text-xs text-text-muted">
              {supervisorOptions.length} labor resource{supervisorOptions.length === 1 ? "" : "s"} in pool
              {supervisorSearch && ` · ${filteredSupervisorOptions.length} match`}
            </p>
          </div>

          <div className="max-h-[420px] overflow-y-auto border-t border-border">
            {isLoadingPool ? (
              <div className="px-4 py-8 text-center text-sm text-text-muted">
                Loading project pool...
              </div>
            ) : supervisorOptions.length === 0 ? (
              <div className="px-4 py-8 text-center text-sm text-text-muted">
                No labor resources in this project&apos;s pool. Add some via the Pool sub-tab.
              </div>
            ) : filteredSupervisorOptions.length === 0 ? (
              <div className="px-4 py-8 text-center text-sm text-text-muted">
                No matches.
              </div>
            ) : (
              <ul className="divide-y divide-border/50">
                {filteredSupervisorOptions.map((opt) => {
                  const selected = supervisorId === opt.value;
                  return (
                    <li key={opt.value}>
                      <label
                        className={`flex items-center gap-3 px-4 py-2.5 cursor-pointer transition-colors ${
                          selected ? "bg-accent/10" : "hover:bg-surface-hover/40"
                        }`}
                      >
                        <input
                          type="radio"
                          name="supervisor"
                          checked={selected}
                          onChange={() => handleSupervisorChange(opt.value)}
                          className="shrink-0 accent-accent"
                        />
                        <div className="min-w-0 flex-1">
                          <div className={`text-sm font-medium truncate ${selected ? "text-accent" : "text-text-primary"}`}>
                            {opt.name}
                          </div>
                          <div className="text-xs text-text-muted truncate">
                            {opt.code}
                            {opt.role && <span> · {opt.role}</span>}
                          </div>
                        </div>
                      </label>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </div>

        {/* Summary card — only when a supervisor is picked */}
        {supervisorId && (
          <div className="rounded-lg border border-border bg-surface/50 p-4 shadow-sm space-y-2 text-sm">
            <div className="flex items-center justify-between">
              <span className="text-text-secondary">Selected:</span>
              <span className="font-semibold text-text-primary">
                {checkedActivityIds.size}
              </span>
            </div>
            {noOpCount > 0 && (
              <div className="flex items-center justify-between text-xs">
                <span className="text-text-muted">Already set (no change)</span>
                <span className="text-text-secondary">{noOpCount}</span>
              </div>
            )}
            {newAssignmentCount > 0 && (
              <div className="flex items-center justify-between text-xs">
                <span className="text-text-muted">New assignments</span>
                <span className="text-success">{newAssignmentCount}</span>
              </div>
            )}
            {replaceConflicts.length > 0 && (
              <div className="flex items-center justify-between text-xs">
                <span className="text-text-muted">Will replace existing</span>
                <span className="text-warning font-medium">{replaceConflicts.length}</span>
              </div>
            )}

            {replaceConflicts.length > 0 && (
              <div className="mt-2 flex items-start gap-2 rounded-md border border-warning/40 bg-warning/10 p-2 text-xs text-warning">
                <AlertTriangle size={14} className="shrink-0 mt-0.5" />
                <span>
                  Saving will overwrite the supervisor on {replaceConflicts.length}{" "}
                  {replaceConflicts.length === 1 ? "activity" : "activities"}. You will
                  be asked to confirm.
                </span>
              </div>
            )}

            <button
              type="button"
              disabled={!canSave}
              onClick={handleSave}
              className="mt-3 w-full rounded-md bg-accent px-4 py-2 text-sm font-medium text-accent-foreground hover:bg-accent-hover disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {saveMutation.isPending
                ? "Saving..."
                : `Save (${checkedActivityIds.size} selected)`}
            </button>
          </div>
        )}
      </div>

      <Dialog
        open={confirmReplaceOpen}
        onOpenChange={(next) => setConfirmReplaceOpen(next)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Replace existing supervisor?</DialogTitle>
          </DialogHeader>
          <DialogBody>
            <p>
              {replaceConflicts.length}{" "}
              {replaceConflicts.length === 1 ? "activity has" : "activities have"} a
              different supervisor already. Saving will replace{" "}
              {replaceConflicts.length === 1 ? "it" : "them"} with{" "}
              <span className="font-semibold text-charcoal">{supervisorName}</span>.
            </p>
          </DialogBody>
          <DialogFooter>
            <button
              type="button"
              onClick={() => setConfirmReplaceOpen(false)}
              className="rounded-md border border-border px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={() => {
                setConfirmReplaceOpen(false);
                saveMutation.mutate();
              }}
              className="rounded-md bg-warning px-4 py-2 text-sm font-medium text-text-primary hover:bg-warning/80"
            >
              Replace &amp; save
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* RIGHT panel — activities table */}
      <div className="rounded-lg border border-border bg-surface/50 shadow-sm">
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          <div className="text-sm font-semibold text-text-primary">
            Activities
            {sortedActivities.length > 0 && (
              <span className="ml-2 text-xs text-text-muted">
                ({checkedActivityIds.size} of {sortedActivities.length} selected)
              </span>
            )}
          </div>
          <button
            type="button"
            onClick={toggleAll}
            disabled={!supervisorId || sortedActivities.length === 0}
            className="text-xs text-accent hover:underline disabled:text-text-muted disabled:no-underline disabled:cursor-not-allowed"
          >
            {allChecked ? "Deselect all" : "Select all"}
          </button>
        </div>

        {!supervisorId ? (
          <div className="px-4 py-12 text-center text-sm text-text-muted">
            Pick a supervisor on the left to choose activities.
          </div>
        ) : isLoadingActivities ? (
          <div className="px-4 py-12 text-center text-sm text-text-muted">
            Loading activities...
          </div>
        ) : sortedActivities.length === 0 ? (
          <div className="px-4 py-12 text-center text-sm text-text-muted">
            No activities in this project.
          </div>
        ) : (
          <div className="max-h-[640px] overflow-auto">
            <table className="w-full text-sm">
              <thead className="sticky top-0 bg-surface/90 backdrop-blur border-b border-border z-10">
                <tr>
                  <th className="px-4 py-2 text-left text-xs font-medium text-text-secondary w-10"></th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-text-secondary whitespace-nowrap">Code</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-text-secondary">Name</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-text-secondary whitespace-nowrap">Status</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-text-secondary whitespace-nowrap">Planned Start</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-text-secondary whitespace-nowrap">Planned Finish</th>
                  <th className="px-4 py-2 text-right text-xs font-medium text-text-secondary whitespace-nowrap">% Complete</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-text-secondary whitespace-nowrap">Current Supervisor</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/50">
                {sortedActivities.map((a) => {
                  const checked = checkedActivityIds.has(a.id);
                  const matchesPicked =
                    a.responsibleResourceId === supervisorId &&
                    a.responsibleResourceId != null;
                  const isConflict =
                    checked &&
                    a.responsibleResourceId != null &&
                    a.responsibleResourceId !== supervisorId;
                  return (
                    <tr
                      key={a.id}
                      onClick={() => toggleActivity(a.id)}
                      className={`cursor-pointer hover:bg-surface-hover/30 ${
                        isConflict ? "bg-warning/5" : ""
                      }`}
                    >
                      <td className="px-4 py-2">
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={() => toggleActivity(a.id)}
                          onClick={(e) => e.stopPropagation()}
                          className="rounded border-border"
                        />
                      </td>
                      <td className="px-4 py-2 text-text-secondary whitespace-nowrap">{a.code}</td>
                      <td className="px-4 py-2 text-text-primary">{a.name}</td>
                      <td className="px-4 py-2 whitespace-nowrap">
                        <StatusBadge status={a.status} />
                      </td>
                      <td className="px-4 py-2 text-text-secondary whitespace-nowrap">{formatDate(a.plannedStartDate)}</td>
                      <td className="px-4 py-2 text-text-secondary whitespace-nowrap">{formatDate(a.plannedFinishDate)}</td>
                      <td className="px-4 py-2 text-right text-text-primary whitespace-nowrap">
                        {a.percentComplete ?? 0}%
                      </td>
                      <td className="px-4 py-2 text-text-secondary whitespace-nowrap">
                        {a.responsibleResourceName ? (
                          <span
                            className={
                              matchesPicked
                                ? "text-accent font-medium"
                                : isConflict
                                  ? "text-warning"
                                  : "text-text-secondary"
                            }
                          >
                            {a.responsibleResourceName}
                            {matchesPicked && (
                              <span className="ml-1 text-xs text-text-muted">(current)</span>
                            )}
                            {isConflict && (
                              <span className="ml-1 text-xs font-semibold">(will replace)</span>
                            )}
                          </span>
                        ) : (
                          <span className="text-text-muted">—</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
