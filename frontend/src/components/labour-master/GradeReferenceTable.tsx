"use client";

import type { LabourGradeReference } from "@/lib/api/labourMasterApi";

type Props = { rows: LabourGradeReference[]; regulatoryNotes: string[] };

export function GradeReferenceTable({ rows, regulatoryNotes }: Props) {
  return (
    <div className="space-y-6">
      <div className="overflow-auto rounded-lg border bg-white">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-50">
            <tr className="text-left">
              {["Grade", "Classification", "Daily Rate", "Description"].map((h) => (
                <th key={h} className="px-3 py-2 font-semibold">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((g) => (
              <tr key={g.grade} className="border-t align-top">
                <td className="px-3 py-2 font-medium">{g.grade}</td>
                <td className="px-3 py-2">{g.classification}</td>
                <td className="px-3 py-2">{g.dailyRateRange}</td>
                <td className="px-3 py-2">{g.description}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <section>
        <h3 className="font-semibold mb-2">Regulatory & Compliance Notes — Sultanate of Oman</h3>
        <ul className="list-disc pl-5 text-sm space-y-1">
          {regulatoryNotes.map((n, i) => (
            <li key={i}>{n}</li>
          ))}
        </ul>
      </section>
    </div>
  );
}
