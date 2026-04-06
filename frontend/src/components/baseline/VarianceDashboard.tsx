"use client";

import type { BaselineVarianceRow } from "@/lib/api/baselineApi";

interface VarianceDashboardProps {
  data: BaselineVarianceRow[];
}

function getVarianceColor(value: number): string {
  if (value > 0) return "text-red-400";
  if (value < 0) return "text-emerald-400";
  return "text-slate-400";
}

function getVarianceBg(value: number): string {
  if (value > 0) return "bg-red-500/10 border-red-500/20";
  if (value < 0) return "bg-emerald-500/10 border-emerald-500/20";
  return "bg-slate-800/50 border-slate-700/50";
}

export function VarianceDashboard({ data }: VarianceDashboardProps) {
  if (data.length === 0) {
    return (
      <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-8 text-center text-slate-400">
        No variance data available
      </div>
    );
  }

  // Calculate summary metrics
  const totalActivities = data.length;
  const delayedStart = data.filter((r) => r.startVarianceDays > 0).length;
  const delayedFinish = data.filter((r) => r.finishVarianceDays > 0).length;
  const onTrack = data.filter(
    (r) => r.startVarianceDays === 0 && r.finishVarianceDays === 0
  ).length;
  const ahead = data.filter(
    (r) => r.startVarianceDays < 0 || r.finishVarianceDays < 0
  ).length;

  const avgStartVariance =
    data.reduce((sum, r) => sum + r.startVarianceDays, 0) / totalActivities;
  const avgFinishVariance =
    data.reduce((sum, r) => sum + r.finishVarianceDays, 0) / totalActivities;
  const totalCostVariance = data.reduce((sum, r) => sum + r.costVariance, 0);

  const onTrackPct = Math.round((onTrack / totalActivities) * 100);
  const delayedPct = Math.round((delayedFinish / totalActivities) * 100);
  const aheadPct = Math.round((ahead / totalActivities) * 100);

  // Variance distribution buckets
  const buckets = [
    { label: "> 10d early", count: data.filter((r) => r.finishVarianceDays < -10).length },
    { label: "1-10d early", count: data.filter((r) => r.finishVarianceDays >= -10 && r.finishVarianceDays < 0).length },
    { label: "On time", count: data.filter((r) => r.finishVarianceDays === 0).length },
    { label: "1-10d late", count: data.filter((r) => r.finishVarianceDays > 0 && r.finishVarianceDays <= 10).length },
    { label: "11-30d late", count: data.filter((r) => r.finishVarianceDays > 10 && r.finishVarianceDays <= 30).length },
    { label: "> 30d late", count: data.filter((r) => r.finishVarianceDays > 30).length },
  ];
  const maxBucket = Math.max(...buckets.map((b) => b.count), 1);

  const bucketColors = [
    "bg-emerald-500",
    "bg-emerald-400",
    "bg-slate-500",
    "bg-amber-400",
    "bg-red-400",
    "bg-red-500",
  ];

  return (
    <div className="space-y-6">
      {/* Summary Cards */}
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <div className={`rounded-lg border p-4 ${getVarianceBg(avgStartVariance)}`}>
          <p className="text-xs font-medium uppercase tracking-wider text-slate-400">
            Avg Start Variance
          </p>
          <p className={`mt-1 text-2xl font-bold ${getVarianceColor(avgStartVariance)}`}>
            {avgStartVariance > 0 ? "+" : ""}
            {avgStartVariance.toFixed(1)}d
          </p>
        </div>
        <div className={`rounded-lg border p-4 ${getVarianceBg(avgFinishVariance)}`}>
          <p className="text-xs font-medium uppercase tracking-wider text-slate-400">
            Avg Finish Variance
          </p>
          <p className={`mt-1 text-2xl font-bold ${getVarianceColor(avgFinishVariance)}`}>
            {avgFinishVariance > 0 ? "+" : ""}
            {avgFinishVariance.toFixed(1)}d
          </p>
        </div>
        <div className={`rounded-lg border p-4 ${getVarianceBg(totalCostVariance)}`}>
          <p className="text-xs font-medium uppercase tracking-wider text-slate-400">
            Total Cost Variance
          </p>
          <p className={`mt-1 text-2xl font-bold ${getVarianceColor(totalCostVariance)}`}>
            {totalCostVariance > 0 ? "+" : ""}${Math.abs(totalCostVariance).toLocaleString()}
          </p>
        </div>
        <div className="rounded-lg border border-slate-700/50 bg-slate-800/50 p-4">
          <p className="text-xs font-medium uppercase tracking-wider text-slate-400">
            Activities On Track
          </p>
          <p className="mt-1 text-2xl font-bold text-white">
            {onTrackPct}%
          </p>
          <p className="text-xs text-slate-400">
            {onTrack} of {totalActivities}
          </p>
        </div>
      </div>

      {/* Status Breakdown */}
      <div className="grid grid-cols-3 gap-4">
        <div className="rounded-lg border border-emerald-500/20 bg-emerald-500/10 p-4 text-center">
          <p className="text-3xl font-bold text-emerald-400">{ahead}</p>
          <p className="text-sm text-emerald-300">Ahead ({aheadPct}%)</p>
        </div>
        <div className="rounded-lg border border-slate-700/50 bg-slate-800/50 p-4 text-center">
          <p className="text-3xl font-bold text-white">{onTrack}</p>
          <p className="text-sm text-slate-400">On Track ({onTrackPct}%)</p>
        </div>
        <div className="rounded-lg border border-red-500/20 bg-red-500/10 p-4 text-center">
          <p className="text-3xl font-bold text-red-400">{delayedFinish}</p>
          <p className="text-sm text-red-300">Delayed ({delayedPct}%)</p>
        </div>
      </div>

      {/* Variance Distribution Chart */}
      <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
        <h4 className="mb-4 text-sm font-semibold text-white">
          Finish Variance Distribution
        </h4>
        <div className="space-y-2">
          {buckets.map((bucket, i) => (
            <div key={bucket.label} className="flex items-center gap-3">
              <span className="w-24 text-right text-xs text-slate-400">
                {bucket.label}
              </span>
              <div className="flex-1">
                <div
                  className={`h-6 rounded ${bucketColors[i]} transition-all`}
                  style={{
                    width: `${(bucket.count / maxBucket) * 100}%`,
                    minWidth: bucket.count > 0 ? "16px" : "0",
                  }}
                />
              </div>
              <span className="w-8 text-right text-xs font-medium text-slate-300">
                {bucket.count}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
