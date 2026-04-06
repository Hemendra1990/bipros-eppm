"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation } from "@tanstack/react-query";
import { Play, AlertCircle, CheckCircle2, Clock } from "lucide-react";
import { monteCarloApi } from "@/lib/api/monteCarloApi";
import { PageHeader } from "@/components/common/PageHeader";
import { TabTip } from "@/components/common/TabTip";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/common/EmptyState";
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, ReferenceLine } from "recharts";

export default function RiskAnalysisPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const [iterations, setIterations] = useState(10000);
  const [showRunDialog, setShowRunDialog] = useState(false);

  const { data: latestSim, isLoading, refetch } = useQuery({
    queryKey: ["monte-carlo", projectId],
    queryFn: () => monteCarloApi.getLatestSimulation(projectId),
    retry: false,
  });

  const { data: allSims } = useQuery({
    queryKey: ["monte-carlo-list", projectId],
    queryFn: () => monteCarloApi.listSimulations(projectId),
    retry: false,
  });

  const runMutation = useMutation({
    mutationFn: () => monteCarloApi.runSimulation(projectId, iterations),
    onSuccess: () => {
      setShowRunDialog(false);
      refetch();
    },
  });

  // Prepare histogram data from simulation results
  const histogramData = latestSim?.data?.results && latestSim.data
    ? Array.from({ length: 10 }, (_, i) => {
        const data = latestSim.data!;
        const min = data.baselineDuration * 0.8 + (data.baselineDuration * 0.4 * i) / 10;
        const max = min + (data.baselineDuration * 0.4) / 10;
        const count = (data.results || []).filter(
          (r) => r.projectDuration >= min && r.projectDuration < max
        ).length;
        return {
          range: `${Math.round(min)}-${Math.round(max)}`,
          count,
          minVal: min,
        };
      })
    : [];

  const statusConfig = {
    PENDING: { icon: Clock, color: "text-amber-400", bg: "bg-amber-500/10" },
    RUNNING: { icon: Clock, color: "text-blue-400", bg: "bg-blue-500/10" },
    COMPLETED: { icon: CheckCircle2, color: "text-emerald-400", bg: "bg-emerald-500/10" },
    FAILED: { icon: AlertCircle, color: "text-red-400", bg: "bg-red-500/10" },
  };

  const status = latestSim?.data?.status || "COMPLETED";
  const StatusIcon = statusConfig[status as keyof typeof statusConfig]?.icon || Clock;
  const statusColor = statusConfig[status as keyof typeof statusConfig]?.color || "text-slate-400";
  const statusBg = statusConfig[status as keyof typeof statusConfig]?.bg || "bg-slate-900/80";

  return (
    <div className="space-y-8">
      <PageHeader title="Risk Analysis - Monte Carlo Simulation" description="Run probabilistic simulations to understand project duration and cost ranges" />

      <TabTip
        title="Monte Carlo Risk Simulation"
        description="Run probabilistic simulations (10,000 iterations) to determine the likely range of project completion dates and costs. Accounts for uncertainty in activity durations."
      />

      {/* Run Simulation Card */}
      <div className="bg-slate-900/50 rounded-lg border border-slate-800 p-6">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-lg font-semibold text-white">Monte Carlo Simulation</h2>
          <Button
            onClick={() => setShowRunDialog(!showRunDialog)}
            disabled={runMutation.isPending}
            className="flex items-center gap-2"
          >
            <Play className="w-4 h-4" />
            {runMutation.isPending ? "Running..." : "Run Simulation"}
          </Button>
        </div>

        {showRunDialog && (
          <div className="mb-6 p-4 bg-blue-500/10 border border-blue-200 rounded-lg">
            <label className="block text-sm font-medium text-white mb-2">
              Number of Iterations
            </label>
            <input
              type="number"
              value={iterations}
              onChange={(e) => setIterations(parseInt(e.target.value))}
              min="1000"
              max="100000"
              step="1000"
              className="w-full px-3 py-2 border border-slate-700 rounded-md text-sm"
            />
            <p className="text-xs text-slate-400 mt-2">More iterations = more accurate results (default 10,000)</p>
            <div className="flex gap-2 mt-4">
              <Button
                onClick={() => runMutation.mutate()}
                disabled={runMutation.isPending}
              >
                {runMutation.isPending ? "Running..." : "Start Simulation"}
              </Button>
              <Button
                onClick={() => setShowRunDialog(false)}
                variant="outline"
              >
                Cancel
              </Button>
            </div>
          </div>
        )}

        {!latestSim?.data && !isLoading && (
          <EmptyState
            icon={AlertCircle}
            title="No Simulation Data"
            description="Run a Monte Carlo simulation to analyze project duration and cost distributions"
          />
        )}
      </div>

      {/* Simulation Results */}
      {latestSim?.data && (
        <>
          {/* Status and Key Metrics */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className={`${statusBg} rounded-lg border border-slate-800 p-4`}>
              <div className="flex items-center gap-2 mb-2">
                <StatusIcon className={`w-5 h-5 ${statusColor}`} />
                <span className="text-sm font-medium text-slate-400">Status</span>
              </div>
              <p className={`text-2xl font-bold ${statusColor}`}>{status}</p>
            </div>

            <div className="bg-blue-500/10 rounded-lg border border-slate-800 p-4">
              <p className="text-sm font-medium text-slate-400 mb-2">Baseline Duration</p>
              <p className="text-2xl font-bold text-blue-400">{Math.round(latestSim.data.baselineDuration)} days</p>
            </div>

            <div className="bg-emerald-500/10 rounded-lg border border-slate-800 p-4">
              <p className="text-sm font-medium text-slate-400 mb-2">P50 Duration (50th percentile)</p>
              <p className="text-2xl font-bold text-emerald-400">{Math.round(latestSim.data.confidenceP50Duration || 0)} days</p>
              <p className="text-xs text-slate-400 mt-1">
                {latestSim.data.confidenceP50Duration && latestSim.data.baselineDuration
                  ? `+${Math.round(((latestSim.data.confidenceP50Duration - latestSim.data.baselineDuration) / latestSim.data.baselineDuration) * 100)}% vs baseline`
                  : ""}
              </p>
            </div>

            <div className="bg-orange-500/10 rounded-lg border border-slate-800 p-4">
              <p className="text-sm font-medium text-slate-400 mb-2">P80 Duration (80th percentile)</p>
              <p className="text-2xl font-bold text-orange-600">{Math.round(latestSim.data.confidenceP80Duration || 0)} days</p>
              <p className="text-xs text-slate-400 mt-1">
                {latestSim.data.confidenceP80Duration && latestSim.data.baselineDuration
                  ? `+${Math.round(((latestSim.data.confidenceP80Duration - latestSim.data.baselineDuration) / latestSim.data.baselineDuration) * 100)}% vs baseline`
                  : ""}
              </p>
            </div>
          </div>

          {/* Cost Metrics */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="bg-purple-500/10 rounded-lg border border-slate-800 p-4">
              <p className="text-sm font-medium text-slate-400 mb-2">Baseline Cost</p>
              <p className="text-2xl font-bold text-purple-600">${latestSim.data.baselineCost}</p>
            </div>

            <div className="bg-indigo-500/10 rounded-lg border border-slate-800 p-4">
              <p className="text-sm font-medium text-slate-400 mb-2">P50 Cost</p>
              <p className="text-2xl font-bold text-indigo-600">${latestSim.data.confidenceP50Cost}</p>
            </div>

            <div className="bg-pink-50 rounded-lg border border-slate-800 p-4">
              <p className="text-sm font-medium text-slate-400 mb-2">P80 Cost</p>
              <p className="text-2xl font-bold text-pink-600">${latestSim.data.confidenceP80Cost}</p>
            </div>
          </div>

          {/* Duration Distribution Chart */}
          {histogramData.length > 0 && (
            <div className="bg-slate-900/50 rounded-lg border border-slate-800 p-6">
              <h3 className="text-lg font-semibold text-white mb-4">Project Duration Distribution</h3>
              <ResponsiveContainer width="100%" height={400}>
                <BarChart data={histogramData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis
                    dataKey="range"
                    angle={-45}
                    textAnchor="end"
                    height={80}
                    tick={{ fontSize: 12 }}
                  />
                  <YAxis label={{ value: "Frequency", angle: -90, position: "insideLeft" }} />
                  <Tooltip formatter={(value) => `${value} iterations`} />
                  <Legend />
                  <ReferenceLine
                    x={`${Math.round(latestSim.data.baselineDuration)}`}
                    stroke="#ef4444"
                    label={{ value: "Baseline", position: "top", fill: "#ef4444" }}
                  />
                  <Bar dataKey="count" fill="#3b82f6" name="Count" />
                </BarChart>
              </ResponsiveContainer>
              <div className="mt-4 p-4 bg-blue-500/10 rounded border border-blue-200">
                <p className="text-sm text-slate-300">
                  <strong>Interpretation:</strong> This histogram shows the frequency distribution of project durations across all {latestSim.data.iterations} simulation iterations.
                  The <span className="text-red-400 font-medium">red line</span> shows your baseline duration.
                  A 50% chance the project will complete by the P50 value (median), and 80% by the P80 value.
                </p>
              </div>
            </div>
          )}

          {/* Simulation Details */}
          <div className="bg-slate-900/50 rounded-lg border border-slate-800 p-6">
            <h3 className="text-lg font-semibold text-white mb-4">Simulation Details</h3>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div>
                <p className="text-xs text-slate-400 uppercase font-medium">Iterations</p>
                <p className="text-lg font-semibold text-white">{latestSim.data.iterations}</p>
              </div>
              <div>
                <p className="text-xs text-slate-400 uppercase font-medium">Completed At</p>
                <p className="text-lg font-semibold text-white">
                  {latestSim.data.completedAt
                    ? new Date(latestSim.data.completedAt).toLocaleDateString()
                    : "Pending"}
                </p>
              </div>
              <div>
                <p className="text-xs text-slate-400 uppercase font-medium">Created At</p>
                <p className="text-lg font-semibold text-white">
                  {new Date(latestSim.data.createdAt).toLocaleDateString()}
                </p>
              </div>
              <div>
                <p className="text-xs text-slate-400 uppercase font-medium">Simulation ID</p>
                <p className="text-sm font-mono text-white">{latestSim.data.id.substring(0, 8)}...</p>
              </div>
            </div>
          </div>

          {/* Previous Simulations */}
          {allSims?.data && allSims.data.length > 1 && (
            <div className="bg-slate-900/50 rounded-lg border border-slate-800 p-6">
              <h3 className="text-lg font-semibold text-white mb-4">Previous Simulations</h3>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-slate-800">
                      <th className="text-left py-3 px-4 font-medium text-slate-300">Date</th>
                      <th className="text-left py-3 px-4 font-medium text-slate-300">Iterations</th>
                      <th className="text-left py-3 px-4 font-medium text-slate-300">P50 Duration</th>
                      <th className="text-left py-3 px-4 font-medium text-slate-300">P80 Duration</th>
                      <th className="text-left py-3 px-4 font-medium text-slate-300">Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {allSims.data.slice(1, 6).map((sim) => (
                      <tr key={sim.id} className="border-b border-slate-800/50">
                        <td className="py-3 px-4">{new Date(sim.createdAt).toLocaleDateString()}</td>
                        <td className="py-3 px-4">{sim.iterations}</td>
                        <td className="py-3 px-4">{Math.round(sim.confidenceP50Duration || 0)} days</td>
                        <td className="py-3 px-4">{Math.round(sim.confidenceP80Duration || 0)} days</td>
                        <td className="py-3 px-4">
                          <span className={`px-2 py-1 rounded text-xs font-medium ${
                            sim.status === "COMPLETED"
                              ? "bg-emerald-500/10 text-emerald-400"
                              : sim.status === "FAILED"
                                ? "bg-red-500/10 text-red-400"
                                : "bg-amber-500/10 text-amber-400"
                          }`}>
                            {sim.status}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
