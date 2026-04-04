"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { PageHeader } from "@/components/common/PageHeader";
import { scheduleHealthApi } from "@/lib/api/scheduleHealthApi";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from "recharts";

export default function ScheduleHealthPage() {
  const params = useParams();
  const projectId = params.projectId as string;

  const { data: health, isLoading, error } = useQuery({
    queryKey: ["schedule-health", projectId],
    queryFn: () => scheduleHealthApi.getLatestHealth(projectId),
    enabled: !!projectId,
  });

  if (isLoading) {
    return <div className="text-center text-gray-500">Loading schedule health...</div>;
  }

  if (error) {
    return (
      <div className="text-center text-red-500">
        Failed to load schedule health. Please run a schedule first.
      </div>
    );
  }

  if (!health) {
    return (
      <div className="text-center text-gray-500">
        No schedule health data available. Please run a schedule.
      </div>
    );
  }

  const getRiskColor = (riskLevel: string) => {
    switch (riskLevel) {
      case "LOW":
        return "bg-green-100 text-green-800";
      case "MEDIUM":
        return "bg-yellow-100 text-yellow-800";
      case "HIGH":
        return "bg-orange-100 text-orange-800";
      case "CRITICAL":
        return "bg-red-100 text-red-800";
      default:
        return "bg-gray-100 text-gray-800";
    }
  };

  const getHealthScoreColor = (score: number) => {
    if (score >= 80) return "text-green-600";
    if (score >= 60) return "text-yellow-600";
    if (score >= 40) return "text-orange-600";
    return "text-red-600";
  };

  const chartData = [
    {
      name: "0 days",
      count: health.floatDistribution.zero || 0,
    },
    {
      name: "1-5 days",
      count: health.floatDistribution["1to5"] || 0,
    },
    {
      name: "6-10 days",
      count: health.floatDistribution["6to10"] || 0,
    },
    {
      name: "10+ days",
      count: health.floatDistribution["10plus"] || 0,
    },
  ];

  return (
    <div>
      <PageHeader
        title="Schedule Health"
        description="Overall health status and metrics of the project schedule"
      />

      <div className="space-y-6">
        {/* Health Score Card */}
        <div className="rounded-lg border border-gray-200 bg-white p-8 shadow-sm">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-sm font-medium text-gray-700">Health Score</h3>
              <p className={`mt-2 text-5xl font-bold ${getHealthScoreColor(health.healthScore)}`}>
                {health.healthScore.toFixed(1)}
              </p>
              <p className="mt-2 text-sm text-gray-600">out of 100</p>
            </div>
            <div className={`rounded-lg ${getRiskColor(health.riskLevel)} px-6 py-3`}>
              <span className="text-lg font-semibold">{health.riskLevel}</span>
              <p className="mt-1 text-sm">Risk Level</p>
            </div>
          </div>
        </div>

        {/* Key Metrics */}
        <div className="grid grid-cols-2 gap-6 lg:grid-cols-4">
          <MetricCard
            label="Total Activities"
            value={health.totalActivities}
            color="blue"
          />
          <MetricCard
            label="Critical Activities"
            value={health.criticalActivities}
            color="red"
          />
          <MetricCard
            label="Near-Critical Activities"
            value={health.nearCriticalActivities}
            color="amber"
          />
          <MetricCard
            label="Avg Float (days)"
            value={health.totalFloatAverage.toFixed(2)}
            color="green"
          />
        </div>

        {/* Float Distribution Chart */}
        <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
          <h3 className="mb-4 text-lg font-semibold text-gray-900">
            Float Distribution
          </h3>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" />
              <YAxis />
              <Tooltip />
              <Bar dataKey="count" fill="#3b82f6" name="Activities" />
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Activity Summary Table */}
        <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
          <h3 className="mb-4 text-lg font-semibold text-gray-900">
            Activity Status Summary
          </h3>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="border-b border-gray-200 bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-gray-700">
                    Category
                  </th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-gray-700">
                    Count
                  </th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-gray-700">
                    Percentage
                  </th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-gray-700">
                    Status
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                <tr>
                  <td className="px-4 py-3 text-sm text-gray-900">Critical Path</td>
                  <td className="px-4 py-3 text-sm font-semibold text-gray-900">
                    {health.criticalActivities}
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-600">
                    {(
                      (health.criticalActivities / health.totalActivities) *
                      100
                    ).toFixed(1)}
                    %
                  </td>
                  <td className="px-4 py-3">
                    <span className="inline-block rounded-full bg-red-100 px-3 py-1 text-xs font-semibold text-red-800">
                      Risk
                    </span>
                  </td>
                </tr>
                <tr>
                  <td className="px-4 py-3 text-sm text-gray-900">
                    Near-Critical (1-5 days float)
                  </td>
                  <td className="px-4 py-3 text-sm font-semibold text-gray-900">
                    {health.nearCriticalActivities}
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-600">
                    {(
                      (health.nearCriticalActivities / health.totalActivities) *
                      100
                    ).toFixed(1)}
                    %
                  </td>
                  <td className="px-4 py-3">
                    <span className="inline-block rounded-full bg-amber-100 px-3 py-1 text-xs font-semibold text-amber-800">
                      Watch
                    </span>
                  </td>
                </tr>
                <tr>
                  <td className="px-4 py-3 text-sm text-gray-900">
                    Healthy (&gt;5 days float)
                  </td>
                  <td className="px-4 py-3 text-sm font-semibold text-gray-900">
                    {health.totalActivities -
                      health.criticalActivities -
                      health.nearCriticalActivities}
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-600">
                    {(
                      ((health.totalActivities -
                        health.criticalActivities -
                        health.nearCriticalActivities) /
                        health.totalActivities) *
                      100
                    ).toFixed(1)}
                    %
                  </td>
                  <td className="px-4 py-3">
                    <span className="inline-block rounded-full bg-green-100 px-3 py-1 text-xs font-semibold text-green-800">
                      Good
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}

interface MetricCardProps {
  label: string;
  value: string | number;
  color: "blue" | "red" | "amber" | "green";
}

function MetricCard({ label, value, color }: MetricCardProps) {
  const colorClasses = {
    blue: "bg-blue-50 border-blue-200",
    red: "bg-red-50 border-red-200",
    amber: "bg-amber-50 border-amber-200",
    green: "bg-green-50 border-green-200",
  };

  const textColorClasses = {
    blue: "text-blue-900",
    red: "text-red-900",
    amber: "text-amber-900",
    green: "text-green-900",
  };

  return (
    <div
      className={`rounded-lg border ${colorClasses[color]} ${textColorClasses[color]} p-4`}
    >
      <p className="text-sm font-medium">{label}</p>
      <p className="mt-2 text-2xl font-bold">{value}</p>
    </div>
  );
}
