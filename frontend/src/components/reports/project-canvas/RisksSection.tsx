"use client";

import { useQuery } from "@tanstack/react-query";
import { reportDataApi } from "@/lib/api/reportDataApi";
import { KpiTile } from "@/components/common/KpiTile";
import { ragCls } from "@/lib/utils/rag";
import {
  EmptyBlock,
  LoadingBlock,
  SectionCard,
  truncate,
} from "@/components/common/dashboard/primitives";

export function RisksSection({ projectId }: { projectId: string }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["project-risk-register", projectId],
    queryFn: () => reportDataApi.getRiskRegister(projectId),
    staleTime: 60_000,
    retry: false,
  });

  if (isLoading)
    return (
      <SectionCard title="Risks">
        <LoadingBlock />
      </SectionCard>
    );
  if (isError || !data)
    return (
      <SectionCard title="Risks">
        <EmptyBlock label="Unavailable" />
      </SectionCard>
    );
  if (data.totalRisks === 0) {
    return (
      <SectionCard title="Risks">
        <EmptyBlock label="No risks recorded for this project" />
      </SectionCard>
    );
  }

  return (
    <SectionCard title="Risks" subtitle="Project-level risk register summary">
      <div className="mb-4 grid grid-cols-2 gap-3 md:grid-cols-4">
        <KpiTile label="Total risks" value={data.totalRisks} />
        <KpiTile label="High" value={data.highRisks} tone={data.highRisks > 0 ? "danger" : "default"} />
        <KpiTile label="Medium" value={data.mediumRisks} tone="warning" />
        <KpiTile label="Low" value={data.lowRisks} tone="success" />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div>
          <h3 className="mb-2 text-sm font-medium text-text-secondary">By category</h3>
          {Object.keys(data.risksByCategory ?? {}).length === 0 ? (
            <EmptyBlock label="No category breakdown" />
          ) : (
            <ul className="space-y-2">
              {Object.entries(data.risksByCategory).map(([cat, count]) => (
                <li
                  key={cat}
                  className="flex items-center justify-between rounded-md border border-border bg-surface-hover/30 px-3 py-2 text-sm"
                >
                  <span className="text-text-primary">{cat}</span>
                  <span className="font-mono text-text-secondary">{count}</span>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div>
          <h3 className="mb-2 text-sm font-medium text-text-secondary">Top risks</h3>
          {!data.topRisks || data.topRisks.length === 0 ? (
            <EmptyBlock label="No top risks" />
          ) : (
            <ul className="space-y-2">
              {data.topRisks.slice(0, 5).map((r) => (
                <li
                  key={r.code}
                  className="rounded-md border border-border bg-surface-hover/30 p-3"
                >
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-[11px] text-text-muted">{r.code}</span>
                    <span className="text-xs text-text-muted">{r.category}</span>
                    <span
                      className={`ml-auto rounded-full border px-2 py-0.5 text-[10px] font-semibold ${ragCls(
                        r.probability === "HIGH" || r.impact === "HIGH" ? "RED" : "AMBER",
                      )}`}
                    >
                      P:{r.probability} · I:{r.impact}
                    </span>
                  </div>
                  <div className="mt-1 text-sm text-text-primary">{truncate(r.title, 90)}</div>
                  <div className="mt-1 text-xs text-text-muted">Score: {r.score}</div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </SectionCard>
  );
}
