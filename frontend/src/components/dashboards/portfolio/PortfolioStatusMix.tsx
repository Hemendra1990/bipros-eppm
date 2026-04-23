"use client";

import { useQuery } from "@tanstack/react-query";
import {
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
} from "recharts";
import { portfolioReportApi } from "@/lib/api/portfolioReportApi";
import {
  CHART_COLORS,
  CHART_TOOLTIP_STYLE,
  EmptyBlock,
  LoadingBlock,
  SectionCard,
} from "@/components/common/dashboard/primitives";

const STATUS_COLORS: Record<string, string> = {
  ACTIVE: CHART_COLORS.ev,
  PLANNED: CHART_COLORS.pv,
  COMPLETED: "#64748b",
  ON_HOLD: CHART_COLORS.warning,
  CANCELLED: CHART_COLORS.danger,
};

export function PortfolioStatusMix() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-scorecard"],
    queryFn: () => portfolioReportApi.getScorecard(),
    staleTime: 60_000,
  });

  if (isLoading)
    return (
      <SectionCard title="Status & Health">
        <LoadingBlock />
      </SectionCard>
    );
  if (isError || !data)
    return (
      <SectionCard title="Status & Health">
        <EmptyBlock label="Unavailable" />
      </SectionCard>
    );

  const statusData = Object.entries(data.byStatus ?? {})
    .filter(([, v]) => (v ?? 0) > 0)
    .map(([name, value]) => ({ name, value }));

  const ragTotal = data.rag.green + data.rag.amber + data.rag.red || 1;
  const ragSegments = [
    { label: "Green", count: data.rag.green, color: CHART_COLORS.ev },
    { label: "Amber", count: data.rag.amber, color: CHART_COLORS.warning },
    { label: "Red", count: data.rag.red, color: CHART_COLORS.danger },
  ];

  return (
    <SectionCard title="Status & Health" subtitle="Distribution and RAG banding across the portfolio">
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        <div>
          <h3 className="mb-3 text-sm font-medium text-text-secondary">Project status mix</h3>
          {statusData.length === 0 ? (
            <EmptyBlock label="No projects" />
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <PieChart>
                <Pie
                  data={statusData}
                  dataKey="value"
                  nameKey="name"
                  innerRadius={50}
                  outerRadius={90}
                  paddingAngle={2}
                >
                  {statusData.map((entry) => (
                    <Cell key={entry.name} fill={STATUS_COLORS[entry.name] ?? "#64748b"} />
                  ))}
                </Pie>
                <Tooltip contentStyle={CHART_TOOLTIP_STYLE} />
                <Legend wrapperStyle={{ fontSize: "12px" }} />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>

        <div>
          <h3 className="mb-3 text-sm font-medium text-text-secondary">RAG banding</h3>
          <div className="flex h-10 w-full overflow-hidden rounded-md border border-border">
            {ragSegments.map((seg) => {
              const pct = (seg.count / ragTotal) * 100;
              if (pct === 0) return null;
              return (
                <div
                  key={seg.label}
                  className="flex items-center justify-center text-xs font-semibold text-white"
                  style={{ width: `${pct}%`, backgroundColor: seg.color }}
                  title={`${seg.label}: ${seg.count}`}
                >
                  {pct > 10 ? `${seg.label} ${seg.count}` : seg.count}
                </div>
              );
            })}
          </div>
          <div className="mt-4 grid grid-cols-3 gap-3">
            {ragSegments.map((seg) => (
              <div
                key={seg.label}
                className="rounded-md border border-border bg-surface-hover/40 p-3 text-center"
              >
                <div className="text-xs text-text-secondary">{seg.label}</div>
                <div className="mt-1 text-xl font-bold" style={{ color: seg.color }}>
                  {seg.count}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </SectionCard>
  );
}
