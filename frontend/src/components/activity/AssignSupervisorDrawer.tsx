"use client";

import { useEffect } from "react";
import { X } from "lucide-react";
import { SupervisorAssignmentTab } from "@/components/resource/SupervisorAssignmentTab";

interface Props {
  open: boolean;
  onClose: () => void;
  projectId: string;
}

/**
 * Right-side drawer that surfaces the existing SupervisorAssignmentTab on the Activities
 * page so planners can bulk-assign a supervisor to many activities without leaving the list.
 * The inner tab does its own data fetching, validation, conflict warning, and bulk save.
 */
export function AssignSupervisorDrawer({ open, onClose, projectId }: Props) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <>
      {/* Dim backdrop — click to close. The drawer itself stops propagation. */}
      <div
        className="fixed inset-0 z-30 bg-charcoal/30"
        onClick={onClose}
        aria-hidden
      />
      <aside
        className="fixed right-0 top-0 z-40 flex h-screen w-full flex-col border-l border-border bg-surface shadow-xl md:w-[920px] lg:w-[1100px] xl:w-[1240px]"
        role="dialog"
        aria-modal="true"
        aria-label="Bulk assign supervisor"
      >
        <header className="flex items-center justify-between gap-3 border-b border-border px-5 py-4">
          <div>
            <h2 className="text-lg font-semibold text-text-primary">Assign Supervisor</h2>
            <p className="text-xs text-text-secondary">
              Pick one Labor / Manpower resource and apply to many activities at once.
            </p>
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
          <SupervisorAssignmentTab projectId={projectId} />
        </div>
      </aside>
    </>
  );
}
