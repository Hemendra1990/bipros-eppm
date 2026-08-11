"use client";

import { memo, Suspense, useMemo, useState } from "react";
import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { AlertTriangle, FileSpreadsheet, PlusCircle } from "lucide-react";
import toast from "react-hot-toast";
import {
  capacityUtilizationApi,
  type CapacityGroupBy,
  type CapacityNormType,
  type CapacityPeriod,
  type CapacitySection,
  type CapacityUtilizationRow,
} from "@/lib/api/capacityUtilizationApi";
import { activityApi } from "@/lib/api/activityApi";
import { TabTip } from "@/components/common/TabTip";
import { MultiSelect } from "@/components/common/MultiSelect";
import { SearchableSelect, type SelectOption } from "@/components/common/SearchableSelect";
import { SupervisorPerformanceSections } from "@/components/capacity-utilization/SupervisorPerformanceSections";
import { SupervisorComparisonSections } from "@/components/capacity-utilization/SupervisorComparisonSections";
import { CapacityTrendStrip } from "@/components/capacity-utilization/CapacityTrendStrip";
import { PeriodCell as RolePeriodCellShared, utilBand } from "@/components/capacity/PeriodCell";
import {
  hiddenSideSentence,
  SHOW_HIDDEN_SIDE_NOTES,
  type CapacitySide,
} from "@/lib/capacity/reconciliation";
import { useProjectCurrency } from "@/lib/currency/ProjectCurrencyProvider";

const today = () => new Date().toISOString().split("T")[0];
const startOfMonth = () => {
  const d = new Date();
  d.setDate(1);
  return d.toISOString().split("T")[0];
};

function fmt(n: number | null | undefined, digits = 2): string {
  if (n === null || n === undefined) return "—";
  return n.toLocaleString("en-IN", { maximumFractionDigits: digits });
}

