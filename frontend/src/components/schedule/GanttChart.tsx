"use client";

import React, { useRef, useEffect, useState } from "react";
import {
  differenceInDays,
  min as getMin,
  max as getMax,
  format,
  addDays,
  startOfDay,
} from "date-fns";
import type { ActivityResponse } from "@/lib/types";
import { GanttTimescale } from "./GanttTimescale";
import { GanttSidebar } from "./GanttSidebar";
import { GanttTaskRow } from "./GanttTaskRow";

interface ActivityRelationship {
  predecessorActivityId: string;
  successorActivityId: string;
  relationshipType: string;
}

interface BaselineActivityData {
  activityId: string;
  baselineStartDate: string | null;
  baselineFinishDate: string | null;
}

interface GanttChartProps {
  activities: ActivityResponse[];
  relationships?: ActivityRelationship[];
  baselineActivities?: BaselineActivityData[];
  onActivityClick?: (id: string) => void;
  onActivityReschedule?: (id: string, newStart: string, newEnd: string) => void;
  spotlightStartDate?: string | null;
  spotlightEndDate?: string | null;
}

interface DateRange {
  start: Date;
  end: Date;
  days: number;
}

export function GanttChart({
  activities,
  relationships = [],
  baselineActivities = [],
  onActivityClick,
  onActivityReschedule,
  spotlightStartDate,
  spotlightEndDate,
}: GanttChartProps) {
  const chartContainerRef = useRef<HTMLDivElement>(null);
  const sidebarRef = useRef<HTMLDivElement>(null);
  const [pixelsPerDay, setPixelsPerDay] = useState(20);
  const [startDateFilter, setStartDateFilter] = useState(spotlightStartDate || "");
  const [endDateFilter, setEndDateFilter] = useState(spotlightEndDate || "");

  // Calculate date range from all activities
  const dateRange = calculateDateRange(activities);

  if (!dateRange || activities.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-slate-700 py-12 text-center">
        <p className="text-slate-400">No activities to display</p>
      </div>
    );
  }

  const handleSidebarScroll = (e: React.UIEvent<HTMLDivElement>) => {
    if (chartContainerRef.current) {
      chartContainerRef.current.scrollTop = (e.target as HTMLDivElement).scrollTop;
    }
  };

  const handleChartScroll = (e: React.UIEvent<HTMLDivElement>) => {
    if (sidebarRef.current) {
      sidebarRef.current.scrollTop = (e.target as HTMLDivElement).scrollTop;
    }
  };

  const totalWidth = dateRange.days * pixelsPerDay;
  const rowHeight = 32;
  const timelineStartY = 80; // Space for header

  const getActivityOpacity = (activity: ActivityResponse): number => {
    if (!startDateFilter && !endDateFilter) return 1;

    const actStart = (activity.plannedStartDate || activity.earlyStartDate) ? new Date((activity.plannedStartDate || activity.earlyStartDate)!) : null;
    const actEnd = (activity.plannedFinishDate || activity.earlyFinishDate) ? new Date((activity.plannedFinishDate || activity.earlyFinishDate)!) : null;
    const filterStart = startDateFilter ? new Date(startDateFilter) : null;
    const filterEnd = endDateFilter ? new Date(endDateFilter) : null;

    if (!actStart || !actEnd) return 0.3;

    const isInRange =
      (!filterStart || !filterEnd || actStart <= filterEnd || actEnd >= filterStart) &&
      (!filterEnd || !filterStart || actEnd >= filterStart || actStart <= filterEnd);

    return isInRange ? 1 : 0.3;
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-white">Gantt Chart</h2>
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2 text-sm text-slate-300">
            <label>Progress Spotlight:</label>
            <input
              type="date"
              value={startDateFilter}
              onChange={(e) => setStartDateFilter(e.target.value)}
              className="rounded border border-slate-700 px-2 py-1 bg-slate-900/50 text-white"
              placeholder="Start"
            />
            <span>to</span>
            <input
              type="date"
              value={endDateFilter}
              onChange={(e) => setEndDateFilter(e.target.value)}
              className="rounded border border-slate-700 px-2 py-1 bg-slate-900/50 text-white"
              placeholder="End"
            />
            {(startDateFilter || endDateFilter) && (
              <button
                onClick={() => {
                  setStartDateFilter("");
                  setEndDateFilter("");
                }}
                className="text-xs text-blue-400 hover:underline"
              >
                Clear
              </button>
            )}
          </div>

          <label className="flex items-center gap-2 text-sm text-slate-300">
            Zoom:
            <input
              type="range"
              min="5"
              max="50"
              value={pixelsPerDay}
              onChange={(e) => setPixelsPerDay(Number(e.target.value))}
              className="w-24"
            />
            {pixelsPerDay}px
          </label>
        </div>
      </div>

      <div className="flex gap-4 border border-slate-800 rounded-lg overflow-hidden bg-slate-900/50">
        {/* Sidebar */}
        <div
          ref={sidebarRef}
          className="w-80 overflow-y-auto border-r border-slate-800"
          onScroll={handleSidebarScroll}
        >
          <GanttSidebar activities={activities} rowHeight={rowHeight} />
        </div>

        {/* Chart */}
        <div
          ref={chartContainerRef}
          className="flex-1 overflow-auto"
          onScroll={handleChartScroll}
        >
          <div className="inline-block min-w-full">
            <GanttTimescale dateRange={dateRange} pixelsPerDay={pixelsPerDay} />

            <svg
              width={totalWidth}
              height={activities.length * rowHeight + timelineStartY}
              className="bg-slate-900/50"
            >
              <defs>
                {/* Arrowhead marker for relationship lines */}
                <marker
                  id="arrowhead"
                  markerWidth="10"
                  markerHeight="10"
                  refX="9"
                  refY="3"
                  orient="auto"
                >
                  <polygon points="0 0, 10 3, 0 6" fill="#9ca3af" />
                </marker>
                <marker
                  id="arrowhead-critical"
                  markerWidth="10"
                  markerHeight="10"
                  refX="9"
                  refY="3"
                  orient="auto"
                >
                  <polygon points="0 0, 10 3, 0 6" fill="#ef4444" />
                </marker>
              </defs>

              {/* Grid lines for weeks/days */}
              {renderGridLines(dateRange, pixelsPerDay, activities.length * rowHeight)}

              {/* Today line */}
              {renderTodayLine(dateRange, pixelsPerDay, activities.length * rowHeight, timelineStartY)}

              {/* Relationship lines */}
              {relationships.length > 0 &&
                renderRelationshipLines(
                  relationships,
                  activities,
                  dateRange,
                  pixelsPerDay,
                  rowHeight,
                  timelineStartY
                )}

              {/* Activity bars */}
              {activities.map((activity, index) => {
                const baselineData = baselineActivities.find((b) => b.activityId === activity.id);
                const opacity = getActivityOpacity(activity);
                return (
                  <g key={activity.id} opacity={opacity}>
                    <GanttTaskRow
                      activity={activity}
                      dateRange={dateRange}
                      pixelsPerDay={pixelsPerDay}
                      rowIndex={index}
                      rowHeight={rowHeight}
                      timelineStartY={timelineStartY}
                      baselineData={baselineData}
                      onActivityClick={onActivityClick}
                      onActivityReschedule={onActivityReschedule}
                    />
                  </g>
                );
              })}
            </svg>
          </div>
        </div>
      </div>

      {/* Legend */}
      <div className="flex flex-wrap gap-6 rounded-lg border border-slate-800 bg-slate-900/80 p-4">
        <div className="flex items-center gap-2">
          <div className="h-4 w-4 rounded bg-blue-500" />
          <span className="text-sm text-slate-300">Normal</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="h-4 w-4 rounded bg-red-500" />
          <span className="text-sm text-slate-300">Critical Path</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="h-4 w-4 rounded bg-emerald-500" />
          <span className="text-sm text-slate-300">Completed</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="h-4 w-4 rounded bg-slate-500" />
          <span className="text-sm text-slate-300">Not Started</span>
        </div>
        {(startDateFilter || endDateFilter) && (
          <div className="flex items-center gap-2">
            <div className="h-4 w-4 rounded bg-yellow-300" />
            <span className="text-sm text-slate-300">In Spotlight Range</span>
          </div>
        )}
        {baselineActivities.length > 0 && (
          <div className="flex items-center gap-2">
            <div className="h-2 w-4 rounded bg-slate-500" style={{ opacity: 0.5 }} />
            <span className="text-sm text-slate-300">Baseline</span>
          </div>
        )}
        {relationships.length > 0 && (
          <div className="flex items-center gap-2">
            <div className="w-6 h-4 relative">
              <svg width="24" height="16" viewBox="0 0 24 16" className="absolute">
                <path d="M 0 8 L 24 8" stroke="#64748b" strokeWidth="1.5" fill="none" />
                <polygon points="24,8 18,5 18,11" fill="#64748b" />
              </svg>
            </div>
            <span className="text-sm text-slate-300">Relationships</span>
          </div>
        )}
      </div>
    </div>
  );
}

