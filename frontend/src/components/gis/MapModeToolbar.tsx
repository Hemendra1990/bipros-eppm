"use client";

export type MapMode = "view" | "draw" | "modify" | "delete";

interface MapModeToolbarProps {
  mode: MapMode;
  onModeChange: (m: MapMode) => void;
  canEdit: boolean;
  editDisabledReason?: string;
}

const MODES: { id: MapMode; label: string; editOnly: boolean }[] = [
  { id: "view", label: "View", editOnly: false },
  { id: "draw", label: "Draw", editOnly: true },
  { id: "modify", label: "Modify", editOnly: true },
  { id: "delete", label: "Delete", editOnly: true },
];

/**
 * Segmented control above the map. When the user lacks a WBS_POLYGON layer
 * or edit permission, the edit modes are disabled with a tooltip explaining
 * why, so the UI never ends up in a half-enabled state.
 */
export function MapModeToolbar({
  mode,
  onModeChange,
  canEdit,
  editDisabledReason,
}: MapModeToolbarProps) {
  return (
    <div
      role="tablist"
      className="inline-flex rounded-lg border border-border bg-surface/50 p-0.5"
    >
      {MODES.map((m) => {
        const disabled = m.editOnly && !canEdit;
        const selected = mode === m.id;
        return (
          <button
            key={m.id}
            type="button"
            role="tab"
            aria-selected={selected}
            disabled={disabled}
            title={
              disabled
                ? editDisabledReason ?? "Editing not available"
                : undefined
            }
            onClick={() => onModeChange(m.id)}
            className={`px-3 py-1.5 text-sm rounded-md transition-colors ${
              selected
                ? "bg-accent/20 text-accent"
                : disabled
                  ? "text-text-muted cursor-not-allowed"
                  : "text-text-secondary hover:text-text-primary"
            }`}
          >
            {m.label}
          </button>
        );
      })}
    </div>
  );
}
