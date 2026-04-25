"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  ArrowDownUp,
  ArrowDownWideNarrow,
  ArrowUpNarrowWide,
  Download,
  Flag,
  Sparkles,
} from "lucide-react";
import { Badge, type BadgeVariant } from "@/components/ui/badge";
import { downloadCsv, toCsv } from "@/lib/utils/csvExport";
import {
  varianceReportApi,
  type ScheduleVarianceRow,
  type ActivityStatusName,
} from "@/lib/api/varianceReportApi";

interface Props {
  projectId: string;
  baselineId?: string;
}

type SortKey =
  | "code"
  | "name"
  | "finishVarianceDays"
  | "startVarianceDays"
  | "percentComplete"
  | "totalFloat";

type SortDir = "asc" | "desc";

const STATUS_OPTIONS: ActivityStatusName[] = [
  "NOT_STARTED",
  "IN_PROGRESS",
  "COMPLETED",
];

function statusBadge(status: ActivityStatusName): BadgeVariant {
  switch (status) {
    case "COMPLETED":
      return "success";
    case "IN_PROGRESS":
      return "info";
    case "NOT_STARTED":
      return "neutral";
    case "ON_HOLD":
      return "warning";
    case "CANCELLED":
      return "danger";
    default:
      return "neutral";
  }
}

function formatActivityType(t: string): string {
  return t
    .split("_")
    .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
    .join(" ");
}

function varianceClass(days: number): string {
  if (days > 0) return "text-burgundy font-semibold tabular-nums";
  if (days < 0) return "text-emerald font-semibold tabular-nums";
  return "text-slate tabular-nums";
}

const VARIANCE_BUCKETS: Array<{ label: string; test: (d: number) => boolean }> = [
  { label: "≤ −5 ahead", test: (d) => d <= -5 },
  { label: "−5..0", test: (d) => d > -5 && d < 0 },
  { label: "0", test: (d) => d === 0 },
  { label: "0..5", test: (d) => d > 0 && d <= 5 },
  { label: "5..10", test: (d) => d > 5 && d <= 10 },
  { label: "10..20", test: (d) => d > 10 && d <= 20 },
  { label: "> 20", test: (d) => d > 20 },
];

