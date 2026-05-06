"use client";

import { useMemo } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import type { LabourGradeReference } from "@/lib/api/labourMasterApi";
import { SimpleTable } from "@/components/common/SimpleTable";
import { GRADE_BADGE } from "./labourMasterTokens";

type Props = { rows: LabourGradeReference[]; regulatoryNotes: string[] };

export function GradeReferenceTable({ rows, regulatoryNotes }: Props) {
  const columns = useMemo<ColumnDef<LabourGradeReference>[]>(() => [
    {
      accessorKey: "grade",
      header: "Grade",
      cell: ({ row }) => (
        <span
          className={`inline-flex items-center justify-center rounded-md border w-9 py-1 text-[12px] font-bold ${GRADE_BADGE[row.original.grade]}`}
        >
          {row.original.grade}
        </span>
      ),
    },
    {
      accessorKey: "classification",
      header: "Classification",
      cell: ({ row }) => (
        <span className="font-medium text-charcoal">
          {row.original.classification}
        </span>
      ),
    },
    {
      accessorKey: "dailyRateRange",
      header: "Daily rate",
      cell: ({ row }) => (
        <span className="font-display text-[14px] text-gold-deep whitespace-nowrap">
          {row.original.dailyRateRange}
        </span>
      ),
    },
    {
      accessorKey: "description",
      header: "Description",
      cell: ({ row }) => (
        <span className="text-slate">{row.original.description}</span>
      ),
    },
  ], []);

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
        <SimpleTable
          data={rows}
          columns={columns}
          sortable={false}
          className="border-0 rounded-none"
        />
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
            <li
              key={i}
              className="flex items-start gap-3 text-[13px] text-charcoal"
            >
              <span className="mt-2 h-1.5 w-1.5 flex-none rounded-full bg-gold" />
              <span className="leading-relaxed text-slate">{n}</span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
