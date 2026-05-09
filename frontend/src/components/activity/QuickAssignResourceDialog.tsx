"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogBody,
  DialogFooter,
} from "@/components/ui/dialog";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { resourceApi } from "@/lib/api/resourceApi";
import { projectResourceApi, type ProjectResourceResponse } from "@/lib/api/projectResourceApi";
import { getErrorMessage } from "@/lib/utils/error";

export type ResourceKind = "MANPOWER" | "MATERIAL" | "EQUIPMENT";

interface Props {
  open: boolean;
  onClose: () => void;
  projectId: string;
  activityId: string;
  activityCode: string;
  activityName: string;
  kind: ResourceKind;
}

const KIND_LABEL: Record<ResourceKind, string> = {
  MANPOWER: "Manpower",
  MATERIAL: "Material",
  EQUIPMENT: "Equipment",
};

// Map a project-pool entry's resourceTypeName onto our kind selector. The backend treats
// LABOR and MANPOWER interchangeably (LABOR is the underlying ResourceType code; manpower is
// the user-facing label) — we match either.
function poolMatchesKind(entry: ProjectResourceResponse, kind: ResourceKind): boolean {
  const t = (entry.resourceTypeName ?? "").trim().toLowerCase();
  if (!t) return false;
  if (kind === "MANPOWER") return t === "manpower" || t === "labor" || t === "labour";
  if (kind === "MATERIAL") return t === "material";
  if (kind === "EQUIPMENT") return t === "equipment";
  return false;
}

export function QuickAssignResourceDialog({
  open,
  onClose,
  projectId,
  activityId,
  activityCode,
  activityName,
  kind,
}: Props) {
  const queryClient = useQueryClient();
  // Form state is local — the parent re-mounts this dialog when the (activity, kind) pair
  // changes (it's rendered conditionally on `quickAssign != null`), so we don't need to
  // reset state imperatively.
  const [resourceId, setResourceId] = useState("");
  const [plannedUnits, setPlannedUnits] = useState("");
  const [rateType, setRateType] = useState("STANDARD");

  const { data: poolData, isLoading: isLoadingPool } = useQuery({
    queryKey: ["resource-pool", projectId],
    queryFn: () => projectResourceApi.listPool(projectId),
    enabled: open,
  });

  const pool = useMemo<ProjectResourceResponse[]>(() => {
    const raw = poolData?.data;
    return Array.isArray(raw) ? raw : [];
  }, [poolData]);

  const filteredPool = useMemo(
    () => pool.filter((p) => poolMatchesKind(p, kind)),
    [pool, kind]
  );

  const options = filteredPool.map((p) => ({
    value: p.resourceId,
    label: `${p.resourceCode ?? p.resourceId} — ${p.resourceName ?? "Unknown"}`,
  }));

  const assignMutation = useMutation({
    mutationFn: () =>
      resourceApi.createProjectResourceAssignment(projectId, {
        activityId,
        resourceId,
        projectId,
        plannedUnits: parseFloat(plannedUnits),
        rateType,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["resource-assignments", projectId] });
      queryClient.invalidateQueries({ queryKey: ["activity-assignments", projectId, activityId] });
      toast.success(`${KIND_LABEL[kind]} assigned to ${activityCode}`);
      onClose();
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to assign resource"));
    },
  });

  const parsedUnits = parseFloat(plannedUnits);
  const canSubmit =
    !!resourceId &&
    Number.isFinite(parsedUnits) &&
    parsedUnits > 0 &&
    !assignMutation.isPending;

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Assign {KIND_LABEL[kind]}</DialogTitle>
          <p className="mt-1 text-xs text-slate">
            {activityCode} — {activityName}
          </p>
        </DialogHeader>
        <DialogBody>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-text-secondary">
                {KIND_LABEL[kind]} Resource
              </label>
              {isLoadingPool ? (
                <p className="mt-1 text-xs text-text-muted">Loading project pool…</p>
              ) : filteredPool.length === 0 ? (
                <p className="mt-1 text-xs text-amber-600">
                  No {KIND_LABEL[kind].toLowerCase()} resources in this project&apos;s pool.{" "}
                  <a
                    href={`/projects/${projectId}/resources`}
                    className="text-accent underline"
                  >
                    Add one →
                  </a>
                </p>
              ) : (
                <div className="mt-1">
                  <SearchableSelect
                    value={resourceId}
                    onChange={setResourceId}
                    placeholder={`Search ${KIND_LABEL[kind].toLowerCase()} resources...`}
                    options={options}
                  />
                </div>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-text-secondary">
                Planned Units
              </label>
              <input
                type="number"
                min={0}
                step="0.01"
                value={plannedUnits}
                onChange={(e) => setPlannedUnits(e.target.value)}
                className="mt-1 block w-full rounded-md border border-border bg-surface/50 px-3 py-2 text-sm text-text-primary shadow-sm focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                placeholder="e.g. 8"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-text-secondary">Rate Type</label>
              <select
                value={rateType}
                onChange={(e) => setRateType(e.target.value)}
                className="mt-1 block w-full rounded-md border border-border bg-surface/50 px-3 py-2 text-sm text-text-primary shadow-sm focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
              >
                <option value="STANDARD">Standard</option>
                <option value="OVERTIME">Overtime</option>
                <option value="CPWD_SOR">CPWD SOR</option>
              </select>
            </div>
          </div>
        </DialogBody>
        <DialogFooter>
          <button
            type="button"
            onClick={onClose}
            className="rounded-md border border-border px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => assignMutation.mutate()}
            disabled={!canSubmit}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-accent-foreground hover:bg-accent-hover disabled:opacity-50"
          >
            {assignMutation.isPending ? "Saving…" : "Save"}
          </button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
