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
import { activityApi, type ActivityResponse } from "@/lib/api/activityApi";
import { projectResourceApi } from "@/lib/api/projectResourceApi";
import { getErrorMessage } from "@/lib/utils/error";

const CLEAR_VALUE = "__clear__";

interface Props {
  open: boolean;
  onClose: () => void;
  projectId: string;
  activity: ActivityResponse | null;
}

export function SetSupervisorDialog({ open, onClose, projectId, activity }: Props) {
  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="max-w-md">
        {activity && (
          <DialogInner
            // Re-mount whenever we point at a different activity so local state initialises
            // fresh from the activity's current supervisor — no setState-in-effect needed.
            key={activity.id}
            projectId={projectId}
            activity={activity}
            onClose={onClose}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function DialogInner({
  projectId,
  activity,
  onClose,
}: {
  projectId: string;
  activity: ActivityResponse;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [pickedId, setPickedId] = useState<string>(activity.responsibleResourceId ?? "");

  const { data: poolData, isLoading: isLoadingPool } = useQuery({
    queryKey: ["resource-pool", projectId],
    queryFn: () => projectResourceApi.listPool(projectId),
    enabled: !!projectId,
  });

  // LABOR / Manpower-only — supervisors are always a person. Same filter the full edit form
  // uses on the activity detail page.
  const supervisorOptions = useMemo(() => {
    const pool = poolData?.data ?? [];
    return pool
      .filter((p) => {
        const t = (p.resourceTypeName ?? "").toLowerCase();
        return t.includes("labor") || t.includes("labour") || t.includes("manpower");
      })
      .map((p) => ({
        value: p.resourceId,
        label: `${p.resourceCode ? p.resourceCode + " — " : ""}${p.resourceName ?? p.resourceId}`,
      }));
  }, [poolData]);

  const optionsWithClear = useMemo(
    () => [{ value: CLEAR_VALUE, label: "— Clear supervisor —" }, ...supervisorOptions],
    [supervisorOptions]
  );

  const mutation = useMutation({
    mutationFn: () => {
      const isClear = !pickedId || pickedId === CLEAR_VALUE;
      const matched = supervisorOptions.find((o) => o.value === pickedId);
      return activityApi.updateActivity(projectId, activity.id, {
        supervisorResourceId: isClear ? null : pickedId,
        supervisorResourceName: isClear ? null : (matched?.label ?? null),
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["activity", projectId, activity.id] });
      queryClient.invalidateQueries({ queryKey: ["activities", projectId] });
      queryClient.invalidateQueries({ queryKey: ["wbs", projectId] });
      toast.success("Supervisor updated");
      onClose();
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to update supervisor"));
    },
  });

  const isClearing = pickedId === CLEAR_VALUE;
  const currentId = activity.responsibleResourceId ?? "";
  const isUnchanged = !isClearing && pickedId === currentId;
  const cantClear = isClearing && !currentId;

  return (
    <>
      <DialogHeader>
        <DialogTitle>Set supervisor</DialogTitle>
        <p className="mt-1 text-xs text-slate">
          {activity.code} — {activity.name}
        </p>
      </DialogHeader>
      <DialogBody>
        <label className="block text-sm font-medium text-text-secondary">
          Supervisor (Manpower / Labor)
        </label>
        {isLoadingPool ? (
          <p className="mt-1 text-xs text-text-muted">Loading project pool…</p>
        ) : supervisorOptions.length === 0 ? (
          <p className="mt-1 text-xs text-amber-600">
            No labor / manpower resources in this project&apos;s pool.{" "}
            <a href={`/projects/${projectId}/resources`} className="text-accent underline">
              Add one →
            </a>
          </p>
        ) : (
          <div className="mt-1">
            <SearchableSelect
              value={pickedId}
              onChange={setPickedId}
              placeholder="Search supervisor candidates…"
              options={optionsWithClear}
            />
          </div>
        )}
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
          onClick={() => mutation.mutate()}
          disabled={mutation.isPending || isUnchanged || cantClear}
          className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-accent-foreground hover:bg-accent-hover disabled:opacity-50"
        >
          {mutation.isPending ? "Saving…" : isClearing ? "Clear" : "Save"}
        </button>
      </DialogFooter>
    </>
  );
}
