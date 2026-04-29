"use client";

import type { LabourCategorySummary } from "@/lib/api/labourMasterApi";
import { formatOMR } from "./labourMasterTokens";

type Props = { rows: LabourCategorySummary[] };

export function WorkforceSummaryTable({ rows }: Props) {
  const totalDesigs = rows.reduce((a, r) => a + r.designationCount, 0);
  const totalWorkers = rows.reduce((a, r) => a + r.workerCount, 0);
  const totalCost = rows.reduce((a, r) => a + r.dailyCost, 0);
  return (
    <div className="overflow-auto rounded-lg border bg-white">
      <table className="min-w-full text-sm">
        <thead className="bg-slate-50">
          <tr className="text-left">
            {[
              "Category",
              "Total Designations",
              "Total Workers",
              "Grade Range",
              "Daily Rate Range (OMR)",
              "Daily Cost (OMR)",
              "Key Roles",
            ].map((h) => (
              <th key={h} className="px-3 py-2 font-semibold">{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.category} className="border-t">
              <td className="px-3 py-2 font-medium">{r.categoryDisplay}</td>
              <td className="px-3 py-2">{r.designationCount}</td>
              <td className="px-3 py-2">{r.workerCount}</td>
              <td className="px-3 py-2">{r.gradeRange}</td>
              <td className="px-3 py-2">{r.dailyRateRange}</td>
              <td className="px-3 py-2">{formatOMR(r.dailyCost)}</td>
              <td className="px-3 py-2 text-xs">{r.keyRolesSummary}</td>
            </tr>
          ))}
          <tr className="border-t bg-slate-50 font-semibold">
            <td className="px-3 py-2">TOTAL</td>
            <td className="px-3 py-2">{totalDesigs}</td>
            <td className="px-3 py-2">{totalWorkers}</td>
            <td className="px-3 py-2">A – E</td>
            <td className="px-3 py-2">—</td>
            <td className="px-3 py-2">{formatOMR(totalCost)}</td>
            <td className="px-3 py-2 text-xs">{rows.length} categories</td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}
