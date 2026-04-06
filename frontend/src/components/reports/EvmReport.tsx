"use client";

import { useMemo } from "react";
import { TrendingUp, TrendingDown } from "lucide-react";
import type { EvmReportData } from "@/lib/api/reportDataApi";

interface EvmReportProps {
  data: EvmReportData;
}

function MetricCard({
  label,
  value,
  isGood,
  suffix = "",
}: {
  label: string;
  value: number;
  isGood: boolean;
  suffix?: string;
}) {
  const isPositive = value >= 1 || value >= 0;
  const bgColor = isGood ? "bg-emerald-500/10" : "bg-red-500/10";
  const textColor = isGood ? "text-emerald-400" : "text-red-400";
  const borderColor = isGood ? "border-green-200" : "border-red-200";

  return (
    <div className={`${bgColor} border ${borderColor} rounded-lg p-4`}>
      <p className="text-xs text-slate-400 uppercase tracking-wider mb-1">{label}</p>
      <div className="flex items-center justify-between">
        <p className={`text-2xl font-bold ${textColor}`}>
          {typeof value === "number" && !isNaN(value) ? value.toFixed(2) : "0.00"}
          {suffix}
        </p>
        {isGood ? (
          <TrendingUp className={textColor} size={24} />
        ) : (
          <TrendingDown className={textColor} size={24} />
        )}
      </div>
    </div>
  );
}

export function EvmReport({ data }: EvmReportProps) {
  const metrics = useMemo(
    () => ({
      spiGood: data.spi >= 1,
      cpiGood: data.cpi >= 1,
      eacGood: data.eac <= data.pv,
      vacGood: data.vac >= 0,
      tcpiGood: data.tcpi <= 1,
    }),
    [data]
  );

  const overallHealth = useMemo(() => {
    const score = [
      metrics.spiGood,
      metrics.cpiGood,
      metrics.eacGood,
      metrics.vacGood,
    ].filter(Boolean).length;
    const percentage = (score / 4) * 100;

    if (percentage >= 75) return { status: "Healthy", color: "bg-emerald-500/10 text-emerald-400" };
    if (percentage >= 50) return { status: "At Risk", color: "bg-amber-500/10 text-amber-400" };
    return { status: "Critical", color: "bg-red-500/10 text-red-400" };
  }, [metrics]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="bg-gradient-to-r from-purple-50 to-pink-50 p-4 rounded-lg border border-purple-200">
        <div className="flex justify-between items-start">
          <div>
            <h3 className="font-semibold text-lg text-white">{data.projectName}</h3>
            <p className="text-sm text-slate-400">Earned Value Management Report</p>
          </div>
          <div className={`px-3 py-1 rounded-full text-sm font-semibold ${overallHealth.color}`}>
            {overallHealth.status}
          </div>
        </div>
      </div>

      {/* Core EVM Metrics */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <MetricCard label="Planned Value (PV)" value={data.pv} isGood={true} suffix="$" />
        <MetricCard label="Earned Value (EV)" value={data.ev} isGood={true} suffix="$" />
        <MetricCard label="Actual Cost (AC)" value={data.ac} isGood={data.ac <= data.ev} suffix="$" />
      </div>

      {/* Performance Indices */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <MetricCard
          label="Schedule Performance Index (SPI)"
          value={data.spi}
          isGood={metrics.spiGood}
        />
        <MetricCard
          label="Cost Performance Index (CPI)"
          value={data.cpi}
          isGood={metrics.cpiGood}
        />
      </div>

      {/* Forecast Metrics */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <MetricCard label="Estimate at Completion (EAC)" value={data.eac} isGood={metrics.eacGood} suffix="$" />
        <MetricCard label="Estimate to Complete (ETC)" value={data.etc} isGood={data.etc >= 0} suffix="$" />
        <MetricCard label="Variance at Completion (VAC)" value={data.vac} isGood={metrics.vacGood} suffix="$" />
      </div>

      {/* Completion Metric */}
      <div className="grid grid-cols-1 gap-4">
        <MetricCard
          label="To-Complete Performance Index (TCPI)"
          value={data.tcpi}
          isGood={metrics.tcpiGood}
        />
      </div>

      {/* Interpretation */}
      <div className="bg-slate-900/50 border border-slate-800 rounded-lg p-4">
        <h4 className="font-semibold text-white mb-4">Interpretation</h4>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 text-sm">
          <div className="space-y-3">
            <div>
              <p className="text-slate-400 mb-1">
                <span className="font-semibold">Schedule Status (SPI):</span>
              </p>
              <p className="text-white">
                {data.spi >= 1
                  ? "✓ Project is ahead of schedule"
                  : `✗ Project is behind schedule by ${((1 - data.spi) * 100).toFixed(0)}%`}
              </p>
            </div>
            <div>
              <p className="text-slate-400 mb-1">
                <span className="font-semibold">Cost Status (CPI):</span>
              </p>
              <p className="text-white">
                {data.cpi >= 1
                  ? "✓ Project is under budget"
                  : `✗ Project is over budget by ${((1 - data.cpi) * 100).toFixed(0)}%`}
              </p>
            </div>
          </div>
          <div className="space-y-3">
            <div>
              <p className="text-slate-400 mb-1">
                <span className="font-semibold">Cost Variance (VAC):</span>
              </p>
              <p className="text-white">
                {data.vac >= 0
                  ? `✓ Project will save $${data.vac.toFixed(2)}`
                  : `✗ Project will overrun by $${Math.abs(data.vac).toFixed(2)}`}
              </p>
            </div>
            <div>
              <p className="text-slate-400 mb-1">
                <span className="font-semibold">Final Estimate (EAC):</span>
              </p>
              <p className="text-white">
                Project is estimated to cost ${data.eac.toFixed(2)} (originally planned: $
                {data.pv.toFixed(2)})
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
