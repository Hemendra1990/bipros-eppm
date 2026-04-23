"use client";

import { useMemo } from "react";
import { SatelliteImage } from "@/lib/api/gisApi";
import { Button } from "@/components/ui/button";

interface ScenePickerProps {
  scenes: SatelliteImage[];
  selectedSceneId: string | null;
  onChange: (id: string | null) => void;
}

/**
 * Dropdown + prev/next for stepping through the scene list. Scenes are sorted
 * newest-first; prev walks back in time, next walks forward. Disabled when
 * there are no scenes to pick from.
 */
export function ScenePicker({
  scenes,
  selectedSceneId,
  onChange,
}: ScenePickerProps) {
  const sorted = useMemo(
    () =>
      [...scenes].sort(
        (a, b) =>
          new Date(b.captureDate).getTime() -
          new Date(a.captureDate).getTime()
      ),
    [scenes]
  );

  const idx = selectedSceneId
    ? sorted.findIndex((s) => s.id === selectedSceneId)
    : -1;

  if (sorted.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-border bg-surface/50 px-4 py-3 text-sm text-text-muted">
        No scenes match this project area
      </div>
    );
  }

  return (
    <div className="flex flex-wrap items-center gap-2 rounded-lg border border-border bg-surface/50 px-3 py-2">
      <label className="text-xs text-text-secondary">Scene</label>
      <select
        value={selectedSceneId ?? ""}
        onChange={(e) => onChange(e.target.value || null)}
        className="flex-1 min-w-[16rem] rounded-md border border-border bg-surface px-2 py-1.5 text-sm text-text-primary"
      >
        {sorted.map((s) => {
          const date = new Date(s.captureDate).toISOString().slice(0, 10);
          const source = s.source.replace(/_/g, " ");
          return (
            <option key={s.id as string} value={s.id as string}>
              {date} · {source} · {s.status}
            </option>
          );
        })}
      </select>
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={idx >= sorted.length - 1}
        onClick={() => {
          const next = sorted[idx + 1];
          if (next) onChange(next.id as string);
        }}
      >
        ← Older
      </Button>
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={idx <= 0}
        onClick={() => {
          const next = sorted[idx - 1];
          if (next) onChange(next.id as string);
        }}
      >
        Newer →
      </Button>
      <span className="text-xs text-text-muted">
        {idx >= 0 ? `${idx + 1} / ${sorted.length}` : `— / ${sorted.length}`}
      </span>
    </div>
  );
}
