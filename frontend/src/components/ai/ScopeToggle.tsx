"use client";

import { Pin, Globe } from "lucide-react";

export type ScopeMode = "auto" | "general";

interface ScopeToggleProps {
  mode: ScopeMode;
  onChange: (mode: ScopeMode) => void;
}

/**
 * Two-button segmented control rendered next to the module pill in the AI chat
 * header. Lets the user flip the chat between "pin to current project" (auto)
 * and "portfolio mode — all accessible projects" (general).
 *
 * Only shown when a project id can be inferred from the URL — off a project
 * page the chat is forced into general mode and the toggle is hidden.
 */
export function ScopeToggle({ mode, onChange }: ScopeToggleProps) {
  const baseBtn =
    "p-1 rounded-md transition-colors flex items-center justify-center";
  const activeBtn = "bg-accent text-accent-foreground";
  const idleBtn = "text-text-secondary hover:text-text-primary hover:bg-surface-hover";

  return (
    <div
      role="group"
      aria-label="Chat scope"
      className="flex items-center gap-0.5 rounded-md border border-border bg-surface p-0.5"
    >
      <button
        type="button"
        onClick={() => onChange("auto")}
        aria-pressed={mode === "auto"}
        title="Pin to current project"
        className={`${baseBtn} ${mode === "auto" ? activeBtn : idleBtn}`}
      >
        <Pin size={12} />
      </button>
      <button
        type="button"
        onClick={() => onChange("general")}
        aria-pressed={mode === "general"}
        title="Portfolio mode (all projects)"
        className={`${baseBtn} ${mode === "general" ? activeBtn : idleBtn}`}
      >
        <Globe size={12} />
      </button>
    </div>
  );
}
