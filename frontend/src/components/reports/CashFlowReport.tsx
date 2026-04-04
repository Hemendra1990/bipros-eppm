"use client";

import { useState, useMemo } from "react";
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
import { Button } from "@/components/ui/button";
import type { CashFlowEntry } from "@/lib/api/reportDataApi";

interface CashFlowReportProps {
  data: CashFlowEntry[];
}

export function CashFlowReport({ data }: CashFlowReportProps) {
  const [showCumulative, setShowCumulative] = useState(false);

  const chartData = useMemo(() => {
    return data.map((entry) => ({
      period: entry.period,
      "Planned": showCumulative ? entry.cumulativePlanned : entry.planned,
      "Actual": showCumulative ? entry.cumulativeActual : entry.actual,
      "Forecast": showCumulative ? entry.cumulativeForecast : entry.forecast,
    }));
  }, [data, showCumulative]);

  const totals = useMemo(() => {
    const lastEntry = data[data.length - 1];
    return {
      totalPlanned: lastEntry?.cumulativePlanned || 0,
      totalActual: lastEntry?.cumulativeActual || 0,
      totalForecast: lastEntry?.cumulativeForecast || 0,
    };
  }, [data]);

  const variance = useMemo(() => {
    return {
      planned: totals.totalPlanned - totals.totalActual,
      forecast: totals.totalForecast - totals.totalActual,
    };
  }, [totals]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="bg-gradient-to-r from-green-50 to-emerald-50 p-4 rounded-lg border border-green-200">
        <div className="flex justify-between items-start">
          <div>
            <h3 className="font-semibold text-lg text-gray-900">Cash Flow Report</h3>
            <p className="text-sm text-gray-600">Planned vs Actual vs Forecasted cash flows</p>
          </div>
          <div className="flex gap-2">
            <Button
              variant={showCumulative ? "primary" : "outline"}
              size="sm"
              onClick={() => setShowCumulative(!showCumulative)}
            >
              {showCumulative ? "Show Period" : "Show Cumulative"}
            </Button>
          </div>
        </div>
      </div>

      {/* Chart */}
      <div className="bg-white border border-gray-200 rounded-lg p-4">
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
              formatter={(value) => `$${Number(value).toFixed(2)}`}
              contentStyle={{
                backgroundColor: "#ffffff",
                border: "1px solid #e5e7eb",
                borderRadius: "0.5rem",
              }}
            />
            <Legend />
            <Line
              type="monotone"
              dataKey="Planned"
              stroke="#3b82f6"
              strokeWidth={2}
              dot={false}
            />
            <Line
              type="monotone"
              dataKey="Actual"
              stroke="#10b981"
              strokeWidth={2}
              dot={false}
            />
            <Line
              type="monotone"
              dataKey="Forecast"
              stroke="#f59e0b"
              strokeWidth={2}
              dot={false}
              strokeDasharray="5 5"
            />
          </LineChart>
        </ResponsiveContainer>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <p className="text-xs text-gray-500 uppercase tracking-wider mb-2">Total Planned</p>
          <p className="text-3xl font-bold text-blue-600">${totals.totalPlanned.toFixed(2)}</p>
        </div>
        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <p className="text-xs text-gray-500 uppercase tracking-wider mb-2">Total Actual</p>
          <p className="text-3xl font-bold text-green-600">${totals.totalActual.toFixed(2)}</p>
        </div>
        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <p className="text-xs text-gray-500 uppercase tracking-wider mb-2">Total Forecast</p>
          <p className="text-3xl font-bold text-amber-600">${totals.totalForecast.toFixed(2)}</p>
        </div>
      </div>

      {/* Variance Summary */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <h4 className="font-semibold text-gray-900 mb-3">Planned vs Actual</h4>
          <div className="space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-gray-600">Variance</span>
              <span className={`font-semibold ${variance.planned >= 0 ? "text-green-600" : "text-red-600"}`}>
                ${variance.planned.toFixed(2)}
              </span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-gray-600">Status</span>
              <span className={`px-2 py-1 rounded text-xs font-semibold ${
                variance.planned >= 0 ? "bg-green-100 text-green-700" : "bg-red-100 text-red-700"
              }`}>
                {variance.planned >= 0 ? "Under Budget" : "Over Budget"}
              </span>
            </div>
          </div>
        </div>

        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <h4 className="font-semibold text-gray-900 mb-3">Forecast vs Actual</h4>
          <div className="space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-gray-600">Remaining</span>
              <span className={`font-semibold ${variance.forecast >= 0 ? "text-green-600" : "text-red-600"}`}>
                ${variance.forecast.toFixed(2)}
              </span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-gray-600">Status</span>
              <span className={`px-2 py-1 rounded text-xs font-semibold ${
                variance.forecast >= 0 ? "bg-green-100 text-green-700" : "bg-orange-100 text-orange-700"
              }`}>
                {variance.forecast >= 0 ? "On Track" : "At Risk"}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Data Table */}
      <div className="bg-white border border-gray-200 rounded-lg p-4">
        <h4 className="font-semibold text-gray-900 mb-4">Detailed Cash Flow</h4>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200">
                <th className="text-left py-3 px-2 text-gray-600 font-semibold">Period</th>
                {!showCumulative && (
                  <>
                    <th className="text-right py-3 px-2 text-gray-600 font-semibold">Planned</th>
                    <th className="text-right py-3 px-2 text-gray-600 font-semibold">Actual</th>
                    <th className="text-right py-3 px-2 text-gray-600 font-semibold">Forecast</th>
                  </>
                )}
                {showCumulative && (
                  <>
                    <th className="text-right py-3 px-2 text-gray-600 font-semibold">Cum. Planned</th>
                    <th className="text-right py-3 px-2 text-gray-600 font-semibold">Cum. Actual</th>
                    <th className="text-right py-3 px-2 text-gray-600 font-semibold">Cum. Forecast</th>
                  </>
                )}
              </tr>
            </thead>
            <tbody>
              {data.map((entry, idx) => (
                <tr key={idx} className="border-b border-gray-100 hover:bg-gray-50">
                  <td className="py-3 px-2 text-gray-900 font-semibold">{entry.period}</td>
                  {!showCumulative && (
                    <>
                      <td className="py-3 px-2 text-right text-gray-700">${entry.planned.toFixed(2)}</td>
                      <td className="py-3 px-2 text-right text-gray-700">${entry.actual.toFixed(2)}</td>
                      <td className="py-3 px-2 text-right text-gray-700">${entry.forecast.toFixed(2)}</td>
                    </>
                  )}
                  {showCumulative && (
                    <>
                      <td className="py-3 px-2 text-right text-gray-700">${entry.cumulativePlanned.toFixed(2)}</td>
                      <td className="py-3 px-2 text-right text-gray-700">${entry.cumulativeActual.toFixed(2)}</td>
                      <td className="py-3 px-2 text-right text-gray-700">${entry.cumulativeForecast.toFixed(2)}</td>
                    </>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
