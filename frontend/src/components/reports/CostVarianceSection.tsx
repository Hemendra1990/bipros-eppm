"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Download } from "lucide-react";
import { Badge, type BadgeVariant } from "@/components/ui/badge";
import { downloadCsv, toCsv } from "@/lib/utils/csvExport";
import {
  varianceReportApi,
  type ActivityStatusName,
  type CostVarianceActivityRow,
} from "@/lib/api/varianceReportApi";

interface Props {
  projectId: string;
  baselineId?: string;
}

const ONE_CRORE = 10_000_000;

function formatRupees(n: number | null | undefined, opts: { sign?: boolean } = {}): string {
  if (n == null || !Number.isFinite(n)) return "—";
  const abs = Math.abs(n);
  let body: string;
  if (abs >= ONE_CRORE) {
    body = `₹${(n / ONE_CRORE).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })} cr`;
  } else if (abs >= 100_000) {
    body = `₹${(n / 100_000).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })} L`;
  } else {
    body = `₹${n.toLocaleString("en-IN", { maximumFractionDigits: 0 })}`;
  }
  if (opts.sign && n > 0) return `+${body}`;
  return body;
}

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

function cpiTone(cpi: number | null | undefined): "emerald" | "burgundy" | "bronze" | "default" {
  if (cpi == null) return "default";
  if (cpi >= 1) return "emerald";
  if (cpi >= 0.95) return "bronze";
  return "burgundy";
}

function costToneClass(value: number | null | undefined): string {
  if (value == null) return "text-slate";
  if (value > 0) return "text-burgundy font-semibold";
  if (value < 0) return "text-emerald font-semibold";
  return "text-slate";
}

