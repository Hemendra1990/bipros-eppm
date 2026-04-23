"use client";

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
import { portfolioReportApi } from "@/lib/api/portfolioReportApi";
import { ragFill, ragFromScore } from "@/lib/utils/rag";
import {
  CHART_TOOLTIP_STYLE,
  EmptyBlock,
  LoadingBlock,
  SectionCard,
  formatCrore,
  truncate,
} from "./chartPrimitives";

export function ContractorLeagueChart() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-contractor-league"],
    queryFn: () => portfolioReportApi.getContractorLeague(),
    staleTime: 60_000,
  });

  if (isLoading)
    return (
      <SectionCard title="Contractor Performance">
        <LoadingBlock />
      </SectionCard>
    );
  if (isError)
    return (
      <SectionCard title="Contractor Performance">
        <EmptyBlock label="Unavailable" />
      </SectionCard>
    );

  const rows = data ?? [];
  if (rows.length === 0) {
    return (
      <SectionCard title="Contractor Performance">
        <EmptyBlock label="No contractor data" />
      </SectionCard>
    );
  }

  const sorted = [...rows].sort((a, b) => b.avgPerformance - a.avgPerformance);
  const chartData = sorted.map((r) => ({
    name: truncate(r.contractorName, 28),
    code: r.contractorCode,
    performance: r.avgPerformance,
    contractValue: r.totalContractValueCrores,
    projects: r.activeProjects,
    spi: r.avgSpi,
    cpi: r.avgCpi,
  }));

  return (
    <SectionCard
      title="Contractor Performance"
      subtitle="Average performance score across all contracts. Sorted best-to-worst."
    >
      <ResponsiveContainer width="100%" height={Math.max(200, chartData.length * 40)}>
        <BarChart
          data={chartData}
          layout="vertical"
          margin={{ top: 5, right: 30, left: 10, bottom: 5 }}
        >
          <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
          <XAxis
            type="number"
            stroke="#64748b"
            style={{ fontSize: "12px" }}
            domain={[0, 100]}
            tickFormatter={(v: number) => `${v}`}
          />
          <YAxis
            type="category"
            dataKey="name"
            stroke="#64748b"
            style={{ fontSize: "11px" }}
            width={180}
          />
          <Tooltip
            contentStyle={CHART_TOOLTIP_STYLE}
            formatter={(value, _name, props) => {
              const row = props.payload as (typeof chartData)[number];
              return [
                `${Number(value ?? 0).toFixed(1)} · ${row.projects} projects · ${formatCrore(row.contractValue)}`,
                "Perf",
              ];
            }}
          />
          <Bar dataKey="performance" radius={[0, 4, 4, 0]}>
            {chartData.map((row) => (
              <Cell key={row.code} fill={ragFill(ragFromScore(row.performance))} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </SectionCard>
  );
}
