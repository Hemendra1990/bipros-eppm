"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { documentApi } from "@/lib/api/documentApi";
import { getErrorMessage } from "@/lib/utils/error";

export interface RaiseRfiPrefill {
  subject: string;
  description: string;
  raisedBy: string;
}

interface Props {
  projectId: string;
  prefill: RaiseRfiPrefill;
  onClose: () => void;
  onCreated?: () => void;
}

const PRIORITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"] as const;

function plusDays(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().split("T")[0];
}

/**
 * Raise an RFI straight from a failed QC test (client ask, 2026-08-19) — the
 * FAIL-row counterpart of RaiseNcrDialog, posting to the document module's
 * RFI register so the responsible supervisor/engineer can re-offer the section
 * for inspection.
 */
export function RaiseRfiDialog({ projectId, prefill, onClose, onCreated }: Props) {
  const [rfiNumber, setRfiNumber] = useState("");
  const [subject, setSubject] = useState(prefill.subject);
  const [description, setDescription] = useState(prefill.description);
  const [raisedBy, setRaisedBy] = useState(prefill.raisedBy);
  const [assignedTo, setAssignedTo] = useState("");
  const [dueDate, setDueDate] = useState(plusDays(7));
  const [priority, setPriority] = useState<(typeof PRIORITIES)[number]>("MEDIUM");
  const [error, setError] = useState("");

  const mutation = useMutation({
    mutationFn: () =>
      documentApi.createRfi(projectId, {
        projectId,
        rfiNumber,
        subject,
        description,
        priority,
        raisedBy,
        assignedTo,
        raisedDate: new Date().toISOString().split("T")[0],
        dueDate,
      }),
    onSuccess: () => {
      toast.success("RFI raised");
      onCreated?.();
      onClose();
    },
    onError: (err: unknown) => toast.error(getErrorMessage(err, "Failed to raise RFI")),
  });

  const submit = () => {
    if (!rfiNumber.trim() || !subject.trim() || !raisedBy.trim() || !assignedTo.trim()) {
      setError("RFI Number, Subject, Raised By and Assigned To are required");
      return;
    }
    setError("");
    mutation.mutate();
  };

  const inputCls =
    "w-full rounded-lg border border-border bg-surface-hover px-3 py-2 text-text-primary";

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/30" onClick={onClose}>
      <div className="h-full w-full max-w-md overflow-y-auto bg-surface p-6 shadow-xl" onClick={(e) => e.stopPropagation()}>
        <h2 className="mb-4 text-lg font-bold text-text-primary">Raise RFI from failed test</h2>
        <div className="space-y-3">
          <input value={rfiNumber} onChange={(e) => setRfiNumber(e.target.value)} placeholder="RFI Number, e.g. RFI-001" className={inputCls} />
          <input value={subject} onChange={(e) => setSubject(e.target.value)} className={inputCls} />
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={4} className={inputCls} />
          <div className="flex gap-2">
            <input value={raisedBy} onChange={(e) => setRaisedBy(e.target.value)} placeholder="Raised by" className={inputCls} />
            <input value={assignedTo} onChange={(e) => setAssignedTo(e.target.value)} placeholder="Assigned to, e.g. Consultant" className={inputCls} />
          </div>
          <div className="flex gap-2">
            <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} className={inputCls} />
            <select value={priority} onChange={(e) => setPriority(e.target.value as (typeof PRIORITIES)[number])} className={inputCls}>
              {PRIORITIES.map((p) => <option key={p} value={p}>{p}</option>)}
            </select>
          </div>
          {error && <div className="text-sm text-burgundy">{error}</div>}
          <div className="flex gap-2">
            <button type="button" disabled={mutation.isPending} onClick={submit} className="flex-1 rounded-lg bg-accent px-4 py-2 font-medium text-accent-foreground">
              {mutation.isPending ? "Raising…" : "Raise RFI"}
            </button>
            <button type="button" onClick={onClose} className="flex-1 rounded-lg border border-border bg-surface-hover px-4 py-2 text-text-secondary">Cancel</button>
          </div>
        </div>
      </div>
    </div>
  );
}
