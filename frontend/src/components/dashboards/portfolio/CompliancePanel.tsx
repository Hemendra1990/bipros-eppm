"use client";

import { useMemo } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { useQuery } from "@tanstack/react-query";
import { Check, X } from "lucide-react";
import { portfolioReportApi } from "@/lib/api/portfolioReportApi";
import { ragFromScore } from "@/lib/utils/rag";
import { SimpleTable } from "@/components/common/SimpleTable";
import {
  EmptyBlock,
  LoadingBlock,
  SectionCard,
  truncate,
} from "@/components/common/dashboard/primitives";

function tick(ok: boolean | null | undefined) {
  if (ok === true)
    return <Check size={16} className="text-success" strokeWidth={3} />;
  if (ok === false)
    return <X size={16} className="text-danger" strokeWidth={3} />;
  return <span className="text-text-muted">—</span>;
}

function scoreColor(score: number): string {
  const rag = ragFromScore(score);
  return rag === "GREEN"
    ? "text-success"
    : rag === "AMBER"
      ? "text-warning"
      : "text-danger";
}

type Row = {
  projectId: string;
  projectCode: string;
  projectName: string;
  pfmsSanctionOk: boolean | null;
  gstnCheckOk: boolean | null;
  gemLinkedOk: boolean | null;
  cpppPublishedOk: boolean | null;
  pariveshClearanceOk: boolean | null;
  overallScore: number;
};

export function CompliancePanel() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-compliance"],
    queryFn: () => portfolioReportApi.getCompliance(),
    staleTime: 60_000,
  });

  const rows = useMemo(() => (data ?? []) as Row[], [data]);

  const columns = useMemo<ColumnDef<Row>[]>(() => [
    {
      accessorKey: "projectName",
      header: "Project",
      cell: ({ row }) => (
        <div className="flex flex-col">
          <span className="font-mono text-[10px] text-text-muted">
            {row.original.projectCode}
          </span>
          <span className="text-text-primary">
            {truncate(row.original.projectName, 48)}
          </span>
        </div>
      ),
    },
    {
      accessorKey: "pfmsSanctionOk",
      header: "PFMS",
      cell: ({ row }) => (
        <div className="text-center">{tick(row.original.pfmsSanctionOk)}</div>
      ),
    },
    {
      accessorKey: "gstnCheckOk",
      header: "GSTN",
      cell: ({ row }) => (
        <div className="text-center">{tick(row.original.gstnCheckOk)}</div>
      ),
    },
    {
      accessorKey: "gemLinkedOk",
      header: "GeM",
      cell: ({ row }) => (
        <div className="text-center">{tick(row.original.gemLinkedOk)}</div>
      ),
    },
    {
      accessorKey: "cpppPublishedOk",
      header: "CPPP",
      cell: ({ row }) => (
        <div className="text-center">{tick(row.original.cpppPublishedOk)}</div>
      ),
    },
    {
      accessorKey: "pariveshClearanceOk",
      header: "PARIVESH",
      cell: ({ row }) => (
        <div className="text-center">
          {tick(row.original.pariveshClearanceOk)}
        </div>
      ),
    },
    {
      accessorKey: "overallScore",
      header: "Score",
      cell: ({ row }) => (
        <div className="text-right">
          <span className={`font-semibold ${scoreColor(row.original.overallScore)}`}>
            {row.original.overallScore.toFixed(0)}%
          </span>
        </div>
      ),
    },
  ], []);

  if (isLoading)
    return (
      <SectionCard title="Compliance Status">
        <LoadingBlock />
      </SectionCard>
    );
  if (isError)
    return (
      <SectionCard title="Compliance Status">
        <EmptyBlock label="Unavailable" />
      </SectionCard>
    );

  if (rows.length === 0) {
    return (
      <SectionCard title="Compliance Status">
        <EmptyBlock label="No projects" />
      </SectionCard>
    );
  }

  return (
    <SectionCard
      title="Compliance Status"
      subtitle="Regulatory and integration checks per project"
    >
      <SimpleTable
        data={rows}
        columns={columns}
        sortable
        className="border-0 rounded-none"
      />
    </SectionCard>
  );
}
