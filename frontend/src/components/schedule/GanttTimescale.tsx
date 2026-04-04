"use client";

import React from "react";
import { format, eachMonthOfInterval, eachDayOfInterval, addDays, differenceInDays } from "date-fns";

interface DateRange {
  start: Date;
  end: Date;
  days: number;
}

interface GanttTimescaleProps {
  dateRange: DateRange;
  pixelsPerDay: number;
}

export function GanttTimescale({ dateRange, pixelsPerDay }: GanttTimescaleProps) {
  const months = eachMonthOfInterval({
    start: dateRange.start,
    end: addDays(dateRange.start, dateRange.days - 1),
  });

  const totalWidth = dateRange.days * pixelsPerDay;
  const headerHeight = 80;

  return (
    <svg width={totalWidth} height={headerHeight} className="sticky top-0 bg-white border-b border-gray-200">
      {/* Month headers */}
      <g>
        {months.map((month, idx) => {
          const monthStart = new Date(month.getFullYear(), month.getMonth(), 1);
          const monthEnd = new Date(month.getFullYear(), month.getMonth() + 1, 0);

          const adjustedStart = monthStart < dateRange.start ? dateRange.start : monthStart;
          const adjustedEnd = monthEnd > addDays(dateRange.start, dateRange.days - 1)
            ? addDays(dateRange.start, dateRange.days - 1)
            : monthEnd;

          const startOffset = differenceInDays(adjustedStart, dateRange.start);
          const endOffset = differenceInDays(adjustedEnd, dateRange.start);
          const width = (endOffset - startOffset + 1) * pixelsPerDay;
          const x = startOffset * pixelsPerDay;

          return (
            <g key={`month-${idx}`}>
              <rect
                x={x}
                y="0"
                width={width}
                height="40"
                fill="#f3f4f6"
                stroke="#d1d5db"
                strokeWidth="1"
              />
              <text
                x={x + width / 2}
                y="28"
                textAnchor="middle"
                fontSize="13"
                fontWeight="bold"
                fill="#1f2937"
              >
                {format(month, "MMM yyyy")}
              </text>
            </g>
          );
        })}
      </g>

      {/* Week/Day markers */}
      <g>
        {Array.from({ length: dateRange.days }).map((_, i) => {
          const date = addDays(dateRange.start, i);
          const x = i * pixelsPerDay;
          const dayOfWeek = date.getDay();
          const isMonday = dayOfWeek === 1;

          return (
            <g key={`day-${i}`}>
              {isMonday && (
                <text
                  x={x + 5}
                  y="72"
                  fontSize="11"
                  fill="#6b7280"
                  fontWeight="600"
                >
                  W{Math.ceil((i + 1) / 7)}
                </text>
              )}
              <line
                x1={x}
                y1="40"
                x2={x}
                y2="80"
                stroke={isMonday ? "#9ca3af" : "#e5e7eb"}
                strokeWidth={isMonday ? "1" : "0.5"}
              />
            </g>
          );
        })}
      </g>
    </svg>
  );
}
