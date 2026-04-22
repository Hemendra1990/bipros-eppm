"use client";

import React from "react";
import { format } from "date-fns";
import type { ActivityResponse } from "@/lib/types";

interface GanttSidebarProps {
  activities: ActivityResponse[];
  rowHeight: number;
}

export function GanttSidebar({ activities, rowHeight }: GanttSidebarProps) {
  const headerHeight = 80;

  return (
    <div>
      {/* Header */}
      <div
        className="sticky top-0 bg-surface/80 border-b border-border flex"
        style={{ height: headerHeight }}
      >
        <div style={{ minWidth: "60px" }} className="p-3 flex items-center justify-center border-r border-border">
          <span className="text-xs font-semibold text-text-secondary text-center">Code</span>
        </div>
        <div style={{ minWidth: "180px" }} className="p-3 flex items-center justify-center border-r border-border">
          <span className="text-xs font-semibold text-text-secondary text-center">Name</span>
        </div>
        <div style={{ minWidth: "48px" }} className="p-3 flex items-center justify-center border-r border-border">
          <span className="text-xs font-semibold text-text-secondary text-center">Dur</span>
        </div>
        <div style={{ minWidth: "90px" }} className="p-3 flex items-center justify-center border-r border-border">
          <span className="text-xs font-semibold text-text-secondary text-center">Start</span>
        </div>
        <div style={{ minWidth: "90px" }} className="p-3 flex items-center justify-center">
          <span className="text-xs font-semibold text-text-secondary text-center">End</span>
        </div>
      </div>

      {/* Rows */}
      {activities.map((activity) => {
        const a = activity;
        const durationValue = a.originalDuration ?? a.duration;
        const durationText =
          durationValue != null && !Number.isNaN(Number(durationValue))
            ? `${Number(durationValue)}d`
            : "—";
        const startStr = a.plannedStartDate ?? a.earlyStartDate ?? null;
        const finishStr = a.plannedFinishDate ?? a.earlyFinishDate ?? null;
        return (
          <div
            key={a.id}
            className="flex border-b border-border hover:bg-surface-hover/50"
            style={{ height: rowHeight }}
          >
            <div style={{ minWidth: "60px" }} className="p-2 flex items-center border-r border-border overflow-hidden">
              <span className="text-xs font-medium text-text-primary truncate">{a.code}</span>
            </div>
            <div style={{ minWidth: "180px" }} className="p-2 flex items-center border-r border-border overflow-hidden">
              <span className="text-xs text-text-secondary truncate" title={a.name}>
                {a.name}
              </span>
            </div>
            <div style={{ minWidth: "48px" }} className="p-2 flex items-center justify-center border-r border-border">
              <span className="text-xs text-text-secondary">{durationText}</span>
            </div>
            <div style={{ minWidth: "90px" }} className="p-2 flex items-center justify-center border-r border-border">
              <span className="text-xs text-text-secondary">
                {startStr ? format(new Date(startStr), "d MMM yyyy") : "-"}
              </span>
            </div>
            <div style={{ minWidth: "90px" }} className="p-2 flex items-center justify-center">
              <span className="text-xs text-text-secondary">
                {finishStr ? format(new Date(finishStr), "d MMM yyyy") : "-"}
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}
