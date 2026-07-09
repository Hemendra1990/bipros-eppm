"use client";

import { useMemo } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { TrendingUp, Calendar, Gauge, AlertTriangle } from "lucide-react";
import { PageHeader } from "@/components/common/PageHeader";
import { Card } from "@/components/ui/card";
import { EChart } from "@/components/ai/charts/EChart";
import { monteCarloApi } from "@/lib/api/monteCarloApi";
import { evmApi } from "@/lib/api/evmApi";
import { agentApi } from "@/lib/api/agentApi";
import { useProjectCurrency } from "@/lib/currency/ProjectCurrencyProvider";
import type { ChartSpec, AgentFindingDto } from "@/lib/types";
import { FindingCard } from "@/components/ai/agents/FindingCard";

const FORECAST_TYPES = new Set([
  "COMPLETION_FORECAST",
  "COST_AT_COMPLETION",
  "CASHFLOW_PRESSURE",
]);

function num(x: unknown): number | null {
  if (x == null) return null;
  const n = typeof x === "number" ? x : Number(x);
  return Number.isFinite(n) ? n : null;
}

function EmptyChart({ title, hint }: { title: string; hint: string }) {
  return (
    <Card variant="flat" className="flex h-[316px] flex-col items-center justify-center text-center">
      <span className="mb-2 text-[11px] font-semibold uppercase tracking-[0.16em] text-slate">
        {title}
      </span>
      <AlertTriangle size={22} className="mb-2 text-ash" />
      <p className="max-w-xs text-xs text-text-muted">{hint}</p>
    </Card>
  );
}

function Kpi({
  label,
  value,
  sub,
  icon,
  tone = "text-charcoal",
}: {
  label: string;
  value: string;
  sub?: string;
  icon: React.ReactNode;
  tone?: string;
}) {
  return (
    <Card variant="flat" className="p-4">
      <div className="flex items-center gap-2 text-[11px] font-medium uppercase tracking-wide text-slate">
        <span className="text-gold-deep">{icon}</span>
        {label}
      </div>
      <div className={`mt-1.5 font-display text-2xl font-semibold tabular-nums ${tone}`}>
        {value}
      </div>
      {sub && <div className="mt-0.5 text-[11px] text-text-muted">{sub}</div>}
    </Card>
  );
}

