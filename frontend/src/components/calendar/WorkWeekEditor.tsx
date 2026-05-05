"use client";

import { useState } from "react";
import type {
  CalendarWorkWeekResponse,
  CalendarWorkWeekRequest,
} from "@/lib/api/calendarApi";
import { SimpleTable } from "@/components/common/SimpleTable";
import type { ColumnDef } from "@tanstack/react-table";

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
    "w-20 rounded border border-border bg-surface-hover/50 px-2 py-1 text-xs text-text-primary focus:border-accent focus:outline-none";

  const data = DAYS_OF_WEEK.map((day) => ({ day, ...days[day] }));

  return (
    <div className="space-y-4">
      <h3 className="text-sm font-semibold text-text-primary">
        Work Week Pattern
      </h3>

      <SimpleTable
        columns={[
          {
            accessorKey: "day",
            header: "Day",
            cell: ({ row }) => <span className="font-medium text-text-secondary">{DAY_LABELS[row.original.day]}</span>,
          },
          {
            accessorKey: "working",
            header: "Working",
            cell: ({ row }) => (
              <button
                type="button"
                onClick={() => toggleDay(row.original.day)}
                className={`rounded px-3 py-1 text-xs font-medium transition-colors ${
                  row.original.working
                    ? "bg-success/20 text-success"
                    : "bg-surface-active/50 text-text-muted"
                }`}
              >
                {row.original.working ? "Working" : "Non-Working"}
              </button>
            ),
          },
          {
            accessorKey: "startTime1",
            header: "Shift 1 Start",
            cell: ({ row }) => (
              <input
                type="time"
                value={row.original.startTime1}
                onChange={(e) => updateTime(row.original.day, "startTime1", e.target.value)}
                disabled={!row.original.working}
                className={inputClass}
              />
            ),
          },
          {
            accessorKey: "endTime1",
            header: "Shift 1 End",
            cell: ({ row }) => (
              <input
                type="time"
                value={row.original.endTime1}
                onChange={(e) => updateTime(row.original.day, "endTime1", e.target.value)}
                disabled={!row.original.working}
                className={inputClass}
              />
            ),
          },
          {
            accessorKey: "startTime2",
            header: "Shift 2 Start",
            cell: ({ row }) => (
              <input
                type="time"
                value={row.original.startTime2}
                onChange={(e) => updateTime(row.original.day, "startTime2", e.target.value)}
                disabled={!row.original.working}
                className={inputClass}
              />
            ),
          },
          {
            accessorKey: "endTime2",
            header: "Shift 2 End",
            cell: ({ row }) => (
              <input
                type="time"
                value={row.original.endTime2}
                onChange={(e) => updateTime(row.original.day, "endTime2", e.target.value)}
                disabled={!row.original.working}
                className={inputClass}
              />
            ),
          },
        ]}
        data={data}
        sortable={false}
      />

      <button
        type="button"
        onClick={handleSave}
        disabled={saving}
        className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-accent-foreground hover:bg-accent-hover disabled:bg-border"
      >
        {saving ? "Saving..." : "Save Work Week"}
      </button>
    </div>
  );
}
