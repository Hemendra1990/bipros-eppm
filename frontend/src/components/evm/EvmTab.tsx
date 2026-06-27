"use client";

import { useState, useMemo } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { costApi, type WbsEvmRow, type CostSummary } from "@/lib/api/costApi";
import { periodPerformanceApi, type PeriodPerformanceRollup } from "@/lib/api/periodPerformanceApi";

// Only the forecast methods the EVM tab supports (Manual / Management-Override were removed —
// they require an EAC input the tab does not collect).
type EtcMethod = "CPI_BASED" | "SPI_BASED" | "CPI_SPI_COMPOSITE";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from "recharts";
import { VirtualDataTable } from "@/components/common/VirtualDataTable";
import type { ColumnDef } from "@tanstack/react-table";
import { KpiTile } from "@/components/common/KpiTile";
import { budgetApi } from "@/lib/api/budgetApi";
import { formatMoney } from "@/lib/currency/format";
import { CHART_TOOLTIP_STYLE, CHART_TOOLTIP_LABEL_STYLE, CHART_TOOLTIP_ITEM_STYLE } from "@/components/common/dashboard/primitives";

/**
 * Money formatter for the EVM tab. Delegates to the canonical per-currency
 * compact formatter so INR uses k / Lakh / Crore (en-IN) and every other
 * currency uses K / M / B (en-US). Input is a RAW amount (EVM metrics are
 * already in the project's currency).
 */
function makeFormatter(currency: string) {
  const code = (currency ?? "INR").toUpperCase();
  return (value: number | null | undefined): string =>
    formatMoney(value ?? 0, { code }, { compact: true });
}

function etcForecast(s: CostSummary, method: EtcMethod) {
  const { bac, earnedValue: ev, totalActual: ac,
          costPerformanceIndex: cpi, schedulePerformanceIndex: spi } = s;
  if (method === "CPI_BASED")
    return { eac: s.estimateAtCompletion, etc: s.estimateToComplete, vac: s.varianceAtCompletion };
  let eac = bac;
  if (method === "SPI_BASED") eac = spi ? ac + (bac - ev) / spi : bac;
  else if (method === "CPI_SPI_COMPOSITE") eac = cpi && spi ? ac + (bac - ev) / (cpi * spi) : bac;
  return { eac, etc: eac - ac, vac: bac - eac };
}

const ETC_METHODS: { value: EtcMethod; label: string }[] = [
  { value: "CPI_BASED", label: "CPI-Based" },
  { value: "SPI_BASED", label: "SPI-Based" },
  { value: "CPI_SPI_COMPOSITE", label: "CPI × SPI Composite" },
];

const fmtIdx = (v: number | null | undefined) => (v ?? 0).toFixed(2);
const fmtPct = (v: number | null | undefined) => `${(v ?? 0).toFixed(1)}%`;


