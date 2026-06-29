"use client";

import { useQuery } from "@tanstack/react-query";
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { portfolioReportApi } from "@/lib/api/portfolioReportApi";
import { formatMoney, resolveCurrencyMeta } from "@/lib/currency/format";
import {
  CHART_COLORS,
  CHART_TOOLTIP_STYLE,
  CHART_TOOLTIP_LABEL_STYLE,
  CHART_TOOLTIP_ITEM_STYLE,
  EmptyBlock,
  LoadingBlock,
  SectionCard,
} from "@/components/common/dashboard/primitives";

export function CashFlowOutlookChart({ currency }: { currency?: string }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-cash-flow"],
    queryFn: () => portfolioReportApi.getCashFlowOutlook(12),
    staleTime: 60_000,
  });

  if (isLoading)
    return (
      <SectionCard title="Cash Flow Outlook">
        <LoadingBlock />
      </SectionCard>
    );
  if (isError || !data)
    return (
      <SectionCard title="Cash Flow Outlook">
        <EmptyBlock label="Unavailable" />
      </SectionCard>
    );

  // Pick dominant currency (most data points), filter to it — no FX, never mix currencies
  const all = data;
  const counts = all.reduce(
    (m, p) => {
      const c = p.currency ?? "INR";
      m[c] = (m[c] ?? 0) + 1;
      return m;
    },
    {} as Record<string, number>,
  );
  const cur = currency ?? Object.entries(counts).sort((a, b) => b[1] - a[1])[0]?.[0] ?? "INR";
  const series = all.filter((p) => (p.currency ?? cur) === cur);

  const hasData = series.some(
    (r) => r.plannedOutflowRaw !== 0 || r.plannedInflowRaw !== 0,
  );
  if (!hasData) {
    return (
      <SectionCard
        title="Cash Flow Outlook"
        subtitle="Next 12 months: monthly bars (net) + cumulative line"
      >
        <EmptyBlock label="No forecast data seeded yet" />
      </SectionCard>
    );
  }

  const currencyMeta = resolveCurrencyMeta(cur);

  const chartData = series.map((r) => ({
    month: r.yearMonth,
    Outflow: -r.plannedOutflowRaw,
    Inflow: r.plannedInflowRaw,
    Net: r.netRaw,
    Cumulative: r.cumulativeRaw,
  }));

  return (
    <SectionCard
      title="Cash Flow Outlook"
      subtitle="Next 12 months. Bars = monthly net; line = cumulative position."
    >
      <ResponsiveContainer width="100%" height={360}>
        <ComposedChart data={chartData} margin={{ top: 5, right: 30, left: 0, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
          <XAxis dataKey="month" stroke="#64748b" style={{ fontSize: "11px" }} />
          <YAxis
            yAxisId="left"
            stroke="#64748b"
            style={{ fontSize: "12px" }}
            tickFormatter={(v: number) => formatMoney(v, currencyMeta, { compact: true })}
          />
          <YAxis
            yAxisId="right"
            orientation="right"
            stroke="#64748b"
            style={{ fontSize: "12px" }}
            tickFormatter={(v: number) => formatMoney(v, currencyMeta, { compact: true })}
          />
          <Tooltip
            contentStyle={CHART_TOOLTIP_STYLE}
            labelStyle={CHART_TOOLTIP_LABEL_STYLE}
            itemStyle={CHART_TOOLTIP_ITEM_STYLE}
            formatter={(value) =>
              formatMoney(Math.abs(Number(value ?? 0)), currencyMeta, { compact: true })
            }
          />
          <Legend wrapperStyle={{ fontSize: "12px" }} />
          <Bar yAxisId="left" dataKey="Inflow" fill={CHART_COLORS.ev} />
          <Bar yAxisId="left" dataKey="Outflow" fill={CHART_COLORS.ac} />
          <Line
            yAxisId="right"
            type="monotone"
            dataKey="Cumulative"
            stroke={CHART_COLORS.forecast}
            strokeWidth={2}
            dot={{ r: 3 }}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </SectionCard>
  );
}