function calculateDateRange(activities: ActivityResponse[]): DateRange | null {
  const validDates = activities
    .flatMap((a) => [
      a.plannedStartDate || a.earlyStartDate,
      a.plannedFinishDate || a.earlyFinishDate,
    ])
    .filter((d): d is string => d != null)
    .map((d) => startOfDay(new Date(d)));

  if (validDates.length === 0) {
    return null;
  }

  const start = getMin(validDates);
  const end = getMax(validDates);
  const days = Math.max(differenceInDays(end, start) + 1, 7); // Minimum 7 days

  return { start, end, days };
}

function renderGridLines(
  dateRange: DateRange,
  pixelsPerDay: number,
  height: number
): React.ReactNode {
  const lines: React.ReactNode[] = [];
  const weekInDays = 7;

  for (let i = 0; i <= dateRange.days; i++) {
    if (i % weekInDays === 0) {
      const x = i * pixelsPerDay;
      lines.push(
        <line
          key={`grid-${i}`}
          x1={x}
          y1="0"
          x2={x}
          y2={height}
          stroke="#1e293b"
          strokeWidth="1"
          strokeDasharray="2,2"
        />
      );
    }
  }

  return lines;
}

function renderTodayLine(
  dateRange: DateRange,
  pixelsPerDay: number,
  height: number,
  startY: number
): React.ReactNode | null {
  const today = startOfDay(new Date());

  if (today < dateRange.start || today > addDays(dateRange.start, dateRange.days)) {
    return null;
  }

  const daysFromStart = differenceInDays(today, dateRange.start);
  const x = daysFromStart * pixelsPerDay;

  return (
    <g key="today-line">
      <line
        x1={x}
        y1="0"
        x2={x}
        y2={height}
        stroke="#ef4444"
        strokeWidth="2"
        strokeDasharray="4,4"
      />
      <text
        x={x + 4}
        y="12"
        fontSize="12"
        fill="#ef4444"
        fontWeight="bold"
      >
        Today
      </text>
    </g>
  );
}

