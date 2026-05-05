"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { dailyCostReportApi } from "@/lib/api/dailyCostReportApi";
import { projectApi } from "@/lib/api/projectApi";
import type { ProjectResponse } from "@/lib/types";
import { formatDefaultCurrency } from "@/lib/hooks/useCurrency";

interface SupervisorCostCardProps {
  /** Resource id of the supervisor — used to filter the per-supervisor rollup. */
  supervisorResourceId: string;
}

/**
 * Cost summary for a single supervisor, scoped to a project + date range.
 * Calls {@code GET /v1/projects/{id}/daily-cost-report/by-supervisor} and
 * keeps only the row matching {@code supervisorResourceId}.
 */
export function SupervisorCostCard({ supervisorResourceId }: SupervisorCostCardProps) {
  const { data: projectsData } = useQuery({
    queryKey: ["projects", "list", "for-supervisor-cost-card"],
    queryFn: () => projectApi.listProjects(0, 200),
  });
  const projects = useMemo<ProjectResponse[]>(
    () => projectsData?.data?.content ?? [],
    [projectsData],
  );

  const [projectId, setProjectId] = useState<string>("");
  const [from, setFrom] = useState<string>(defaultFrom());
  const [to, setTo] = useState<string>(today());

  const { data, isFetching, error } = useQuery({
    queryKey: ["daily-cost-report-by-supervisor", projectId, from, to],
    queryFn: () => dailyCostReportApi.bySupervisor(projectId, { from, to }),
    enabled: !!projectId,
  });

  const row = useMemo(
    () => (data?.data ?? []).find((r) => r.supervisorResourceId === supervisorResourceId),
    [data, supervisorResourceId],
  );

  return (
    <section className="rounded-xl border border-border bg-surface/50 p-6">
      <div className="mb-3 flex items-center justify-between gap-3 flex-wrap">
        <h2 className="text-lg font-semibold text-text-primary">Supervisor Cost Summary</h2>
        <p className="text-xs text-text-muted">
          DPRs filed by this supervisor + costed against project BOQ rates.
        </p>
      </div>

      <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-3">
        <div>
          <label className="block text-xs font-medium text-text-secondary">Project</label>
          <select
            value={projectId}
            onChange={(e) => setProjectId(e.target.value)}
            className="w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-text-primary"
          >
            <option value="">— pick a project —</option>
            {projects.map((p) => (
              <option key={p.id} value={p.id}>
                {p.code ? `${p.code} — ` : ""}
                {p.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-text-secondary">From</label>
          <input
            type="date"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            className="w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-text-primary"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-text-secondary">To</label>
          <input
            type="date"
            value={to}
            onChange={(e) => setTo(e.target.value)}
            className="w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-text-primary"
          />
        </div>
      </div>

      {!projectId ? (
        <p className="text-sm text-text-muted">Pick a project to see the supervisor&apos;s cost rollup.</p>
      ) : isFetching ? (
        <p className="text-sm text-text-muted">Loading…</p>
      ) : error ? (
        <p className="text-sm text-danger">Failed to load cost summary.</p>
      ) : !row ? (
        <p className="text-sm text-text-muted">
          This supervisor has no DPRs in the chosen window for this project.
        </p>
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
          <Stat label="DPRs filed" value={String(row.dprCount)} />
          <Stat label="Total qty executed" value={fmtNum(row.totalQtyExecuted)} />
          <Stat label="Budgeted cost" value={formatDefaultCurrency(row.budgetedCost)} />
          <Stat
            label="Actual cost"
            value={formatDefaultCurrency(row.actualCost)}
            sub={`Variance ${formatDefaultCurrency(row.variance)}${
              row.variancePercent != null
                ? ` (${(row.variancePercent * 100).toFixed(1)}%)`
                : ""
            }`}
            tone={row.variance > 0 ? "danger" : "ok"}
          />
        </div>
      )}
    </section>
  );
}

function Stat({
  label,
  value,
  sub,
  tone,
}: {
  label: string;
  value: string;
  sub?: string;
  tone?: "ok" | "danger";
}) {
  return (
    <div className="rounded-md border border-border bg-surface px-4 py-3">
      <p className="text-xs text-text-muted">{label}</p>
      <p className="mt-1 text-lg font-semibold text-text-primary">{value}</p>
      {sub && (
        <p className={`mt-1 text-xs ${tone === "danger" ? "text-danger" : "text-text-secondary"}`}>
          {sub}
        </p>
      )}
    </div>
  );
}

function defaultFrom(): string {
  const d = new Date();
  d.setDate(d.getDate() - 30);
  return d.toISOString().slice(0, 10);
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

function fmtNum(v: number | null | undefined): string {
  if (v == null) return "—";
  return v.toLocaleString(undefined, { maximumFractionDigits: 2 });
}
