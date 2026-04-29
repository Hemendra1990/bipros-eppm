"use client";

import type { LabourDesignationResponse } from "@/lib/api/labourMasterApi";
import { GRADE_BADGE, formatOMR } from "./labourMasterTokens";

type Props = {
  rows: LabourDesignationResponse[];
  onRowClick?: (d: LabourDesignationResponse) => void;
};

export function WorkerTable({ rows, onRowClick }: Props) {
  return (
    <div className="overflow-auto rounded-lg border bg-white">
      <table className="min-w-full text-sm">
        <thead className="bg-slate-50">
          <tr className="text-left">
            {[
              "Code",
              "Designation",
              "Category",
              "Trade",
              "Grade",
              "Count",
              "Experience",
              "Daily Rate (OMR)",
              "Nationality",
              "Status",
            ].map((h) => (
              <th key={h} className="px-3 py-2 font-semibold text-slate-700">{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((d) => (
            <tr
              key={d.id}
              onClick={onRowClick ? () => onRowClick(d) : undefined}
              className="border-t hover:bg-slate-50 cursor-pointer"
            >
              <td className="px-3 py-2 font-mono">{d.code}</td>
              <td className="px-3 py-2">{d.designation}</td>
              <td className="px-3 py-2">{d.categoryDisplay}</td>
              <td className="px-3 py-2">{d.trade}</td>
              <td className="px-3 py-2">
                <span className={`px-2 py-0.5 rounded text-xs ${GRADE_BADGE[d.grade]}`}>
                  {d.grade}
                </span>
              </td>
              <td className="px-3 py-2">{d.deployment?.workerCount ?? 0}</td>
              <td className="px-3 py-2">{d.experienceYearsMin}+ yrs</td>
              <td className="px-3 py-2">
                {formatOMR(d.deployment?.effectiveRate ?? d.defaultDailyRate)}
              </td>
              <td className="px-3 py-2">{d.nationality.replace("_", " / ")}</td>
              <td className="px-3 py-2">{d.status}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
