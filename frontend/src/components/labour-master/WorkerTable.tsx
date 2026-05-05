"use client";

import type { ColumnDef } from "@tanstack/react-table";
import type { LabourDesignationResponse } from "@/lib/api/labourMasterApi";
import { VirtualDataTable } from "@/components/common/VirtualDataTable";
import { CATEGORY_ACCENT, GRADE_BADGE, formatOMR } from "./labourMasterTokens";

type Props = {
  rows: LabourDesignationResponse[];
  onRowClick?: (d: LabourDesignationResponse) => void;
};

export function WorkerTable({ rows, onRowClick }: Props) {
  const columns: ColumnDef<LabourDesignationResponse>[] = [
    {
      accessorKey: "code",
      header: "Code",
      cell: ({ row }) => (
        <span className="font-mono text-[12px] text-gold-ink whitespace-nowrap">
          {row.original.code}
        </span>
      ),
    },
    {
      accessorKey: "designation",
      header: "Designation",
      cell: ({ row }) => (
        <span className="font-medium text-charcoal">
          {row.original.designation}
        </span>
      ),
    },
    {
      accessorKey: "category",
      header: "Category",
      cell: ({ row }) => {
        const accent = CATEGORY_ACCENT[row.original.category];
        return (
          <span
            className={`inline-flex items-center rounded-md border px-2 py-0.5 text-[11px] font-medium ${accent.chip}`}
          >
            {row.original.categoryDisplay}
          </span>
        );
      },
    },
    {
      accessorKey: "trade",
      header: "Trade",
      cell: ({ row }) => <span className="text-slate">{row.original.trade}</span>,
    },
    {
      accessorKey: "grade",
      header: "Grade",
      cell: ({ row }) => (
        <span
          className={`inline-flex items-center rounded border px-2 py-0.5 text-[11px] font-semibold ${GRADE_BADGE[row.original.grade]}`}
        >
          {row.original.grade}
        </span>
      ),
    },
    {
      accessorKey: "deployment.workerCount",
      header: "Count",
      cell: ({ row }) => {
        const workerCount = row.original.deployment?.workerCount ?? 0;
        return (
          <span className="font-display text-[15px] font-semibold text-charcoal">
            {workerCount}
          </span>
        );
      },
    },
    {
      accessorKey: "experienceYearsMin",
      header: "Experience",
      cell: ({ row }) => (
        <span className="text-slate">{row.original.experienceYearsMin}+ yrs</span>
      ),
    },
    {
      accessorKey: "defaultDailyRate",
      header: "Daily Rate (OMR)",
      cell: ({ row }) => {
        const dailyRate =
          row.original.deployment?.effectiveRate ?? row.original.defaultDailyRate;
        return (
          <span className="font-display text-[15px] font-semibold text-gold-deep">
            {formatOMR(dailyRate)}
          </span>
        );
      },
    },
    {
      accessorKey: "nationality",
      header: "Nationality",
      cell: ({ row }) => (
        <span className="text-slate">
          {row.original.nationality.replace(/_/g, " / ")}
        </span>
      ),
    },
    {
      accessorKey: "status",
      header: "Status",
      cell: ({ row }) => (
        <span
          className={`inline-flex items-center rounded-md border px-2 py-0.5 text-[11px] font-medium ${
            row.original.status === "ACTIVE"
              ? "border-emerald/30 bg-emerald/10 text-emerald"
              : "border-hairline bg-ivory text-ash"
          }`}
        >
          {row.original.status}
        </span>
      ),
    },
  ];

  return (
    <VirtualDataTable
      data={rows}
      columns={columns}
      sortable
      searchable
      resizable
      onRowClick={onRowClick}
      emptyMessage="No designations match the current filters."
      className="border-0 rounded-none"
    />
  );
}
