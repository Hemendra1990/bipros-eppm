"use client";

import { useQuery } from "@tanstack/react-query";
import { costApi } from "@/lib/api/costApi";
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

export function CostsTab({ projectId }: { projectId: string }) {
  const { data: summaryData, isLoading: isLoadingSummary } = useQuery({
    queryKey: ["cost-summary", projectId],
    queryFn: () => costApi.getCostSummary(projectId),
  });

  const { data: expensesData, isLoading: isLoadingExpenses } = useQuery({
    queryKey: ["expenses", projectId],
    queryFn: () => costApi.getExpensesByProject(projectId, 0, 100),
  });

  const { data: cashFlowData } = useQuery({
    queryKey: ["cash-flow", projectId],
    queryFn: () => costApi.getCashFlowForecast(projectId),
  });

  const summary = summaryData?.data;
  const expenses = expensesData?.data?.content ?? [];
  const cashFlowItems = cashFlowData?.data ?? [];

  // Transform cash flow data for chart
  const chartData = cashFlowItems
    .sort((a: any, b: any) => {
      const dateA = new Date(a.forecastPeriod).getTime();
      const dateB = new Date(b.forecastPeriod).getTime();
      return dateA - dateB;
    })
    .map((item: any) => ({
      period: new Date(item.forecastPeriod).toLocaleDateString("en-US", {
        month: "short",
        year: "2-digit",
      }),
      planned: item.plannedAmount || 0,
      actual: item.actualAmount || 0,
      forecast: item.forecastAmount || 0,
      cumulativePlanned: item.cumulativePlanned || 0,
      cumulativeActual: item.cumulativeActual || 0,
      cumulativeForecast: item.cumulativeForecast || 0,
    }))

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
    blue: "bg-blue-50 border-blue-200",
    green: "bg-green-50 border-green-200",
    yellow: "bg-yellow-50 border-yellow-200",
    purple: "bg-purple-50 border-purple-200",
  };

  const textColorMap: Record<string, string> = {
    blue: "text-blue-700",
    green: "text-green-700",
    yellow: "text-yellow-700",
    purple: "text-purple-700",
  };

  return (
    <div className="space-y-6">
      {isLoadingSummary ? (
        <div className="text-center text-gray-500">Loading cost summary...</div>
      ) : (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {summaryCards.map((card) => (
            <div
              key={card.label}
              className={`rounded-lg border p-4 ${colorMap[card.color]}`}
            >
              <h3 className="text-sm font-medium text-gray-700">{card.label}</h3>
              <p className={`mt-2 text-2xl font-bold ${textColorMap[card.color]}`}>
                {card.value}
              </p>
            </div>
          ))}
        </div>
      )}

      {/* Cash Flow S-Curve */}
      <div className="rounded-lg border border-gray-200 bg-white p-6">
        <h3 className="mb-4 text-lg font-semibold text-gray-900">
          Cash Flow S-Curve
        </h3>
        {chartData.length === 0 ? (
          <div className="rounded-lg border border-dashed border-gray-300 py-12 text-center">
            <p className="text-gray-500">No cash flow data available</p>
          </div>
        ) : (
          <ResponsiveContainer width="100%" height={400}>
            <LineChart data={chartData} margin={{ top: 5, right: 30, left: 0, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
              <XAxis
                dataKey="period"
                stroke="#6b7280"
                style={{ fontSize: "12px" }}
              />
              <YAxis
                stroke="#6b7280"
                style={{ fontSize: "12px" }}
                label={{ value: "Amount ($)", angle: -90, position: "insideLeft" }}
              />
              <Tooltip
                contentStyle={{
                  backgroundColor: "#fff",
                  border: "1px solid #e5e7eb",
                  borderRadius: "0.5rem",
                }}
                formatter={(value: any) =>
                  typeof value === "number"
                    ? `$${value.toLocaleString("en-US", {
                        minimumFractionDigits: 0,
                        maximumFractionDigits: 0,
                      })}`
                    : value
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

      <div>
        <h3 className="mb-4 text-lg font-semibold text-gray-900">Expenses</h3>
        {isLoadingExpenses ? (
          <div className="text-center text-gray-500">Loading expenses...</div>
        ) : expenses.length === 0 ? (
          <div className="rounded-lg border border-dashed border-gray-300 py-12 text-center">
            <h3 className="text-lg font-medium text-gray-900">No Expenses</h3>
            <p className="mt-2 text-gray-500">No expenses recorded yet.</p>
          </div>
        ) : (
          <DataTable columns={expenseColumns} data={expenses} rowKey="id" />
        )}
      </div>
    </div>
  );
}
