"use client";

import { useQuery } from "@tanstack/react-query";
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
  truncate,
} from "./chartPrimitives";

export function EvmRollupChart() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-evm-rollup"],
    queryFn: () => portfolioReportApi.getEvmRollup(),
    staleTime: 60_000,
  });

  if (isLoading)
    return (
      <SectionCard title="EVM Performance">
        <LoadingBlock />
      </SectionCard>
    );
  if (isError)
    return (
      <SectionCard title="EVM Performance">
        <EmptyBlock label="Unavailable" />
      </SectionCard>
    );

  const rows = data?.data ?? [];
  if (rows.length === 0) {
    return (
      <SectionCard title="EVM Performance" subtitle="Planned vs Earned vs Actual per project">
        <EmptyBlock label="No EVM data" />
      </SectionCard>
    );
  }

  const totalEv = rows.reduce((s, r) => s + (r.ev ?? 0), 0);
  const totalAc = rows.reduce((s, r) => s + (r.ac ?? 0), 0);
  const totalPv = rows.reduce((s, r) => s + (r.pv ?? 0), 0);
  const wCpi = totalAc > 0 ? totalEv / totalAc : 0;
  const wSpi = totalPv > 0 ? totalEv / totalPv : 0;

  const chartData = rows.map((r) => ({
    name: truncate(r.projectName, 24),
    PV: r.pv,
    EV: r.ev,
    AC: r.ac,
  }));

  return (
    <SectionCard
      title="EVM Performance"
      subtitle="Planned Value vs Earned Value vs Actual Cost per project"
    >
      <ResponsiveContainer width="100%" height={320}>
        <BarChart data={chartData} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
          <XAxis dataKey="name" stroke="#64748b" style={{ fontSize: "11px" }} />
          <YAxis stroke="#64748b" style={{ fontSize: "12px" }} />
          <Tooltip contentStyle={CHART_TOOLTIP_STYLE} />
          <Legend wrapperStyle={{ fontSize: "12px" }} />
          <Bar dataKey="PV" fill={CHART_COLORS.pv} radius={[4, 4, 0, 0]} />
          <Bar dataKey="EV" fill={CHART_COLORS.ev} radius={[4, 4, 0, 0]} />
          <Bar dataKey="AC" fill={CHART_COLORS.ac} radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>

      <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <MiniStat label="Portfolio Σ PV" value={totalPv.toFixed(0)} />
        <MiniStat label="Portfolio Σ EV" value={totalEv.toFixed(0)} />
        <MiniStat
          label="Weighted CPI"
          value={wCpi.toFixed(3)}
          tone={wCpi >= 0.95 ? "good" : wCpi >= 0.85 ? "amber" : "red"}
        />
        <MiniStat
          label="Weighted SPI"
          value={wSpi.toFixed(3)}
          tone={wSpi >= 0.95 ? "good" : wSpi >= 0.85 ? "amber" : "red"}
        />
      </div>
    </SectionCard>
  );
}

function MiniStat({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone?: "good" | "amber" | "red";
}) {
  const color =
    tone === "good" ? "text-success" : tone === "amber" ? "text-warning" : tone === "red" ? "text-danger" : "text-text-primary";
  return (
    <div className="rounded-md border border-border bg-surface-hover/40 p-3">
      <div className="text-xs text-text-secondary">{label}</div>
      <div className={`mt-1 text-lg font-semibold ${color}`}>{value}</div>
    </div>
  );
}