const PeriodCell = memo(function PeriodCell({
  period,
}: {
  period: CapacityPeriod;
}) {
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
          className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${utilBand(period.utilizationPct)}`}
        >
          {period.utilizationPct === null
            ? "—"
            : `${fmt(period.utilizationPct, 1)} %`}
        </span>
      </div>
    </div>
  );
});

// ─── SC180-style Summary table ─────────────────────────────────────────────────────────────
// One row per Role with three time buckets (Day · Month · Cumulative), each carrying the SC180
// column set: Budget Days·Nos · Planned Days · Actual Days·Nos · %Util · Cost Implication.

const RolePeriodCell = RolePeriodCellShared;

const Sc180SectionTable = memo(function Sc180SectionTable({
  title,
  section,
  side,
}: {
  title: string;
  section: CapacitySection | null;
  side: CapacitySide;
}) {
  const { money } = useProjectCurrency();
  if (!section || section.rows.length === 0) {
    return (
      <div className="rounded-xl border border-border bg-paper px-6 py-6 text-center text-sm text-text-muted">
        No {title.toLowerCase()} data in this date range.
      </div>
    );
  }
  return (
    <div className="rounded-xl border border-border bg-paper overflow-hidden mb-6">
      <div className="bg-ivory border-b border-border px-4 py-3">
        <h3 className="font-semibold text-text-primary uppercase tracking-wide text-sm">
          {title}
        </h3>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-sm border-collapse">
          <thead className="bg-ivory border-b border-border">
            <tr>
              <th className="px-4 py-2 text-left text-[11px] font-semibold uppercase tracking-wide text-text-secondary">
                Role
              </th>
              <th className="px-4 py-2 text-right text-[11px] font-semibold uppercase tracking-wide text-text-secondary">
                Rate / Day
              </th>
              <th className="px-4 py-2 text-left text-[11px] font-semibold uppercase tracking-wide text-text-secondary border-l border-border">
                For the Day
              </th>
              <th className="px-4 py-2 text-left text-[11px] font-semibold uppercase tracking-wide text-text-secondary">
                For the Month
              </th>
              <th className="px-4 py-2 text-left text-[11px] font-semibold uppercase tracking-wide text-text-secondary">
                Cumulative
              </th>
            </tr>
          </thead>
          <tbody>
            {section.rows.map((r, i) => (
              <tr key={`${r.roleId ?? "no-role"}-${i}`} className="border-t border-border/50 hover:bg-surface/30">
                <td className="px-4 py-3 align-top">
                  <div className="text-text-primary">{r.roleName ?? "(role)"}</div>
                  {r.roleCode && (
                    <div className="text-xs text-text-muted font-mono">{r.roleCode}</div>
                  )}
                  {r.normSource === "UNSCOPED" ? null : (
                    <div className="text-xs text-text-muted mt-1">
                      {r.normSource === "NONE"
                        ? "Productivity not tracked on this activity"
                        : `Norm: ${r.normSource.toLowerCase()}`}
                    </div>
                  )}
                </td>
                <td className="px-4 py-3 align-top text-right text-xs">
                  {/* Show real rate precision — sub-1 rates (e.g. 0.58 OMR/day) must not
                      round to "1", or the cost-implication arithmetic looks wrong on screen. */}
                  {r.ratePerDay == null ? "—" : money(r.ratePerDay, { decimals: 2 })}
                </td>
                <td className="px-4 py-3 align-top border-l border-border">
                  <RolePeriodCell period={r.forTheDay} side={side} />
                </td>
                <td className="px-4 py-3 align-top">
                  <RolePeriodCell period={r.forTheMonth} side={side} />
                </td>
                <td className="px-4 py-3 align-top">
                  <RolePeriodCell period={r.cumulative} side={side} />
                </td>
              </tr>
            ))}
            <tr className="bg-ivory/60 border-t border-border font-semibold">
              <td className="px-4 py-2 text-text-primary">Total</td>
              <td />
              <td className="px-4 py-2 border-l border-border">
                <RolePeriodCell period={section.totalForTheDay} side={side} />
              </td>
              <td className="px-4 py-2">
                <RolePeriodCell period={section.totalForTheMonth} side={side} />
              </td>
              <td className="px-4 py-2">
                <RolePeriodCell period={section.totalCumulative} side={side} />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      {SHOW_HIDDEN_SIDE_NOTES && section.hiddenSideNotes && section.hiddenSideNotes.length > 0 && (
        <div className="mt-2 mx-4 mb-4 rounded border border-warning/30 bg-warning/5 px-3 py-2 text-xs text-text-muted space-y-1">
          {section.hiddenSideNotes.map((n) => (
            <div key={n.activityId}>
              <span className="font-medium text-warning">
                {n.workActivityName ?? "Activity"}
              </span>
              {hiddenSideSentence(n.mode, n.governingSide)}
            </div>
          ))}
        </div>
      )}
    </div>
  );
});

interface GroupedRow {
  label: string;
  rows: CapacityUtilizationRow[];
}

const MAX_VISIBLE_ROWS = 200;

const ResultTable = memo(function ResultTable({
  groups,
  totalRows,
}: {
  groups: GroupedRow[];
  totalRows: number;
}) {
  const overflow = totalRows - MAX_VISIBLE_ROWS;
  if (groups.length === 0) {
    return (
      <div className="rounded-xl border border-border bg-paper px-6 py-8 text-center text-sm text-text-muted">
        No data in this date range. Record some entries on the Daily Outputs page first.
      </div>
    );
  }
  return (
    <div className="rounded-xl border border-border bg-paper overflow-hidden">
      {overflow > 0 && (
        <div className="px-3 py-2 bg-warning/10 border-b border-warning/30 text-xs text-warning">
          Showing first {MAX_VISIBLE_ROWS} of {totalRows} rows. Narrow the date range or filter.
        </div>
      )}
      <div className="overflow-x-auto">
        <table className="w-full text-sm border-collapse">
          <thead className="bg-ivory border-b border-border">
            <tr>
              <th className="px-4 py-3 text-left text-[11px] font-semibold uppercase tracking-wide text-text-secondary w-12">
                S.No.
              </th>
              <th className="px-4 py-3 text-left text-[11px] font-semibold uppercase tracking-wide text-text-secondary">
                Work Activity
              </th>
              <th className="px-4 py-3 text-right text-[11px] font-semibold uppercase tracking-wide text-text-secondary">
                Norm / Day
              </th>
              <th
                colSpan={3}
                className="px-4 py-3 text-center text-[11px] font-semibold uppercase tracking-wide text-text-secondary border-l border-border"
              >
                Metrics
              </th>
            </tr>
            <tr className="border-b border-border">
              <th />
              <th />
              <th />
              <th className="px-4 py-2 text-left text-[11px] font-semibold text-text-secondary border-l border-border">
                For the Day
              </th>
              <th className="px-4 py-2 text-left text-[11px] font-semibold text-text-secondary">
                For the Month
              </th>
              <th className="px-4 py-2 text-left text-[11px] font-semibold text-text-secondary">
                Cumulative
              </th>
            </tr>
          </thead>
          <tbody>
            {groups.map((g, gi) => (
              <GroupSection key={`${gi}-${g.label}`} group={g} sNo={gi + 1} />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
});

const GroupSection = memo(function GroupSection({
  group,
  sNo,
}: {
  group: GroupedRow;
  sNo: number;
}) {
  return (
    <>
      <tr className="bg-accent/10">
        <td className="px-4 py-2 font-bold text-text-primary">{sNo}</td>
        <td
          colSpan={5}
          className="px-4 py-2 font-bold uppercase tracking-wide text-text-primary"
        >
          {group.label}
        </td>
      </tr>
      {group.rows.map((r, i) => (
        <DataRow key={`${r.workActivity?.id ?? r.groupKey.displayLabel}-${i}`} row={r} />
      ))}
    </>
  );
});

const DataRow = memo(function DataRow({
  row,
}: {
  row: CapacityUtilizationRow;
}) {
  return (
    <tr className="border-t border-border/50 hover:bg-surface/30">
      <td />
      <td className="px-4 py-3 align-top">
        <div className="text-text-primary">{row.workActivity?.name ?? row.groupKey.displayLabel}</div>
        <div className="text-xs text-text-muted font-mono">
          {row.workActivity?.code ?? ""}
        </div>
      </td>
      <td className="px-4 py-3 align-top text-right">
        <div>{fmt(row.budgeted.outputPerDay)}</div>
        <div className="text-xs text-text-muted">
          {row.workActivity?.defaultUnit ?? ""}
        </div>
        <div className="text-xs text-text-muted mt-1">
          {row.budgeted.source.replace("_", " ").toLowerCase()}
        </div>
      </td>
      <td className="px-4 py-3 align-top border-l border-border">
        <PeriodCell period={row.forTheDay} />
      </td>
      <td className="px-4 py-3 align-top">
        <PeriodCell period={row.forTheMonth} />
      </td>
      <td className="px-4 py-3 align-top">
        <PeriodCell period={row.cumulative} />
      </td>
    </tr>
  );
});

function CapacityUtilizationPageInner() {
  const params = useParams();
  const projectId = params.projectId as string;
  const searchParams = useSearchParams();

  const [fromDate, setFromDate] = useState(startOfMonth());
  const [toDate, setToDate] = useState(today());
  const [groupBy, setGroupBy] = useState<CapacityGroupBy>("RESOURCE_TYPE");
  const [normType, setNormType] = useState<CapacityNormType | "">("");
  // Seeded from ?supervisorUserId= so the AI Insights supervisor rows can deep-link straight into
  // that supervisor's breakdown; the dropdown owns it from then on.
  const [supervisorUserId, setSupervisorUserId] = useState<string>(
    () => searchParams.get("supervisorUserId") ?? "",
  );
  const [workDays, setWorkDays] = useState<number>(26);
  const [compareMode, setCompareMode] = useState<boolean>(false);
  const [compareIds, setCompareIds] = useState<string[]>([]);
  // "" = project-wide; an activity id narrows the comparison to that one activity
  // ("comparison between the supervisors for the same activity").
  const [compareActivityId, setCompareActivityId] = useState<string>("");
  const [generating, setGenerating] = useState<boolean>(false);

  // Client-format Excel workbook (Plant / Manpower utilization + SUMMARY) for the current
  // filter window — the printable monthly report from the client's Capacity_Utilization
  // template. Same numbers as this page (same backend accumulators), pivoted resource → activity.
  const generateReport = async () => {
    setGenerating(true);
    try {
      const response = await capacityUtilizationApi.downloadClientWorkbook({
        projectId,
        fromDate,
        toDate,
        workDays,
        supervisorUserId: supervisorUserId || undefined,
      });
      const blob =
        response.data instanceof Blob
          ? response.data
          : new Blob([response.data], {
              type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `capacity-utilization-${fromDate}-to-${toDate}.xlsx`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      toast.success("Capacity Utilization report downloaded");
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Report generation failed";
      toast.error(msg);
    } finally {
      setGenerating(false);
    }
  };

  const { data: supervisorOptions } = useQuery({
    queryKey: ["supervisors-used", projectId, fromDate, toDate],
    queryFn: () =>
      capacityUtilizationApi.getSupervisorsUsed({
        projectId,
        fromDate,
        toDate,
      }),
    placeholderData: keepPreviousData,
  });

  // Activities in this window with no Work Activity linked. Their DPRs are silently excluded
  // by the capacity-utilization SQL (INNER JOIN on work_activities), so we surface a banner
  // that lets the user jump back and fix the link.
  const { data: missingWaData } = useQuery({
    queryKey: ["missing-work-activity", projectId, fromDate, toDate],
    queryFn: () => activityApi.listMissingWorkActivity(projectId, fromDate, toDate),
    placeholderData: keepPreviousData,
  });
  const missingWa = missingWaData?.data ?? [];

  const { data, isLoading, isError, error } = useQuery({
    queryKey: [
      "capacity-utilization",
      projectId,
      fromDate,
      toDate,
      groupBy,
      normType,
      supervisorUserId,
    ],
    queryFn: () =>
      capacityUtilizationApi.get({
        projectId,
        fromDate,
        toDate,
        groupBy,
        normType: normType || undefined,
        supervisorUserId: supervisorUserId || undefined,
      }),
    placeholderData: keepPreviousData,
  });

  const { data: supervisorPerf } = useQuery({
    queryKey: [
      "supervisor-performance",
      projectId,
      fromDate,
      toDate,
      supervisorUserId,
      workDays,
    ],
    queryFn: () =>
      capacityUtilizationApi.getSupervisorPerformance({
        projectId,
        supervisorUserId: supervisorUserId || undefined,
        fromDate,
        toDate,
        workDays,
      }),
    enabled: !compareMode && !!supervisorUserId,
    placeholderData: keepPreviousData,
  });

  const { data: comparisonData } = useQuery({
    queryKey: [
      "supervisor-performance-compare",
      projectId,
      fromDate,
      toDate,
      compareIds.join(","),
      compareActivityId,
      workDays,
    ],
    queryFn: () =>
      capacityUtilizationApi.compareSupervisorPerformance({
        projectId,
        supervisorUserIds: compareIds,
        activityId: compareActivityId || undefined,
        fromDate,
        toDate,
        workDays,
      }),
    enabled: compareMode && compareIds.length >= 2,
    placeholderData: keepPreviousData,
  });

  // Activity options for the compare scope — loaded only once compare is switched on.
  const { data: activitiesData } = useQuery({
    queryKey: ["activities-for-compare", projectId],
    queryFn: () => activityApi.listActivities(projectId, 0, 500),
    enabled: compareMode,
    placeholderData: keepPreviousData,
  });

  const rows = data?.data?.rows;

  const groups: GroupedRow[] = useMemo(() => {
    if (!rows || rows.length === 0) return [];
    const map = new Map<string, GroupedRow>();
    let count = 0;
    for (const r of rows) {
      if (count >= MAX_VISIBLE_ROWS) break;
      const key =
        r.groupKey.resourceTypeDefId ??
        r.groupKey.resourceId ??
        r.groupKey.displayLabel;
      const bucket = map.get(key) ?? { label: r.groupKey.displayLabel, rows: [] };
      bucket.rows.push(r);
      map.set(key, bucket);
      count++;
    }
    return Array.from(map.values());
  }, [rows]);

  const totalRows = rows?.length ?? 0;
  const supervisors = supervisorOptions?.data ?? [];

  const activityOptions: SelectOption[] = [
    { value: "", label: "All activities (project-wide)" },
    ...(activitiesData?.data?.content ?? []).map((a) => ({
      value: a.id,
      label: `${a.code} — ${a.name}`,
    })),
  ];

  const supervisorSelectOptions: SelectOption[] = [
    { value: "", label: "All supervisors (project-wide)" },
    // This page filters strictly by user id, so an entry without one is unusable. The endpoint
    // only returns those under includeUnlinked, which this page doesn't request.
    ...supervisors
      .filter((s) => s.supervisorUserId !== null)
      .map((s) => ({
        value: s.supervisorUserId as string,
        label: `${s.supervisorName} (${s.dprCount} DPRs)`,
      })),
  ];

  return (
    <div className="p-6">
      <TabTip
        title="Capacity Utilization"
        description="Mirrors the Plant utilization / Manpower utilization sheets from the Capacity_Utilization workbook. Each row pairs a Work Activity with a Resource (or Resource Type) and shows the budgeted-vs-actual matrix for the day, the month, and cumulative."
      />
      <div className="mb-6">
        <div className="-mx-6 px-6 pt-2 pb-3 bg-ivory border-b border-border mb-4">
          <div className="flex flex-wrap items-center justify-between gap-3 mb-3">
            <h1 className="text-3xl font-bold text-text-primary">
              Capacity Utilization
            </h1>
            <div className="flex items-center gap-2">
              <button
                onClick={generateReport}
                disabled={generating}
                className="inline-flex items-center gap-2 px-4 py-2 bg-info/10 text-info ring-1 ring-info/30 rounded-lg hover:bg-info/20 text-sm font-semibold disabled:opacity-40 disabled:cursor-not-allowed"
                title="Generate the client-format Resource Capacity Utilization workbook (Plant / Manpower utilization + Summary) for the current filters"
              >
                <FileSpreadsheet size={16} />
                {generating ? "Generating…" : "Generate Report"}
              </button>
              <Link
                href={`/projects/${projectId}/daily-outputs`}
                prefetch={false}
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

          <div className="bg-surface/50 p-4 rounded-lg border border-border grid grid-cols-1 md:grid-cols-[1fr_1fr_1.1fr_0.7fr_2fr_1fr] gap-3">
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
            <div>
              <label className="block text-xs font-medium mb-1 text-text-secondary">
                Supervisor
              </label>
              {compareMode ? (
                <MultiSelect
                  options={supervisorSelectOptions}
                  value={compareIds}
                  onChange={(ids) => setCompareIds(ids.slice(0, 6))}
                  placeholder="Select up to 6 supervisors"
                  className="w-full"
                />
              ) : (
                <SearchableSelect
                  options={supervisorSelectOptions}
                  value={supervisorUserId}
                  onChange={setSupervisorUserId}
                  placeholder="All supervisors (project-wide)"
                  className="w-full"
                />
              )}
            </div>
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
                    if (!compareMode) setSupervisorUserId("");
                    else {
                      setCompareIds([]);
                      setCompareActivityId("");
                    }
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
          </div>
          {compareMode && (
            <div className="mt-3 max-w-md">
              <label className="block text-xs font-medium mb-1 text-text-secondary">
                Compare on activity (optional)
              </label>
              <SearchableSelect
                options={activityOptions}
                value={compareActivityId}
                onChange={setCompareActivityId}
                placeholder="All activities (project-wide)"
                className="w-full"
              />
            </div>
          )}
          <div className="mt-2 text-xs text-text-muted">
            <span className="font-semibold text-text-secondary">Efficiency %</span>
            {" "}color bands: ≥100 % green · 90–99 % yellow · &lt;90 % red · no norm grey.
            <br />
            Efficiency = output vs the productivity norm per resource-day — not deployment utilization.
            {compareMode && compareIds.length < 2 && (
              <span className="ml-3 text-warning">
                Pick at least 2 supervisors to compare.
              </span>
            )}
          </div>
        </div>

        {!compareMode && (
          <CapacityTrendStrip projectId={projectId} toDate={toDate} />
        )}

        {missingWa.length > 0 && (
          <div className="mt-3 flex items-start gap-3 rounded-lg border border-text-muted/20 bg-surface-hover/40 px-4 py-3 text-sm">
            <AlertTriangle className="mt-0.5 h-5 w-5 flex-shrink-0 text-text-muted" />
            <div className="flex-1">
              <p className="font-semibold text-text-primary">
                {missingWa.length}{" "}
                {missingWa.length === 1 ? "activity doesn't" : "activities don't"} track productivity
              </p>
              <p className="mt-1 text-text-secondary">
                These activities have no Work Activity linked — DPRs were filed against them but
                there's no norm to compare actual vs expected, so they don't appear in this
                report. This is fine for design / engineering / office work. Link a Work Activity
                if you want capacity utilization for an activity here.
              </p>
              <Link
                href={`/projects/${projectId}/activities?filter=missing-work-activity`}
                prefetch={false}
                className="mt-2 inline-flex items-center gap-1.5 text-sm font-medium text-accent hover:underline"
              >
                Review activities →
              </Link>
            </div>
          </div>
        )}

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
            {(!data?.data?.manpower && !data?.data?.equipment) ? (
              <div className="rounded-xl border border-border bg-paper px-6 py-8 text-center text-sm text-text-muted">
                No DPR data in this date range. Record DPRs on the project's DPR page; the
                capacity-utilization grid populates from the manpower &amp; equipment rows there.
              </div>
            ) : (
              <>
                {data?.data?.manpower && (
                  <Sc180SectionTable
                    title="Manpower Utilization"
                    section={data.data.manpower}
                    side="MANPOWER"
                  />
                )}
                {data?.data?.equipment && (
                  <Sc180SectionTable
                    title="Equipment Utilization"
                    section={data.data.equipment}
                    side="EQUIPMENT"
                  />
                )}
              </>
            )}
          </div>
        )}

        {!compareMode && supervisorUserId && supervisorPerf?.data && (
          <SupervisorPerformanceSections report={supervisorPerf.data} />
        )}

        {compareMode && comparisonData?.data && (
          <SupervisorComparisonSections comparison={comparisonData.data} />
        )}
      </div>
    </div>
  );
}

export default function CapacityUtilizationPage() {
  // useSearchParams suspends in Next 16 App Router — wrap so the page can be
  // pre-rendered while the params resolve client-side.
  return (
    <Suspense fallback={<div className="p-6 text-text-muted">Loading capacity utilization…</div>}>
      <CapacityUtilizationPageInner />
    </Suspense>
  );
}
