"use client";

import { useQuery } from "@tanstack/react-query";
import { projectInsightsApi } from "@/lib/api/projectInsightsApi";

function ragCls(rag: string): string {
  switch (rag) {
    case "GREEN":
      return "bg-success/15 text-success border-success/40";
    case "AMBER":
      return "bg-warning/15 text-warning border-warning/40";
    case "RED":
    case "CRIMSON":
      return "bg-danger/15 text-danger border-danger/40";
    default:
      return "bg-surface-hover text-text-secondary border-border";
  }
}

function RagTile({ label, rag }: { label: string; rag: string }) {
  return (
    <div className={`rounded-lg border p-4 ${ragCls(rag)}`}>
      <div className="text-xs font-medium uppercase tracking-wide">{label}</div>
      <div className="mt-1 text-2xl font-bold">{rag}</div>
    </div>
  );
}

export function ProjectStatusSnapshot({ projectId }: { projectId: string }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["project-status-snapshot", projectId],
    queryFn: () => projectInsightsApi.getStatusSnapshot(projectId),
  });

  if (isLoading)
    return (
      <div className="rounded-lg border border-border bg-surface/50 p-6 text-center text-text-muted">
        Loading status snapshot…
      </div>
    );
  if (isError || !data)
    return (
      <div className="rounded-lg border border-dashed border-border p-6 text-center text-text-muted">
        Status snapshot unavailable
      </div>
    );

  const s = data;

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 gap-4 md:grid-cols-5">
        <RagTile label="Schedule" rag={s.scheduleRag} />
        <RagTile label="Cost" rag={s.costRag} />
        <RagTile label="Scope" rag={s.scopeRag} />
        <RagTile label="Risk" rag={s.riskRag} />
        <RagTile label="HSE" rag={s.hseRag} />
      </div>

      <div className="grid grid-cols-2 gap-4 md:grid-cols-4 lg:grid-cols-6">
        <MetricTile label="CPI" value={s.currentCpi.toFixed(2)} />
        <MetricTile label="SPI" value={s.currentSpi.toFixed(2)} />
        <MetricTile label="Physical %" value={`${s.physicalPct.toFixed(1)}%`} />
        <MetricTile label="Planned %" value={`${s.plannedPct.toFixed(1)}%`} />
        <MetricTile label="BAC" value={`₹ ${s.bacCrores.toLocaleString("en-IN")} Cr`} />
        <MetricTile label="EAC" value={`₹ ${s.eacCrores.toLocaleString("en-IN")} Cr`} />
      </div>

      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        <div className="rounded-lg border border-border bg-surface/50 p-5">
          <h3 className="mb-3 text-sm font-semibold text-text-secondary">Top issues</h3>
          {s.topIssues.length === 0 ? (
            <p className="text-sm text-text-muted">No open issues</p>
          ) : (
            <ul className="space-y-2 text-sm">
              {s.topIssues.map((t) => (
                <li key={t} className="flex items-start gap-2">
                  <span className="text-danger">•</span>
                  <span className="text-text-primary">{t}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="rounded-lg border border-border bg-surface/50 p-5">
          <h3 className="mb-3 text-sm font-semibold text-text-secondary">Next milestone</h3>
          {s.nextMilestoneName ? (
            <div>
              <p className="text-lg font-medium text-text-primary">{s.nextMilestoneName}</p>
              <p className="mt-1 text-sm text-text-muted">
                Planned: {s.nextMilestoneDate ?? "—"}
              </p>
            </div>
          ) : (
            <p className="text-sm text-text-muted">No upcoming milestones</p>
          )}
          <div className="mt-4 grid grid-cols-2 gap-3 text-xs">
            <div>
              <div className="text-text-muted">Active risks</div>
              <div className="text-lg font-semibold text-text-primary">
                {s.activeRisksCount}
              </div>
            </div>
            <div>
              <div className="text-text-muted">Open HSE incidents</div>
              <div className="text-lg font-semibold text-text-primary">
                {s.openHseIncidents}
              </div>
            </div>
          </div>
        </div>
      </div>

      <p className="text-xs text-text-muted">
        Data date: {new Date(s.lastUpdatedAt).toLocaleString()}
      </p>
    </div>
  );
}

function MetricTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border bg-surface-hover/40 p-4">
      <div className="text-xs font-medium text-text-secondary">{label}</div>
      <div className="mt-1 text-xl font-bold text-text-primary">{value}</div>
    </div>
  );
}
