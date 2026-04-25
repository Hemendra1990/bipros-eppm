"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  evmApi,
  type EvmTechnique,
  type EtcMethod,
  type WbsEvmNode,
  type EvmCalculationResult,
} from "@/lib/api/evmApi";
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
import { KpiTile } from "@/components/common/KpiTile";

// EVM endpoints (and the seeded data) carry absolute INR values — e.g. BAC = 4,850,000,000.
// Display in ₹cr to stay consistent with the Costs tab, which uses the same convention
// (1 crore = 10,000,000 INR). Indices (SPI / CPI / TCPI) and the percent-complete remain
// unitless and use their own formatters.
const INR_PER_CRORE = 10_000_000;

function formatInrAsCrores(inr: number | null | undefined): string {
  const cr = (inr ?? 0) / INR_PER_CRORE;
  return `₹${cr.toLocaleString("en-IN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}cr`;
}

const TECHNIQUES: { value: EvmTechnique; label: string }[] = [
  { value: "ACTIVITY_PERCENT_COMPLETE", label: "Activity % Complete" },
  { value: "ZERO_ONE_HUNDRED", label: "0/100" },
  { value: "FIFTY_FIFTY", label: "50/50" },
  { value: "WEIGHTED_STEPS", label: "Weighted Steps" },
  { value: "LEVEL_OF_EFFORT", label: "Level of Effort" },
];

const ETC_METHODS: { value: EtcMethod; label: string }[] = [
  { value: "CPI_BASED", label: "CPI-Based" },
  { value: "SPI_BASED", label: "SPI-Based" },
  { value: "CPI_SPI_COMPOSITE", label: "CPI × SPI Composite" },
  { value: "MANUAL", label: "Manual" },
  { value: "MANAGEMENT_OVERRIDE", label: "Management Override" },
];

const fmt = (v: number | null | undefined) => formatInrAsCrores(v);
const fmtIdx = (v: number | null | undefined) => (v ?? 0).toFixed(2);
const fmtPct = (v: number | null | undefined) => `${(v ?? 0).toFixed(1)}%`;

function WbsEvmRow({ node, depth = 0 }: { node: WbsEvmNode; depth?: number }) {
  const [expanded, setExpanded] = useState(depth < 1);
  const hasChildren = node.children && node.children.length > 0;
  const svColor = node.scheduleVariance >= 0 ? "text-success" : "text-danger";
  const cvColor = node.costVariance >= 0 ? "text-success" : "text-danger";

  return (
    <>
      <tr className="border-b border-border hover:bg-surface-hover/50">
        <td className="px-3 py-2 text-sm" style={{ paddingLeft: `${depth * 20 + 12}px` }}>
          {hasChildren && (
            <button
              onClick={() => setExpanded(!expanded)}
              className="mr-1 text-text-secondary hover:text-text-primary"
            >
              {expanded ? "\u25BC" : "\u25B6"}
            </button>
          )}
          <span className="text-text-secondary">{node.code}</span>{" "}
          <span className="text-text-primary">{node.name}</span>
        </td>
        <td className="px-3 py-2 text-right text-sm">{fmt(node.budgetAtCompletion)}</td>
        <td className="px-3 py-2 text-right text-sm text-accent">{fmt(node.plannedValue)}</td>
        <td className="px-3 py-2 text-right text-sm text-success">{fmt(node.earnedValue)}</td>
        <td className="px-3 py-2 text-right text-sm text-danger">{fmt(node.actualCost)}</td>
        <td className={`px-3 py-2 text-right text-sm ${svColor}`}>{fmt(node.scheduleVariance)}</td>
        <td className={`px-3 py-2 text-right text-sm ${cvColor}`}>{fmt(node.costVariance)}</td>
        <td className="px-3 py-2 text-right text-sm">{fmtIdx(node.schedulePerformanceIndex)}</td>
        <td className="px-3 py-2 text-right text-sm">{fmtIdx(node.costPerformanceIndex)}</td>
      </tr>
      {expanded &&
        hasChildren &&
        node.children.map((child) => (
          <WbsEvmRow key={child.wbsNodeId} node={child} depth={depth + 1} />
        ))}
    </>
  );
}

