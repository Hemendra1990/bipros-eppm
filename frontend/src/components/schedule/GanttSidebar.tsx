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
        className="sticky top-0 bg-slate-900/80 border-b border-slate-700 flex"
        style={{ height: headerHeight }}
      >
        <div style={{ minWidth: "60px" }} className="p-3 flex items-center justify-center border-r border-slate-700">
          <span className="text-xs font-semibold text-slate-300 text-center">Code</span>
        </div>
        <div style={{ minWidth: "180px" }} className="p-3 flex items-center justify-center border-r border-slate-700">
          <span className="text-xs font-semibold text-slate-300 text-center">Name</span>
        </div>
        <div style={{ minWidth: "40px" }} className="p-3 flex items-center justify-center border-r border-slate-700">
          <span className="text-xs font-semibold text-slate-300 text-center">Dur</span>
        </div>
        <div style={{ minWidth: "70px" }} className="p-3 flex items-center justify-center border-r border-slate-700">
          <span className="text-xs font-semibold text-slate-300 text-center">Start</span>
        </div>
        <div style={{ minWidth: "70px" }} className="p-3 flex items-center justify-center">
          <span className="text-xs font-semibold text-slate-300 text-center">End</span>
        </div>
      </div>

      {/* Rows */}
      {activities.map((activity, index) => (
        <div
          key={activity.id}
          className="flex border-b border-slate-800 hover:bg-slate-800/50"
          style={{ height: rowHeight }}
        >
          <div style={{ minWidth: "60px" }} className="p-2 flex items-center border-r border-slate-800 overflow-hidden">
            <span className="text-xs font-medium text-white truncate">{activity.code}</span>
          </div>
          <div style={{ minWidth: "180px" }} className="p-2 flex items-center border-r border-slate-800 overflow-hidden">
            <span className="text-xs text-slate-300 truncate" title={activity.name}>
              {activity.name}
            </span>
          </div>
          <div style={{ minWidth: "40px" }} className="p-2 flex items-center justify-center border-r border-slate-800">
            <span className="text-xs text-slate-300">{activity.duration}d</span>
          </div>
          <div style={{ minWidth: "70px" }} className="p-2 flex items-center justify-center border-r border-slate-800">
            <span className="text-xs text-slate-300">
              {activity.plannedStartDate
                ? format(new Date(activity.plannedStartDate), "MMM d")
                : "-"}
            </span>
          </div>
          <div style={{ minWidth: "70px" }} className="p-2 flex items-center justify-center">
            <span className="text-xs text-slate-300">
              {activity.plannedFinishDate
                ? format(new Date(activity.plannedFinishDate), "MMM d")
                : "-"}
            </span>
          </div>
        </div>
      ))}
    </div>
  );
}
