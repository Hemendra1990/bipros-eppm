"use client";

import { Briefcase, CalendarDays, CloudSun, HardHat, Package, User } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { DailyProgressReportResponse } from "@/lib/types/dpr";
import { DprActivityCard } from "./DprActivityCard";

interface Props {
  rows: DailyProgressReportResponse[];
  onEdit: (row: DailyProgressReportResponse) => void;
  onDelete: (row: DailyProgressReportResponse) => void;
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

const fmt = (n: number, digits = 2) =>
  isFinite(n) ? n.toLocaleString(undefined, { maximumFractionDigits: digits }) : "—";

export function DprDayList({ rows, onEdit, onDelete }: Props) {
  const groups = groupByDay(rows);

  if (groups.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-hairline bg-ivory/30 px-6 py-12 text-center">
        <CalendarDays className="mx-auto h-8 w-8 text-slate" />
        <p className="mt-2 text-sm text-slate">No DPR rows in this date range yet.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {groups.map((g) => {
        const supervisors = Array.from(new Set(g.rows.map((r) => r.supervisorName))).filter(Boolean);
        const weather = g.rows.find((r) => r.weatherCondition)?.weatherCondition ?? null;
        const totalQty = g.rows.reduce((a, r) => a + (r.qtyExecuted ?? 0), 0);
        const manpower = g.rows.reduce(
          (a, r) => a + (r.manpower ?? []).reduce((b, m) => b + (m.nos ?? 0), 0),
          0
        );
        const equipment = g.rows.reduce(
          (a, r) => a + (r.equipment ?? []).reduce((b, e) => b + (e.nos ?? 0), 0),
          0
        );
        const materials = g.rows.reduce((a, r) => a + (r.materials ?? []).length, 0);

        return (
          <section key={g.date} className="space-y-2">
            <div className="flex flex-wrap items-center gap-3 rounded-lg border border-hairline bg-gold-tint/40 px-4 py-2.5">
              <div className="flex items-center gap-2 font-display text-base font-semibold text-charcoal">
                <CalendarDays className="h-4 w-4 text-gold-deep" />
                {fmtDate(g.date)}
              </div>
              <Badge variant="gold">
                {g.rows.length} {g.rows.length === 1 ? "activity" : "activities"}
              </Badge>
              <span className="text-xs text-slate inline-flex items-center gap-1">
                <User className="h-3 w-3" /> {supervisors.join(", ") || "—"}
              </span>
              {weather && (
                <span className="text-xs text-slate inline-flex items-center gap-1">
                  <CloudSun className="h-3 w-3" /> {weather}
                </span>
              )}
              <div className="ml-auto flex items-center gap-3 text-xs text-slate">
                <span className="font-semibold text-charcoal tabular-nums">{fmt(totalQty)} qty</span>
                <span className="inline-flex items-center gap-1">
                  <HardHat className="h-3 w-3" /> {manpower}
                </span>
                <span className="inline-flex items-center gap-1">
                  <Briefcase className="h-3 w-3" /> {equipment}
                </span>
                <span className="inline-flex items-center gap-1">
                  <Package className="h-3 w-3" /> {materials}
                </span>
              </div>
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
