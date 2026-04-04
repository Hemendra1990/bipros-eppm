"use client";

import React from "react";
import { differenceInDays, startOfDay } from "date-fns";
import type { ActivityResponse } from "@/lib/types";

interface DateRange {
  start: Date;
  end: Date;
  days: number;
}

interface GanttTaskRowProps {
  activity: ActivityResponse;
  dateRange: DateRange;
  pixelsPerDay: number;
  rowIndex: number;
  rowHeight: number;
  timelineStartY: number;
  onActivityClick?: (id: string) => void;
}

export function GanttTaskRow({
  activity,
  dateRange,
  pixelsPerDay,
  rowIndex,
  rowHeight,
  timelineStartY,
  onActivityClick,
}: GanttTaskRowProps) {
  const startDate = activity.plannedStartDate
    ? startOfDay(new Date(activity.plannedStartDate))
    : null;
  const endDate = activity.plannedFinishDate
    ? startOfDay(new Date(activity.plannedFinishDate))
    : null;

  if (!startDate || !endDate) {
    return null;
  }

  const startOffset = Math.max(0, differenceInDays(startDate, dateRange.start));
  const endOffset = differenceInDays(endDate, dateRange.start);
  const duration = Math.max(1, endOffset - startOffset + 1);

  const x = startOffset * pixelsPerDay;
  const width = duration * pixelsPerDay;
  const y = rowIndex * rowHeight + timelineStartY;

  // Determine bar color
  const color = getBarColor(activity);
  const barHeight = rowHeight - 4;

  // Calculate percent complete indicator
  const percentComplete = activity.percentComplete || 0;
  const completeWidth = (width * percentComplete) / 100;

  return (
    <g key={`task-${activity.id}`}>
      {/* Main bar */}
      <rect
        x={x}
        y={y + 2}
        width={width}
        height={barHeight}
        fill={color}
        opacity="0.8"
        rx="2"
        className="cursor-pointer hover:opacity-100 transition-opacity"
        onClick={() => onActivityClick?.(activity.id)}
      />

      {/* Percent complete overlay */}
      {percentComplete > 0 && (
        <rect
          x={x}
          y={y + 2}
          width={completeWidth}
          height={barHeight}
          fill={color}
          opacity="1"
          rx="2"
        />
      )}

      {/* Activity label */}
      {width > 50 && (
        <text
          x={x + 4}
          y={y + rowHeight / 2 + 4}
          fontSize="11"
          fill="white"
          fontWeight="600"
          className="pointer-events-none"
        >
          {activity.code}
        </text>
      )}

      {/* Tooltip on hover */}
      <title>
        {activity.name} | {activity.code} | {percentComplete}% complete | Float: {activity.totalFloat}d
      </title>
    </g>
  );
}

function getBarColor(activity: ActivityResponse): string {
  // Green for completed
  if (activity.status === "COMPLETED") {
    return "#10b981"; // green-600
  }

  // Red for critical path (totalFloat === 0)
  if (activity.totalFloat === 0) {
    return "#ef4444"; // red-500
  }

  // Gray for not started
  if (activity.status === "NOT_STARTED") {
    return "#9ca3af"; // gray-400
  }

  // Blue for in progress or normal
  return "#3b82f6"; // blue-500
}
