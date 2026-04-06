"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  costApi,
  type ForecastMethod,
  type CashFlowForecastItem,
  type PeriodCostAggregation,
} from "@/lib/api/costApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
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

interface SummaryCard {
  label: string;
  value: string;
  color: string;
}

interface ExpenseRow {
  id: string;
  description: string;
  amount: number;
  category: string;
  expenseDate: string;
}

const FORECAST_METHODS: { value: ForecastMethod; label: string }[] = [
  { value: "LINEAR", label: "Linear" },
  { value: "CPI_BASED", label: "CPI-Based" },
  { value: "SPI_CPI_COMPOSITE", label: "SPI×CPI Composite" },
];

export function CostsTab({ projectId }: { projectId: string }) {
  const [forecastMethod, setForecastMethod] = useState<ForecastMethod>("LINEAR");

  const { data: summaryData, isLoading: isLoadingSummary } = useQuery({
    queryKey: ["cost-summary", projectId],
    queryFn: () => costApi.getCostSummary(projectId),
  });

  const { data: expensesData, isLoading: isLoadingExpenses } = useQuery({
    queryKey: ["expenses", projectId],
    queryFn: () => costApi.getExpensesByProject(projectId, 0, 100),
  });

  const { data: forecastData } = useQuery({
    queryKey: ["cost-forecast", projectId, forecastMethod],
    queryFn: () => costApi.generateForecast(projectId, forecastMethod),
  });

  const { data: periodData } = useQuery({
    queryKey: ["cost-periods", projectId],
    queryFn: () => costApi.getCostPeriods(projectId),
  });

  const summary = summaryData?.data;
  const expenses = expensesData?.data?.content ?? [];
  const forecastItems: CashFlowForecastItem[] = forecastData?.data ?? [];
  const periodAggregations: PeriodCostAggregation[] = periodData?.data ?? [];

  const chartData = forecastItems.map((item) => ({
    period: item.period,
    planned: item.plannedAmount || 0,
    actual: item.actualAmount || 0,
    forecast: item.forecastAmount || 0,
    cumulativePlanned: item.cumulativePlanned || 0,
    cumulativeActual: item.cumulativeActual || 0,
    cumulativeForecast: item.cumulativeForecast || 0,
  }));

  const summaryCards: SummaryCard[] = [
    {
      label: "Total Budget",
      value: `$${(summary?.totalBudget ?? 0).toFixed(2)}`,
      color: "blue",
    },
    {
      label: "Total Actual",
      value: `$${(summary?.totalActual ?? 0).toFixed(2)}`,
      color: "green",
    },
    {
      label: "Total Remaining",
      value: `$${(summary?.totalRemaining ?? 0).toFixed(2)}`,
      color: "yellow",
    },
    {
      label: "At Completion",
      value: `$${(summary?.atCompletion ?? 0).toFixed(2)}`,
      color: "purple",
    },
  ];

  const evmCards: SummaryCard[] = summary
    ? [
        {
          label: "Cost Variance (CV)",
          value: `$${summary.costVariance.toFixed(2)}`,
          color: summary.costVariance >= 0 ? "green" : "red",
        },
        {
          label: "CPI",
          value: summary.costPerformanceIndex.toFixed(4),
          color: summary.costPerformanceIndex >= 1 ? "green" : "red",
        },
        {
          label: "Expenses",
          value: String(summary.expenseCount),
          color: "slate",
        },
      ]
    : [];

  const expenseColumns: ColumnDef<ExpenseRow>[] = [
    { key: "description", label: "Description", sortable: true },
    { key: "category", label: "Category", sortable: true },
    {
      key: "amount",
      label: "Amount",
      sortable: true,
      render: (value) => `$${Number(value).toFixed(2)}`,
    },
    { key: "expenseDate", label: "Date", sortable: true },
  ];

  const colorMap: Record<string, string> = {
    blue: "bg-blue-950 border-blue-700",
    green: "bg-green-950 border-green-700",
    yellow: "bg-yellow-950 border-yellow-700",
    purple: "bg-purple-950 border-purple-700",
    red: "bg-red-950 border-red-700",
    slate: "bg-slate-900 border-slate-700",
  };

  const textColorMap: Record<string, string> = {
    blue: "text-blue-300",
    green: "text-green-300",
    yellow: "text-yellow-300",
    purple: "text-purple-300",
    red: "text-red-300",
    slate: "text-slate-300",
  };

  return (
    <div className="space-y-6">
      {isLoadingSummary ? (
        <div className="text-center text-slate-400">Loading cost summary...</div>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            {summaryCards.map((card) => (
              <div
                key={card.label}
                className={`rounded-lg border p-4 ${colorMap[card.color]}`}
              >
                <h3 className="text-sm font-medium text-slate-300">{card.label}</h3>
                <p className={`mt-2 text-2xl font-bold ${textColorMap[card.color]}`}>
                  {card.value}
                </p>
              </div>
            ))}
          </div>

          {evmCards.length > 0 && (
            <div className="grid grid-cols-3 gap-4">
              {evmCards.map((card) => (
                <div
                  key={card.label}
                  className={`rounded-lg border p-4 ${colorMap[card.color]}`}
                >
                  <h3 className="text-sm font-medium text-slate-300">{card.label}</h3>
                  <p className={`mt-2 text-xl font-bold ${textColorMap[card.color]}`}>
                    {card.value}
                  </p>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {/* Cash Flow S-Curve with Forecast Method Selector */}
      <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-semibold text-white">Cash Flow S-Curve</h3>
          <div className="flex items-center gap-2">
            <label className="text-sm text-slate-400">Forecast Method:</label>
            <select
              value={forecastMethod}
              onChange={(e) => setForecastMethod(e.target.value as ForecastMethod)}
              className="rounded-md border border-slate-700 bg-slate-800 px-3 py-1.5 text-sm text-white focus:border-blue-500 focus:outline-none"
            >
              {FORECAST_METHODS.map((m) => (
                <option key={m.value} value={m.value}>
                  {m.label}
                </option>
              ))}
            </select>
          </div>
        </div>
        {chartData.length === 0 ? (
          <div className="rounded-lg border border-dashed border-slate-700 py-12 text-center">
            <p className="text-slate-400">No forecast data available. Create financial periods and expenses first.</p>
          </div>
        ) : (
          <ResponsiveContainer width="100%" height={400}>
            <LineChart data={chartData} margin={{ top: 5, right: 30, left: 0, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
              <XAxis
                dataKey="period"
                stroke="#64748b"
                style={{ fontSize: "12px" }}
              />
              <YAxis
                stroke="#64748b"
                style={{ fontSize: "12px" }}
                label={{ value: "Amount ($)", angle: -90, position: "insideLeft" }}
              />
              <Tooltip
                contentStyle={{
                  backgroundColor: "#1e293b",
                  border: "1px solid #334155",
                  borderRadius: "0.5rem",
                }}
                formatter={(value) =>
                  typeof value === "number"
                    ? `$${value.toLocaleString("en-US", {
                        minimumFractionDigits: 0,
                        maximumFractionDigits: 0,
                      })}`
                    : String(value ?? "")
                }
              />
              <Legend />
              <Line
                type="monotone"
                dataKey="cumulativePlanned"
                name="Planned"
                stroke="#3b82f6"
                strokeWidth={2}
                dot={false}
              />
              <Line
                type="monotone"
                dataKey="cumulativeActual"
                name="Actual"
                stroke="#10b981"
                strokeWidth={2}
                dot={false}
              />
              <Line
                type="monotone"
                dataKey="cumulativeForecast"
                name="Forecast"
                stroke="#f59e0b"
                strokeWidth={2}
                strokeDasharray="5 5"
                dot={false}
              />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>

      {/* Period-by-Period Cost Table */}
      {periodAggregations.length > 0 && (
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
          <h3 className="mb-4 text-lg font-semibold text-white">
            Period Cost Breakdown
          </h3>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-700 text-left text-slate-400">
                  <th className="px-3 py-2">Period</th>
                  <th className="px-3 py-2 text-right">Budget</th>
                  <th className="px-3 py-2 text-right">Actual</th>
                  <th className="px-3 py-2 text-right">Variance</th>
                  <th className="px-3 py-2 text-right">Earned Value</th>
                  <th className="px-3 py-2 text-right">Planned Value</th>
                </tr>
              </thead>
              <tbody>
                {periodAggregations.map((pa) => (
                  <tr
                    key={pa.periodId}
                    className="border-b border-slate-800 hover:bg-slate-800/50"
                  >
                    <td className="px-3 py-2 text-white">{pa.periodName}</td>
                    <td className="px-3 py-2 text-right text-blue-300">
                      ${pa.budget.toFixed(2)}
                    </td>
                    <td className="px-3 py-2 text-right text-green-300">
                      ${pa.actual.toFixed(2)}
                    </td>
                    <td
                      className={`px-3 py-2 text-right ${
                        pa.variance >= 0 ? "text-green-300" : "text-red-300"
                      }`}
                    >
                      ${pa.variance.toFixed(2)}
                    </td>
                    <td className="px-3 py-2 text-right text-yellow-300">
                      ${pa.earnedValue.toFixed(2)}
                    </td>
                    <td className="px-3 py-2 text-right text-purple-300">
                      ${pa.plannedValue.toFixed(2)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <div>
        <h3 className="mb-4 text-lg font-semibold text-white">Expenses</h3>
        {isLoadingExpenses ? (
          <div className="text-center text-slate-500">Loading expenses...</div>
        ) : expenses.length === 0 ? (
          <div className="rounded-lg border border-dashed border-slate-700 py-12 text-center">
            <h3 className="text-lg font-medium text-white">No Expenses</h3>
            <p className="mt-2 text-slate-500">No expenses recorded yet.</p>
          </div>
        ) : (
          <DataTable columns={expenseColumns} data={expenses} rowKey="id" />
        )}
      </div>
    </div>
  );
}
