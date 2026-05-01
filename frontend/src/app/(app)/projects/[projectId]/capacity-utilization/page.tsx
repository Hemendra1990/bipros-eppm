"use client";

import { Fragment, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Download, PlusCircle } from "lucide-react";
import {
  capacityUtilizationApi,
  type CapacityGroupBy,
  type CapacityNormType,
  type CapacityPeriod,
  type CapacityUtilizationRow,
} from "@/lib/api/capacityUtilizationApi";
import { AiInsightsPanel } from "@/components/ai/AiInsightsPanel";
import { TabTip } from "@/components/common/TabTip";

const today = () => new Date().toISOString().split("T")[0];
const startOfMonth = () => {
  const d = new Date();
  d.setDate(1);
  return d.toISOString().split("T")[0];
};

function fmt(n: number | null, digits = 2): string {
  if (n === null || n === undefined) return "—";
  return n.toLocaleString("en-IN", { maximumFractionDigits: digits });
}

/** Convert the matrix into a CSV blob and trigger a download. Browser-only. */
function downloadCsv(
  filename: string,
  rows: CapacityUtilizationRow[],
  fromDate: string,
  toDate: string,
): void {
  const header = [
    "Group",
    "Activity Code",
    "Activity Name",
    "Unit",
    "Norm/Day",
    "Norm Source",
    "Day Qty",
    "Day Bud Days",
    "Day Act Days",
    "Day Act/Day",
    "Day Util %",
    "Month Qty",
    "Month Bud Days",
    "Month Act Days",
    "Month Act/Day",
    "Month Util %",
    "Cum Qty",
    "Cum Bud Days",
    "Cum Act Days",
    "Cum Act/Day",
    "Cum Util %",
  ];
  const csvRows: string[] = [header.join(",")];
  for (const r of rows) {
    csvRows.push(
      [
        r.groupKey.displayLabel,
        r.workActivity.code,
        r.workActivity.name,
        r.workActivity.defaultUnit ?? "",
        r.budgeted.outputPerDay ?? "",
        r.budgeted.source,
        r.forTheDay.qty ?? "",
        r.forTheDay.budgetedDays ?? "",
        r.forTheDay.actualDays ?? "",
        r.forTheDay.actualOutputPerDay ?? "",
        r.forTheDay.utilizationPct ?? "",
        r.forTheMonth.qty ?? "",
        r.forTheMonth.budgetedDays ?? "",
        r.forTheMonth.actualDays ?? "",
        r.forTheMonth.actualOutputPerDay ?? "",
        r.forTheMonth.utilizationPct ?? "",
        r.cumulative.qty ?? "",
        r.cumulative.budgetedDays ?? "",
        r.cumulative.actualDays ?? "",
        r.cumulative.actualOutputPerDay ?? "",
        r.cumulative.utilizationPct ?? "",
      ]
        .map((v) => {
          const s = String(v ?? "");
          // Quote any field containing comma / quote / newline.
          return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
        })
        .join(","),
    );
  }
  const meta = `# Capacity Utilization · ${fromDate} → ${toDate}\n`;
  const blob = new Blob([meta + csvRows.join("\n")], {
    type: "text/csv;charset=utf-8;",
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

function utilizationBand(util: number | null): string {
  if (util === null) return "bg-surface/30 text-text-muted";
  if (util >= 100) return "bg-success/15 text-success ring-1 ring-success/30";
  if (util >= 80) return "bg-warning/15 text-warning ring-1 ring-warning/30";
  return "bg-danger/15 text-danger ring-1 ring-danger/30";
}

function PeriodCell({ period }: { period: CapacityPeriod }) {
  return (
    <div className="space-y-0.5 text-xs">
      <div>
        <span className="text-text-muted">Qty:</span> {fmt(period.qty)}
      </div>
      <div>
        <span className="text-text-muted">Bud days:</span> {fmt(period.budgetedDays)}
      </div>
      <div>
        <span className="text-text-muted">Act days:</span> {fmt(period.actualDays)}
      </div>
      <div>
        <span className="text-text-muted">Act/day:</span> {fmt(period.actualOutputPerDay)}
      </div>
      <div>
        <span
          className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${utilizationBand(period.utilizationPct)}`}
        >
          {period.utilizationPct === null ? "—" : `${fmt(period.utilizationPct, 1)} %`}
        </span>
      </div>
    </div>
  );
}

export default function CapacityUtilizationPage() {
  const params = useParams();
  const projectId = params.projectId as string;

  const [fromDate, setFromDate] = useState(startOfMonth());
  const [toDate, setToDate] = useState(today());
  const [groupBy, setGroupBy] = useState<CapacityGroupBy>("RESOURCE_TYPE");
  const [normType, setNormType] = useState<CapacityNormType | "">("");

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ["capacity-utilization", projectId, fromDate, toDate, groupBy, normType],
    queryFn: () =>
      capacityUtilizationApi.get({
        projectId,
        fromDate,
        toDate,
        groupBy,
        normType: normType || undefined,
      }),
  });

  const rows: CapacityUtilizationRow[] = data?.data?.rows ?? [];

  // Group rows by groupKey (Resource Type or Specific Resource) so the matrix mirrors the
  // spreadsheet's S.No. → equipment-section → activities layout.
  const groupedRows = (() => {
    const map = new Map<string, { label: string; rows: CapacityUtilizationRow[] }>();
    for (const r of rows) {
      const key = r.groupKey.resourceTypeDefId ?? r.groupKey.resourceId ?? r.groupKey.displayLabel;
      const bucket = map.get(key) ?? { label: r.groupKey.displayLabel, rows: [] };
      bucket.rows.push(r);
      map.set(key, bucket);
    }
    return Array.from(map.entries()).map(([key, value], idx) => ({
      key,
      sNo: idx + 1,
      ...value,
    }));
  })();

  return (
    <div className="p-6">
      <AiInsightsPanel projectId={projectId} endpoint={`/v1/projects/${projectId}/capacity-utilization/ai/insights`} />
      <TabTip
        title="Capacity Utilization"
        description="Mirrors the Plant utilization / Manpower utilization sheets from the Capacity_Utilization workbook. Each row pairs a Work Activity with a Resource (or Resource Type) and shows the budgeted-vs-actual matrix for the day, the month, and cumulative."
      />
      <div className="mb-6">
        <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
          <h1 className="text-3xl font-bold text-text-primary">Capacity Utilization</h1>
          <div className="flex items-center gap-2">
            <button
              onClick={() => downloadCsv(`capacity-utilization-${fromDate}-to-${toDate}.csv`, rows, fromDate, toDate)}
              disabled={rows.length === 0}
              className="inline-flex items-center gap-2 px-4 py-2 bg-info/10 text-info ring-1 ring-info/30 rounded-lg hover:bg-info/20 text-sm font-semibold disabled:opacity-40 disabled:cursor-not-allowed"
              title="Download the matrix as CSV (opens in Excel)"
            >
              <Download size={16} />
              Export CSV
            </button>
            <Link
              href={`/projects/${projectId}/daily-outputs`}
              className="inline-flex items-center gap-2 px-4 py-2 bg-accent text-text-primary rounded-lg hover:bg-accent-hover text-sm font-semibold"
            >
              <PlusCircle size={16} />
              Record Daily Output
            </Link>
          </div>
        </div>
        <p className="text-sm text-text-muted mb-4">
          This view is computed from <strong>Daily Outputs</strong>. Add a row there for each
          (date × activity × resource) and the metrics below populate automatically. The
          budgeted norm comes from{" "}
          <em>Admin → Productivity Norms</em>.
        </p>

        <div className="bg-surface/50 p-4 rounded-lg border border-border mb-4 grid grid-cols-1 md:grid-cols-5 gap-3">
          <div>
            <label className="block text-xs font-medium mb-1 text-text-secondary">From</label>
            <input
              type="date"
              value={fromDate}
              onChange={(e) => setFromDate(e.target.value)}
              className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
            />
          </div>
          <div>
            <label className="block text-xs font-medium mb-1 text-text-secondary">To</label>
            <input
              type="date"
              value={toDate}
              onChange={(e) => setToDate(e.target.value)}
              className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
            />
          </div>
          <div>
            <label className="block text-xs font-medium mb-1 text-text-secondary">Group By</label>
            <select
              value={groupBy}
              onChange={(e) => setGroupBy(e.target.value as CapacityGroupBy)}
              className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
            >
              <option value="RESOURCE_TYPE">Resource Type</option>
              <option value="RESOURCE">Specific Resource</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium mb-1 text-text-secondary">Norm Type</label>
            <select
              value={normType}
              onChange={(e) => setNormType(e.target.value as CapacityNormType | "")}
              className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
            >
              <option value="">All</option>
              <option value="EQUIPMENT">Equipment</option>
              <option value="MANPOWER">Manpower</option>
            </select>
          </div>
          <div className="flex items-end">
            <span className="text-xs text-text-muted">
              Color band: ≥100% green · 80–99% yellow · &lt;80% red · no norm grey
            </span>
          </div>
        </div>

        {isLoading && <div className="text-text-muted">Loading report...</div>}
        {isError && (
          <div className="text-danger">
            Failed to load: {(error as Error)?.message ?? "unknown error"}
          </div>
        )}

        {!isLoading && !isError && (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse border border-border text-sm">
              <thead>
                <tr className="bg-surface/80">
                  <th className="border border-border px-3 py-2 text-center text-text-secondary w-12" rowSpan={2}>S.No.</th>
                  <th className="border border-border px-3 py-2 text-left text-text-secondary" rowSpan={2}>
                    Work Activity
                  </th>
                  <th className="border border-border px-3 py-2 text-right text-text-secondary" rowSpan={2}>
                    Norm / Day
                  </th>
                  <th className="border border-border px-3 py-2 text-center text-text-secondary" colSpan={3}>
                    Metrics
                  </th>
                </tr>
                <tr className="bg-surface/80">
                  <th className="border border-border px-3 py-1 text-left text-text-secondary">For the Day</th>
                  <th className="border border-border px-3 py-1 text-left text-text-secondary">For the Month</th>
                  <th className="border border-border px-3 py-1 text-left text-text-secondary">Cumulative</th>
                </tr>
              </thead>
              <tbody>
                {groupedRows.map((group) => (
                  <Fragment key={`g-${group.key}`}>
                    <tr className="bg-accent/10 text-text-primary">
                      <td className="border border-border px-3 py-2 text-center font-bold">{group.sNo}</td>
                      <td className="border border-border px-3 py-2 font-bold uppercase tracking-wide" colSpan={5}>
                        {group.label}
                      </td>
                    </tr>
                    {group.rows.map((r, idx) => (
                      <tr
                        key={`${group.key}-${r.workActivity.id}-${idx}`}
                        className="hover:bg-surface-hover/30 text-text-primary align-top"
                      >
                        <td className="border border-border px-3 py-2"></td>
                        <td className="border border-border px-3 py-2">
                          <div>{r.workActivity.name}</div>
                          <div className="text-xs text-text-muted font-mono">{r.workActivity.code}</div>
                        </td>
                        <td className="border border-border px-3 py-2 text-right">
                          <div>{fmt(r.budgeted.outputPerDay)}</div>
                          <div className="text-xs text-text-muted">
                            {r.workActivity.defaultUnit ?? ""}
                          </div>
                          <div className="text-xs text-text-muted mt-1">
                            {r.budgeted.source.replace("_", " ").toLowerCase()}
                          </div>
                        </td>
                        <td className="border border-border px-3 py-2">
                          <PeriodCell period={r.forTheDay} />
                        </td>
                        <td className="border border-border px-3 py-2">
                          <PeriodCell period={r.forTheMonth} />
                        </td>
                        <td className="border border-border px-3 py-2">
                          <PeriodCell period={r.cumulative} />
                        </td>
                      </tr>
                    ))}
                  </Fragment>
                ))}
                {rows.length === 0 && (
                  <tr>
                    <td
                      colSpan={6}
                      className="border border-border px-4 py-8 text-center text-text-muted"
                    >
                      No data in this date range. Record some entries on the{" "}
                      <em>Daily Outputs</em> page first.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
