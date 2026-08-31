"use client";

import { ResourceAvatar } from "./ResourceAvatar";

export interface SupervisorOption {
  value: string;
  code: string;
  name: string;
  role: string | null;
}

interface Props {
  option: SupervisorOption;
  selected: boolean;
  workload: number;
  onSelect: () => void;
}

function workloadTone(n: number) {
  if (n === 0) return { dot: "bg-text-muted", text: "text-text-muted" };
  if (n <= 10) return { dot: "bg-warning", text: "text-text-secondary" };
  return { dot: "bg-accent", text: "text-text-primary" };
}

export function SupervisorRow({ option, selected, workload, onSelect }: Props) {
  const tone = workloadTone(workload);
  return (
    <li>
      <label
        className={`flex cursor-pointer items-center gap-3 px-3 py-2.5 transition-colors ${
          selected
            ? "bg-accent-glow ring-1 ring-inset ring-accent/40"
            : "hover:bg-surface-hover/60"
        }`}
      >
        <input
          type="radio"
          name="supervisor"
          checked={selected}
          onChange={onSelect}
          className="sr-only"
          aria-label={`Select ${option.name}`}
        />
        <ResourceAvatar id={option.value} name={option.name} />
        <div className="min-w-0 flex-1">
          <div
            className={`truncate text-sm font-medium ${
              selected ? "text-text-primary" : "text-text-primary"
            }`}
          >
            {option.name}
          </div>
          <div className="truncate text-xs text-text-muted">
            {option.code}
            {option.role && <span> · {option.role}</span>}
          </div>
        </div>
        <span
          className={`inline-flex shrink-0 items-center gap-1 rounded-full border border-border/60 bg-surface px-2 py-0.5 text-[11px] ${tone.text}`}
          title={`Currently supervises ${workload} ${
            workload === 1 ? "activity" : "activities"
          } in this project`}
        >
          <span className={`h-1.5 w-1.5 rounded-full ${tone.dot}`} />
          {workload}
        </span>
      </label>
    </li>
  );
}