export default function ForecastPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const currency = useProjectCurrency();

  // Money charts avoid ECharts function formatters (they break the theme
  // deep-clone), so values are pre-scaled to the currency's major unit and the
  // unit is called out in the chart note. Same "relabel, don't convert" idea as
  // the compact ladder.
  const scale = currency.isIndian ? 1e7 : 1e6;
  const unitNote = `${currency.code} — ${currency.isIndian ? "crore" : "millions"}`;
  const scaled = (v: number | null): number | null => (v == null ? null : +(v / scale).toFixed(3));

  const { data: simRes } = useQuery({
    queryKey: ["mc-latest", projectId],
    queryFn: () => monteCarloApi.getLatestSimulation(projectId),
    enabled: !!projectId,
    retry: false,
  });
  const sim = simRes?.data ?? null;

  const { data: cashRes } = useQuery({
    queryKey: ["mc-cashflow", projectId, sim?.id],
    queryFn: () => monteCarloApi.getCashflow(projectId, sim!.id),
    enabled: !!projectId && !!sim?.id,
    retry: false,
  });
  const cashflow = cashRes?.data ?? [];

  const { data: evmRes } = useQuery({
    queryKey: ["evm-latest", projectId],
    queryFn: () => evmApi.getLatest(projectId),
    enabled: !!projectId,
    retry: false,
  });
  const evm = evmRes?.data ?? null;

  const { data: evmHistRes } = useQuery({
    queryKey: ["evm-history", projectId],
    queryFn: () => evmApi.getHistory(projectId),
    enabled: !!projectId,
    retry: false,
  });
  const evmHistory = evmHistRes?.data ?? [];

  const { data: agentsRes } = useQuery({
    queryKey: ["agents", projectId],
    queryFn: () => agentApi.listAgents(projectId),
    enabled: !!projectId,
  });
  const agentNames = useMemo(
    () => Object.fromEntries((agentsRes?.data ?? []).map((a) => [a.key, a.displayName])),
    [agentsRes],
  );

  const { data: findingsRes } = useQuery({
    queryKey: ["agent-findings", projectId, "forecast"],
    queryFn: () => agentApi.listFindings(projectId, { status: "ACTIVE", size: 100 }),
    enabled: !!projectId,
  });
  const forecastFindings: AgentFindingDto[] = useMemo(
    () => (findingsRes?.data?.content ?? []).filter((f) => FORECAST_TYPES.has(f.findingType)),
    [findingsRes],
  );

  // 1) Completion confidence ladder (duration percentiles, days).
  const completionSpec: ChartSpec | null = useMemo(() => {
    if (!sim) return null;
    const rows: [string, number | null][] = [
      ["P10", sim.p10Duration ?? null],
      ["P25", sim.p25Duration ?? null],
      ["P50", sim.confidenceP50Duration ?? null],
      ["P80", sim.confidenceP80Duration ?? null],
      ["P90", sim.p90Duration ?? null],
    ];
    const data = rows.map(([, v]) => (v == null ? null : Math.round(v)));
    if (data.every((d) => d == null)) return null;
    return {
      id: "completion-fan",
      title: "Completion confidence (duration, days)",
      type: "bar",
      note: `Baseline ${Math.round(sim.baselineDuration)}d`,
      option: {
        grid: { left: 44, right: 16, top: 16, bottom: 28 },
        tooltip: { trigger: "axis" },
        xAxis: { type: "category", data: rows.map(([k]) => k) },
        yAxis: { type: "value" },
        series: [
          {
            type: "bar",
            barMaxWidth: 44,
            data,
            markLine: {
              symbol: "none",
              data: [{ yAxis: Math.round(sim.baselineDuration) }],
              lineStyle: { color: "#64748B", type: "dashed" },
              label: { formatter: "Baseline", color: "#64748B", position: "insideEndTop" },
            },
          },
        ],
      },
    };
  }, [sim]);

  // 2) Cost fan P10–P90 over time (cumulative, money → major unit).
  const cashFanSpec: ChartSpec | null = useMemo(() => {
    if (cashflow.length === 0) return null;
    const labels = cashflow.map((b) =>
      new Date(b.periodEndDate).toLocaleDateString(undefined, { month: "short", year: "2-digit" }),
    );
    const p10 = cashflow.map((b) => scaled(num(b.p10Cumulative)));
    const p50 = cashflow.map((b) => scaled(num(b.p50Cumulative)));
    const p90 = cashflow.map((b) => scaled(num(b.p90Cumulative)));
    const band = cashflow.map((b) => {
      const lo = num(b.p10Cumulative);
      const hi = num(b.p90Cumulative);
      return lo == null || hi == null ? null : scaled(hi - lo);
    });
    if (p50.every((d) => d == null)) return null;
    return {
      id: "cash-fan",
      title: "Cash forecast fan (cumulative)",
      type: "line",
      note: unitNote,
      option: {
        grid: { left: 48, right: 16, top: 16, bottom: 28 },
        tooltip: { trigger: "axis" },
        legend: { data: ["P50"], bottom: 0 },
        xAxis: { type: "category", boundaryGap: false, data: labels },
        yAxis: { type: "value" },
        series: [
          { name: "P10", type: "line", stack: "band", data: p10, symbol: "none", lineStyle: { opacity: 0 }, areaStyle: { opacity: 0 } },
          { name: "P10–P90", type: "line", stack: "band", data: band, symbol: "none", lineStyle: { opacity: 0 }, areaStyle: { color: "#D4AF37", opacity: 0.16 } },
          { name: "P50", type: "line", data: p50, symbol: "none", smooth: true, lineStyle: { width: 2, color: "#B8962E" }, itemStyle: { color: "#B8962E" } },
        ],
      },
    };
  }, [cashflow, scale, unitNote]);

  // 3) EAC vs BAC (money → major unit).
  const eacSpec: ChartSpec | null = useMemo(() => {
    if (!evm) return null;
    const data = [
      scaled(num(evm.budgetAtCompletion)),
      scaled(num(evm.earnedValue)),
      scaled(num(evm.actualCost)),
      scaled(num(evm.estimateAtCompletion)),
    ];
    if (data.every((d) => d == null)) return null;
    return {
      id: "eac-bac",
      title: "EAC vs BAC",
      type: "bar",
      note: unitNote,
      option: {
        grid: { left: 48, right: 16, top: 16, bottom: 28 },
        tooltip: { trigger: "axis" },
        xAxis: { type: "category", data: ["BAC", "EV", "AC", "EAC"] },
        yAxis: { type: "value" },
        series: [{ type: "bar", barMaxWidth: 48, data }],
      },
    };
  }, [evm, scale, unitNote]);

  // 4) CPI / SPI trend.
  const trendSpec: ChartSpec | null = useMemo(() => {
    if (evmHistory.length < 2) return null;
    const sorted = [...evmHistory].sort(
      (a, b) => new Date(a.dataDate).getTime() - new Date(b.dataDate).getTime(),
    );
    const labels = sorted.map((r) =>
      new Date(r.dataDate).toLocaleDateString(undefined, { month: "short", day: "numeric" }),
    );
    return {
      id: "cpi-spi",
      title: "CPI / SPI trend",
      type: "line",
      note: "1.0 = on plan",
      option: {
        grid: { left: 40, right: 16, top: 20, bottom: 40 },
        tooltip: { trigger: "axis" },
        legend: { data: ["CPI", "SPI"], bottom: 0 },
        xAxis: { type: "category", boundaryGap: false, data: labels },
        yAxis: { type: "value", scale: true },
        series: [
          {
            name: "CPI",
            type: "line",
            smooth: true,
            symbol: "circle",
            data: sorted.map((r) => +(r.costPerformanceIndex ?? 0).toFixed(3)),
            markLine: {
              symbol: "none",
              data: [{ yAxis: 1 }],
              lineStyle: { color: "#64748B", type: "dashed" },
              label: { show: false },
            },
          },
          {
            name: "SPI",
            type: "line",
            smooth: true,
            symbol: "circle",
            data: sorted.map((r) => +(r.schedulePerformanceIndex ?? 0).toFixed(3)),
          },
        ],
      },
    };
  }, [evmHistory]);

  const vac = num(evm?.varianceAtCompletion);
  const cpi = num(evm?.costPerformanceIndex);

  return (
    <div className="px-6 pb-10">
      <PageHeader
        title="Forecast"
        description="Where this project is headed — completion, cost at completion, and performance trend."
      />

      {/* KPI strip */}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Kpi
          label="P50 duration"
          value={sim ? `${Math.round(sim.confidenceP50Duration)}d` : "—"}
          sub={sim ? `P80 ${Math.round(sim.confidenceP80Duration)}d` : "no simulation"}
          icon={<Calendar size={14} />}
        />
        <Kpi
          label="EAC"
          value={evm ? currency.moneyCompact(num(evm.estimateAtCompletion) ?? 0) : "—"}
          sub={evm ? `BAC ${currency.moneyCompact(num(evm.budgetAtCompletion) ?? 0)}` : "no EVM run"}
          icon={<TrendingUp size={14} />}
        />
        <Kpi
          label="VAC"
          value={vac != null ? currency.moneyCompact(vac) : "—"}
          sub={vac != null ? (vac >= 0 ? "under budget" : "over budget") : undefined}
          icon={<TrendingUp size={14} />}
          tone={vac == null ? "text-charcoal" : vac >= 0 ? "text-emerald" : "text-burgundy"}
        />
        <Kpi
          label="CPI"
          value={cpi != null ? cpi.toFixed(2) : "—"}
          sub={cpi != null ? (cpi >= 1 ? "cost efficient" : "cost overrun") : undefined}
          icon={<Gauge size={14} />}
          tone={cpi == null ? "text-charcoal" : cpi >= 1 ? "text-emerald" : "text-burgundy"}
        />
      </div>

      {/* Charts */}
      <div className="mt-5 grid gap-4 lg:grid-cols-2">
        {completionSpec ? (
          <EChart spec={completionSpec} height={264} />
        ) : (
          <EmptyChart title="Completion confidence" hint="Run a Monte Carlo simulation to see the P10–P90 completion spread." />
        )}
        {cashFanSpec ? (
          <EChart spec={cashFanSpec} height={264} />
        ) : (
          <EmptyChart title="Cash forecast fan" hint="Cash-flow percentiles appear once a Monte Carlo simulation with cashflow buckets exists." />
        )}
        {eacSpec ? (
          <EChart spec={eacSpec} height={264} />
        ) : (
          <EmptyChart title="EAC vs BAC" hint="Calculate EVM for this project to compare estimate at completion against budget." />
        )}
        {trendSpec ? (
          <EChart spec={trendSpec} height={264} />
        ) : (
          <EmptyChart title="CPI / SPI trend" hint="At least two EVM data dates are needed to plot a performance trend." />
        )}
      </div>

      {/* Forecasting agent findings */}
      {forecastFindings.length > 0 && (
        <div className="mt-8">
          <h2 className="mb-3 font-display text-sm font-semibold uppercase tracking-[0.16em] text-slate">
            Forecasting agent findings
          </h2>
          <div className="space-y-4">
            {forecastFindings.map((f) => (
              <FindingCard key={f.id} finding={f} agentName={agentNames[f.agentKey]} projectId={projectId} />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
