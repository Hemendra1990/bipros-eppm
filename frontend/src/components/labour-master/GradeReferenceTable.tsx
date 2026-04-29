"use client";

import type { LabourGradeReference } from "@/lib/api/labourMasterApi";
import { GRADE_BADGE } from "./labourMasterTokens";

type Props = { rows: LabourGradeReference[]; regulatoryNotes: string[] };

export function GradeReferenceTable({ rows, regulatoryNotes }: Props) {
  return (
    <div className="space-y-6">
      <section className="overflow-hidden rounded-xl border border-hairline bg-paper">
        <header className="flex items-end justify-between border-b border-hairline bg-ivory/60 px-4 py-3">
          <div>
            <div className="text-[10px] font-semibold uppercase tracking-[0.12em] text-slate">
              Sultanate of Oman
            </div>
            <h3 className="font-display text-[18px] font-semibold text-charcoal">
              Grade reference (A – E)
            </h3>
          </div>
        </header>
        <div className="overflow-auto">
          <table className="min-w-full text-[13px]">
            <thead className="bg-ivory">
              <tr className="text-left">
                {["Grade", "Classification", "Daily rate", "Description"].map((h) => (
                  <th
                    key={h}
                    className="px-4 py-2.5 font-semibold text-[11px] uppercase tracking-[0.10em] text-slate whitespace-nowrap"
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-hairline">
              {rows.map((g) => (
                <tr key={g.grade} className="hover:bg-ivory/40 transition align-top">
                  <td className="px-4 py-3">
                    <span className={`inline-flex items-center justify-center rounded-md border w-9 py-1 text-[12px] font-bold ${GRADE_BADGE[g.grade]}`}>
                      {g.grade}
                    </span>
                  </td>
                  <td className="px-4 py-3 font-medium text-charcoal">{g.classification}</td>
                  <td className="px-4 py-3 font-display text-[14px] text-gold-deep whitespace-nowrap">{g.dailyRateRange}</td>
                  <td className="px-4 py-3 text-slate">{g.description}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="rounded-xl border border-hairline bg-paper p-5">
        <header className="mb-3">
          <div className="text-[10px] font-semibold uppercase tracking-[0.12em] text-slate">
            Compliance
          </div>
          <h3 className="font-display text-[18px] font-semibold text-charcoal">
            Regulatory & compliance notes — Sultanate of Oman
          </h3>
        </header>
        <ul className="space-y-2">
          {regulatoryNotes.map((n, i) => (
            <li key={i} className="flex items-start gap-3 text-[13px] text-charcoal">
              <span className="mt-2 h-1.5 w-1.5 flex-none rounded-full bg-gold" />
              <span className="leading-relaxed text-slate">{n}</span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
