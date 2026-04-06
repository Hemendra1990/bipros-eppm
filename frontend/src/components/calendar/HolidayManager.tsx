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
    "rounded-md border border-slate-700 bg-slate-800/50 px-3 py-2 text-sm text-white placeholder-gray-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500";

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-slate-200">
          Holidays & Exceptions
        </h3>
        <button
          type="button"
          onClick={() => setShowAddForm(!showAddForm)}
          className="inline-flex items-center gap-1 rounded-md bg-blue-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-500"
        >
          <Plus size={14} />
          Add Holiday
        </button>
      </div>

      {/* Quick-add presets */}
      <div className="flex flex-wrap gap-2">
        <span className="text-xs text-slate-500">Quick add:</span>
        {PRESET_HOLIDAYS.map((preset) => (
          <button
            key={preset.name}
            type="button"
            onClick={() => handleAddPreset(preset)}
            disabled={saving}
            className="rounded bg-slate-800 px-2 py-1 text-xs text-slate-400 hover:bg-slate-700 hover:text-white disabled:opacity-50"
          >
            {preset.name}
          </button>
        ))}
      </div>

      {/* Add form */}
      {showAddForm && (
        <div className="flex items-end gap-3 rounded-lg border border-slate-700 bg-slate-900/60 p-4">
          <div>
            <label className="block text-xs font-medium text-slate-400">
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
            <label className="block text-xs font-medium text-slate-400">
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
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:bg-slate-600"
          >
            {saving ? "Adding..." : "Add"}
          </button>
          <button
            type="button"
            onClick={() => setShowAddForm(false)}
            className="rounded-md bg-slate-700/50 px-4 py-2 text-sm font-medium text-slate-300 hover:bg-slate-700"
          >
            Cancel
          </button>
        </div>
      )}

      {/* Exceptions list */}
      {exceptions.length === 0 && (
        <p className="py-4 text-center text-sm text-slate-500">
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
                className="flex items-center justify-between rounded-lg border border-slate-800/50 bg-slate-900/30 px-4 py-3"
              >
                <div className="flex items-center gap-3">
                  <span className="text-sm font-medium text-slate-300">
                    {new Date(exc.exceptionDate).toLocaleDateString(undefined, {
                      weekday: "short",
                      year: "numeric",
                      month: "short",
                      day: "numeric",
                    })}
                  </span>
                  {exc.name && (
                    <span className="text-sm text-slate-400">
                      {exc.name}
                    </span>
                  )}
                  <span
                    className={`rounded px-2 py-0.5 text-xs ${
                      exc.dayType === "NON_WORKING"
                        ? "bg-red-500/10 text-red-400"
                        : "bg-emerald-500/10 text-emerald-400"
                    }`}
                  >
                    {exc.dayType.replace("_", " ")}
                  </span>
                </div>
                <button
                  type="button"
                  onClick={() => onRemove(exc.id)}
                  className="rounded p-1.5 text-slate-500 hover:bg-red-500/10 hover:text-red-400"
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
