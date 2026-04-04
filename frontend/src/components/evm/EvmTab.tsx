"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { evmApi } from "@/lib/api/evmApi";
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

interface MetricCard {
  label: string;
  value: string;
  color: string;
}

interface EvmDataPoint {
  periodDate: string;
  pv: number;
  ev: number;
  ac: number;
}

export function EvmTab({ projectId }: { projectId: string }) {
  const queryClient = useQueryClient();
  const [calculating, setCalculating] = useState(false);

  const { data: metricsData, isLoading: isLoadingMetrics } = useQuery({
    queryKey: ["evm-metrics", projectId],
    queryFn: () => evmApi.getLatest(projectId),
  });

  const { data: historyData, isLoading: isLoadingHistory } = useQuery({
    queryKey: ["evm-history", projectId],
    queryFn: () => evmApi.getHistory(projectId),
  });

  const calculateMutation = useMutation({
    mutationFn: () => evmApi.calculateEvm(projectId, "ACTIVITY_PERCENT_COMPLETE", "CPI_BASED"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["evm-metrics", projectId] });
      queryClient.invalidateQueries({ queryKey: ["evm-history", projectId] });
      setCalculating(false);
    },
  });

  const metrics = metricsData?.data;
  const chartData: EvmDataPoint[] = historyData?.data ?? [];

  const metricCards: MetricCard[] = [
    { label: "PV", value: `$${(metrics?.pv ?? 0).toFixed(2)}`, color: "blue" },
    { label: "EV", value: `$${(metrics?.ev ?? 0).toFixed(2)}`, color: "green" },
    { label: "AC", value: `$${(metrics?.ac ?? 0).toFixed(2)}`, color: "red" },
  ];

  const performanceCards: MetricCard[] = [
    { label: "SV", value: `$${(metrics?.sv ?? 0).toFixed(2)}`, color: "purple" },
    { label: "CV", value: `$${(metrics?.cv ?? 0).toFixed(2)}`, color: "indigo" },
    {
      label: "SPI",
      value: `${(metrics?.spi ?? 0).toFixed(2)}`,
      color: "cyan",
    },
    {
      label: "CPI",
      value: `${(metrics?.cpi ?? 0).toFixed(2)}`,
      color: "pink",
    },
  ];

  const completionCards: MetricCard[] = [
    { label: "EAC", value: `$${(metrics?.eac ?? 0).toFixed(2)}`, color: "orange" },
    { label: "ETC", value: `$${(metrics?.etc ?? 0).toFixed(2)}`, color: "yellow" },
    { label: "VAC", value: `$${(metrics?.vac ?? 0).toFixed(2)}`, color: "lime" },
    {
      label: "TCPI",
      value: `${(metrics?.tcpi ?? 0).toFixed(2)}`,
      color: "slate",
    },
  ];

  const colorMap: Record<string, string> = {
    blue: "bg-blue-50 border-blue-200 text-blue-700",
    green: "bg-green-50 border-green-200 text-green-700",
    red: "bg-red-50 border-red-200 text-red-700",
    purple: "bg-purple-50 border-purple-200 text-purple-700",
    indigo: "bg-indigo-50 border-indigo-200 text-indigo-700",
    cyan: "bg-cyan-50 border-cyan-200 text-cyan-700",
    pink: "bg-pink-50 border-pink-200 text-pink-700",
    orange: "bg-orange-50 border-orange-200 text-orange-700",
    yellow: "bg-yellow-50 border-yellow-200 text-yellow-700",
    lime: "bg-lime-50 border-lime-200 text-lime-700",
    slate: "bg-slate-50 border-slate-200 text-slate-700",
  };

  return (
    <div className="space-y-6">
      <div>
        <button
          onClick={() => {
            setCalculating(true);
            calculateMutation.mutate();
          }}
          disabled={calculateMutation.isPending}
          className="rounded-md bg-blue-600 px-6 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-gray-400"
        >
          {calculateMutation.isPending ? "Calculating..." : "Calculate EVM"}
        </button>
      </div>

      {isLoadingMetrics ? (
        <div className="text-center text-gray-500">Loading EVM metrics...</div>
      ) : (
        <>
          <div>
            <h3 className="mb-3 text-sm font-semibold text-gray-700">Basic Values</h3>
            <div className="grid grid-cols-3 gap-4">
              {metricCards.map((card) => (
                <div
                  key={card.label}
                  className={`rounded-lg border p-4 ${colorMap[card.color]}`}
                >
                  <p className="text-xs font-medium text-gray-600">{card.label}</p>
                  <p className="mt-2 text-xl font-bold">{card.value}</p>
                </div>
              ))}
            </div>
          </div>

          <div>
            <h3 className="mb-3 text-sm font-semibold text-gray-700">Performance Metrics</h3>
            <div className="grid grid-cols-4 gap-4">
              {performanceCards.map((card) => (
                <div
                  key={card.label}
                  className={`rounded-lg border p-4 ${colorMap[card.color]}`}
                >
                  <p className="text-xs font-medium text-gray-600">{card.label}</p>
                  <p className="mt-2 text-xl font-bold">{card.value}</p>
                </div>
              ))}
            </div>
          </div>

          <div>
            <h3 className="mb-3 text-sm font-semibold text-gray-700">Completion Metrics</h3>
            <div className="grid grid-cols-4 gap-4">
              {completionCards.map((card) => (
                <div
                  key={card.label}
                  className={`rounded-lg border p-4 ${colorMap[card.color]}`}
                >
                  <p className="text-xs font-medium text-gray-600">{card.label}</p>
                  <p className="mt-2 text-xl font-bold">{card.value}</p>
                </div>
              ))}
            </div>
          </div>
        </>
      )}

      {isLoadingHistory ? (
        <div className="text-center text-gray-500">Loading EVM history...</div>
      ) : chartData.length === 0 ? (
        <div className="rounded-lg border border-dashed border-gray-300 py-12 text-center">
          <h3 className="text-lg font-medium text-gray-900">No History</h3>
          <p className="mt-2 text-gray-500">Calculate EVM to generate historical data.</p>
        </div>
      ) : (
        <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
          <h3 className="mb-4 text-lg font-semibold text-gray-900">EVM S-Curve</h3>
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
              <YAxis tick={{ fontSize: 12 }} />
              <Tooltip
                formatter={(value) => `$${Number(value).toFixed(2)}`}
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
    </div>
  );
}
