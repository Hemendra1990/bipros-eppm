"use client";

import { useQuery } from "@tanstack/react-query";
import { AlertTriangle } from "lucide-react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { portfolioReportApi } from "@/lib/api/portfolioReportApi";
import {
  CHART_COLORS,
  CHART_TOOLTIP_STYLE,
  EmptyBlock,
  LoadingBlock,
  SectionCard,
  formatCrore,
  truncate,
} from "@/components/common/dashboard/primitives";

export function FundingUtilizationChart() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-funding"],
    queryFn: () => portfolioReportApi.getFundingUtilization(),
    staleTime: 60_000,
  });

  if (isLoading)
    return (
      <SectionCard title="Funding Utilization">
        <LoadingBlock />
      </SectionCard>
    );
  if (isError)
    return (
      <SectionCard title="Funding Utilization">
        <EmptyBlock label="Unavailable" />
      </SectionCard>
    );

  const rows = data ?? [];
  if (rows.length === 0) {
    return (
      <SectionCard title="Funding Utilization">
        <EmptyBlock label="No funding data" />
      </SectionCard>
    );
  }

  const stuck = rows.filter((r) => r.utilizationPct < 50 && r.totalSanctionedCrores > 0);

  const chartData = rows.map((r) => ({
    name: truncate(r.projectName, 24),
    Sanctioned: r.totalSanctionedCrores,
    Released: r.totalReleasedCrores,
    Utilized: r.totalUtilizedCrores,
    status: r.fundingStatus,
  }));

  return (
    <SectionCard
      title="Funding Utilization"
      subtitle="Sanctioned vs released vs utilised per project (₹ Cr)"
    >
      {stuck.length > 0 && (
        <div className="mb-4 flex items-start gap-2 rounded-md border border-warning/40 bg-warning/10 p-3 text-sm text-warning">
          <AlertTriangle size={18} className="mt-0.5 shrink-0" />
          <span>
            {stuck.length} project{stuck.length > 1 ? "s have" : " has"} utilization below 50%.
            Funds are released but not being spent.
          </span>
        </div>
      )}

      <ResponsiveContainer width="100%" height={320}>
        <BarChart data={chartData} margin={{ top: 5, right: 30, left: 0, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
          <XAxis dataKey="name" stroke="#64748b" style={{ fontSize: "11px" }} />
          <YAxis
            stroke="#64748b"
            style={{ fontSize: "12px" }}
            tickFormatter={(v: number) => `₹${v.toFixed(0)}Cr`}
          />
          <Tooltip
            contentStyle={CHART_TOOLTIP_STYLE}
            formatter={(value) => formatCrore(Number(value ?? 0))}
          />
          <Legend wrapperStyle={{ fontSize: "12px" }} />
          <Bar dataKey="Sanctioned" fill={CHART_COLORS.pv} radius={[4, 4, 0, 0]} />
          <Bar dataKey="Released" fill={CHART_COLORS.committed} radius={[4, 4, 0, 0]} />
          <Bar dataKey="Utilized" fill={CHART_COLORS.ev} radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </SectionCard>
  );
}
