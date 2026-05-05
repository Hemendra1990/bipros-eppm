"use client";

import { useQuery } from "@tanstack/react-query";
import type { ColumnDef } from "@tanstack/react-table";
import { portfolioReportApi } from "@/lib/api/portfolioReportApi";
import { ragCls } from "@/lib/utils/rag";
import { VirtualDataTable } from "@/components/common/VirtualDataTable";
import {
  EmptyBlock,
  LoadingBlock,
  SectionCard,
  truncate,
} from "@/components/common/dashboard/primitives";

function cellColor(probability: number, impact: number): string {
  const score = probability * impact;
  if (score >= 15) return "bg-danger/25 text-danger";
  if (score >= 9) return "bg-warning/25 text-warning";
  if (score >= 5) return "bg-accent/20 text-accent";
  return "bg-success/20 text-success";
}

export function RiskHeatmapPanel() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-risk-heatmap"],
    queryFn: () => portfolioReportApi.getRiskHeatmap(),
    staleTime: 60_000,
  });

  if (isLoading)
    return (
      <SectionCard title="Risk Heatmap">
        <LoadingBlock />
      </SectionCard>
    );
  if (isError || !data)
    return (
      <SectionCard title="Risk Heatmap">
        <EmptyBlock label="Unavailable" />
      </SectionCard>
    );

  const grid = new Map<string, number>();
  data.cells.forEach((c) => grid.set(`${c.probability}-${c.impact}`, c.count));

  return (
    <SectionCard
      title="Risk Heatmap"
      subtitle="Open risks across the portfolio, mapped by probability × impact"
    >
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[auto_1fr]">
        <div>
          <div className="mb-2 text-xs font-medium uppercase tracking-wide text-text-muted">
            5×5 heatmap
          </div>
          <VirtualDataTable
            data={[5, 4, 3, 2, 1].map((p) => ({
              probability: p,
              i1: grid.get(`${p}-1`) ?? 0,
              i2: grid.get(`${p}-2`) ?? 0,
              i3: grid.get(`${p}-3`) ?? 0,
              i4: grid.get(`${p}-4`) ?? 0,
              i5: grid.get(`${p}-5`) ?? 0,
            }))}
            columns={[
              {
                accessorKey: "probability",
                header: "P \\ I",
                size: 40,
                cell: ({ row }) => (
                  <div className="text-right text-text-muted w-full">
                    {row.original.probability}
                  </div>
                ),
              },
              {
                accessorKey: "i1",
                header: "1",
                size: 56,
                cell: ({ row }) => {
                  const n = row.original.i1;
                  return (
                    <div className={`-mx-4 -my-3 h-12 flex items-center justify-center ${cellColor(row.original.probability, 1)}`}>
                      {n > 0 ? <span className="text-base font-bold">{n}</span> : "·"}
                    </div>
                  );
                },
              },
              {
                accessorKey: "i2",
                header: "2",
                size: 56,
                cell: ({ row }) => {
                  const n = row.original.i2;
                  return (
                    <div className={`-mx-4 -my-3 h-12 flex items-center justify-center ${cellColor(row.original.probability, 2)}`}>
                      {n > 0 ? <span className="text-base font-bold">{n}</span> : "·"}
                    </div>
                  );
                },
              },
              {
                accessorKey: "i3",
                header: "3",
                size: 56,
                cell: ({ row }) => {
                  const n = row.original.i3;
                  return (
                    <div className={`-mx-4 -my-3 h-12 flex items-center justify-center ${cellColor(row.original.probability, 3)}`}>
                      {n > 0 ? <span className="text-base font-bold">{n}</span> : "·"}
                    </div>
                  );
                },
              },
              {
                accessorKey: "i4",
                header: "4",
                size: 56,
                cell: ({ row }) => {
                  const n = row.original.i4;
                  return (
                    <div className={`-mx-4 -my-3 h-12 flex items-center justify-center ${cellColor(row.original.probability, 4)}`}>
                      {n > 0 ? <span className="text-base font-bold">{n}</span> : "·"}
                    </div>
                  );
                },
              },
              {
                accessorKey: "i5",
                header: "5",
                size: 56,
                cell: ({ row }) => {
                  const n = row.original.i5;
                  return (
                    <div className={`-mx-4 -my-3 h-12 flex items-center justify-center ${cellColor(row.original.probability, 5)}`}>
                      {n > 0 ? <span className="text-base font-bold">{n}</span> : "·"}
                    </div>
                  );
                },
              },
            ]}
            sortable={false}
            searchable={false}
            resizable={false}
            maxHeight="none"
            className="border-0 rounded-none"
          />
          <div className="mt-2 text-[10px] text-text-muted">
            Bottom-left = low exposure. Top-right = extreme.
          </div>
        </div>

        <div>
          <div className="mb-2 text-xs font-medium uppercase tracking-wide text-text-muted">
            Top 5 exposure
          </div>
          {data.topExposureRisks.length === 0 ? (
            <EmptyBlock label="No open risks" />
          ) : (
            <ul className="space-y-2">
              {data.topExposureRisks.map((r) => (
                <li
                  key={r.riskId}
                  className="flex items-start justify-between gap-3 rounded-md border border-border bg-surface-hover/30 p-3"
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-[11px] text-text-muted">{r.code}</span>
                      <span
                        className={`rounded-full border px-2 py-0.5 text-[10px] font-semibold ${ragCls(r.rag)}`}
                      >
                        {r.rag}
                      </span>
                      <span className="text-[11px] text-text-muted">{r.projectCode}</span>
                    </div>
                    <div className="mt-1 truncate text-sm text-text-primary">
                      {truncate(r.title, 90)}
                    </div>
                    <div className="mt-1 text-[11px] text-text-muted">
                      Prob: {r.probability} · Impact: {r.impact}
                    </div>
                  </div>
                  <div className="shrink-0 text-right">
                    <div className="text-xs text-text-muted">Score</div>
                    <div className="text-lg font-bold text-text-primary">
                      {r.score.toFixed(1)}
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </SectionCard>
  );
}