function renderRelationshipLines(
  relationships: ActivityRelationship[],
  activities: ActivityResponse[],
  dateRange: DateRange,
  pixelsPerDay: number,
  rowHeight: number,
  timelineStartY: number
): React.ReactNode[] {
  const lines: React.ReactNode[] = [];
  const activityMap = new Map(activities.map((a, idx) => [a.id, idx]));

  relationships.forEach((rel, idx) => {
    const predIdx = activityMap.get(rel.predecessorActivityId);
    const succIdx = activityMap.get(rel.successorActivityId);

    if (predIdx === undefined || succIdx === undefined) return;

    const predActivity = activities[predIdx];
    const succActivity = activities[succIdx];

    // Get dates
    const predStartStr = predActivity.plannedStartDate || predActivity.earlyStartDate;
    const predEndStr = predActivity.plannedFinishDate || predActivity.earlyFinishDate;
    const succStartStr = succActivity.plannedStartDate || succActivity.earlyStartDate;

    const predStart = predStartStr ? startOfDay(new Date(predStartStr)) : null;
    const predEnd = predEndStr ? startOfDay(new Date(predEndStr)) : null;
    const succStart = succStartStr ? startOfDay(new Date(succStartStr)) : null;

    if (!predEnd || !succStart) return;

    // Calculate bar positions
    const predEndOffset = differenceInDays(predEnd, dateRange.start);
    const succStartOffset = Math.max(0, differenceInDays(succStart, dateRange.start));

    const predX = predEndOffset * pixelsPerDay;
    const predY = predIdx * rowHeight + timelineStartY + rowHeight / 2;

    const succX = succStartOffset * pixelsPerDay;
    const succY = succIdx * rowHeight + timelineStartY + rowHeight / 2;

    // Determine if critical (both activities have totalFloat === 0)
    const isCritical = predActivity.totalFloat === 0 && succActivity.totalFloat === 0;

    // Draw connection line with horizontal routing
    const midX = (predX + succX) / 2;

    lines.push(
      <g key={`rel-${idx}`} opacity="0.6">
        <path
          d={`M ${predX} ${predY} L ${midX} ${predY} L ${midX} ${succY} L ${succX} ${succY}`}
          stroke={isCritical ? "#ef4444" : "#9ca3af"}
          strokeWidth="1.5"
          fill="none"
          markerEnd={isCritical ? "url(#arrowhead-critical)" : "url(#arrowhead)"}
        />
      </g>
    );
  });

  return lines;
}
