"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { PageHeader } from "@/components/common/PageHeader";
import { TabTip } from "@/components/common/TabTip";
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
    return <div className="text-center text-slate-500">Loading schedule health...</div>;
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
      <div className="text-center text-slate-500">
        No schedule health data available. Please run a schedule.
      </div>
    );
  }

  const getRiskColor = (riskLevel: string) => {
    switch (riskLevel) {
      case "LOW":
        return "bg-emerald-500/10 text-emerald-300";
      case "MEDIUM":
        return "bg-amber-500/10 text-amber-300";
      case "HIGH":
        return "bg-orange-500/10 text-orange-300";
      case "CRITICAL":
        return "bg-red-500/10 text-red-300";
      default:
        return "bg-slate-800/50 text-slate-100";
    }
  };

  const getHealthScoreColor = (score: number) => {
    if (score >= 80) return "text-emerald-400";
    if (score >= 60) return "text-amber-400";
    if (score >= 40) return "text-orange-600";
    return "text-red-400";
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
        <TabTip
          title="Schedule Health Analysis"
          description="Analyzes your schedule quality by examining float distribution, near-critical activities, and schedule density. Run a schedule first to see results."
        />
        {/* Health Score Card */}
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-8 shadow-sm">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-sm font-medium text-slate-300">Health Score</h3>
              <p className={`mt-2 text-5xl font-bold ${getHealthScoreColor(health.healthScore)}`}>
                {health.healthScore.toFixed(1)}
              </p>
              <p className="mt-2 text-sm text-slate-400">out of 100</p>
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
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6 shadow-sm">
          <h3 className="mb-4 text-lg font-semibold text-white">
            Float Distribution
          </h3>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" />
              <YAxis />
              <Tooltip contentStyle={{ backgroundColor: "#1e293b", border: "1px solid #334155", borderRadius: "8px", color: "#e2e8f0" }} />
              <Bar dataKey="count" fill="#3b82f6" name="Activities" />
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Activity Summary Table */}
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6 shadow-sm">
          <h3 className="mb-4 text-lg font-semibold text-white">
            Activity Status Summary
          </h3>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="border-b border-slate-800 bg-slate-900/80">
                <tr>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-slate-300">
                    Category
                  </th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-slate-300">
                    Count
                  </th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-slate-300">
                    Percentage
                  </th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-slate-300">
                    Status
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/50">
                <tr>
                  <td className="px-4 py-3 text-sm text-white">Critical Path</td>
                  <td className="px-4 py-3 text-sm font-semibold text-white">
                    {health.criticalActivities}
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-400">
                    {(
                      (health.criticalActivities / health.totalActivities) *
                      100
                    ).toFixed(1)}
                    %
                  </td>
                  <td className="px-4 py-3">
                    <span className="inline-block rounded-full bg-red-500/10 px-3 py-1 text-xs font-semibold text-red-300">
                      Risk
                    </span>
                  </td>
                </tr>
                <tr>
                  <td className="px-4 py-3 text-sm text-white">
                    Near-Critical (1-5 days float)
                  </td>
                  <td className="px-4 py-3 text-sm font-semibold text-white">
                    {health.nearCriticalActivities}
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-400">
                    {(
                      (health.nearCriticalActivities / health.totalActivities) *
                      100
                    ).toFixed(1)}
                    %
                  </td>
                  <td className="px-4 py-3">
                    <span className="inline-block rounded-full bg-amber-500/10 px-3 py-1 text-xs font-semibold text-amber-800">
                      Watch
                    </span>
                  </td>
                </tr>
                <tr>
                  <td className="px-4 py-3 text-sm text-white">
                    Healthy (&gt;5 days float)
                  </td>
                  <td className="px-4 py-3 text-sm font-semibold text-white">
                    {health.totalActivities -
                      health.criticalActivities -
                      health.nearCriticalActivities}
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-400">
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
                    <span className="inline-block rounded-full bg-emerald-500/10 px-3 py-1 text-xs font-semibold text-emerald-300">
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
    blue: "bg-blue-500/10 border-blue-200",
    red: "bg-red-500/10 border-red-200",
    amber: "bg-amber-500/10 border-amber-200",
    green: "bg-emerald-500/10 border-green-200",
  };

  const textColorClasses = {
    blue: "text-blue-400",
    red: "text-red-400",
    amber: "text-amber-900",
    green: "text-emerald-400",
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
