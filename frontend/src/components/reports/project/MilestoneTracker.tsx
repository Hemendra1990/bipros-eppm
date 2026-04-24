"use client";

import { useQuery } from "@tanstack/react-query";
import { projectInsightsApi } from "@/lib/api/projectInsightsApi";

export function MilestoneTracker({ projectId }: { projectId: string }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["project-milestones", projectId],
    queryFn: () => projectInsightsApi.getMilestones(projectId),
  });

  if (isLoading)
    return (
      <div className="rounded-lg border border-border bg-surface/50 p-6 text-center text-text-muted">
        Loading milestones…
      </div>
    );
  if (isError)
    return (
      <div className="rounded-lg border border-dashed border-border p-6 text-center text-text-muted">
        Milestones unavailable
      </div>
    );

  const rows = data ?? [];
  if (rows.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-border bg-surface/30 p-6 text-center text-text-muted">
        No milestones defined for this project. Activities with type
        START_MILESTONE or FINISH_MILESTONE will appear here.
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {rows.map((m) => {
        const slipCls =
          m.daysSlip > 30
            ? "text-danger"
            : m.daysSlip > 0
              ? "text-warning"
              : "text-success";
        const statusCls =
          m.status === "COMPLETED"
            ? "bg-success/15 text-success"
            : m.status === "IN_PROGRESS"
              ? "bg-accent/15 text-accent"
              : "bg-surface-hover text-text-secondary";
        return (
          <div
            key={m.milestoneId}
            className="rounded-lg border border-border bg-surface/50 p-4"
          >
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="font-mono text-xs text-text-muted">{m.code}</span>
                  <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${statusCls}`}>
                    {m.status}
                  </span>
                  <span className="text-xs text-text-muted">{m.milestoneType}</span>
                </div>
                <h4 className="mt-1 truncate text-base font-medium text-text-primary">
                  {m.name}
                </h4>
              </div>
              <div className="grid grid-cols-3 gap-4 text-right">
                <div>
                  <div className="text-xs text-text-muted">Planned</div>
                  <div className="text-sm font-medium text-text-primary">
                    {m.plannedDate ?? "—"}
                  </div>
                </div>
                <div>
                  <div className="text-xs text-text-muted">Actual</div>
                  <div className="text-sm font-medium text-text-primary">
                    {m.actualDate ?? "—"}
                  </div>
                </div>
                <div>
                  <div className="text-xs text-text-muted">Slip</div>
                  <div className={`text-sm font-semibold ${slipCls}`}>
                    {m.daysSlip > 0 ? `+${m.daysSlip}d` : `${m.daysSlip}d`}
                  </div>
                </div>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
