"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  resourceApi,
  type LevelingMode,
  type ResourceLevelingResponse,
} from "@/lib/api/resourceApi";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  ReferenceLine,
} from "recharts";

const LEVELING_MODES: { value: LevelingMode; label: string; description: string }[] = [
  {
    value: "LEVEL_WITHIN_FLOAT",
    label: "Level Within Float",
    description: "Resolve overallocations by delaying activities only within their available float. Does not extend the project end date.",
  },
  {
    value: "LEVEL_ALL",
    label: "Level All",
    description: "Resolve all overallocations by delaying activities as needed. May extend the project end date.",
  },
  {
    value: "SMOOTH",
    label: "Smooth",
    description: "Minimize peak resource demand by redistributing non-critical activities within their float.",
  },
];

interface ResourceLevelingDialogProps {
  projectId: string;
  open: boolean;
  onClose: () => void;
}

export function ResourceLevelingDialog({ projectId, open, onClose }: ResourceLevelingDialogProps) {
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<LevelingMode>("LEVEL_ALL");
  const [result, setResult] = useState<ResourceLevelingResponse | null>(null);

  const { data: utilizationData } = useQuery({
    queryKey: ["utilization-profile", projectId],
    queryFn: () => resourceApi.getUtilizationProfile(projectId),
    enabled: open,
  });

  const levelMutation = useMutation({
    mutationFn: () => resourceApi.levelResources(projectId, { mode }),
    onSuccess: (data) => {
      setResult(data.data ?? null);
      queryClient.invalidateQueries({ queryKey: ["resource-assignments", projectId] });
      queryClient.invalidateQueries({ queryKey: ["utilization-profile", projectId] });
      queryClient.invalidateQueries({ queryKey: ["resource-histogram", projectId] });
      queryClient.invalidateQueries({ queryKey: ["activities", projectId] });
    },
  });

  if (!open) return null;

  const utilizationEntries = utilizationData?.data ?? [];

  // Aggregate utilization by date (sum demand across resources, use max capacity)
  const dateMap = new Map<string, { date: string; demand: number; capacity: number }>();
  for (const entry of utilizationEntries) {
    const existing = dateMap.get(entry.date);
    if (existing) {
      existing.demand += entry.demand;
      existing.capacity += entry.capacity;
    } else {
      dateMap.set(entry.date, { date: entry.date, demand: entry.demand, capacity: entry.capacity });
    }
  }
  const chartData = Array.from(dateMap.values()).sort((a, b) => a.date.localeCompare(b.date));

  const selectedMode = LEVELING_MODES.find((m) => m.value === mode);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
      <div className="w-full max-w-4xl max-h-[90vh] overflow-y-auto rounded-lg border border-border bg-surface p-6 shadow-xl">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-bold text-text-primary">Resource Leveling</h2>
          <button
            onClick={onClose}
            className="rounded-md p-1 text-text-secondary hover:bg-surface-hover hover:text-text-primary"
          >
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Mode Selector */}
        <div className="mb-6">
          <label className="block text-sm font-medium text-text-secondary mb-2">Leveling Mode</label>
          <div className="grid grid-cols-3 gap-3">
            {LEVELING_MODES.map((m) => (
              <button
                key={m.value}
                onClick={() => { setMode(m.value); setResult(null); }}
                className={`rounded-lg border p-3 text-left transition-colors ${
                  mode === m.value
                    ? "border-accent bg-blue-950/50"
                    : "border-border bg-surface-hover/50 hover:border-border"
                }`}
              >
                <div className="text-sm font-medium text-text-primary">{m.label}</div>
                <div className="mt-1 text-xs text-text-secondary">{m.description}</div>
              </button>
            ))}
          </div>
        </div>

        {/* Utilization Chart (Before) */}
        {chartData.length > 0 && (
          <div className="mb-6 rounded-lg border border-border bg-surface/50 p-4">
            <h3 className="mb-3 text-sm font-semibold text-text-secondary">Resource Utilization Profile</h3>
            <ResponsiveContainer width="100%" height={250}>
              <BarChart data={chartData} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                <XAxis dataKey="date" stroke="#64748b" style={{ fontSize: "10px" }} />
                <YAxis stroke="#64748b" style={{ fontSize: "11px" }} />
                <Tooltip
                  contentStyle={{ backgroundColor: "#1e293b", border: "1px solid #334155", borderRadius: "0.5rem" }}
                  formatter={(value) => typeof value === "number" ? value.toFixed(2) : value}
                />
                <Legend />
                <Bar dataKey="demand" fill="#3b82f6" name="Demand" />
                <Bar dataKey="capacity" fill="#334155" name="Capacity" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}

        {/* Run Button */}
        <div className="mb-6">
          <button
            onClick={() => levelMutation.mutate()}
            disabled={levelMutation.isPending}
            className="rounded-md bg-accent px-6 py-2.5 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:bg-surface-active disabled:text-text-secondary"
          >
            {levelMutation.isPending ? "Running..." : `Run ${selectedMode?.label ?? "Leveling"}`}
          </button>
        </div>

        {/* Results */}
        {result && (
          <div className="space-y-4">
            {/* Metrics Cards */}
            <div className="grid grid-cols-4 gap-3">
              <div className="rounded-lg border border-border bg-surface-hover/50 p-3">
                <div className="text-xs text-text-secondary">Activities Shifted</div>
                <div className="mt-1 text-lg font-bold text-text-primary">{result.activitiesShifted}</div>
              </div>
              <div className="rounded-lg border border-border bg-surface-hover/50 p-3">
                <div className="text-xs text-text-secondary">Iterations</div>
                <div className="mt-1 text-lg font-bold text-text-primary">{result.iterationsUsed}</div>
              </div>
              <div className="rounded-lg border border-border bg-surface-hover/50 p-3">
                <div className="text-xs text-text-secondary">Peak Before</div>
                <div className="mt-1 text-lg font-bold text-danger">
                  {(result.peakUtilizationBefore * 100).toFixed(1)}%
                </div>
              </div>
              <div className="rounded-lg border border-border bg-surface-hover/50 p-3">
                <div className="text-xs text-text-secondary">Peak After</div>
                <div className={`mt-1 text-lg font-bold ${
                  result.peakUtilizationAfter <= 1 ? "text-green-300" : "text-yellow-300"
                }`}>
                  {(result.peakUtilizationAfter * 100).toFixed(1)}%
                </div>
              </div>
            </div>

            {/* Shifted Activities Table */}
            {result.shiftedActivities.length > 0 && (
              <div className="rounded-lg border border-border bg-surface/50 p-4">
                <h3 className="mb-3 text-sm font-semibold text-text-secondary">Shifted Activities</h3>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-border text-left text-text-secondary">
                        <th className="px-3 py-2">Activity ID</th>
                        <th className="px-3 py-2">Original Start</th>
                        <th className="px-3 py-2">New Start</th>
                        <th className="px-3 py-2 text-right">Delay (days)</th>
                      </tr>
                    </thead>
                    <tbody>
                      {result.shiftedActivities.map((sa) => (
                        <tr key={sa.activityId} className="border-b border-border hover:bg-surface-hover/50">
                          <td className="px-3 py-2 font-mono text-xs text-text-primary">
                            {sa.activityId.substring(0, 8)}...
                          </td>
                          <td className="px-3 py-2 text-text-secondary">{sa.originalStart}</td>
                          <td className="px-3 py-2 text-blue-300">{sa.newStart}</td>
                          <td className="px-3 py-2 text-right text-yellow-300">+{sa.delayDays}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {/* Messages */}
            {result.messages.length > 0 && (
              <div className="rounded-lg border border-border bg-surface/50 p-4">
                <h3 className="mb-2 text-sm font-semibold text-text-secondary">Log</h3>
                <div className="max-h-40 overflow-y-auto space-y-1">
                  {result.messages.map((msg, i) => (
                    <div key={i} className="text-xs text-text-secondary">{msg}</div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {/* Error */}
        {levelMutation.isError && (
          <div className="mt-4 rounded-lg border border-red-800 bg-red-950/50 p-3 text-sm text-danger">
            Leveling failed: {levelMutation.error instanceof Error ? levelMutation.error.message : "Unknown error"}
          </div>
        )}
      </div>
    </div>
  );
}
