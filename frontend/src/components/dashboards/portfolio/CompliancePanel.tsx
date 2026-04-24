"use client";

import { useQuery } from "@tanstack/react-query";
import { Check, X } from "lucide-react";
import { portfolioReportApi } from "@/lib/api/portfolioReportApi";
import { ragFromScore } from "@/lib/utils/rag";
import {
  EmptyBlock,
  LoadingBlock,
  SectionCard,
  truncate,
} from "@/components/common/dashboard/primitives";

function tick(ok: boolean | null | undefined) {
  if (ok === true) return <Check size={16} className="text-success" strokeWidth={3} />;
  if (ok === false) return <X size={16} className="text-danger" strokeWidth={3} />;
  return <span className="text-text-muted">—</span>;
}

function scoreColor(score: number): string {
  const rag = ragFromScore(score);
  return rag === "GREEN" ? "text-success" : rag === "AMBER" ? "text-warning" : "text-danger";
}

export function CompliancePanel() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-compliance"],
    queryFn: () => portfolioReportApi.getCompliance(),
    staleTime: 60_000,
  });

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

  const rows = data ?? [];
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
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-text-muted">
              <th className="px-2 py-2 font-medium">Project</th>
              <th className="px-2 py-2 text-center font-medium">PFMS</th>
              <th className="px-2 py-2 text-center font-medium">GSTN</th>
              <th className="px-2 py-2 text-center font-medium">GeM</th>
              <th className="px-2 py-2 text-center font-medium">CPPP</th>
              <th className="px-2 py-2 text-center font-medium">PARIVESH</th>
              <th className="px-2 py-2 text-right font-medium">Score</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.projectId} className="border-b border-border/50">
                <td className="px-2 py-2">
                  <div className="flex flex-col">
                    <span className="font-mono text-[10px] text-text-muted">{r.projectCode}</span>
                    <span className="text-text-primary">{truncate(r.projectName, 48)}</span>
                  </div>
                </td>
                <td className="px-2 py-2 text-center">{tick(r.pfmsSanctionOk)}</td>
                <td className="px-2 py-2 text-center">{tick(r.gstnCheckOk)}</td>
                <td className="px-2 py-2 text-center">{tick(r.gemLinkedOk)}</td>
                <td className="px-2 py-2 text-center">{tick(r.cpppPublishedOk)}</td>
                <td className="px-2 py-2 text-center">{tick(r.pariveshClearanceOk)}</td>
                <td className="px-2 py-2 text-right">
                  <span className={`font-semibold ${scoreColor(r.overallScore)}`}>
                    {r.overallScore.toFixed(0)}%
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </SectionCard>
  );
}
