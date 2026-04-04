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

interface GanttChartProps {
  activities: ActivityResponse[];
  onActivityClick?: (id: string) => void;
}

interface DateRange {
  start: Date;
  end: Date;
  days: number;
}

export function GanttChart({ activities, onActivityClick }: GanttChartProps) {
  const chartContainerRef = useRef<HTMLDivElement>(null);
  const sidebarRef = useRef<HTMLDivElement>(null);
  const [pixelsPerDay, setPixelsPerDay] = useState(20);

  // Calculate date range from all activities
  const dateRange = calculateDateRange(activities);

  if (!dateRange || activities.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-gray-300 py-12 text-center">
        <p className="text-gray-500">No activities to display</p>
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

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-gray-900">Gantt Chart</h2>
        <div className="flex items-center gap-4">
          <label className="flex items-center gap-2 text-sm text-gray-700">
            Pixels per day:
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

      <div className="flex gap-4 border border-gray-200 rounded-lg overflow-hidden bg-white">
        {/* Sidebar */}
        <div
          ref={sidebarRef}
          className="w-80 overflow-y-auto border-r border-gray-200"
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
              className="bg-white"
            >
              {/* Grid lines for weeks/days */}
              {renderGridLines(dateRange, pixelsPerDay, activities.length * rowHeight)}

              {/* Today line */}
              {renderTodayLine(dateRange, pixelsPerDay, activities.length * rowHeight, timelineStartY)}

              {/* Activity bars */}
              {activities.map((activity, index) => (
                <g key={activity.id}>
                  <GanttTaskRow
                    activity={activity}
                    dateRange={dateRange}
                    pixelsPerDay={pixelsPerDay}
                    rowIndex={index}
                    rowHeight={rowHeight}
                    timelineStartY={timelineStartY}
                    onActivityClick={onActivityClick}
                  />
                </g>
              ))}
            </svg>
          </div>
        </div>
      </div>

      {/* Legend */}
      <div className="flex flex-wrap gap-6 rounded-lg border border-gray-200 bg-gray-50 p-4">
        <div className="flex items-center gap-2">
          <div className="h-4 w-4 rounded bg-blue-500" />
          <span className="text-sm text-gray-700">Normal</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="h-4 w-4 rounded bg-red-500" />
          <span className="text-sm text-gray-700">Critical Path</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="h-4 w-4 rounded bg-green-500" />
          <span className="text-sm text-gray-700">Completed</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="h-4 w-4 rounded bg-gray-400" />
          <span className="text-sm text-gray-700">Not Started</span>
        </div>
      </div>
    </div>
  );
}

function calculateDateRange(activities: ActivityResponse[]): DateRange | null {
  const validDates = activities
    .flatMap((a) => [a.plannedStartDate, a.plannedFinishDate])
    .filter((d): d is string => d !== null)
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
          stroke="#e5e7eb"
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