export function EvmTab({ projectId }: { projectId: string }) {
  const queryClient = useQueryClient();
  const [etcMethod, setEtcMethod] = useState<EtcMethod>("CPI_BASED");
  const [activeTab, setActiveTab] = useState<"summary" | "wbs">("summary");

  const { data: budgetData } = useQuery({
    queryKey: ["project-budget", projectId],
    queryFn: () => budgetApi.getBudgetSummary(projectId),
  });

  const currency = budgetData?.data?.budgetCurrency ?? "INR";
  const fmt = makeFormatter(currency);

  const { data: metricsData, isLoading: isLoadingMetrics } = useQuery({
    queryKey: ["evm-cost-summary", projectId],
    queryFn: () => costApi.getCostSummary(projectId),
  });

  const { data: historyData, isLoading: isLoadingHistory } = useQuery({
    queryKey: ["evm-perf-scurve", projectId],
    queryFn: () => periodPerformanceApi.getPerformanceRollup(projectId, "M"),
  });

  const { data: wbsData, isLoading: isLoadingWbs } = useQuery({
    queryKey: ["evm-wbs-cost", projectId],
    queryFn: () => costApi.getEvmByWbs(projectId),
    enabled: activeTab === "wbs",
  });

  const summary = metricsData?.data as CostSummary | undefined;
  const fc = summary ? etcForecast(summary, etcMethod) : null;

  const chartData = (() => {
    const rows = (historyData?.data as PeriodPerformanceRollup[] | undefined) ?? [];
    let cumPv = 0, cumEv = 0, cumAc = 0;
    return rows.map((p) => {
      cumPv += p.plannedValue ?? 0;
      cumEv += p.earnedValue ?? 0;
      cumAc += p.actualCost ?? 0;
      return { periodDate: p.periodName, pv: cumPv, ev: cumEv, ac: cumAc };
    });
  })();

  const wbsRows = (wbsData?.data as WbsEvmRow[] | undefined) ?? [];

  const wbsColumns = useMemo<ColumnDef<WbsEvmRow>[]>(
    () => [
      {
        accessorKey: "name",
        header: "WBS",
        cell: (info) => {
          const row = info.row.original;
          return (
            <div className="text-sm px-3">
              <span className="text-text-secondary">{row.code}</span>{" "}
              <span className="text-text-primary">{row.name}</span>
            </div>
          );
        },
      },
      {
        accessorKey: "bac",
        header: "BAC",
        cell: (info) => (
          <span className="block text-right text-sm">
            {fmt(Number(info.getValue()))}
          </span>
        ),
      },
      {
        accessorKey: "plannedValue",
        header: "PV",
        cell: (info) => (
          <span className="block text-right text-sm text-accent">
            {fmt(Number(info.getValue()))}
          </span>
        ),
      },
      {
        accessorKey: "earnedValue",
        header: "EV",
        cell: (info) => (
          <span className="block text-right text-sm text-success">
            {fmt(Number(info.getValue()))}
          </span>
        ),
      },
      {
        accessorKey: "actualCost",
        header: "AC",
        cell: (info) => (
          <span className="block text-right text-sm text-danger">
            {fmt(Number(info.getValue()))}
          </span>
        ),
      },
      {
        accessorKey: "scheduleVariance",
        header: "SV",
        cell: (info) => {
          const val = Number(info.getValue());
          const color = val >= 0 ? "text-success" : "text-danger";
          return (
            <span className={`block text-right text-sm ${color}`}>
              {fmt(val)}
            </span>
          );
        },
      },
      {
        accessorKey: "costVariance",
        header: "CV",
        cell: (info) => {
          const val = Number(info.getValue());
          const color = val >= 0 ? "text-success" : "text-danger";
          return (
            <span className={`block text-right text-sm ${color}`}>
              {fmt(val)}
            </span>
          );
        },
      },
      {
        accessorKey: "schedulePerformanceIndex",
        header: "SPI",
        cell: (info) => (
          <span className="block text-right text-sm">
            {fmtIdx(Number(info.getValue()))}
          </span>
        ),
      },
      {
        accessorKey: "costPerformanceIndex",
        header: "CPI",
        cell: (info) => (
          <span className="block text-right text-sm">
            {fmtIdx(Number(info.getValue()))}
          </span>
        ),
      },
    ],
    []
  );

  return (
    <div className="space-y-6">
      {/* <AiInsightsPanel projectId={projectId} endpoint={`/v1/projects/${projectId}/evm/ai/insights`} /> */}
      {/* Controls */}
      <div className="flex flex-wrap items-end gap-4">
        <div>
          <label className="mb-1 block text-xs font-medium text-text-secondary">ETC Method</label>
          <select
            value={etcMethod}
            onChange={(e) => setEtcMethod(e.target.value as EtcMethod)}
            className="rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none"
          >
            {ETC_METHODS.map((m) => (
              <option key={m.value} value={m.value}>
                {m.label}
              </option>
            ))}
          </select>
        </div>
        <button
          onClick={() => {
            queryClient.invalidateQueries({ queryKey: ["evm-cost-summary", projectId] });
            queryClient.invalidateQueries({ queryKey: ["evm-wbs-cost", projectId] });
            queryClient.invalidateQueries({ queryKey: ["evm-perf-scurve", projectId] });
          }}
          className="rounded-md bg-accent px-6 py-2 text-sm font-medium text-accent-foreground hover:bg-accent-hover disabled:bg-surface-active"
        >
          Calculate EVM
        </button>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-border">
        <button
          onClick={() => setActiveTab("summary")}
          className={`px-4 py-2 text-sm font-medium ${
            activeTab === "summary"
              ? "border-b-2 border-accent text-accent"
              : "text-text-secondary hover:text-text-primary"
          }`}
        >
          Summary
        </button>
        <button
          onClick={() => setActiveTab("wbs")}
          className={`px-4 py-2 text-sm font-medium ${
            activeTab === "wbs"
              ? "border-b-2 border-accent text-accent"
              : "text-text-secondary hover:text-text-primary"
          }`}
        >
          WBS Drill-Down
        </button>
      </div>

      {activeTab === "summary" && (
        <>
          {isLoadingMetrics ? (
            <div className="text-center text-text-secondary">Loading EVM metrics...</div>
          ) : (
            <>
              <div>
                <h3 className="mb-3 text-sm font-semibold text-text-secondary">Basic Values</h3>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  <KpiTile label="PV (Planned Value)" value={fmt(summary?.plannedValue)} />
                  <KpiTile label="EV (Earned Value)" value={fmt(summary?.earnedValue)} />
                  <KpiTile label="AC (Actual Cost)" value={fmt(summary?.totalActual)} />
                </div>
              </div>

              <div>
                <h3 className="mb-3 text-sm font-semibold text-text-secondary">Performance Metrics</h3>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
                  <KpiTile
                    label="SV (Schedule Var.)"
                    value={fmt(summary?.scheduleVariance)}
                    tone={summary && summary.scheduleVariance >= 0 ? "success" : "danger"}
                  />
                  <KpiTile
                    label="CV (Cost Var.)"
                    value={fmt(summary?.costVariance)}
                    tone={summary && summary.costVariance >= 0 ? "success" : "danger"}
                  />
                  <KpiTile
                    label="SPI"
                    value={fmtIdx(summary?.schedulePerformanceIndex)}
                    tone={summary && (summary.schedulePerformanceIndex ?? 0) >= 1 ? "success" : "danger"}
                  />
                  <KpiTile
                    label="CPI"
                    value={fmtIdx(summary?.costPerformanceIndex)}
                    tone={summary && (summary.costPerformanceIndex ?? 0) >= 1 ? "success" : "danger"}
                  />
                </div>
              </div>

              <div>
                <h3 className="mb-3 text-sm font-semibold text-text-secondary">Completion Metrics</h3>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
                  <KpiTile
                    label="EAC"
                    value={fmt(fc?.eac)}
                    tone={
                      fc && summary && fc.eac <= summary.bac
                        ? "success"
                        : "danger"
                    }
                  />
                  <KpiTile label="ETC" value={fmt(fc?.etc)} />
                  <KpiTile
                    label="VAC"
                    value={fmt(fc?.vac)}
                    tone={fc && fc.vac >= 0 ? "success" : "danger"}
                  />
                  <KpiTile
                    label="TCPI"
                    value={fmtIdx(summary?.toCompletePerformanceIndex)}
                    tone={summary && (summary.toCompletePerformanceIndex ?? 0) <= 1 ? "success" : "danger"}
                  />
                  <KpiTile label="Perf. %" value={fmtPct((summary?.costPercentComplete ?? 0) * 100)} tone="accent" />
                </div>
              </div>
            </>
          )}

          {isLoadingHistory ? (
            <div className="text-center text-text-secondary">Loading EVM history...</div>
          ) : chartData.length === 0 ? (
            <div className="rounded-lg border border-dashed border-border py-12 text-center">
              <h3 className="text-lg font-medium text-text-primary">No History</h3>
              <p className="mt-2 text-text-secondary">Calculate EVM to generate historical data.</p>
            </div>
          ) : (
            <div className="rounded-lg border border-border bg-surface/50 p-6 shadow-sm">
              <h3 className="mb-4 text-lg font-semibold text-text-primary">EVM S-Curve</h3>
              <ResponsiveContainer width="100%" height={400}>
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis
                    dataKey="periodDate"
                    tick={{ fontSize: 12 }}
                    angle={-45}
                    textAnchor="end"
                    height={100}
                  />
                  <YAxis
                    tick={{ fontSize: 12 }}
                    tickFormatter={(v: number) => fmt(v)}
                    width={80}
                  />
                  <Tooltip
                    formatter={(value) => fmt(Number(value))}
                    labelFormatter={(label) => `Date: ${label}`}
                    contentStyle={CHART_TOOLTIP_STYLE}
                    labelStyle={CHART_TOOLTIP_LABEL_STYLE}
                    itemStyle={CHART_TOOLTIP_ITEM_STYLE}
                  />
                  <Legend />
                  <Line
                    type="monotone"
                    dataKey="pv"
                    stroke="#3b82f6"
                    name="Planned Value (PV)"
                    strokeWidth={2}
                  />
                  <Line
                    type="monotone"
                    dataKey="ev"
                    stroke="#10b981"
                    name="Earned Value (EV)"
                    strokeWidth={2}
                  />
                  <Line
                    type="monotone"
                    dataKey="ac"
                    stroke="#ef4444"
                    name="Actual Cost (AC)"
                    strokeWidth={2}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </>
      )}

      {activeTab === "wbs" && (
        <div className="rounded-lg border border-border bg-surface/50 shadow-sm">
          {isLoadingWbs ? (
            <div className="py-12 text-center text-text-secondary">Loading WBS EVM data...</div>
          ) : wbsRows.length === 0 ? (
            <div className="py-12 text-center">
              <h3 className="text-lg font-medium text-text-primary">No WBS Data</h3>
              <p className="mt-2 text-text-secondary">
                Calculate EVM to generate WBS-level metrics.
              </p>
            </div>
          ) : (
            <VirtualDataTable
              columns={wbsColumns}
              data={wbsRows}
              sortable={false}
              resizable
              searchable={false}
            />
          )}
        </div>
      )}
    </div>
  );
}
