"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import type { ColumnDef } from "@tanstack/react-table";
import { Download, PlusCircle } from "lucide-react";
import {
  capacityUtilizationApi,
  type CapacityGroupBy,
  type CapacityNormType,
  type CapacityPeriod,
  type CapacityUtilizationRow,
} from "@/lib/api/capacityUtilizationApi";
import { VirtualDataTable } from "@/components/common/VirtualDataTable";
// import { AiInsightsPanel } from "@/components/ai/AiInsightsPanel";
import { TabTip } from "@/components/common/TabTip";
import { useStickyMeasure } from "@/hooks/useStickyMeasure";
import { SupervisorPerformanceSections } from "@/components/capacity-utilization/SupervisorPerformanceSections";
import { SupervisorComparisonSections } from "@/components/capacity-utilization/SupervisorComparisonSections";

// ─── DIAGNOSTIC KILL SWITCH ─────────────────────────────────────────────────
// Flip to `false` to disable EVERY addition from the SC180 / supervisor-performance
// work and run only the original capacity-utilization grid. If `false` is fast and
// `true` freezes, my new code is the cause. If `false` ALSO freezes, something
// outside my code is the issue (project layout, browser extension, etc.).
const SC180_ENABLED = true;
// ─────────────────────────────────────────────────────────────────────────────

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
        <span className="text-text-muted">Bud days:</span>{" "}
        {fmt(period.budgetedDays)}
      </div>
      <div>
        <span className="text-text-muted">Act days:</span>{" "}
        {fmt(period.actualDays)}
      </div>
      <div>
        <span className="text-text-muted">Act/day:</span>{" "}
        {fmt(period.actualOutputPerDay)}
      </div>
      <div>
        <span
          className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${utilizationBand(period.utilizationPct)}`}
        >
          {period.utilizationPct === null
            ? "—"
            : `${fmt(period.utilizationPct, 1)} %`}
        </span>
      </div>
    </div>
  );
}

type TableRow =
  | { type: "group"; sNo: number; label: string }
  | {
      type: "data";
      workActivity: {
        name: string;
        code: string;
        defaultUnit?: string | null;
      };
      budgeted: { outputPerDay: number | null; source: string };
      forTheDay: CapacityPeriod;
      forTheMonth: CapacityPeriod;
      cumulative: CapacityPeriod;
    };

export default function CapacityUtilizationPage() {
  const params = useParams();
  const projectId = params.projectId as string;

  const [fromDate, setFromDate] = useState(startOfMonth());
  const [toDate, setToDate] = useState(today());
  const [groupBy, setGroupBy] = useState<CapacityGroupBy>("RESOURCE_TYPE");
  const [normType, setNormType] = useState<CapacityNormType | "">("");
  const [supervisorResourceId, setSupervisorResourceId] = useState<string>("");
  const [workDays, setWorkDays] = useState<number>(26);
  const [compareMode, setCompareMode] = useState<boolean>(false);
  const [compareIds, setCompareIds] = useState<string[]>([]);
  const { ref: stickyHeaderRef, height: upperH } =
    useStickyMeasure<HTMLDivElement>();

  const { data: supervisorOptions } = useQuery({
    queryKey: ["supervisors-used", projectId, fromDate, toDate],
    queryFn: () =>
      capacityUtilizationApi.getSupervisorsUsed({
        projectId,
        fromDate,
        toDate,
      }),
    enabled: SC180_ENABLED,
  });

  const { data, isLoading, isError, error } = useQuery({
    queryKey: [
      "capacity-utilization",
      projectId,
      fromDate,
      toDate,
      groupBy,
      normType,
      supervisorResourceId,
    ],
    queryFn: () =>
      capacityUtilizationApi.get({
        projectId,
        fromDate,
        toDate,
        groupBy,
        normType: normType || undefined,
        // Skip the supervisor join when SC180 is disabled — gives the API a chance
        // to return the original (pre-my-changes) result shape and timing.
        supervisorResourceId: SC180_ENABLED ? supervisorResourceId || undefined : undefined,
      }),
  });

  const { data: supervisorPerf } = useQuery({
    queryKey: [
      "supervisor-performance",
      projectId,
      fromDate,
      toDate,
      supervisorResourceId,
      workDays,
    ],
    queryFn: () =>
      capacityUtilizationApi.getSupervisorPerformance({
        projectId,
        supervisorResourceId: supervisorResourceId || undefined,
        fromDate,
        toDate,
        workDays,
      }),
    // Only fetch + render the SC180-style sections when a specific supervisor is picked or
    // Compare mode is active. Project-wide data can be many hundreds of (activity × resource)
    // cells which freezes the browser on render. The existing per-activity grid above still
    // covers project-wide; SC180 is supervisor-scoped on purpose.
    enabled: SC180_ENABLED && !compareMode && !!supervisorResourceId,
  });

  const { data: comparisonData } = useQuery({
    queryKey: [
      "supervisor-performance-compare",
      projectId,
      fromDate,
      toDate,
      compareIds.join(","),
      workDays,
    ],
    queryFn: () =>
      capacityUtilizationApi.compareSupervisorPerformance({
        projectId,
        supervisorResourceIds: compareIds,
        fromDate,
        toDate,
        workDays,
      }),
    enabled: SC180_ENABLED && compareMode && compareIds.length >= 2,
  });

  const rows: CapacityUtilizationRow[] = data?.data?.rows ?? [];

  const tableRows: TableRow[] = useMemo(() => {
    const result: TableRow[] = [];
    const map = new Map<
      string,
      { label: string; rows: CapacityUtilizationRow[] }
    >();
    for (const r of rows) {
      const key =
        r.groupKey.resourceTypeDefId ??
        r.groupKey.resourceId ??
        r.groupKey.displayLabel;
      const bucket = map.get(key) ?? {
        label: r.groupKey.displayLabel,
        rows: [],
      };
      bucket.rows.push(r);
      map.set(key, bucket);
    }
    let sNo = 1;
    for (const [, value] of map) {
      result.push({ type: "group", sNo: sNo++, label: value.label });
      for (const r of value.rows) {
        result.push({
          type: "data",
          workActivity: r.workActivity,
          budgeted: r.budgeted,
          forTheDay: r.forTheDay,
          forTheMonth: r.forTheMonth,
          cumulative: r.cumulative,
        });
      }
    }
    return result;
  }, [rows]);

  const columns = useMemo<ColumnDef<TableRow>[]>(() => [
    {
      accessorKey: "sNo",
      header: "S.No.",
      size: 48,
      cell: ({ row }) => {
        const r = row.original;
        if (r.type === "group") {
          return <span className="font-bold text-text-primary">{r.sNo}</span>;
        }
        return null;
      },
    },
    {
      accessorKey: "workActivity",
      header: "Work Activity",
      cell: ({ row }) => {
        const r = row.original;
        if (r.type === "group") {
          return (
            <span className="font-bold uppercase tracking-wide text-text-primary">
              {r.label}
            </span>
          );
        }
        return (
          <div className="whitespace-normal">
            <div className="text-text-primary">{r.workActivity.name}</div>
            <div className="text-xs text-text-muted font-mono">
              {r.workActivity.code}
            </div>
          </div>
        );
      },
    },
    {
      accessorKey: "budgeted.outputPerDay",
      header: "Norm / Day",
      cell: ({ row }) => {
        const r = row.original;
        if (r.type === "group") return null;
        return (
          <div className="whitespace-normal text-right">
            <div>{fmt(r.budgeted.outputPerDay)}</div>
            <div className="text-xs text-text-muted">
              {r.workActivity.defaultUnit ?? ""}
            </div>
            <div className="text-xs text-text-muted mt-1">
              {r.budgeted.source.replace("_", " ").toLowerCase()}
            </div>
          </div>
        );
      },
    },
    {
      header: "Metrics",
      columns: [
        {
          accessorKey: "forTheDay",
          header: "For the Day",
          cell: ({ row }) => {
            const r = row.original;
            if (r.type === "group") return null;
            return <PeriodCell period={r.forTheDay} />;
          },
        },
        {
          accessorKey: "forTheMonth",
          header: "For the Month",
          cell: ({ row }) => {
            const r = row.original;
            if (r.type === "group") return null;
            return <PeriodCell period={r.forTheMonth} />;
          },
        },
        {
          accessorKey: "cumulative",
          header: "Cumulative",
          cell: ({ row }) => {
            const r = row.original;
            if (r.type === "group") return null;
            return <PeriodCell period={r.cumulative} />;
          },
        },
      ],
    },
  ], []);

  return (
    <div className="p-6">
      {/* <AiInsightsPanel
        projectId={projectId}
        endpoint={`/v1/projects/${projectId}/capacity-utilization/ai/insights`}
      /> */}
      <TabTip
        title="Capacity Utilization"
        description="Mirrors the Plant utilization / Manpower utilization sheets from the Capacity_Utilization workbook. Each row pairs a Work Activity with a Resource (or Resource Type) and shows the budgeted-vs-actual matrix for the day, the month, and cumulative."
      />
      <div className="mb-6">
        <div
          ref={stickyHeaderRef}
          className="sticky top-[var(--tab-nav-h,53px)] z-20 -mx-6 px-6 pt-2 pb-3 bg-ivory border-b border-border"
        >
          <div className="flex flex-wrap items-center justify-between gap-3 mb-3">
            <h1 className="text-3xl font-bold text-text-primary">
              Capacity Utilization
            </h1>
            <div className="flex items-center gap-2">
              <button
                onClick={() =>
                  downloadCsv(
                    `capacity-utilization-${fromDate}-to-${toDate}.csv`,
                    rows,
                    fromDate,
                    toDate,
                  )
                }
                disabled={rows.length === 0}
                className="inline-flex items-center gap-2 px-4 py-2 bg-info/10 text-info ring-1 ring-info/30 rounded-lg hover:bg-info/20 text-sm font-semibold disabled:opacity-40 disabled:cursor-not-allowed"
                title="Download the matrix as CSV (opens in Excel)"
              >
                <Download size={16} />
                Export CSV
              </button>
              <Link
                href={`/projects/${projectId}/daily-outputs`}
                className="inline-flex items-center gap-2 px-4 py-2 bg-accent text-accent-foreground rounded-lg hover:bg-accent-hover text-sm font-semibold"
              >
                <PlusCircle size={16} />
                Record Daily Output
              </Link>
            </div>
          </div>
          <p className="text-sm text-text-muted mb-3">
            This view is computed from <strong>Daily Outputs</strong>. Add a row
            there for each (date × activity × resource) and the metrics below
            populate automatically. The budgeted norm comes from{" "}
            <em>Admin → Productivity Norms</em>.
          </p>

          <div className={`bg-surface/50 p-4 rounded-lg border border-border grid grid-cols-1 ${SC180_ENABLED ? "md:grid-cols-6" : "md:grid-cols-4"} gap-3`}>
            <div>
              <label className="block text-xs font-medium mb-1 text-text-secondary">
                From
              </label>
              <input
                type="date"
                value={fromDate}
                onChange={(e) => setFromDate(e.target.value)}
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
              />
            </div>
            <div>
              <label className="block text-xs font-medium mb-1 text-text-secondary">
                To
              </label>
              <input
                type="date"
                value={toDate}
                onChange={(e) => setToDate(e.target.value)}
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
              />
            </div>
            <div>
              <label className="block text-xs font-medium mb-1 text-text-secondary">
                Group By
              </label>
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
              <label className="block text-xs font-medium mb-1 text-text-secondary">
                Norm Type
              </label>
              <select
                value={normType}
                onChange={(e) =>
                  setNormType(e.target.value as CapacityNormType | "")
                }
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
              >
                <option value="">All</option>
                <option value="EQUIPMENT">Equipment</option>
                <option value="MANPOWER">Manpower</option>
              </select>
            </div>
            {SC180_ENABLED && (
            <div>
              <label className="block text-xs font-medium mb-1 text-text-secondary">
                Supervisor
              </label>
              {compareMode ? (
                <select
                  multiple
                  value={compareIds}
                  onChange={(e) => {
                    const opts = Array.from(e.target.selectedOptions).map(
                      (o) => o.value,
                    );
                    setCompareIds(opts.slice(0, 6));
                  }}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg h-24"
                >
                  {(supervisorOptions?.data ?? []).map((s) => (
                    <option key={s.supervisorResourceId} value={s.supervisorResourceId}>
                      {s.supervisorName} ({s.dprCount})
                    </option>
                  ))}
                </select>
              ) : (
                <select
                  value={supervisorResourceId}
                  onChange={(e) => setSupervisorResourceId(e.target.value)}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                >
                  <option value="">All supervisors (project-wide)</option>
                  {(supervisorOptions?.data ?? []).map((s) => (
                    <option key={s.supervisorResourceId} value={s.supervisorResourceId}>
                      {s.supervisorName} ({s.dprCount} DPRs)
                    </option>
                  ))}
                </select>
              )}
            </div>
            )}
            {SC180_ENABLED && (
            <div>
              <label className="block text-xs font-medium mb-1 text-text-secondary">
                Work days / Compare
              </label>
              <div className="flex gap-2">
                <input
                  type="number"
                  min={1}
                  max={31}
                  value={workDays}
                  onChange={(e) => setWorkDays(Number(e.target.value) || 26)}
                  className="w-20 px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  title="Work days in the period — used to derive Nos from Days"
                />
                <button
                  type="button"
                  onClick={() => {
                    setCompareMode((m) => !m);
                    if (!compareMode) setSupervisorResourceId("");
                    else setCompareIds([]);
                  }}
                  className={`flex-1 px-3 py-2 rounded-lg text-sm font-semibold ${
                    compareMode
                      ? "bg-accent text-accent-foreground"
                      : "bg-surface-hover text-text-primary border border-border"
                  }`}
                  title="Compare 2–6 supervisors side by side"
                >
                  {compareMode ? "Compare ON" : "Compare"}
                </button>
              </div>
            </div>
            )}
          </div>
          <div className="mt-2 text-xs text-text-muted">
            Color band: ≥100% green · 80–99% yellow · &lt;80% red · no norm grey
            {compareMode && compareIds.length < 2 && (
              <span className="ml-3 text-warning">
                Pick at least 2 supervisors to compare.
              </span>
            )}
          </div>
        </div>

        {isLoading && (
          <div className="text-text-muted mt-4">Loading report...</div>
        )}
        {isError && (
          <div className="text-danger mt-4">
            Failed to load: {(error as Error)?.message ?? "unknown error"}
          </div>
        )}

        {!isLoading && !isError && !compareMode && (
          <div className="mt-4">
            <VirtualDataTable
              data={tableRows}
              columns={columns}
              sortable={false}
              searchable={false}
              resizable={false}
              maxHeight="none"
              rowClassName={(row) =>
                row.type === "group" ? "bg-accent/10 text-text-primary" : ""
              }
              emptyMessage="No data in this date range. Record some entries on the Daily Outputs page first."
              className="border-0 rounded-none"
            />
          </div>
        )}

        {SC180_ENABLED && !compareMode && supervisorResourceId && supervisorPerf?.data && (
          <SupervisorPerformanceSections report={supervisorPerf.data} />
        )}

        {SC180_ENABLED && compareMode && comparisonData?.data && (
          <SupervisorComparisonSections comparison={comparisonData.data} />
        )}
      </div>
    </div>
  );
}