export function CostVarianceSection({ projectId, baselineId }: Props) {
  const { data, isLoading, error } = useQuery({
    queryKey: ["cost-variance", projectId, baselineId ?? null],
    queryFn: () => varianceReportApi.getCostVariance(projectId, baselineId),
    enabled: !!projectId,
    staleTime: 30_000,
    retry: 1,
  });

  const [showOnlyNonZero, setShowOnlyNonZero] = useState(false);
  const [overrunOnly, setOverrunOnly] = useState(false);

  const activityRows = useMemo(() => data?.data?.activityRows ?? [], [data]);

  const filtered = useMemo(() => {
    let r = activityRows;
    if (showOnlyNonZero) {
      r = r.filter(
        (x) =>
          (x.estimateVariance != null && x.estimateVariance !== 0) ||
          (x.burnVariance != null && x.burnVariance !== 0)
      );
    }
    if (overrunOnly) {
      r = r.filter((x) => (x.burnVariance ?? 0) > 0);
    }
    return r;
  }, [activityRows, showOnlyNonZero, overrunOnly]);

  if (error) {
    return <ErrorState message="Could not load the cost variance report. The backend may be down or no baseline is set." />;
  }
  if (isLoading) return <LoadingState />;
  if (!data?.data) return <EmptyBaselineState />;

  const summary = data.data.summary;
  const wbsRows = data.data.wbsRows;

  const onExport = () => {
    if (!data?.data) return;
    const csv = toCsv<CostVarianceActivityRow>(filtered, [
      { key: "code", header: "Activity code" },
      { key: "name", header: "Activity name" },
      { key: "activityType", header: "Type" },
      { key: "status", header: "Status" },
      { key: "percentComplete", header: "% complete" },
      { key: "baselinePlannedCost", header: "BL planned (₹)" },
      { key: "currentPlannedCost", header: "Cur planned (₹)" },
      { key: "estimateVariance", header: "Estimate var (₹)" },
      { key: "actualCost", header: "Actual (₹)" },
      { key: "burnVariance", header: "Burn var (₹)" },
    ]);
    const projectCode = data.data.project.code.replace(/[^a-zA-Z0-9-]/g, "_");
    downloadCsv(`cost-variance-${projectCode}`, csv);
  };

  return (
    <div className="space-y-6">
      {/* Summary KPIs */}
      <div className="grid grid-cols-2 gap-3.5 lg:grid-cols-4">
        <Kpi label="Budget at completion" value={formatRupees(summary.budgetAtCompletion)} accent="gold" />
        <Kpi label="Earned value" value={formatRupees(summary.earnedValue)} accent="default" />
        <Kpi label="Actual cost" value={formatRupees(summary.actualCost)} accent="default" />
        <Kpi
          label="Cost variance"
          value={formatRupees(summary.costVariance, { sign: true })}
          accent={(summary.costVariance ?? 0) < 0 ? "burgundy" : "emerald"}
          hint={
            summary.costVariance != null
              ? (summary.costVariance < 0 ? "Over budget" : "Under budget")
              : undefined
          }
        />
      </div>

      <div className="grid grid-cols-2 gap-3.5 lg:grid-cols-4">
        <CpiKpi label="CPI" value={summary.costPerformanceIndex} />
        <CpiKpi label="SPI" value={summary.schedulePerformanceIndex} />
        <Kpi
          label="Estimate at completion"
          value={formatRupees(summary.estimateAtCompletion)}
          accent="default"
        />
        <Kpi
          label="Variance at completion"
          value={formatRupees(summary.varianceAtCompletion, { sign: true })}
          accent={(summary.varianceAtCompletion ?? 0) < 0 ? "burgundy" : "emerald"}
        />
      </div>

      {/* WBS rollup table */}
      <div>
        <div className="mb-2 text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">
          Cost variance by WBS
        </div>
        <div className="overflow-hidden rounded-2xl border border-hairline bg-paper">
          {wbsRows.length === 0 ? (
            <div className="p-6 text-center text-sm text-slate">
              No EVM rollup found per WBS — the EVM module has not been calculated yet for this project.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead className="border-b border-hairline bg-ivory">
                  <tr>
                    <th className="px-3 py-2.5 text-left text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">Code</th>
                    <th className="px-3 py-2.5 text-left text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">Name</th>
                    <th className="px-3 py-2.5 text-right text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">Budget</th>
                    <th className="px-3 py-2.5 text-right text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">Earned</th>
                    <th className="px-3 py-2.5 text-right text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">Actual</th>
                    <th className="px-3 py-2.5 text-right text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">CV</th>
                    <th className="px-3 py-2.5 text-right text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">CPI</th>
                  </tr>
                </thead>
                <tbody>
                  {wbsRows.map((row) => (
                    <tr key={row.wbsNodeId} className="border-b border-hairline transition-colors last:border-b-0 hover:bg-ivory">
                      <td className="px-3 py-2.5 font-medium text-charcoal">{row.wbsCode}</td>
                      <td className="px-3 py-2.5 text-charcoal">{row.wbsName}</td>
                      <td className="px-3 py-2.5 text-right text-charcoal tabular-nums">{formatRupees(row.budget)}</td>
                      <td className="px-3 py-2.5 text-right text-charcoal tabular-nums">{formatRupees(row.earnedValue)}</td>
                      <td className="px-3 py-2.5 text-right text-charcoal tabular-nums">{formatRupees(row.actualCost)}</td>
                      <td className={`px-3 py-2.5 text-right tabular-nums ${costToneClass(row.costVariance != null ? -row.costVariance : null)}`}>
                        {formatRupees(row.costVariance, { sign: true })}
                      </td>
                      <td className="px-3 py-2.5 text-right text-charcoal tabular-nums">
                        <CpiBadge value={row.costPerformanceIndex} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      {/* Activity-level cost variance */}
      <div>
        <div className="mb-2 flex flex-wrap items-center gap-2">
          <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">
            Cost variance by activity
          </div>
          <Toggle label="Non-zero only" active={showOnlyNonZero} onClick={() => setShowOnlyNonZero((v) => !v)} />
          <Toggle label="Overrun only" active={overrunOnly} onClick={() => setOverrunOnly((v) => !v)} />
          <div className="ml-auto flex items-center gap-2">
            <span className="text-xs text-slate tabular-nums">
              {filtered.length} of {activityRows.length}
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
        <div className="overflow-hidden rounded-2xl border border-hairline bg-paper">
          {filtered.length === 0 ? (
            <div className="p-6 text-center text-sm text-slate">
              {activityRows.length === 0
                ? "No baselined activities for this project."
                : "No activities match the current filters."}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead className="border-b border-hairline bg-ivory">
                  <tr>
                    <th className="px-3 py-2.5 text-left text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">Code</th>
                    <th className="px-3 py-2.5 text-left text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">Name</th>
                    <th className="px-3 py-2.5 text-left text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">Status</th>
                    <th className="px-3 py-2.5 text-right text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">% complete</th>
                    <th className="px-3 py-2.5 text-right text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">BL planned</th>
                    <th className="px-3 py-2.5 text-right text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">Cur planned</th>
                    <th className="px-3 py-2.5 text-right text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">Estimate var</th>
                    <th className="px-3 py-2.5 text-right text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">Actual</th>
                    <th className="px-3 py-2.5 text-right text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">Burn var</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((row) => (
                    <tr key={row.activityId} className="border-b border-hairline transition-colors last:border-b-0 hover:bg-ivory">
                      <td className="px-3 py-2.5 font-medium text-charcoal whitespace-nowrap">{row.code}</td>
                      <td className="px-3 py-2.5 text-charcoal max-w-[260px] truncate">{row.name}</td>
                      <td className="px-3 py-2.5">
                        <Badge variant={statusBadge(row.status)} withDot>
                          {row.status.replace(/_/g, " ").toLowerCase()}
                        </Badge>
                      </td>
                      <td className="px-3 py-2.5 text-right text-charcoal tabular-nums">
                        {row.percentComplete != null ? `${row.percentComplete.toFixed(0)}%` : "—"}
                      </td>
                      <td className="px-3 py-2.5 text-right text-charcoal tabular-nums">{formatRupees(row.baselinePlannedCost)}</td>
                      <td className="px-3 py-2.5 text-right text-charcoal tabular-nums">{formatRupees(row.currentPlannedCost)}</td>
                      <td className={`px-3 py-2.5 text-right tabular-nums ${costToneClass(row.estimateVariance)}`}>
                        {formatRupees(row.estimateVariance, { sign: true })}
                      </td>
                      <td className="px-3 py-2.5 text-right text-charcoal tabular-nums">{formatRupees(row.actualCost)}</td>
                      <td className={`px-3 py-2.5 text-right tabular-nums ${costToneClass(row.burnVariance)}`}>
                        {formatRupees(row.burnVariance, { sign: true })}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function Kpi({
  label,
  value,
  accent = "default",
  hint,
}: {
  label: string;
  value: string;
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
        className="font-display text-[24px] font-semibold leading-tight tracking-tight text-charcoal"
        style={{ fontVariationSettings: "'opsz' 144" }}
      >
        {value}
      </div>
      {hint && <div className="mt-1.5 text-[11px] text-slate">{hint}</div>}
    </div>
  );
}

function CpiKpi({ label, value }: { label: string; value: number | null | undefined }) {
  const tone = cpiTone(value);
  const display = value != null ? value.toFixed(2) : "—";
  const accent =
    tone === "emerald" ? "emerald" : tone === "burgundy" ? "burgundy" : tone === "bronze" ? "gold" : "default";
  return <Kpi label={label} value={display} accent={accent} />;
}

function CpiBadge({ value }: { value: number | null | undefined }) {
  if (value == null) return <span className="text-ash">—</span>;
  const tone = cpiTone(value);
  const variant: BadgeVariant =
    tone === "emerald" ? "success" : tone === "burgundy" ? "danger" : tone === "bronze" ? "warning" : "neutral";
  return (
    <Badge variant={variant} withDot>
      {value.toFixed(2)}
    </Badge>
  );
}

function Toggle({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`inline-flex items-center gap-1.5 rounded-md border px-2.5 py-1.5 text-xs font-medium transition-colors ${
        active
          ? "border-gold/45 bg-gold-tint/40 text-gold-ink"
          : "border-hairline bg-ivory text-slate hover:border-gold/30 hover:text-charcoal"
      }`}
    >
      {label}
    </button>
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
