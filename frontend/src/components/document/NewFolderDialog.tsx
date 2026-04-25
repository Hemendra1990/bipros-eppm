"use client";

import { useEffect, useRef, useState } from "react";
import { FolderPlus } from "lucide-react";
import type { DocumentFolder } from "@/lib/api/documentApi";

const CATEGORY_OPTIONS = [
  "DPR",
  "DRAWING",
  "SPECIFICATION",
  "CONTRACT",
  "APPROVAL",
  "CORRESPONDENCE",
  "AS_BUILT",
  "GENERAL",
] as const;

export interface NewFolderFormValues {
  name: string;
  code: string;
  category: string;
}

interface NewFolderDialogProps {
  open: boolean;
  parent: DocumentFolder | null;
  submitting: boolean;
  errorMessage: string | null;
  onSubmit: (values: NewFolderFormValues) => void;
  onCancel: () => void;
}

export function NewFolderDialog({
  open,
  parent,
  submitting,
  errorMessage,
  onSubmit,
  onCancel,
}: NewFolderDialogProps) {
  const nameRef = useRef<HTMLInputElement>(null);
  const [name, setName] = useState("");
  const [code, setCode] = useState("");
  const [category, setCategory] = useState<string>("GENERAL");

  // Reset fields whenever the dialog (re-)opens, and pre-fill category from parent.
  useEffect(() => {
    if (open) {
      setName("");
      setCode("");
      setCategory(parent?.category ?? "GENERAL");
      // Defer focus to next tick so the input exists in the DOM.
      requestAnimationFrame(() => nameRef.current?.focus());
    }
  }, [open, parent]);

  useEffect(() => {
    if (!open) return;
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onCancel();
    };
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [open, onCancel]);

  if (!open) return null;

  const title = parent ? `New sub-folder under ${parent.name}` : "New folder";

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (submitting) return;
    onSubmit({ name: name.trim(), code: code.trim(), category });
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
      role="dialog"
      aria-modal="true"
      aria-labelledby="new-folder-title"
    >
      <div className="w-full max-w-md rounded-xl border border-border bg-surface p-6 shadow-xl">
        <div className="flex items-start gap-3">
          <div className="mt-0.5 text-accent">
            <FolderPlus size={20} />
          </div>
          <div className="flex-1">
            <h3 id="new-folder-title" className="text-base font-semibold text-text-primary">
              {title}
            </h3>
            <p className="mt-1 text-xs text-text-muted">
              Folders organise documents in this project. Both name and code are required.
            </p>
          </div>
        </div>

        {errorMessage && (
          <div className="mt-4 rounded-md bg-danger/10 p-3 text-sm text-danger">
            {errorMessage}
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-4 space-y-3">
          <div>
            <label htmlFor="new-folder-name" className="mb-1 block text-sm font-medium text-text-secondary">
              Name
            </label>
            <input
              id="new-folder-name"
              ref={nameRef}
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              maxLength={255}
              required
              className="w-full rounded-lg border border-border bg-surface-hover px-3 py-2 text-text-primary placeholder-text-muted focus:border-accent focus:ring-2 focus:ring-accent focus:ring-opacity-50"
              placeholder="e.g., Site Photos"
            />
          </div>

          <div>
            <label htmlFor="new-folder-code" className="mb-1 block text-sm font-medium text-text-secondary">
              Code
            </label>
            <input
              id="new-folder-code"
              type="text"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              maxLength={100}
              required
              className="w-full rounded-lg border border-border bg-surface-hover px-3 py-2 text-text-primary placeholder-text-muted focus:border-accent focus:ring-2 focus:ring-accent focus:ring-opacity-50"
              placeholder="e.g., SP"
            />
          </div>

          <div>
            <label htmlFor="new-folder-category" className="mb-1 block text-sm font-medium text-text-secondary">
              Category
            </label>
            <select
              id="new-folder-category"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              className="w-full rounded-lg border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:ring-2 focus:ring-accent focus:ring-opacity-50"
            >
              {CATEGORY_OPTIONS.map((opt) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
          </div>

          <div className="mt-5 flex justify-end gap-2">
            <button
              type="button"
              onClick={onCancel}
              className="rounded-md border border-border bg-surface/50 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover focus:outline-none focus:ring-1 focus:ring-border"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting || !name.trim() || !code.trim()}
              className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:bg-border focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2 focus:ring-offset-background"
            >
              {submitting ? "Creating..." : "Create folder"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
