"use client";

import { useState } from "react";
import { Pencil, Trash2, Check, Layers } from "lucide-react";
import type { ThemeDefinition } from "@/lib/themes/definitions";
import { accentForeground } from "@/lib/themes/themeService";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";

interface SavedThemesListProps {
  themes: ThemeDefinition[];
  activeThemeId: string;
  onSelect: (id: string) => void;
  onEdit: (theme: ThemeDefinition) => void;
  onDelete: (id: string) => void;
  isAdmin: boolean;
}

export function SavedThemesList({
  themes,
  activeThemeId,
  onSelect,
  onEdit,
  onDelete,
  isAdmin,
}: SavedThemesListProps) {
  const [deleteId, setDeleteId] = useState<string | null>(null);

  if (themes.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-border py-8 text-center">
        <p className="text-text-muted text-sm">No custom themes yet.</p>
      </div>
    );
  }

  const capWarning = themes.length >= 16;
  const atCap = themes.length >= 20;

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Layers size={14} className="text-text-secondary" />
          <h3 className="text-sm font-semibold text-text-primary">My Themes</h3>
        </div>
        <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-surface-hover text-text-muted">
          {themes.length}/20
        </span>
      </div>

      {capWarning && (
        <div className={`text-xs px-3 py-2 rounded-lg border ${atCap ? "border-warning/30 bg-warning/5 text-warning" : "border-border bg-surface-hover text-text-secondary"}`}>
          {atCap
            ? "Maximum of 20 custom themes reached. Delete one to create more."
            : "Approaching limit (20 custom themes max)."}
        </div>
      )}

      <div className="space-y-2">
        {themes.map((theme) => {
          const isActive = theme.id === activeThemeId;
          const l = theme.light;
          const d = theme.dark;

          return (
            <div
              key={theme.id}
              className={`group flex items-center gap-3 rounded-xl border bg-surface/50 p-2.5 transition-all duration-200 ${
                isActive
                  ? "ring-1 ring-accent shadow-sm"
                  : "hover:border-accent/30 hover:bg-surface-hover/50"
              }`}
            >
              {/* ── Mini UI thumbnail ── */}
              <div
                className="relative w-[72px] h-[52px] rounded-lg border shrink-0 overflow-hidden flex"
                style={{ backgroundColor: l.background, borderColor: l.border }}
              >
                {/* Sidebar strip */}
                <div
                  className="w-2.5 h-full"
                  style={{ backgroundColor: l.backgroundSubtle }}
                />
                <div className="flex-1 p-1 flex flex-col gap-1">
                  {/* Button row */}
                  <div className="flex gap-0.5">
                    <div
                      className="h-1.5 rounded-sm flex-1"
                      style={{ backgroundColor: l.accent }}
                    />
                    <div
                      className="h-1.5 rounded-sm flex-1 border"
                      style={{ borderColor: l.border, backgroundColor: "transparent" }}
                    />
                  </div>
                  {/* Mini card */}
                  <div
                    className="flex-1 rounded-sm border p-0.5 flex flex-col gap-0.5"
                    style={{ backgroundColor: l.backgroundSubtle, borderColor: l.borderSubtle }}
                  >
                    <div className="flex justify-between">
                      <div className="w-4 h-0.5 rounded-sm" style={{ backgroundColor: l.foreground }} />
                      <div className="w-2 h-0.5 rounded-sm" style={{ backgroundColor: l.accentTint }} />
                    </div>
                    <div className="w-6 h-0.5 rounded-sm" style={{ backgroundColor: l.foregroundSecondary }} />
                  </div>
                </div>

                {/* Dark mode swatch */}
                <div className="absolute bottom-0.5 right-0.5 flex gap-0.5">
                  <div
                    className="w-1.5 h-1.5 rounded-full border"
                    style={{ backgroundColor: d.background, borderColor: l.border }}
                  />
                  <div
                    className="w-1.5 h-1.5 rounded-full border"
                    style={{ backgroundColor: d.accent, borderColor: l.border }}
                  />
                </div>
              </div>

              {/* ── Info ── */}
              <div className="flex-1 min-w-0 flex flex-col justify-center">
                <div className="flex items-center gap-2">
                  <p className="text-sm font-semibold text-text-primary truncate">
                    {theme.name}
                  </p>
                  {isActive && (
                    <span className="shrink-0 inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[10px] font-medium bg-accent/10 text-accent">
                      <Check size={9} strokeWidth={3} />
                      Active
                    </span>
                  )}
                </div>
                <p className="text-[10px] text-text-muted mt-0.5">
                  {theme.borderRadius}px radius
                  {theme.fontFamily ? ` · ${theme.fontFamily}` : ""}
                </p>
              </div>

              {/* ── Actions ── */}
              {isAdmin && (
                <div className="flex items-center gap-1 shrink-0">
                  {!isActive && (
                    <button
                      onClick={() => onSelect(theme.id)}
                      className="px-2.5 py-1.5 rounded-md text-xs font-medium transition-colors"
                      style={{
                        backgroundColor: l.accent,
                        color: accentForeground(l.accent),
                      }}
                    >
                      Apply
                    </button>
                  )}
                  <button
                    onClick={() => onEdit(theme)}
                    className="p-1.5 rounded-md text-text-secondary hover:text-text-primary hover:bg-surface-hover transition-colors"
                    title="Edit"
                  >
                    <Pencil size={13} />
                  </button>
                  <button
                    onClick={() => setDeleteId(theme.id)}
                    className="p-1.5 rounded-md text-text-muted hover:text-danger hover:bg-danger/5 transition-colors"
                    title="Delete"
                  >
                    <Trash2 size={13} />
                  </button>
                </div>
              )}
            </div>
          );
        })}
      </div>

      <ConfirmDialog
        open={deleteId !== null}
        title="Delete Custom Theme"
        message="Are you sure you want to delete this theme? This cannot be undone."
        confirmLabel="Delete"
        variant="danger"
        onConfirm={() => {
          if (deleteId) onDelete(deleteId);
          setDeleteId(null);
        }}
        onCancel={() => setDeleteId(null)}
      />
    </div>
  );
}
