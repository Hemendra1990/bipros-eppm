"use client";

import { useCallback, useState } from "react";

import {
  AlertCircle,
  ChevronDown,
  ChevronUp,
  Loader2,
  Minus,
  RefreshCw,
  Sparkles,
  TrendingDown,
  TrendingUp,
} from "lucide-react";

import { aiApi } from "@/lib/api/aiApi";
import type { InsightsResponse } from "@/lib/types";

interface AiInsightsPanelProps {
  projectId: string;
  endpoint: string;
  defaultCollapsed?: boolean;
}

type PanelState =
  | { status: "empty" }
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "success"; data: InsightsResponse; lastUpdated: Date };

const severityClasses = {
  info: "bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/20",
  warning:
    "bg-yellow-500/10 text-yellow-600 dark:text-yellow-400 border-yellow-500/20",
  critical:
    "bg-red-500/10 text-red-600 dark:text-red-400 border-red-500/20",
};

const priorityClasses = {
  low: "bg-green-500/10 text-green-600 dark:text-green-400",
  medium:
    "bg-yellow-500/10 text-yellow-600 dark:text-yellow-400",
  high: "bg-red-500/10 text-red-600 dark:text-red-400",
};

function TrendIcon({ trend }: { trend: "up" | "down" | "flat" | null }) {
  if (trend === "up") return <TrendingUp size={14} className="text-success" />;
  if (trend === "down")
    return <TrendingDown size={14} className="text-danger" />;
  if (trend === "flat")
    return <Minus size={14} className="text-text-muted" />;
  return null;
}

