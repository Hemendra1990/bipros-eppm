"use client";

import { useMemo } from "react";
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from "recharts";
import { AlertTriangle } from "lucide-react";
import type { RiskRegisterData } from "@/lib/api/reportDataApi";

interface RiskRegisterReportProps {
  data: RiskRegisterData;
}

export function RiskRegisterReport({ data }: RiskRegisterReportProps) {
  const categoryChartData = useMemo(() => {
    return Object.entries(data.risksByCategory).map(([category, count]) => ({
      category: category.charAt(0) + category.slice(1).toLowerCase(),
      count,
    }));
  }, [data.risksByCategory]);

  const overallRiskLevel = useMemo(() => {
    if (data.highRisks > 3 || data.totalRisks > 20) return { level: "Critical", color: "bg-red-100 text-red-700" };
    if (data.highRisks > 0 || data.mediumRisks > 10) return { level: "High", color: "bg-orange-100 text-orange-700" };
    return { level: "Moderate", color: "bg-yellow-100 text-yellow-700" };
  }, [data.highRisks, data.mediumRisks, data.totalRisks]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="bg-gradient-to-r from-red-50 to-orange-50 p-4 rounded-lg border border-red-200">
        <div className="flex justify-between items-start">
          <div>
            <h3 className="font-semibold text-lg text-gray-900">{data.projectName}</h3>
            <p className="text-sm text-gray-600">Risk Register & Analysis</p>
          </div>
          <div className={`px-3 py-1 rounded-full text-sm font-semibold ${overallRiskLevel.color}`}>
            {overallRiskLevel.level}
          </div>
        </div>
      </div>

      {/* Risk Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <p className="text-xs text-gray-500 uppercase tracking-wider">Total Risks</p>
          <p className="text-3xl font-bold text-gray-900">{data.totalRisks}</p>
        </div>

        <div className="bg-white border border-red-200 rounded-lg p-4">
          <p className="text-xs text-gray-500 uppercase tracking-wider">High Risks</p>
          <p className="text-3xl font-bold text-red-600">{data.highRisks}</p>
        </div>

        <div className="bg-white border border-orange-200 rounded-lg p-4">
          <p className="text-xs text-gray-500 uppercase tracking-wider">Medium Risks</p>
          <p className="text-3xl font-bold text-orange-600">{data.mediumRisks}</p>
        </div>

        <div className="bg-white border border-yellow-200 rounded-lg p-4">
          <p className="text-xs text-gray-500 uppercase tracking-wider">Low Risks</p>
          <p className="text-3xl font-bold text-yellow-600">{data.lowRisks}</p>
        </div>
      </div>

      {/* Risk Distribution Chart */}
      {categoryChartData.length > 0 && (
        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <h4 className="font-semibold text-gray-900 mb-4">Risks by Category</h4>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={categoryChartData} margin={{ top: 5, right: 30, left: 0, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
              <XAxis dataKey="category" stroke="#6b7280" style={{ fontSize: "12px" }} />
              <YAxis stroke="#6b7280" style={{ fontSize: "12px" }} />
              <Tooltip
                contentStyle={{
                  backgroundColor: "#ffffff",
                  border: "1px solid #e5e7eb",
                  borderRadius: "0.5rem",
                }}
              />
              <Bar dataKey="count" fill="#ef4444" radius={[8, 8, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Top Risks */}
      <div className="bg-white border border-gray-200 rounded-lg p-4">
        <h4 className="font-semibold text-gray-900 mb-4 flex items-center gap-2">
          <AlertTriangle className="text-red-500" size={20} />
          Top Risks
        </h4>
        {data.topRisks.length > 0 ? (
          <div className="space-y-3">
            {data.topRisks.map((risk, idx) => (
              <div key={idx} className="border border-gray-100 rounded-lg p-3 hover:bg-gray-50">
                <div className="flex justify-between items-start mb-2">
                  <div>
                    <p className="font-semibold text-gray-900">{risk.title}</p>
                    <p className="text-xs text-gray-600 mt-1">
                      <span className="font-mono">{risk.code}</span> | Category: {risk.category}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-bold text-gray-900">Risk Score</p>
                    <p className={`text-lg font-bold ${
                      risk.score >= 0.7
                        ? "text-red-600"
                        : risk.score >= 0.4
                        ? "text-orange-600"
                        : "text-yellow-600"
                    }`}>
                      {risk.score.toFixed(2)}
                    </p>
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <span className="text-gray-600">Probability:</span>
                    <span className="ml-2 font-semibold">{risk.probability}</span>
                  </div>
                  <div>
                    <span className="text-gray-600">Impact:</span>
                    <span className="ml-2 font-semibold">{risk.impact}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-gray-500 text-center py-4">No risks identified</p>
        )}
      </div>

      {/* Risk Severity Summary */}
      <div className="bg-white border border-gray-200 rounded-lg p-4">
        <h4 className="font-semibold text-gray-900 mb-4">Severity Distribution</h4>
        <div className="grid grid-cols-3 gap-4">
          <div>
            <div className="bg-red-100 rounded-lg p-4 text-center">
              <p className="text-sm text-gray-600 mb-1">High</p>
              <p className="text-3xl font-bold text-red-700">{data.highRisks}</p>
              <p className="text-xs text-gray-600 mt-2">
                {data.totalRisks > 0 ? ((data.highRisks / data.totalRisks) * 100).toFixed(0) : 0}%
              </p>
            </div>
          </div>
          <div>
            <div className="bg-orange-100 rounded-lg p-4 text-center">
              <p className="text-sm text-gray-600 mb-1">Medium</p>
              <p className="text-3xl font-bold text-orange-700">{data.mediumRisks}</p>
              <p className="text-xs text-gray-600 mt-2">
                {data.totalRisks > 0 ? ((data.mediumRisks / data.totalRisks) * 100).toFixed(0) : 0}%
              </p>
            </div>
          </div>
          <div>
            <div className="bg-yellow-100 rounded-lg p-4 text-center">
              <p className="text-sm text-gray-600 mb-1">Low</p>
              <p className="text-3xl font-bold text-yellow-700">{data.lowRisks}</p>
              <p className="text-xs text-gray-600 mt-2">
                {data.totalRisks > 0 ? ((data.lowRisks / data.totalRisks) * 100).toFixed(0) : 0}%
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