export function ScheduleVarianceSection({ projectId, baselineId }: Props) {
  const { data, isLoading, error } = useQuery({
    queryKey: ["schedule-variance", projectId, baselineId ?? null],
    queryFn: () => varianceReportApi.getScheduleVariance(projectId, baselineId),
    enabled: !!projectId,
    staleTime: 30_000,
    retry: 1,
  });

  const [showOnlyNonZero, setShowOnlyNonZero] = useState(true);
  const [milestonesOnly, setMilestonesOnly] = useState(false);
  const [criticalOnly, setCriticalOnly] = useState(false);
  const [statusFilter, setStatusFilter] = useState<Set<ActivityStatusName>>(new Set());
  const [threshold, setThreshold] = useState<number>(0);
  const [sortKey, setSortKey] = useState<SortKey>("finishVarianceDays");
  const [sortDir, setSortDir] = useState<SortDir>("desc");

  const rows = useMemo(() => data?.data?.rows ?? [], [data]);
  const summary = data?.data?.summary;

  const filtered = useMemo(() => {
    let r = rows;
    if (showOnlyNonZero) {
      r = r.filter((x) => x.startVarianceDays !== 0 || x.finishVarianceDays !== 0);
    }
    if (milestonesOnly) {
      r = r.filter((x) => x.isMilestone);
    }
    if (criticalOnly) {
      r = r.filter((x) => x.isCritical === true);
    }
    if (statusFilter.size > 0) {
      r = r.filter((x) => statusFilter.has(x.status));
    }
    if (threshold > 0) {
      r = r.filter((x) => Math.abs(x.finishVarianceDays) >= threshold);
    }
    return [...r].sort((a, b) => {
      const av = (a as unknown as Record<SortKey, unknown>)[sortKey];
      const bv = (b as unknown as Record<SortKey, unknown>)[sortKey];
      const cmp = compare(av, bv);
      return sortDir === "asc" ? cmp : -cmp;
    });
  }, [rows, showOnlyNonZero, milestonesOnly, criticalOnly, statusFilter, threshold, sortKey, sortDir]);

  const distribution = useMemo(() => {
    const visible = filtered;
    return VARIANCE_BUCKETS.map((b) => ({
      label: b.label,
      count: visible.filter((r) => b.test(r.finishVarianceDays)).length,
      tone: b.label === "0" ? "slate" : b.label.startsWith("≤") || b.label === "−5..0" ? "emerald" : "burgundy",
    }));
  }, [filtered]);

  const handleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir("desc");
    }
  };

  const toggleStatus = (s: ActivityStatusName) => {
    const next = new Set(statusFilter);
    if (next.has(s)) next.delete(s);
    else next.add(s);
    setStatusFilter(next);
  };

  const onExport = () => {
    if (!data?.data) return;
    const csv = toCsv<ScheduleVarianceRow>(filtered, [
      { key: "code", header: "Activity code" },
      { key: "name", header: "Activity name" },
      { key: "activityType", header: "Type", accessor: (r) => formatActivityType(r.activityType) },
      { key: "status", header: "Status" },
      { key: "percentComplete", header: "% complete" },
      { key: "baselineStart", header: "BL start" },
      { key: "currentStart", header: "Cur start" },
      { key: "startVarianceDays", header: "Var start (d)" },
      { key: "baselineFinish", header: "BL finish" },
      { key: "currentFinish", header: "Cur finish" },
      { key: "finishVarianceDays", header: "Var finish (d)" },
      { key: "baselineOriginalDuration", header: "BL duration" },
      { key: "currentOriginalDuration", header: "Cur duration" },
      { key: "durationVarianceDays", header: "Var duration (d)" },
      { key: "totalFloat", header: "Total float" },
      { key: "isCritical", header: "Critical" },
    ]);
    const projectCode = data.data.project.code.replace(/[^a-zA-Z0-9-]/g, "_");
    downloadCsv(`schedule-variance-${projectCode}`, csv);
  };

  if (error) {
    return (
      <ErrorState message="Could not load the schedule variance report. The backend may be down or no baseline is set." />
    );
  }

  if (isLoading) {
    return <LoadingState />;
  }

  if (!data?.data || rows.length === 0) {
    return <EmptyBaselineState />;
  }

  const onTrackPct =
    summary && summary.totalActivities > 0
      ? Math.round((summary.onTrackCount / summary.totalActivities) * 100)
      : 0;

  return (
    <div className="space-y-6">
      {/* KPI strip */}
      <div className="grid grid-cols-2 gap-3.5 lg:grid-cols-4">
        <Kpi
          label="Activities baselined"
          value={summary?.totalActivities ?? 0}
        />
        <Kpi
          label="On-track"
          value={`${onTrackPct}%`}
          accent="emerald"
          hint={`${summary?.onTrackCount ?? 0} activities`}
        />
        <Kpi
          label="Slipped"
          value={summary?.slippedCount ?? 0}
          accent={summary && summary.slippedCount > 0 ? "burgundy" : "default"}
          hint={
            summary && summary.criticalSlippedCount > 0
              ? `${summary.criticalSlippedCount} on critical path`
              : undefined
          }
        />
        <Kpi
          label="Worst slippage"
          value={`${summary?.worstFinishVarianceDays ?? 0}d`}
          accent={summary && summary.worstFinishVarianceDays > 0 ? "burgundy" : "default"}
          hint={summary?.worstActivityName ?? undefined}
        />
      </div>

      {/* Filter bar */}
      <div className="rounded-2xl border border-hairline bg-paper p-4">
        <div className="flex flex-wrap items-center gap-2">
          <Toggle
            label="Show only non-zero variance"
            active={showOnlyNonZero}
            onClick={() => setShowOnlyNonZero((v) => !v)}
          />
          <Toggle
            label="Milestones only"
            active={milestonesOnly}
            onClick={() => setMilestonesOnly((v) => !v)}
            icon={<Flag size={12} />}
          />
          <Toggle
            label="Critical only"
            active={criticalOnly}
            onClick={() => setCriticalOnly((v) => !v)}
            icon={<Sparkles size={12} />}
          />

          <div className="mx-2 h-5 w-px bg-hairline" />

          <div className="flex items-center gap-1.5">
            <span className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">
              Status
            </span>
            {STATUS_OPTIONS.map((s) => (
              <Toggle
                key={s}
                label={formatActivityType(s)}
                active={statusFilter.has(s)}
                onClick={() => toggleStatus(s)}
                size="sm"
              />
            ))}
          </div>

          <div className="mx-2 h-5 w-px bg-hairline" />

          <label className="flex items-center gap-2 text-xs font-medium text-slate">
            <span className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">
              Threshold
            </span>
            <input
              type="number"
              min={0}
              value={threshold}
              onChange={(e) => setThreshold(Math.max(0, Number(e.target.value || 0)))}
              className="h-8 w-16 rounded-md border border-hairline bg-ivory px-2 text-xs text-charcoal focus:border-gold/40 focus:outline-none focus:ring-1 focus:ring-gold/30"
            />
            <span className="text-xs text-slate">≥ d</span>
          </label>

          <div className="ml-auto flex items-center gap-2">
            <span className="text-xs text-slate tabular-nums">
              {filtered.length} of {rows.length}
            </span>
            <button
              type="button"
              onClick={onExport}
              className="inline-flex items-center gap-1.5 rounded-md border border-hairline bg-ivory px-2.5 py-1.5 text-xs font-semibold text-charcoal transition-all duration-200 hover:-translate-y-px hover:border-gold/50 hover:bg-paper hover:text-gold-deep"
            >
              <Download size={12} />
              Export CSV
            </button>
          </div>
        </div>
      </div>

      {/* Distribution */}
      <div className="rounded-2xl border border-hairline bg-paper p-4">
        <div className="mb-2 text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">
          Finish variance distribution
        </div>
        <ResponsiveContainer width="100%" height={140}>
          <BarChart data={distribution} margin={{ top: 8, right: 8, bottom: 8, left: 0 }}>
            <CartesianGrid stroke="rgba(28,28,28,0.05)" vertical={false} />
            <XAxis
              dataKey="label"
              tick={{ fontSize: 10, fill: "#6B7280" }}
              axisLine={{ stroke: "#EDE7D3" }}
              tickLine={false}
            />
            <YAxis
              allowDecimals={false}
              tick={{ fontSize: 10, fill: "#6B7280" }}
              axisLine={false}
              tickLine={false}
              width={28}
            />
            <Tooltip
              contentStyle={{
                background: "#FFFFFF",
                border: "1px solid #EDE7D3",
                borderRadius: 8,
                fontSize: 12,
              }}
              cursor={{ fill: "rgba(212,175,55,0.06)" }}
            />
            <Bar dataKey="count" radius={[4, 4, 0, 0]}>
              {distribution.map((d) => (
                <Cell
                  key={d.label}
                  fill={
                    d.tone === "emerald"
                      ? "#2E7D5B"
                      : d.tone === "burgundy"
                        ? "#9B2C2C"
                        : "#9CA3AF"
                  }
                />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>

      {/* Table */}
      <div className="overflow-hidden rounded-2xl border border-hairline bg-paper">
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead className="border-b border-hairline bg-ivory">
              <tr>
                <Th>Code</Th>
                <Th>Name</Th>
                <Th>Type</Th>
                <Th>Status</Th>
                <Th onClick={() => handleSort("percentComplete")} sortDir={sortKey === "percentComplete" ? sortDir : null} className="text-right">% complete</Th>
                <Th className="text-right">BL start</Th>
                <Th className="text-right">Cur start</Th>
                <Th onClick={() => handleSort("startVarianceDays")} sortDir={sortKey === "startVarianceDays" ? sortDir : null} className="text-right">Var start</Th>
                <Th className="text-right">BL finish</Th>
                <Th className="text-right">Cur finish</Th>
                <Th onClick={() => handleSort("finishVarianceDays")} sortDir={sortKey === "finishVarianceDays" ? sortDir : null} className="text-right">Var finish</Th>
                <Th className="text-right">BL dur</Th>
                <Th className="text-right">Cur dur</Th>
                <Th className="text-right">Var dur</Th>
                <Th onClick={() => handleSort("totalFloat")} sortDir={sortKey === "totalFloat" ? sortDir : null} className="text-right">Float</Th>
                <Th>Critical</Th>
              </tr>
            </thead>
            <tbody>
              {filtered.length === 0 ? (
                <tr>
                  <td colSpan={16} className="px-5 py-8 text-center text-sm text-slate">
                    No activities match the current filters.
                  </td>
                </tr>
              ) : (
                filtered.map((row) => (
                  <tr
                    key={row.activityId}
                    className="border-b border-hairline transition-colors last:border-b-0 hover:bg-ivory"
                  >
                    <td className="px-3 py-2.5 font-medium text-charcoal whitespace-nowrap">
                      {row.code}
                      {row.isMilestone && (
                        <Flag size={11} className="ml-1.5 inline text-gold-deep" />
                      )}
                    </td>
                    <td className="px-3 py-2.5 text-charcoal max-w-[220px] truncate">{row.name}</td>
                    <td className="px-3 py-2.5 text-slate text-xs">{formatActivityType(row.activityType)}</td>
                    <td className="px-3 py-2.5">
                      <Badge variant={statusBadge(row.status)} withDot>
                        {formatActivityType(row.status)}
                      </Badge>
                    </td>
                    <td className="px-3 py-2.5 text-right text-charcoal tabular-nums">
                      {row.percentComplete != null ? `${row.percentComplete.toFixed(0)}%` : "—"}
                    </td>
                    <td className="px-3 py-2.5 text-right text-slate tabular-nums">{row.baselineStart ?? "—"}</td>
                    <td className="px-3 py-2.5 text-right text-charcoal tabular-nums">{row.currentStart ?? "—"}</td>
                    <td className={`px-3 py-2.5 text-right ${varianceClass(row.startVarianceDays)}`}>
                      {row.startVarianceDays > 0 ? "+" : ""}
                      {row.startVarianceDays}
                    </td>
                    <td className="px-3 py-2.5 text-right text-slate tabular-nums">{row.baselineFinish ?? "—"}</td>
                    <td className="px-3 py-2.5 text-right text-charcoal tabular-nums">{row.currentFinish ?? "—"}</td>
                    <td className={`px-3 py-2.5 text-right ${varianceClass(row.finishVarianceDays)}`}>
                      {row.finishVarianceDays > 0 ? "+" : ""}
                      {row.finishVarianceDays}
                    </td>
                    <td className="px-3 py-2.5 text-right text-slate tabular-nums">
                      {row.baselineOriginalDuration != null
                        ? row.baselineOriginalDuration.toFixed(0)
                        : "—"}
                    </td>
                    <td className="px-3 py-2.5 text-right text-charcoal tabular-nums">
                      {row.currentOriginalDuration != null
                        ? row.currentOriginalDuration.toFixed(0)
                        : "—"}
                    </td>
                    <td className={`px-3 py-2.5 text-right ${varianceClass(row.durationVarianceDays)}`}>
                      {row.durationVarianceDays > 0 ? "+" : ""}
                      {row.durationVarianceDays.toFixed(0)}
                    </td>
                    <td className="px-3 py-2.5 text-right text-slate tabular-nums">
                      {row.totalFloat != null ? row.totalFloat.toFixed(0) : "—"}
                    </td>
                    <td className="px-3 py-2.5">
                      {row.isCritical ? (
                        <Badge variant="danger" withDot>
                          Critical
                        </Badge>
                      ) : (
                        <span className="text-xs text-ash">—</span>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

// ──────────────── helpers ────────────────

function compare(a: unknown, b: unknown): number {
  if (a == null && b == null) return 0;
  if (a == null) return -1;
  if (b == null) return 1;
  if (typeof a === "number" && typeof b === "number") return a - b;
  return String(a).localeCompare(String(b));
}

function Kpi({
  label,
  value,
  accent = "default",
  hint,
}: {
  label: string;
  value: number | string;
  accent?: "default" | "emerald" | "burgundy" | "gold";
  hint?: string;
}) {
  const rail =
    accent === "emerald"
      ? "border-l-[3px] border-l-emerald"
      : accent === "burgundy"
        ? "border-l-[3px] border-l-burgundy"
        : accent === "gold"
          ? "border-l-[3px] border-l-gold"
          : "";
  return (
    <div
      className={`rounded-xl border border-hairline bg-paper p-5 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_4px_20px_rgba(28,28,28,0.05)] ${rail}`}
    >
      <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep mb-2">
        {label}
      </div>
      <div
        className="font-display text-[28px] font-semibold leading-none tracking-tight text-charcoal"
        style={{ fontVariationSettings: "'opsz' 144" }}
      >
        {value}
      </div>
      {hint && <div className="mt-1.5 text-[11px] text-slate truncate">{hint}</div>}
    </div>
  );
}

function Toggle({
  label,
  active,
  onClick,
  icon,
  size = "md",
}: {
  label: string;
  active: boolean;
  onClick: () => void;
  icon?: React.ReactNode;
  size?: "sm" | "md";
}) {
  const padding = size === "sm" ? "px-2 py-1 text-[11px]" : "px-2.5 py-1.5 text-xs";
  return (
    <button
      type="button"
      onClick={onClick}
      className={`inline-flex items-center gap-1.5 rounded-md border font-medium transition-colors ${padding} ${
        active
          ? "border-gold/45 bg-gold-tint/40 text-gold-ink"
          : "border-hairline bg-ivory text-slate hover:border-gold/30 hover:text-charcoal"
      }`}
    >
      {icon}
      {label}
    </button>
  );
}

function Th({
  children,
  className = "",
  onClick,
  sortDir,
}: {
  children: React.ReactNode;
  className?: string;
  onClick?: () => void;
  sortDir?: SortDir | null;
}) {
  const sortable = !!onClick;
  return (
    <th
      onClick={onClick}
      className={`px-3 py-2.5 text-left text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep whitespace-nowrap ${sortable ? "cursor-pointer select-none hover:text-gold-ink" : ""} ${className}`}
    >
      <span className="inline-flex items-center gap-1">
        {children}
        {sortable && (sortDir == null
          ? <ArrowDownUp size={10} className="opacity-40" />
          : sortDir === "asc"
            ? <ArrowUpNarrowWide size={10} />
            : <ArrowDownWideNarrow size={10} />)}
      </span>
    </th>
  );
}

function LoadingState() {
  return (
    <div className="space-y-3">
      {[...Array(4)].map((_, i) => (
        <div key={i} className="h-24 animate-pulse rounded-xl bg-parchment/60" />
      ))}
    </div>
  );
}

function EmptyBaselineState() {
  return (
    <div className="rounded-2xl border border-dashed border-hairline bg-ivory/50 p-10 text-center">
      <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep mb-2">
        No baseline assigned
      </div>
      <p className="text-sm text-slate">
        Create a baseline (Project → Baselines tab) and set it as active to start tracking variance.
      </p>
    </div>
  );
}

function ErrorState({ message }: { message: string }) {
  return (
    <div className="rounded-2xl border border-burgundy/30 bg-burgundy/5 p-6 text-center text-sm text-burgundy">
      {message}
    </div>
  );
}
