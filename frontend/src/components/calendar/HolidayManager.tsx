"use client";

import { useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import type {
  CalendarExceptionResponse,
  CalendarExceptionRequest,
} from "@/lib/api/calendarApi";

interface HolidayManagerProps {
  exceptions: CalendarExceptionResponse[];
  onAdd: (data: CalendarExceptionRequest) => Promise<void>;
  onRemove: (exceptionId: string) => Promise<void>;
}

const PRESET_HOLIDAYS: { name: string; month: number; day: number }[] = [
  { name: "New Year's Day", month: 1, day: 1 },
  { name: "Independence Day (US)", month: 7, day: 4 },
  { name: "Christmas Day", month: 12, day: 25 },
  { name: "Labor Day (Intl)", month: 5, day: 1 },
];

export function HolidayManager({
  exceptions,
  onAdd,
  onRemove,
}: HolidayManagerProps) {
  const [showAddForm, setShowAddForm] = useState(false);
  const [date, setDate] = useState("");
  const [name, setName] = useState("");
  const [saving, setSaving] = useState(false);

  const handleAdd = async () => {
    if (!date) return;
    setSaving(true);
    try {
      await onAdd({
        exceptionDate: date,
        dayType: "NON_WORKING",
        name: name || undefined,
      });
      setDate("");
      setName("");
      setShowAddForm(false);
    } finally {
      setSaving(false);
    }
  };

  const handleAddPreset = async (preset: (typeof PRESET_HOLIDAYS)[number]) => {
    const year = new Date().getFullYear();
    const dateStr = `${year}-${String(preset.month).padStart(2, "0")}-${String(preset.day).padStart(2, "0")}`;
    const alreadyExists = exceptions.some(
      (e) => e.exceptionDate === dateStr
    );
    if (alreadyExists) return;

    setSaving(true);
    try {
      await onAdd({
        exceptionDate: dateStr,
        dayType: "NON_WORKING",
        name: preset.name,
      });
    } finally {
      setSaving(false);
    }
  };

  const inputClass =
    "rounded-md border border-border bg-surface-hover/50 px-3 py-2 text-sm text-text-primary placeholder-gray-500 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent";

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-text-primary">
          Holidays & Exceptions
        </h3>
        <button
          type="button"
          onClick={() => setShowAddForm(!showAddForm)}
          className="inline-flex items-center gap-1 rounded-md bg-accent px-3 py-1.5 text-xs font-medium text-text-primary hover:bg-accent-hover"
        >
          <Plus size={14} />
          Add Holiday
        </button>
      </div>

      {/* Quick-add presets */}
      <div className="flex flex-wrap gap-2">
        <span className="text-xs text-text-muted">Quick add:</span>
        {PRESET_HOLIDAYS.map((preset) => (
          <button
            key={preset.name}
            type="button"
            onClick={() => handleAddPreset(preset)}
            disabled={saving}
            className="rounded bg-surface-hover px-2 py-1 text-xs text-text-secondary hover:bg-surface-active hover:text-text-primary disabled:opacity-50"
          >
            {preset.name}
          </button>
        ))}
      </div>

      {/* Add form */}
      {showAddForm && (
        <div className="flex items-end gap-3 rounded-lg border border-border bg-surface/60 p-4">
          <div>
            <label className="block text-xs font-medium text-text-secondary">
              Date *
            </label>
            <input
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              className={inputClass}
            />
          </div>
          <div className="flex-1">
            <label className="block text-xs font-medium text-text-secondary">
              Name
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g., Company Holiday"
              className={`${inputClass} w-full`}
            />
          </div>
          <button
            type="button"
            onClick={handleAdd}
            disabled={saving || !date}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:bg-border"
          >
            {saving ? "Adding..." : "Add"}
          </button>
          <button
            type="button"
            onClick={() => setShowAddForm(false)}
            className="rounded-md bg-surface-active/50 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-active"
          >
            Cancel
          </button>
        </div>
      )}

      {/* Exceptions list */}
      {exceptions.length === 0 && (
        <p className="py-4 text-center text-sm text-text-muted">
          No holidays or exceptions defined.
        </p>
      )}

      {exceptions.length > 0 && (
        <div className="space-y-1">
          {exceptions
            .sort(
              (a, b) =>
                new Date(a.exceptionDate).getTime() -
                new Date(b.exceptionDate).getTime()
            )
            .map((exc) => (
              <div
                key={exc.id}
                className="flex items-center justify-between rounded-lg border border-border/50 bg-surface/30 px-4 py-3"
              >
                <div className="flex items-center gap-3">
                  <span className="text-sm font-medium text-text-secondary">
                    {new Date(exc.exceptionDate).toLocaleDateString(undefined, {
                      weekday: "short",
                      year: "numeric",
                      month: "short",
                      day: "numeric",
                    })}
                  </span>
                  {exc.name && (
                    <span className="text-sm text-text-secondary">
                      {exc.name}
                    </span>
                  )}
                  <span
                    className={`rounded px-2 py-0.5 text-xs ${
                      exc.dayType === "NON_WORKING"
                        ? "bg-danger/10 text-danger"
                        : "bg-success/10 text-success"
                    }`}
                  >
                    {exc.dayType.replace("_", " ")}
                  </span>
                </div>
                <button
                  type="button"
                  onClick={() => onRemove(exc.id)}
                  className="rounded p-1.5 text-text-muted hover:bg-danger/10 hover:text-danger"
                >
                  <Trash2 size={14} />
                </button>
              </div>
            ))}
        </div>
      )}
    </div>
  );
}
