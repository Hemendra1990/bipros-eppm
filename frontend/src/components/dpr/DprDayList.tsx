"use client";

import { CalendarDays, CloudSun, User } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { DailyProgressReportResponse } from "@/lib/types/dpr";
import { DprActivityCard } from "./DprActivityCard";

interface Props {
  rows: DailyProgressReportResponse[];
  onEdit: (row: DailyProgressReportResponse) => void;
  onDelete: (row: DailyProgressReportResponse) => void;
  /**
   * Pixel offset for sticky day headers, so they park beneath the page's sticky filter bar.
   * Falls back to 0 (sticky still works at top of viewport) when caller doesn't measure.
   */
  stickyOffset?: number;
}

interface DayGroup {
  date: string;
  rows: DailyProgressReportResponse[];
}

const groupByDay = (rows: DailyProgressReportResponse[]): DayGroup[] => {
  const map = new Map<string, DailyProgressReportResponse[]>();
  for (const r of rows) {
    const list = map.get(r.reportDate) ?? [];
    list.push(r);
    map.set(r.reportDate, list);
  }
  return Array.from(map.entries())
    .sort(([a], [b]) => b.localeCompare(a)) // most recent first
    .map(([date, rows]) => ({ date, rows }));
};

const fmtDate = (iso: string): string => {
  const d = new Date(iso + "T00:00:00");
  return d.toLocaleDateString(undefined, {
    weekday: "short",
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
};

export function DprDayList({ rows, onEdit, onDelete, stickyOffset = 0 }: Props) {
  const groups = groupByDay(rows);

  if (groups.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-hairline bg-ivory/30 px-6 py-12 text-center">
        <CalendarDays className="mx-auto h-8 w-8 text-slate" />
        <p className="mt-2 text-sm text-slate">
          No daily progress logged in this range. Tap{" "}
          <span className="font-semibold text-gold-ink">Add Activity</span> to start.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {groups.map((g) => {
        const supervisors = Array.from(new Set(g.rows.map((r) => r.supervisorName))).filter(Boolean);
        const weather = g.rows.find((r) => r.weatherCondition)?.weatherCondition ?? null;

        return (
          <section key={g.date} className="space-y-2">
            <div
              className="sticky z-10 flex flex-wrap items-center gap-3 rounded-lg border border-hairline bg-ivory/80 px-4 py-2.5 backdrop-blur-sm"
              style={{ top: stickyOffset }}
            >
              <div className="flex items-center gap-2 font-display text-base font-semibold text-charcoal">
                <CalendarDays className="h-4 w-4 text-gold-deep" />
                {fmtDate(g.date)}
              </div>
              <Badge variant="gold">
                {g.rows.length} {g.rows.length === 1 ? "activity" : "activities"}
              </Badge>
              {supervisors.length > 0 && (
                <span className="inline-flex items-center gap-1 text-xs text-slate">
                  <User className="h-3 w-3" /> {supervisors.join(", ")}
                </span>
              )}
              {weather && (
                <span className="inline-flex items-center gap-1 text-xs text-slate">
                  <CloudSun className="h-3 w-3" /> {weather}
                </span>
              )}
            </div>
            <div className="space-y-2">
              {g.rows.map((row) => (
                <DprActivityCard
                  key={row.id}
                  row={row}
                  onEdit={() => onEdit(row)}
                  onDelete={() => onDelete(row)}
                />
              ))}
            </div>
          </section>
        );
      })}
    </div>
  );
}

export function DprDaySkeleton() {
  return (
    <section className="space-y-2">
      <div className="flex items-center gap-3 rounded-lg border border-hairline bg-ivory/60 px-4 py-2.5">
        <div className="h-4 w-4 animate-pulse rounded bg-parchment" />
        <div className="h-4 w-40 animate-pulse rounded bg-parchment" />
        <div className="h-5 w-20 animate-pulse rounded-full bg-parchment" />
      </div>
      <div className="space-y-2">
        <div className="h-14 animate-pulse rounded-lg bg-parchment/60" />
        <div className="h-14 animate-pulse rounded-lg bg-parchment/60" />
      </div>
    </section>
  );
}
