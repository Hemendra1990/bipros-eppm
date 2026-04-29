"use client";

import type { LabourCategorySummary } from "@/lib/api/labourMasterApi";
import { CATEGORY_ACCENT, formatOMR } from "./labourMasterTokens";

type Props = { rows: LabourCategorySummary[] };

export function WorkforceSummaryTable({ rows }: Props) {
  const totalDesigs = rows.reduce((a, r) => a + r.designationCount, 0);
  const totalWorkers = rows.reduce((a, r) => a + r.workerCount, 0);
  const totalCost = rows.reduce((a, r) => a + r.dailyCost, 0);

  return (
    <div className="overflow-hidden rounded-xl border border-hairline bg-paper">
      <header className="flex items-end justify-between border-b border-hairline bg-ivory/60 px-4 py-3">
        <div>
          <div className="text-[10px] font-semibold uppercase tracking-[0.12em] text-slate">
            Summary
          </div>
          <h3 className="font-display text-[18px] font-semibold text-charcoal">
            Workforce by category
          </h3>
        </div>
      </header>
      <div className="overflow-auto">
        <table className="min-w-full text-[13px]">
          <thead className="bg-ivory">
            <tr className="text-left">
              {[
                "Category",
                "Designations",
                "Workers",
                "Grade range",
                "Daily rate range (OMR)",
                "Daily cost",
                "Key roles",
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
            {rows.map((r) => {
              const accent = CATEGORY_ACCENT[r.category];
              return (
                <tr key={r.category} className="hover:bg-ivory/40 transition">
                  <td className="px-3 py-2.5">
                    <span className="flex items-center gap-2">
                      <span className={`h-2 w-2 rounded-full ${accent.stripe}`} />
                      <span className="font-medium text-charcoal">{r.categoryDisplay}</span>
                    </span>
                  </td>
                  <td className="px-3 py-2.5 font-display text-[15px] text-charcoal">{r.designationCount}</td>
                  <td className="px-3 py-2.5 font-display text-[15px] text-charcoal">{r.workerCount}</td>
                  <td className="px-3 py-2.5 text-slate">{r.gradeRange}</td>
                  <td className="px-3 py-2.5 text-slate">{r.dailyRateRange}</td>
                  <td className="px-3 py-2.5 font-display text-[15px] font-semibold text-gold-deep">{formatOMR(r.dailyCost)}</td>
                  <td className="px-3 py-2.5 text-[12px] text-slate">{r.keyRolesSummary}</td>
                </tr>
              );
            })}
            <tr className="border-t-2 border-hairline bg-parchment/40 font-semibold">
              <td className="px-3 py-2.5 text-charcoal">TOTAL</td>
              <td className="px-3 py-2.5 font-display text-[15px] text-charcoal">{totalDesigs}</td>
              <td className="px-3 py-2.5 font-display text-[15px] text-charcoal">{totalWorkers}</td>
              <td className="px-3 py-2.5 text-slate">A – E</td>
              <td className="px-3 py-2.5 text-slate">—</td>
              <td className="px-3 py-2.5 font-display text-[15px] text-gold-deep">{formatOMR(totalCost)}</td>
              <td className="px-3 py-2.5 text-[12px] text-slate">{rows.length} categories</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}
