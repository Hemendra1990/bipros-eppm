"use client";

import type { LabourDesignationResponse } from "@/lib/api/labourMasterApi";
import { CATEGORY_ACCENT, GRADE_BADGE, formatOMR } from "./labourMasterTokens";

type Props = {
  rows: LabourDesignationResponse[];
  onRowClick?: (d: LabourDesignationResponse) => void;
};

export function WorkerTable({ rows, onRowClick }: Props) {
  return (
    <div className="overflow-hidden rounded-xl border border-hairline bg-paper">
      <div className="overflow-auto">
        <table className="min-w-full text-[13px]">
          <thead className="bg-ivory">
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
                <th
                  key={h}
                  className="px-3 py-2.5 font-semibold text-[11px] uppercase tracking-[0.10em] text-slate whitespace-nowrap"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-hairline">
            {rows.map((d) => {
              const accent = CATEGORY_ACCENT[d.category];
              const dailyRate = d.deployment?.effectiveRate ?? d.defaultDailyRate;
              const workerCount = d.deployment?.workerCount ?? 0;
              return (
                <tr
                  key={d.id}
                  onClick={onRowClick ? () => onRowClick(d) : undefined}
                  className="cursor-pointer transition hover:bg-ivory/60"
                >
                  <td className="px-3 py-2.5 font-mono text-[12px] text-gold-ink whitespace-nowrap">{d.code}</td>
                  <td className="px-3 py-2.5 font-medium text-charcoal">{d.designation}</td>
                  <td className="px-3 py-2.5">
                    <span className={`inline-flex items-center rounded-md border px-2 py-0.5 text-[11px] font-medium ${accent.chip}`}>
                      {d.categoryDisplay}
                    </span>
                  </td>
                  <td className="px-3 py-2.5 text-slate">{d.trade}</td>
                  <td className="px-3 py-2.5">
                    <span className={`inline-flex items-center rounded border px-2 py-0.5 text-[11px] font-semibold ${GRADE_BADGE[d.grade]}`}>
                      {d.grade}
                    </span>
                  </td>
                  <td className="px-3 py-2.5 font-display text-[15px] font-semibold text-charcoal">{workerCount}</td>
                  <td className="px-3 py-2.5 text-slate">{d.experienceYearsMin}+ yrs</td>
                  <td className="px-3 py-2.5 font-display text-[15px] font-semibold text-gold-deep">{formatOMR(dailyRate)}</td>
                  <td className="px-3 py-2.5 text-slate">{d.nationality.replace(/_/g, " / ")}</td>
                  <td className="px-3 py-2.5">
                    <span
                      className={`inline-flex items-center rounded-md border px-2 py-0.5 text-[11px] font-medium ${
                        d.status === "ACTIVE"
                          ? "border-emerald/30 bg-emerald/10 text-emerald"
                          : "border-hairline bg-ivory text-ash"
                      }`}
                    >
                      {d.status}
                    </span>
                  </td>
                </tr>
              );
            })}
            {rows.length === 0 && (
              <tr>
                <td colSpan={10} className="px-3 py-10 text-center text-slate text-[13px]">
                  No designations match the current filters.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
