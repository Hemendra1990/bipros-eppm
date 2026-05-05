"use client";

import { AlertTriangle, CheckCircle2, Clock, Info, Play, X } from "lucide-react";
import type { ScheduleResultResponse } from "@/lib/api/scheduleApi";

/**
 * Scheduling-log panel rendered after a Run Schedule call completes (Phase 1.5 of the
 * baseline-progress roadmap). The backend already produces:
 *   - dataDate, totalActivities, criticalActivities, criticalPathLength
 *   - status breakdown (Phase 1.6 — notStarted/inProgress/completed)
 *   - warnings: dangling activities, negative float, "scheduled from data date" notes
 *
 * We bucket the warnings into the same categories Primavera uses so the planner
 * can decide what to fix first.
 */
type WarningBucket = "DANGLING" | "NEGATIVE_FLOAT" | "PROGRESS_OVERRIDE" | "OTHER";

function classifyWarning(message: string): WarningBucket {
  const lower = message.toLowerCase();
  if (lower.includes("no predecessors") || lower.includes("no successors") || lower.includes("dangling")) {
    return "DANGLING";
  }
  if (lower.includes("negative float")) return "NEGATIVE_FLOAT";
  if (lower.includes("progress override") || lower.includes("data date")) return "PROGRESS_OVERRIDE";
  return "OTHER";
}

const BUCKET_LABELS: Record<WarningBucket, string> = {
  DANGLING: "Dangling activities",
  NEGATIVE_FLOAT: "Negative float",
  PROGRESS_OVERRIDE: "Progress override notes",
  OTHER: "Other",
};

export interface ScheduleLogPanelProps {
  result: ScheduleResultResponse;
  onDismiss?: () => void;
  onRerun?: () => void;
  isRerunning?: boolean;
}

export function ScheduleLogPanel({ result, onDismiss, onRerun, isRerunning }: ScheduleLogPanelProps) {
  const grouped = new Map<WarningBucket, string[]>();
  for (const w of result.warnings ?? []) {
    const bucket = classifyWarning(w);
    if (!grouped.has(bucket)) grouped.set(bucket, []);
    grouped.get(bucket)!.push(w);
  }

  const hasStatusBreakdown =
    result.notStartedActivities != null ||
    result.inProgressActivities != null ||
    result.completedActivities != null;

  return (
    <div className="rounded-lg border border-border bg-surface/60 p-4 shadow-sm">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div className="flex items-center gap-2">
          <CheckCircle2 size={18} className="text-success" />
          <h3 className="text-sm font-semibold text-text-primary">Scheduling log</h3>
        </div>
        <div className="flex items-center gap-2">
          {onRerun && (
            <button
              type="button"
              onClick={onRerun}
              disabled={isRerunning}
              className="inline-flex items-center gap-1.5 rounded-md border border-border px-2 py-1 text-xs font-medium text-text-secondary hover:bg-surface-hover/50 disabled:opacity-60"
            >
              <Play size={12} />
              {isRerunning ? "Running…" : "Re-run"}
            </button>
          )}
          {onDismiss && (
            <button
              type="button"
              onClick={onDismiss}
              className="rounded-md p-1 text-text-secondary hover:bg-surface-hover/50 hover:text-text-primary"
              title="Hide"
            >
              <X size={14} />
            </button>
          )}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Stat label="Data date" value={result.dataDate ?? "—"} />
        <Stat label="Activities" value={String(result.totalActivities)} />
        <Stat label="Critical" value={String(result.criticalActivities)} />
        <Stat
          label="Critical path"
          value={
            result.criticalPathLength != null
              ? `${result.criticalPathLength.toFixed(1)} d`
              : "—"
          }
        />
      </div>

      {hasStatusBreakdown && (
        <div className="mt-3 grid grid-cols-3 gap-3">
          <Stat
            label="Not started"
            value={result.notStartedActivities != null ? String(result.notStartedActivities) : "—"}
            tone="muted"
          />
          <Stat
            label="In progress"
            value={result.inProgressActivities != null ? String(result.inProgressActivities) : "—"}
            tone="warning"
          />
          <Stat
            label="Completed"
            value={result.completedActivities != null ? String(result.completedActivities) : "—"}
            tone="success"
          />
        </div>
      )}

      {grouped.size === 0 ? (
        <div className="mt-4 inline-flex items-center gap-1.5 text-xs text-success">
          <CheckCircle2 size={12} />
          No warnings raised by the scheduler.
        </div>
      ) : (
        <div className="mt-4 space-y-3">
          {Array.from(grouped.entries()).map(([bucket, messages]) => (
            <div key={bucket}>
              <div className="mb-1 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-text-muted">
                {bucket === "NEGATIVE_FLOAT" || bucket === "DANGLING" ? (
                  <AlertTriangle size={12} className="text-warning" />
                ) : bucket === "PROGRESS_OVERRIDE" ? (
                  <Info size={12} className="text-accent" />
                ) : (
                  <Clock size={12} className="text-text-muted" />
                )}
                {BUCKET_LABELS[bucket]} ({messages.length})
              </div>
              <ul className="space-y-1 text-xs text-text-secondary">
                {messages.map((m, i) => (
                  <li key={i} className="rounded-md bg-surface-active/40 px-2 py-1">
                    {m}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

type Tone = "default" | "muted" | "warning" | "success";

function Stat({ label, value, tone = "default" }: { label: string; value: string; tone?: Tone }) {
  const valueCls =
    tone === "warning"
      ? "text-warning"
      : tone === "success"
        ? "text-success"
        : tone === "muted"
          ? "text-text-secondary"
          : "text-text-primary";
  return (
    <div className="rounded-md border border-border bg-surface/40 px-3 py-2">
      <div className="text-[10px] font-semibold uppercase tracking-wider text-text-muted">{label}</div>
      <div className={`mt-0.5 text-sm font-semibold ${valueCls}`}>{value}</div>
    </div>
  );
}