export function AiInsightsPanel({
  projectId,
  endpoint,
  defaultCollapsed = false,
}: AiInsightsPanelProps) {
  const [state, setState] = useState<PanelState>({ status: "empty" });
  const [collapsed, setCollapsed] = useState(defaultCollapsed);

  const fetchInsights = useCallback(
    async (force = false) => {
      setState({ status: "loading" });
      try {
        const response = await aiApi.getInsights(endpoint, projectId, force);
        if (response.error) {
          setState({ status: "error", message: response.error.message });
        } else if (response.data) {
          setState({
            status: "success",
            data: response.data,
            lastUpdated: new Date(),
          });
        } else {
          setState({ status: "error", message: "No insights data returned" });
        }
      } catch (err) {
        setState({
          status: "error",
          message:
            err instanceof Error ? err.message : "Failed to fetch insights",
        });
      }
    },
    [endpoint, projectId]
  );

  const handleGenerate = () => fetchInsights(false);
  const handleRefresh = () => fetchInsights(true);

  return (
    <div className="rounded-lg border border-border bg-surface/50">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-border">
        <div className="flex items-center gap-2">
          <Sparkles size={16} className="text-accent" />
          <span className="text-sm font-semibold text-text-primary">
            AI Insights
          </span>
        </div>
        <div className="flex items-center gap-1">
          {state.status === "success" && (
            <button
              onClick={handleRefresh}
              className="p-1.5 rounded-md text-text-secondary hover:text-text-primary hover:bg-surface-hover transition-colors"
              title="Refresh insights"
            >
              <RefreshCw size={14} />
            </button>
          )}
          <button
            onClick={() => setCollapsed(!collapsed)}
            className="p-1.5 rounded-md text-text-secondary hover:text-text-primary hover:bg-surface-hover transition-colors"
            title={collapsed ? "Expand" : "Collapse"}
          >
            {collapsed ? <ChevronDown size={16} /> : <ChevronUp size={16} />}
          </button>
        </div>
      </div>

      {!collapsed && (
        <>
          {state.status === "empty" && (
            <div className="px-4 py-8 text-center">
              <Sparkles
                size={32}
                className="mx-auto mb-3 text-text-muted opacity-50"
              />
              <p className="text-sm text-text-muted mb-4">
                Click to generate AI-powered insights for this tab
              </p>
              <button
                onClick={handleGenerate}
                className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover transition-colors"
              >
                <Sparkles size={16} />
                Generate insights
              </button>
            </div>
          )}

          {state.status === "loading" && (
            <div className="px-4 py-8 text-center">
              <Loader2
                size={32}
                className="mx-auto mb-3 animate-spin text-accent"
              />
              <p className="text-sm text-text-muted">Generating insights...</p>
            </div>
          )}

          {state.status === "error" && (
            <div className="px-4 py-6">
              <div className="rounded-lg border border-danger/30 bg-danger/10 p-4 text-sm text-danger mb-4">
                <div className="flex items-start gap-2">
                  <AlertCircle size={16} className="shrink-0 mt-0.5" />
                  <span>{state.message}</span>
                </div>
              </div>
              <button
                onClick={handleGenerate}
                className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover transition-colors"
              >
                Retry
              </button>
            </div>
          )}

          {state.status === "success" && (() => {
            const d = state.data;
            const isEmpty =
              !d.summary?.trim() &&
              !d.rationale?.trim() &&
              !(d.highlights && d.highlights.length > 0) &&
              !(d.variances && d.variances.length > 0) &&
              !(d.recommendations && d.recommendations.length > 0) &&
              !(d.findings && d.findings.length > 0);

            if (isEmpty) {
              return (
                <div className="px-4 py-8 text-center">
                  <Sparkles size={32} className="mx-auto mb-3 text-text-muted opacity-50" />
                  <p className="text-sm text-text-primary mb-1">No insights available</p>
                  <p className="text-xs text-text-muted mb-4">
                    The AI returned an empty response. The configured model may not support
                    strict JSON schema, or this tab has too little data to analyze.
                  </p>
                  <button
                    onClick={handleRefresh}
                    className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover transition-colors"
                  >
                    <RefreshCw size={14} />
                    Try again
                  </button>
                  <p className="text-xs text-text-muted mt-3">
                    Last attempted: {state.lastUpdated.toLocaleString()}
                  </p>
                </div>
              );
            }

            return (
            <div className="p-4 space-y-5">
              {/* Summary */}
              {state.data.summary && (
                <div>
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-text-muted mb-2">
                    Summary
                  </h3>
                  <p className="text-sm text-text-primary leading-relaxed">
                    {state.data.summary}
                  </p>
                </div>
              )}

              {/* Highlights */}
              {state.data.highlights && state.data.highlights.length > 0 && (
                <div>
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-text-muted mb-2">
                    Highlights
                  </h3>
                  <div className="flex flex-wrap gap-2">
                    {state.data.highlights.map((h, i) => (
                      <div
                        key={i}
                        className={`inline-flex items-center gap-1.5 rounded-md border px-2.5 py-1 text-xs font-medium ${severityClasses[h.severity]}`}
                      >
                        <span className="font-semibold">{h.label}:</span>
                        <span>{h.value}</span>
                        <TrendIcon trend={h.trend} />
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Variances */}
              {state.data.variances && state.data.variances.length > 0 && (
                <div>
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-text-muted mb-2">
                    Variances
                  </h3>
                  <div className="overflow-x-auto rounded-lg border border-border">
                    <table className="w-full text-sm">
                      <thead className="border-b border-border bg-surface/80">
                        <tr>
                          <th className="px-3 py-2 text-left font-medium text-text-secondary">
                            Name
                          </th>
                          <th className="px-3 py-2 text-left font-medium text-text-secondary">
                            Delta
                          </th>
                          <th className="px-3 py-2 text-left font-medium text-text-secondary">
                            Explanation
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {state.data.variances.map((v, i) => (
                          <tr
                            key={i}
                            className="border-b border-border last:border-b-0 hover:bg-surface/80"
                          >
                            <td className="px-3 py-2 font-medium text-text-primary">
                              {v.name}
                            </td>
                            <td className="px-3 py-2 text-text-primary">
                              {v.delta}
                            </td>
                            <td className="px-3 py-2 text-text-secondary">
                              {v.explanation}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}

              {/* Recommendations */}
              {state.data.recommendations && state.data.recommendations.length > 0 && (
                <div>
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-text-muted mb-2">
                    Recommendations
                  </h3>
                  <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                    {state.data.recommendations.map((r, i) => (
                      <div
                        key={i}
                        className="rounded-lg border border-border bg-surface/50 p-4"
                      >
                        <div className="flex items-center justify-between mb-2">
                          <h4 className="text-sm font-semibold text-text-primary">
                            {r.title}
                          </h4>
                          <span
                            className={`rounded-full px-2 py-0.5 text-xs font-medium capitalize ${priorityClasses[r.priority]}`}
                          >
                            {r.priority}
                          </span>
                        </div>
                        <p className="text-sm text-text-primary mb-1">
                          {r.action}
                        </p>
                        <p className="text-xs text-text-muted">
                          {r.rationale}
                        </p>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Findings */}
              {state.data.findings && state.data.findings.length > 0 && (
                <div>
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-text-muted mb-2">
                    Findings
                  </h3>
                  <div className="grid gap-3 sm:grid-cols-2">
                    {state.data.findings.map((f, i) => (
                      <div
                        key={i}
                        className={`rounded-lg border px-4 py-3 ${severityClasses[f.severity]}`}
                      >
                        <div className="flex items-center gap-2 mb-1">
                          <span className="text-xs font-semibold uppercase tracking-wider">
                            {f.severity}
                          </span>
                        </div>
                        <p className="text-sm font-medium text-text-primary mb-1">
                          {f.label}
                        </p>
                        <p className="text-xs text-text-secondary">
                          {f.detail}
                        </p>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Rationale */}
              {state.data.rationale && (
                <div>
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-text-muted mb-2">
                    Rationale
                  </h3>
                  <p className="text-sm text-text-secondary leading-relaxed">
                    {state.data.rationale}
                  </p>
                </div>
              )}

              {/* Footer */}
              <div className="flex items-center justify-between pt-3 border-t border-border">
                <span className="text-xs text-text-muted">
                  Last updated: {state.lastUpdated.toLocaleString()}
                </span>
                <button
                  onClick={handleRefresh}
                  className="inline-flex items-center gap-1.5 rounded-md border border-border px-3 py-1.5 text-xs font-medium text-text-secondary hover:bg-surface-hover transition-colors"
                >
                  <RefreshCw size={12} />
                  Refresh
                </button>
              </div>
            </div>
            );
          })()}
        </>
      )}
    </div>
  );
}
