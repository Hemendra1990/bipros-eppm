"use client";

import { useState } from "react";
import type {
  CalendarWorkWeekResponse,
  CalendarWorkWeekRequest,
} from "@/lib/api/calendarApi";

const DAYS_OF_WEEK = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
] as const;

const DAY_LABELS: Record<string, string> = {
  MONDAY: "Mon",
  TUESDAY: "Tue",
  WEDNESDAY: "Wed",
  THURSDAY: "Thu",
  FRIDAY: "Fri",
  SATURDAY: "Sat",
  SUNDAY: "Sun",
};

interface WorkWeekEditorProps {
  workWeeks: CalendarWorkWeekResponse[];
  onSave: (data: CalendarWorkWeekRequest[]) => Promise<void>;
  saving?: boolean;
}

export function WorkWeekEditor({
  workWeeks,
  onSave,
  saving = false,
}: WorkWeekEditorProps) {
  const buildInitialState = (): Record<
    string,
    {
      working: boolean;
      startTime1: string;
      endTime1: string;
      startTime2: string;
      endTime2: string;
    }
  > => {
    const state: Record<
      string,
      {
        working: boolean;
        startTime1: string;
        endTime1: string;
        startTime2: string;
        endTime2: string;
      }
    > = {};
    for (const day of DAYS_OF_WEEK) {
      const existing = workWeeks.find((w) => w.dayOfWeek === day);
      const isWeekend = day === "SATURDAY" || day === "SUNDAY";
      state[day] = {
        working: existing
          ? existing.dayType === "WORKING"
          : !isWeekend,
        startTime1: existing?.startTime1 ?? "08:00",
        endTime1: existing?.endTime1 ?? "12:00",
        startTime2: existing?.startTime2 ?? "13:00",
        endTime2: existing?.endTime2 ?? "17:00",
      };
    }
    return state;
  };

  const [days, setDays] = useState(buildInitialState);

  const toggleDay = (day: string) => {
    setDays((prev) => ({
      ...prev,
      [day]: { ...prev[day], working: !prev[day].working },
    }));
  };

  const updateTime = (day: string, field: string, value: string) => {
    setDays((prev) => ({
      ...prev,
      [day]: { ...prev[day], [field]: value },
    }));
  };

  const handleSave = async () => {
    const data: CalendarWorkWeekRequest[] = DAYS_OF_WEEK.map((day) => {
      const d = days[day];
      return {
        dayOfWeek: day,
        dayType: d.working ? ("WORKING" as const) : ("NON_WORKING" as const),
        ...(d.working
          ? {
              startTime1: d.startTime1,
              endTime1: d.endTime1,
              startTime2: d.startTime2,
              endTime2: d.endTime2,
            }
          : {}),
      };
    });
    await onSave(data);
  };

  const inputClass =
    "w-20 rounded border border-slate-700 bg-slate-800/50 px-2 py-1 text-xs text-white focus:border-blue-500 focus:outline-none";

  return (
    <div className="space-y-4">
      <h3 className="text-sm font-semibold text-slate-200">
        Work Week Pattern
      </h3>

      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-800 text-left text-xs text-slate-400">
              <th className="py-2 pr-4">Day</th>
              <th className="py-2 pr-4">Working</th>
              <th className="py-2 pr-4">Shift 1 Start</th>
              <th className="py-2 pr-4">Shift 1 End</th>
              <th className="py-2 pr-4">Shift 2 Start</th>
              <th className="py-2 pr-4">Shift 2 End</th>
            </tr>
          </thead>
          <tbody>
            {DAYS_OF_WEEK.map((day) => {
              const d = days[day];
              return (
                <tr
                  key={day}
                  className="border-b border-slate-800/50"
                >
                  <td className="py-2 pr-4 font-medium text-slate-300">
                    {DAY_LABELS[day]}
                  </td>
                  <td className="py-2 pr-4">
                    <button
                      type="button"
                      onClick={() => toggleDay(day)}
                      className={`rounded px-3 py-1 text-xs font-medium transition-colors ${
                        d.working
                          ? "bg-emerald-500/20 text-emerald-400"
                          : "bg-slate-700/50 text-slate-500"
                      }`}
                    >
                      {d.working ? "Working" : "Non-Working"}
                    </button>
                  </td>
                  <td className="py-2 pr-4">
                    <input
                      type="time"
                      value={d.startTime1}
                      onChange={(e) =>
                        updateTime(day, "startTime1", e.target.value)
                      }
                      disabled={!d.working}
                      className={inputClass}
                    />
                  </td>
                  <td className="py-2 pr-4">
                    <input
                      type="time"
                      value={d.endTime1}
                      onChange={(e) =>
                        updateTime(day, "endTime1", e.target.value)
                      }
                      disabled={!d.working}
                      className={inputClass}
                    />
                  </td>
                  <td className="py-2 pr-4">
                    <input
                      type="time"
                      value={d.startTime2}
                      onChange={(e) =>
                        updateTime(day, "startTime2", e.target.value)
                      }
                      disabled={!d.working}
                      className={inputClass}
                    />
                  </td>
                  <td className="py-2 pr-4">
                    <input
                      type="time"
                      value={d.endTime2}
                      onChange={(e) =>
                        updateTime(day, "endTime2", e.target.value)
                      }
                      disabled={!d.working}
                      className={inputClass}
                    />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <button
        type="button"
        onClick={handleSave}
        disabled={saving}
        className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:bg-slate-600"
      >
        {saving ? "Saving..." : "Save Work Week"}
      </button>
    </div>
  );
}
