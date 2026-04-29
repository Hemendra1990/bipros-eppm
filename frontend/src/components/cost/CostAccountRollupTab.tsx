"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { DollarSign, TrendingUp, BarChart3, Wallet } from "lucide-react";
import { evmApi, type CostAccountRollupRow } from "@/lib/api/evmApi";
import { KpiTile } from "@/components/common/KpiTile";

const INR_PER_CRORE = 10_000_000;

function formatCrores(val: number | null | undefined): string {
  if (val === null || val === undefined) return "—";
  const v = val / INR_PER_CRORE;
  return `₹${v.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}cr`;
}

function formatRatio(val: number | null | undefined): string {
  if (val === null || val === undefined) return "—";
  return val.toFixed(2);
}

function cvTone(cv: number): "success" | "danger" | "default" {
  if (cv > 0) return "success";
  if (cv < 0) return "danger";
  return "default";
}

function cpiTextClass(cpi: number | null): string {
  if (cpi === null) return "text-text-muted";
  if (cpi >= 1) return "text-emerald font-semibold";
  return "text-burgundy font-semibold";
}

function cvTextClass(cv: number): string {
  if (cv > 0) return "text-emerald font-semibold";
  if (cv < 0) return "text-burgundy font-semibold";
  return "text-text-secondary";
}

export function CostAccountRollupTab({ projectId }: { projectId: string }) {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ["cost-account-rollup", projectId],
    queryFn: () => evmApi.getCostAccountRollup(projectId),
  });

  const rows: CostAccountRollupRow[] = useMemo(() => data?.data ?? [], [data]);

  const totals = useMemo(() => {
    const bac = rows.reduce((acc, r) => acc + (r.bac ?? 0), 0);
    const ev = rows.reduce((acc, r) => acc + (r.ev ?? 0), 0);
    const ac = rows.reduce((acc, r) => acc + (r.ac ?? 0), 0);
    const cpi = ac > 0 ? ev / ac : null;
    return { bac, ev, ac, cpi };
  }, [rows]);

  if (isLoading) {
    return (
      <div className="space-y-6 px-6 pb-8">
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="h-24 animate-pulse rounded-xl bg-surface-hover/50" />
          ))}
        </div>
        <div className="space-y-2">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-10 animate-pulse rounded-md bg-surface-hover/50" />
          ))}
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="px-6 pb-8">
        <div className="rounded-xl border border-burgundy/40 bg-burgundy/5 px-4 py-3 text-sm text-burgundy">
          Failed to load cost account rollup: {String((error as Error)?.message ?? "Unknown error")}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6 px-6 pb-8">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <KpiTile
          label="Total BAC"
          value={formatCrores(totals.bac)}
          hint="Sum of budget at completion"
          tone="accent"
          icon={<Wallet size={14} />}
        />
        <KpiTile
          label="Earned Value"
          value={formatCrores(totals.ev)}
          hint="Sum across all cost accounts"
          tone="success"
          icon={<TrendingUp size={14} />}
        />
        <KpiTile
          label="Actual Cost"
          value={formatCrores(totals.ac)}
          hint="Sum across all cost accounts"
          tone="danger"
          icon={<DollarSign size={14} />}
        />
        <KpiTile
          label="Weighted CPI"
          value={formatRatio(totals.cpi)}
          hint="Total EV ÷ Total AC"
          tone={totals.cpi === null ? "default" : totals.cpi >= 1 ? "success" : "danger"}
          icon={<BarChart3 size={14} />}
        />
      </div>

      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-text-secondary">
          Rollup by Cost Account
        </h2>
        <span className="text-xs text-text-muted">
          {rows.length} {rows.length === 1 ? "bucket" : "buckets"}
        </span>
      </div>

      {rows.length === 0 ? (
        <div className="rounded-xl border border-dashed border-border bg-surface-hover/20 py-12 text-center">
          <p className="text-sm text-text-muted">No cost data to roll up yet.</p>
          <p className="mt-1 text-xs text-text-muted">
            Create cost accounts in <strong>Admin → Cost Accounts</strong>, then assign them via the
            activity edit form or WBS edit panel.
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border bg-surface-hover/60">
                <th className="px-3 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  Code
                </th>
                <th className="px-3 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  Name
                </th>
                <th className="px-3 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  Activities
                </th>
                <th className="px-3 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  BAC
                </th>
                <th className="px-3 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  EV
                </th>
                <th className="px-3 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  AC
                </th>
                <th className="px-3 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  CV
                </th>
                <th className="px-3 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  CPI
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => {
                const isUnassigned = r.costAccountId === null;
                return (
                  <tr
                    key={r.costAccountId ?? "__unassigned__"}
                    className={`border-b border-border last:border-0 ${
                      isUnassigned ? "bg-surface-hover/30 text-text-muted" : "hover:bg-surface-hover/40"
                    }`}
                  >
                    <td className="px-3 py-2.5 font-mono text-xs">
                      {r.costAccountCode ?? "—"}
                    </td>
                    <td className="px-3 py-2.5">
                      {isUnassigned ? (
                        <span className="italic">{r.costAccountName}</span>
                      ) : (
                        <span className="font-medium text-text-primary">{r.costAccountName}</span>
                      )}
                    </td>
                    <td className="px-3 py-2.5 text-right tabular-nums">{r.activityCount}</td>
                    <td className="px-3 py-2.5 text-right tabular-nums">{formatCrores(r.bac)}</td>
                    <td className="px-3 py-2.5 text-right tabular-nums">{formatCrores(r.ev)}</td>
                    <td className="px-3 py-2.5 text-right tabular-nums">{formatCrores(r.ac)}</td>
                    <td className={`px-3 py-2.5 text-right tabular-nums ${cvTextClass(r.cv)}`}>
                      {formatCrores(r.cv)}
                    </td>
                    <td className={`px-3 py-2.5 text-right tabular-nums ${cpiTextClass(r.cpi)}`}>
                      {formatRatio(r.cpi)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
            <tfoot>
              <tr className="border-t-2 border-border bg-surface-hover/40 font-semibold">
                <td className="px-3 py-2.5 text-xs uppercase tracking-wide text-text-secondary" colSpan={2}>
                  Total
                </td>
                <td className="px-3 py-2.5 text-right tabular-nums text-text-primary">
                  {rows.reduce((acc, r) => acc + r.activityCount, 0)}
                </td>
                <td className="px-3 py-2.5 text-right tabular-nums text-text-primary">
                  {formatCrores(totals.bac)}
                </td>
                <td className="px-3 py-2.5 text-right tabular-nums text-text-primary">
                  {formatCrores(totals.ev)}
                </td>
                <td className="px-3 py-2.5 text-right tabular-nums text-text-primary">
                  {formatCrores(totals.ac)}
                </td>
                <td className={`px-3 py-2.5 text-right tabular-nums ${cvTextClass(totals.ev - totals.ac)}`}>
                  {formatCrores(totals.ev - totals.ac)}
                </td>
                <td className={`px-3 py-2.5 text-right tabular-nums ${cpiTextClass(totals.cpi)}`}>
                  {formatRatio(totals.cpi)}
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
      <p className="text-[11px] text-text-muted">
        Cost account is resolved per activity: <span className="font-mono">activity.costAccountId</span> →{" "}
        <span className="font-mono">wbs.costAccountId</span> → unassigned. Activities without either
        roll up into the &ldquo;Unassigned&rdquo; bucket so you can see what&apos;s not yet attributed.
      </p>
    </div>
  );
}
