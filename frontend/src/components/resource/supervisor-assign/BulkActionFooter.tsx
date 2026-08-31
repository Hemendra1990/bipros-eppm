"use client";

import { AlertTriangle, Check } from "lucide-react";
import { ResourceAvatar } from "./ResourceAvatar";

interface Props {
  selectedSupervisor: { id: string; name: string; code: string } | null;
  selectedCount: number;
  newAssignmentCount: number;
  replaceCount: number;
  noOpCount: number;
  isSaving: boolean;
  canSave: boolean;
  onCancel?: () => void;
  onSave: () => void;
}

export function BulkActionFooter({
  selectedSupervisor,
  selectedCount,
  newAssignmentCount,
  replaceCount,
  noOpCount,
  isSaving,
  canSave,
  onCancel,
  onSave,
}: Props) {
  return (
    <div className="sticky bottom-0 z-20 -mx-5 mt-4 border-t border-border bg-surface/95 px-5 py-3 pr-[160px] backdrop-blur sm:flex sm:items-center sm:gap-4">
      <div className="flex flex-1 flex-wrap items-center gap-2 text-xs">
        {selectedSupervisor ? (
          <span className="inline-flex items-center gap-2 rounded-full border border-border bg-surface-hover px-2.5 py-1">
            <ResourceAvatar
              id={selectedSupervisor.id}
              name={selectedSupervisor.name}
              size="sm"
            />
            <span className="font-medium text-text-primary">
              {selectedSupervisor.name}
            </span>
            <span className="text-text-muted">{selectedSupervisor.code}</span>
          </span>
        ) : (
          <span className="text-text-muted">No supervisor picked yet.</span>
        )}

        {newAssignmentCount > 0 && (
          <span className="inline-flex items-center gap-1 rounded-full bg-accent-glow px-2 py-0.5 text-text-primary">
            <Check size={11} />+{newAssignmentCount} new
          </span>
        )}
        {replaceCount > 0 && (
          <span className="inline-flex items-center gap-1 rounded-full border border-warning/40 bg-warning/15 px-2 py-0.5 font-medium text-warning">
            <AlertTriangle size={11} />
            {replaceCount} {replaceCount === 1 ? "replace" : "replaces"}
          </span>
        )}
        {noOpCount > 0 && (
          <span className="inline-flex items-center rounded-full border border-border bg-surface px-2 py-0.5 text-text-muted">
            {noOpCount} unchanged
          </span>
        )}
      </div>

      <div className="mt-3 flex items-center justify-end gap-2 sm:mt-0">
        {onCancel && (
          <button
            type="button"
            onClick={onCancel}
            className="rounded-md border border-border bg-surface px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover"
          >
            Cancel
          </button>
        )}
        <button
          type="button"
          onClick={onSave}
          disabled={!canSave}
          className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-accent-foreground shadow-sm hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isSaving
            ? "Saving…"
            : selectedCount > 0
              ? `Save · ${selectedCount} ${selectedCount === 1 ? "activity" : "activities"}`
              : "Save"}
        </button>
      </div>
    </div>
  );
}
