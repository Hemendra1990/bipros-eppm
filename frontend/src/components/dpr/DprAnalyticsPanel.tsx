"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { BarChart3, ChevronDown, ChevronRight } from "lucide-react";
import { dprApi, type DprAnalyticsResponse } from "@/lib/api/dprApi";

/**
 * DPR-performance analytics strip on the DPR tab (AI Agent sheet, DPR row: "Analysis of DPR
 * performance to be shown on DPR dash board"). Read-only aggregates for the page's current
 * date window: approval funnel, avg time-to-approve, rejection rate, submissions-per-day
 * trend, and per-supervisor compliance against the expected set (supervisors of IN_PROGRESS
 * activities).
 */
export function DprAnalyticsPanel({
  projectId,
  from,
  to,
}: {
  projectId: string;
  from: string;
  to: string;
}) {
  const [open, setOpen] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ["dpr-analytics", projectId, from, to],
    queryFn: () => dprApi.analytics(projectId, from, to),
    enabled: !!projectId && !!from && !!to && open,
  });

  const a: DprAnalyticsResponse | undefined = data?.data ?? undefined;
  const maxDay = a?.perDay.length ? Math.max(...a.perDay.map((d) => d.count)) : 0;

  return (
    <div className="mb-4 rounded-lg border border-border bg-surface/50">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center gap-2 px-4 py-2.5 text-left"
      >
        {open ? (
          <ChevronDown size={16} className="text-text-secondary" />
        ) : (
          <ChevronRight size={16} className="text-text-secondary" />
        )}
        <BarChart3 size={16} className="text-accent" />
        <span className="text-sm font-semibold text-text-primary">DPR Analytics</span>
        <span className="text-xs text-text-muted">
          submission &amp; approval performance for the selected window
        </span>
      </button>

      {open && (
        <div className="border-t border-border px-4 py-4">
          {isLoading || !a ? (
            <p className="text-sm text-text-muted">Loading analytics…</p>
          ) : (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
                <Stat label="Total DPRs" value={String(a.total)} />
                <Stat label="Draft" value={String(a.draft)} />
                <Stat label="Awaiting approval" value={String(a.submitted)} />
                <Stat label="Approved" value={String(a.approved)} tone="success" />
                <Stat
                  label="Rejection rate"
                  value={a.rejectionRatePct != null ? `${a.rejectionRatePct.toFixed(1)} %` : "—"}
                  hint="of decided DPRs"
                  tone={a.rejectionRatePct != null && a.rejectionRatePct > 10 ? "danger" : undefined}
                />
                <Stat
                  label="Avg time to approve"
                  value={a.avgApprovalHours != null ? formatHours(a.avgApprovalHours) : "—"}
                />
              </div>

              {a.perDay.length > 0 && (
                <div>
                  <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-text-secondary">
                    Submissions per day
                  </p>
                  <div className="flex h-16 items-end gap-[2px] overflow-x-auto">
                    {a.perDay.map((d) => (
                      <div
                        key={d.date}
                        title={`${d.date}: ${d.count} DPR${d.count === 1 ? "" : "s"}`}
                        className="min-w-[6px] flex-1 rounded-t bg-accent/70"
                        style={{ height: `${maxDay > 0 ? Math.max(8, (d.count / maxDay) * 100) : 8}%` }}
                      />
                    ))}
                  </div>
                </div>
              )}

              <div>
                <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-text-secondary">
                  Per-supervisor submissions
                  {a.expectedSupervisors > 0 && (
                    <span className="ml-2 normal-case text-text-muted">
                      {a.supervisors.length} reporting of {a.expectedSupervisors} expected (supervisors
                      of in-progress activities)
                    </span>
                  )}
                </p>
                {a.supervisors.length === 0 ? (
                  <p className="text-sm text-text-muted">No submitted DPRs in this window.</p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="min-w-[420px] text-sm">
                      <thead>
                        <tr className="text-left text-xs uppercase tracking-wide text-text-muted">
                          <th className="py-1 pr-6 font-semibold">Supervisor</th>
                          <th className="py-1 pr-6 text-right font-semibold">Filed</th>
                          <th className="py-1 text-right font-semibold">Approved</th>
                        </tr>
                      </thead>
                      <tbody>
                        {a.supervisors.map((s) => (
                          <tr key={s.name} className="border-t border-border/60">
                            <td className="py-1.5 pr-6 text-text-primary">{s.name}</td>
                            <td className="py-1.5 pr-6 text-right text-text-secondary">{s.filed}</td>
                            <td className="py-1.5 text-right text-text-secondary">{s.approved}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function Stat({
  label,
  value,
  hint,
  tone,
}: {
  label: string;
  value: string;
  hint?: string;
  tone?: "success" | "danger";
}) {
  const valueClass =
    tone === "success" ? "text-success" : tone === "danger" ? "text-danger" : "text-text-primary";
  return (
    <div className="rounded-md border border-border bg-surface px-3 py-2">
      <p className="text-[11px] font-medium uppercase tracking-wide text-text-muted">{label}</p>
      <p className={`text-lg font-semibold ${valueClass}`}>{value}</p>
      {hint && <p className="text-[11px] text-text-muted">{hint}</p>}
    </div>
  );
}

function formatHours(hours: number): string {
  if (hours < 1) return `${Math.round(hours * 60)} min`;
  if (hours < 48) return `${hours.toFixed(1)} h`;
  return `${(hours / 24).toFixed(1)} d`;
}
