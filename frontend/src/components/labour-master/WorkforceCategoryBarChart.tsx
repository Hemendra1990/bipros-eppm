"use client";

import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { LabourCategorySummary } from "@/lib/api/labourMasterApi";

type Props = { rows: LabourCategorySummary[] };

export function WorkforceCategoryBarChart({ rows }: Props) {
  const data = rows.map((r) => ({
    name: r.categoryDisplay,
    workers: r.workerCount,
    cost: r.dailyCost,
  }));
  return (
    <div className="rounded-lg border bg-white p-4 shadow-sm h-80">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={data}
          layout="vertical"
          margin={{ top: 8, right: 24, left: 32, bottom: 8 }}
        >
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis type="number" />
          <YAxis type="category" dataKey="name" width={180} />
          <Tooltip />
          <Bar dataKey="workers" fill="#6366f1" />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
