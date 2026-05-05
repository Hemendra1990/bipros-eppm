"use client";

import type { ColumnDef } from "@tanstack/react-table";
import type { LabourCategorySummary } from "@/lib/api/labourMasterApi";
import { SimpleTable } from "@/components/common/SimpleTable";
import { CATEGORY_ACCENT, formatOMR } from "./labourMasterTokens";

type Row =
  | LabourCategorySummary
  | {
      category: "__TOTAL__";
      categoryDisplay: string;
      designationCount: number;
      workerCount: number;
      gradeRange: string;
      dailyRateRange: string;
      dailyCost: number;
      keyRolesSummary: string;
    };

type Props = { rows: LabourCategorySummary[] };

export function WorkforceSummaryTable({ rows }: Props) {
  const totalDesigs = rows.reduce((a, r) => a + r.designationCount, 0);
  const totalWorkers = rows.reduce((a, r) => a + r.workerCount, 0);
  const totalCost = rows.reduce((a, r) => a + r.dailyCost, 0);

  const data: Row[] = [
    ...rows,
    {
      category: "__TOTAL__",
      categoryDisplay: "TOTAL",
      designationCount: totalDesigs,
      workerCount: totalWorkers,
      gradeRange: "A – E",
      dailyRateRange: "—",
      dailyCost: totalCost,
      keyRolesSummary: `${rows.length} categories`,
    },
  ];

  const columns: ColumnDef<Row>[] = [
    {
      accessorKey: "category",
      header: "Category",
      cell: ({ row }) => {
        const r = row.original;
        if (r.category === "__TOTAL__") {
          return (
            <span className="font-medium text-charcoal">
              {r.categoryDisplay}
            </span>
          );
        }
        const accent = CATEGORY_ACCENT[r.category];
        return (
          <span className="flex items-center gap-2">
            <span className={`h-2 w-2 rounded-full ${accent.stripe}`} />
            <span className="font-medium text-charcoal">
              {r.categoryDisplay}
            </span>
          </span>
        );
      },
    },
    {
      accessorKey: "designationCount",
      header: "Designations",
      cell: ({ row }) => (
        <span className="font-display text-[15px] text-charcoal">
          {row.original.designationCount}
        </span>
      ),
    },
    {
      accessorKey: "workerCount",
      header: "Workers",
      cell: ({ row }) => (
        <span className="font-display text-[15px] text-charcoal">
          {row.original.workerCount}
        </span>
      ),
    },
    {
      accessorKey: "gradeRange",
      header: "Grade range",
      cell: ({ row }) => (
        <span className="text-slate">{row.original.gradeRange}</span>
      ),
    },
    {
      accessorKey: "dailyRateRange",
      header: "Daily rate range (OMR)",
      cell: ({ row }) => (
        <span className="text-slate">{row.original.dailyRateRange}</span>
      ),
    },
    {
      accessorKey: "dailyCost",
      header: "Daily cost",
      cell: ({ row }) => (
        <span className="font-display text-[15px] font-semibold text-gold-deep">
          {formatOMR(row.original.dailyCost)}
        </span>
      ),
    },
    {
      accessorKey: "keyRolesSummary",
      header: "Key roles",
      cell: ({ row }) => (
        <span className="text-[12px] text-slate">
          {row.original.keyRolesSummary}
        </span>
      ),
    },
  ];

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
      <SimpleTable
        data={data}
        columns={columns}
        sortable={false}
        className="border-0 rounded-none"
      />
    </div>
  );
}