export function EvmTab({ projectId }: { projectId: string }) {
  const queryClient = useQueryClient();
  const [technique, setTechnique] = useState<EvmTechnique>("ACTIVITY_PERCENT_COMPLETE");
  const [etcMethod, setEtcMethod] = useState<EtcMethod>("CPI_BASED");
  const [activeTab, setActiveTab] = useState<"summary" | "wbs">("summary");

  const { data: metricsData, isLoading: isLoadingMetrics } = useQuery({
    queryKey: ["evm-metrics", projectId],
    queryFn: () => evmApi.getLatest(projectId),
  });

  const { data: historyData, isLoading: isLoadingHistory } = useQuery({
    queryKey: ["evm-history", projectId],
    queryFn: () => evmApi.getHistory(projectId),
  });

  const { data: wbsData, isLoading: isLoadingWbs } = useQuery({
    queryKey: ["evm-wbs", projectId, technique, etcMethod],
    queryFn: () => evmApi.getWbsTree(projectId, technique, etcMethod),
    enabled: activeTab === "wbs",
  });

  const calculateMutation = useMutation({
    mutationFn: () => evmApi.calculateEvm(projectId, technique, etcMethod),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["evm-metrics", projectId] });
      queryClient.invalidateQueries({ queryKey: ["evm-history", projectId] });
      queryClient.invalidateQueries({ queryKey: ["evm-wbs", projectId] });
    },
  });

  const metrics = metricsData?.data as EvmCalculationResult | undefined;

  const chartData =
    (historyData?.data as EvmCalculationResult[] | undefined)?.map((h) => ({
      periodDate: h.dataDate,
      pv: h.plannedValue,
      ev: h.earnedValue,
      ac: h.actualCost,
    })) ?? [];

  const wbsNodes = (wbsData?.data as WbsEvmNode[] | undefined) ?? [];

  return (
    <div className="space-y-6">
      {/* Controls */}
      <div className="flex flex-wrap items-end gap-4">
        <div>
          <label className="mb-1 block text-xs font-medium text-text-secondary">EVM Technique</label>
          <select
            value={technique}
            onChange={(e) => setTechnique(e.target.value as EvmTechnique)}
            className="rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none"
          >
            {TECHNIQUES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>
        </div>
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
          onClick={() => calculateMutation.mutate()}
          disabled={calculateMutation.isPending}
          className="rounded-md bg-accent px-6 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:bg-surface-active"
        >
          {calculateMutation.isPending ? "Calculating..." : "Calculate EVM"}
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
                  <KpiTile label="PV (Planned Value)" value={fmt(metrics?.plannedValue)} />
                  <KpiTile label="EV (Earned Value)" value={fmt(metrics?.earnedValue)} />
                  <KpiTile label="AC (Actual Cost)" value={fmt(metrics?.actualCost)} />
                </div>
              </div>

              <div>
                <h3 className="mb-3 text-sm font-semibold text-text-secondary">Performance Metrics</h3>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
                  <KpiTile
                    label="SV (Schedule Var.)"
                    value={fmt(metrics?.scheduleVariance)}
                    tone={metrics && metrics.scheduleVariance >= 0 ? "success" : "danger"}
                  />
                  <KpiTile
                    label="CV (Cost Var.)"
                    value={fmt(metrics?.costVariance)}
                    tone={metrics && metrics.costVariance >= 0 ? "success" : "danger"}
                  />
                  <KpiTile
                    label="SPI"
                    value={fmtIdx(metrics?.schedulePerformanceIndex)}
                    tone={metrics && metrics.schedulePerformanceIndex >= 1 ? "success" : "danger"}
                  />
                  <KpiTile
                    label="CPI"
                    value={fmtIdx(metrics?.costPerformanceIndex)}
                    tone={metrics && metrics.costPerformanceIndex >= 1 ? "success" : "danger"}
                  />
                </div>
              </div>

              <div>
                <h3 className="mb-3 text-sm font-semibold text-text-secondary">Completion Metrics</h3>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
                  <KpiTile
                    label="EAC"
                    value={fmt(metrics?.estimateAtCompletion)}
                    tone={
                      metrics && metrics.estimateAtCompletion <= metrics.budgetAtCompletion
                        ? "success"
                        : "danger"
                    }
                  />
                  <KpiTile label="ETC" value={fmt(metrics?.estimateToComplete)} />
                  <KpiTile
                    label="VAC"
                    value={fmt(metrics?.varianceAtCompletion)}
                    tone={metrics && metrics.varianceAtCompletion >= 0 ? "success" : "danger"}
                  />
                  <KpiTile
                    label="TCPI"
                    value={fmtIdx(metrics?.toCompletePerformanceIndex)}
                    tone={metrics && metrics.toCompletePerformanceIndex <= 1 ? "success" : "danger"}
                  />
                  <KpiTile label="Perf. %" value={fmtPct(metrics?.performancePercentComplete)} tone="accent" />
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
                    tickFormatter={(v: number) => `₹${(v / INR_PER_CRORE).toFixed(0)}cr`}
                    width={70}
                  />
                  <Tooltip
                    formatter={(value) => formatInrAsCrores(Number(value))}
                    labelFormatter={(label) => `Date: ${label}`}
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
          <div className="overflow-x-auto">
            {isLoadingWbs ? (
              <div className="py-12 text-center text-text-secondary">Loading WBS EVM data...</div>
            ) : wbsNodes.length === 0 ? (
              <div className="py-12 text-center">
                <h3 className="text-lg font-medium text-text-primary">No WBS Data</h3>
                <p className="mt-2 text-text-secondary">
                  Calculate EVM to generate WBS-level metrics.
                </p>
              </div>
            ) : (
              <table className="w-full text-left">
                <thead>
                  <tr className="border-b border-border bg-surface-hover/50">
                    <th className="px-3 py-3 text-xs font-semibold text-text-secondary">WBS</th>
                    <th className="px-3 py-3 text-right text-xs font-semibold text-text-secondary">
                      BAC
                    </th>
                    <th className="px-3 py-3 text-right text-xs font-semibold text-text-secondary">PV</th>
                    <th className="px-3 py-3 text-right text-xs font-semibold text-text-secondary">
                      EV
                    </th>
                    <th className="px-3 py-3 text-right text-xs font-semibold text-text-secondary">AC</th>
                    <th className="px-3 py-3 text-right text-xs font-semibold text-text-secondary">SV</th>
                    <th className="px-3 py-3 text-right text-xs font-semibold text-text-secondary">CV</th>
                    <th className="px-3 py-3 text-right text-xs font-semibold text-text-secondary">
                      SPI
                    </th>
                    <th className="px-3 py-3 text-right text-xs font-semibold text-text-secondary">
                      CPI
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {wbsNodes.map((node) => (
                    <WbsEvmRow key={node.wbsNodeId} node={node} />
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
