"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";

export interface PolygonListItem {
  id: string;
  name?: string; // optional free-text name
  wbsCode: string;
  wbsName: string;
  areaInSqMeters?: number;
}

export interface PolygonListPanelProps {
  polygons: PolygonListItem[];
  selectedPolygonId: string | null;
  onSelect: (id: string | null) => void; // toggle: pass null to clear
  sceneCountByPolygon: Record<string, number>; // polygonId -> scene count
  onBatchDelete: (ids: string[]) => void;
}

function formatArea(sqm?: number): string | null {
  if (sqm == null || !Number.isFinite(sqm)) return null;
  return `${Math.round(sqm).toLocaleString()} m²`;
}

/**
 * Right-column list of the project's WBS polygons. Clicking a row body selects
 * a polygon (drives scene filtering + map zoom on the page); re-clicking the
 * selected row toggles selection off. Row checkboxes track a local multi-select
 * used only for the batch-delete action — the page owns the confirm dialog, so
 * this panel just hands up the chosen ids and clears its own checkboxes.
 */
export default function PolygonListPanel({
  polygons,
  selectedPolygonId,
  onSelect,
  sceneCountByPolygon,
  onBatchDelete,
}: PolygonListPanelProps) {
  const [checkedIds, setCheckedIds] = useState<Set<string>>(new Set());

  const allChecked = polygons.length > 0 && checkedIds.size === polygons.length;
  const someChecked = checkedIds.size > 0;

  const toggleChecked = (id: string) => {
    setCheckedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleSelectAll = () => {
    setCheckedIds((prev) =>
      prev.size === polygons.length
        ? new Set()
        : new Set(polygons.map((p) => p.id))
    );
  };

  const handleRowClick = (id: string) => {
    onSelect(id === selectedPolygonId ? null : id);
  };

  const handleBatchDelete = () => {
    onBatchDelete(Array.from(checkedIds));
    setCheckedIds(new Set());
  };

  return (
    <aside className="flex flex-col gap-3 rounded-lg border border-border bg-surface/50 p-4">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-text-primary">Polygons</h3>
        {selectedPolygonId != null && (
          <button
            type="button"
            onClick={() => onSelect(null)}
            className="text-xs font-medium text-accent hover:underline"
          >
            Clear selection
          </button>
        )}
      </div>

      {polygons.length === 0 ? (
        <p className="text-xs text-text-muted">
          No polygons yet. Use Draw to add one.
        </p>
      ) : (
        <>
          <div className="flex items-center justify-between gap-2 border-b border-border pb-2">
            <label className="flex items-center gap-2 text-xs text-text-secondary cursor-pointer">
              <input
                type="checkbox"
                checked={allChecked}
                onChange={toggleSelectAll}
              />
              <span>Select all</span>
            </label>
            {someChecked && (
              <Button
                type="button"
                variant="danger"
                size="sm"
                onClick={handleBatchDelete}
              >
                Delete selected ({checkedIds.size})
              </Button>
            )}
          </div>

          <ul className="flex flex-col gap-1.5">
            {polygons.map((p) => {
              const label = p.name || p.wbsCode;
              const showWbsName = Boolean(p.wbsName) && p.wbsName !== label;
              const area = formatArea(p.areaInSqMeters);
              const sceneCount = sceneCountByPolygon[p.id] ?? 0;
              const isSelected = p.id === selectedPolygonId;
              const isChecked = checkedIds.has(p.id);

              return (
                <li
                  key={p.id}
                  className={`flex items-start gap-2 rounded-md border p-2 transition-colors ${
                    isSelected
                      ? "border-accent bg-accent/10 ring-1 ring-accent"
                      : "border-border bg-surface hover:bg-surface-hover"
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={isChecked}
                    onChange={() => toggleChecked(p.id)}
                    onClick={(e) => e.stopPropagation()}
                    className="mt-1 shrink-0"
                    aria-label={`Select ${label}`}
                  />
                  <button
                    type="button"
                    onClick={() => handleRowClick(p.id)}
                    className="flex flex-1 flex-col items-start gap-0.5 text-left min-w-0"
                  >
                    <div className="flex w-full items-center justify-between gap-2">
                      <span className="truncate text-sm font-medium text-text-primary">
                        {label}
                      </span>
                      <span className="shrink-0 rounded bg-accent/10 px-2 py-0.5 text-xs font-medium text-accent">
                        {sceneCount} {sceneCount === 1 ? "scene" : "scenes"}
                      </span>
                    </div>
                    {(showWbsName || area) && (
                      <span className="truncate text-xs text-text-muted max-w-full">
                        {showWbsName && <span>{p.wbsName}</span>}
                        {showWbsName && area && <span> · </span>}
                        {area && <span>{area}</span>}
                      </span>
                    )}
                  </button>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </aside>
  );
}
