"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { dailyCostReportApi, type DailyCostReportResponse } from "@/lib/api/dailyCostReportApi";
import { projectApi } from "@/lib/api/projectApi";
import { TabTip } from "@/components/common/TabTip";
import { getErrorMessage } from "@/lib/utils/error";

function formatCurrency(value: number | null): string {
  if (value === null || value === undefined) return "—";
  return value.toLocaleString("en-IN");
}

function formatPercent(value: number | null): string {
  if (value === null || value === undefined) return "—";
  return (value * 100).toFixed(2) + "%";
}

function varianceClass(value: number | null): string {
  if (value === null || value === undefined || value === 0) return "";
  return value > 0 ? "text-danger" : "text-success";
}

export default function DailyCostReportPage() {
  const params = useParams();
  const projectId = params.projectId as string;

  // Date range is data-driven — we wait for the project to load, then seed From/To with the
  // project's planned start/finish so the user sees data on first render regardless of when
  // the project's calendar sits. No hardcoded dates.
  const { data: projectData } = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => projectApi.getProject(projectId),
    enabled: !!projectId,
  });
  const project = projectData?.data;

  const [fromDraft, setFromDraft] = useState<string>("");
  const [toDraft, setToDraft] = useState<string>("");
  const [from, setFrom] = useState<string>("");
  const [to, setTo] = useState<string>("");

  useEffect(() => {
    if (!project) return;
    if (from === "" && project.plannedStartDate) {
      setFromDraft(project.plannedStartDate);
      setFrom(project.plannedStartDate);
    }
    if (to === "" && project.plannedFinishDate) {
      setToDraft(project.plannedFinishDate);
      setTo(project.plannedFinishDate);
    }
  }, [project, from, to]);

  const { data, isLoading, error } = useQuery({
    queryKey: ["daily-cost-report", projectId, from, to],
    queryFn: () => dailyCostReportApi.generate(projectId, { from, to }),
    enabled: !!projectId && !!from && !!to,
  });

  const report: DailyCostReportResponse | undefined = data?.data ?? undefined;
  const rows = report?.rows ?? [];

  const handleApply = () => {
    setFrom(fromDraft);
    setTo(toDraft);
  };

  if (isLoading && !report) {
    return <div className="p-6 text-text-muted">Loading cost report...</div>;
  }

  return (
    <div className="p-6">
      <TabTip
        title="Daily Cost Report"
        description="Cross-joins the Supervisor Daily Report with BOQ-item rates and computes budgeted vs actual cost per day — same formulas as the workbook's Section B."
      />
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-4 text-text-primary">Daily Cost Report</h1>

        {/* Date range filter */}
        <div className="bg-surface/50 p-4 rounded-lg border border-border mb-6 shadow-xl">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 items-end">
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">From</label>
              <input
                type="date"
                value={fromDraft}
                onChange={(e) => setFromDraft(e.target.value)}
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">To</label>
              <input
                type="date"
                value={toDraft}
                onChange={(e) => setToDraft(e.target.value)}
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
              />
            </div>
            <div>
              <button
                onClick={handleApply}
                className="px-4 py-2 bg-accent text-text-primary rounded-lg hover:bg-accent-hover"
              >
                Apply
              </button>
            </div>
          </div>
        </div>

        {error && (
          <div className="text-danger mb-4">
            {getErrorMessage(error, "Failed to load daily cost report")}
          </div>
        )}

        {/* Report Table */}
        <div className="overflow-x-auto">
          <table className="w-full border-collapse border border-border">
            <thead>
              <tr className="bg-surface/80">
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Date</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Activity</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Qty Executed</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Unit</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Budgeted Unit Rate (₹)</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Actual Unit Rate (₹)</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Budgeted Cost (₹)</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Actual Cost (₹)</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Variance (₹)</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Variance %</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Supervisor</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.dprId} className="hover:bg-surface-hover/30 text-text-primary">
                  <td className="border border-border px-4 py-2">{row.date}</td>
                  <td className="border border-border px-4 py-2">{row.activity}</td>
                  <td className="border border-border px-4 py-2 text-right">
                    {row.qtyExecuted.toLocaleString("en-IN")}
                  </td>
                  <td className="border border-border px-4 py-2">{row.unit}</td>
                  <td className="border border-border px-4 py-2 text-right">
                    {formatCurrency(row.budgetedUnitRate)}
                  </td>
                  <td className="border border-border px-4 py-2 text-right">
                    {formatCurrency(row.actualUnitRate)}
                  </td>
                  <td className="border border-border px-4 py-2 text-right">
                    {formatCurrency(row.budgetedCost)}
                  </td>
                  <td className="border border-border px-4 py-2 text-right">
                    {formatCurrency(row.actualCost)}
                  </td>
                  <td className={`border border-border px-4 py-2 text-right ${varianceClass(row.variance)}`}>
                    {formatCurrency(row.variance)}
                  </td>
                  <td className={`border border-border px-4 py-2 text-right ${varianceClass(row.variance)}`}>
                    {formatPercent(row.variancePercent)}
                  </td>
                  <td className="border border-border px-4 py-2">{row.supervisor}</td>
                </tr>
              ))}
              {rows.length === 0 && !isLoading && (
                <tr>
                  <td
                    colSpan={11}
                    className="border border-border px-4 py-6 text-center text-text-muted"
                  >
                    No cost report data for the selected period.
                  </td>
                </tr>
              )}
            </tbody>
            {report && rows.length > 0 && (
              <tfoot className="sticky bottom-0 bg-surface/95 backdrop-blur font-semibold">
                <tr className="text-text-primary">
                  <td className="border border-border px-4 py-2" colSpan={6}>
                    PERIOD TOTAL
                  </td>
                  <td className="border border-border px-4 py-2 text-right">
                    {formatCurrency(report.periodBudgetedCost)}
                  </td>
                  <td className="border border-border px-4 py-2 text-right">
                    {formatCurrency(report.periodActualCost)}
                  </td>
                  <td className={`border border-border px-4 py-2 text-right ${varianceClass(report.periodVariance)}`}>
                    {formatCurrency(report.periodVariance)}
                  </td>
                  <td className={`border border-border px-4 py-2 text-right ${varianceClass(report.periodVariance)}`}>
                    {formatPercent(report.periodVariancePercent)}
                  </td>
                  <td className="border border-border px-4 py-2"></td>
                </tr>
              </tfoot>
            )}
          </table>
        </div>
      </div>
    </div>
  );
}
