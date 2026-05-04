"use client";

import { useCallback, useEffect, useRef, useState } from "react";

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
import type { ChartSpec, InsightsResponse } from "@/lib/types";

import { EChart, chartHasData } from "./charts/EChart";
import { InsightsMdx } from "./InsightsMdx";

interface AiInsightsPanelProps {
  projectId: string;
  endpoint: string;
  defaultCollapsed?: boolean;
  autoLoad?: boolean;
}

type PanelState =
  | { status: "empty" }
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "success"; data: InsightsResponse; lastUpdated: Date };

const severityClasses = {
  info: "bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/20",
  warning: "bg-yellow-500/10 text-yellow-600 dark:text-yellow-400 border-yellow-500/20",
  critical: "bg-red-500/10 text-red-600 dark:text-red-400 border-red-500/20",
};

const priorityClasses = {
  low: "bg-green-500/10 text-green-600 dark:text-green-400",
  medium: "bg-yellow-500/10 text-yellow-600 dark:text-yellow-400",
  high: "bg-red-500/10 text-red-600 dark:text-red-400",
};

function TrendIcon({ trend }: { trend: "up" | "down" | "flat" | null }) {
  if (trend === "up") return <TrendingUp size={12} className="text-success" />;
  if (trend === "down") return <TrendingDown size={12} className="text-danger" />;
  if (trend === "flat") return <Minus size={12} className="text-text-muted" />;
  return null;
}

export function AiInsightsPanel({
  projectId,
  endpoint,
  defaultCollapsed = false,
  autoLoad = true,
}: AiInsightsPanelProps) {
  const [state, setState] = useState<PanelState>({ status: "empty" });
  const [collapsed, setCollapsed] = useState(defaultCollapsed);
  const [detailOpen, setDetailOpen] = useState(false);

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
          message: err instanceof Error ? err.message : "Failed to fetch insights",
        });
      }
    },
    [endpoint, projectId]
  );

  const handleGenerate = () => fetchInsights(false);
  const handleRefresh = () => fetchInsights(true);

  const autoLoadedRef = useRef(false);
  useEffect(() => {
    if (autoLoad && !autoLoadedRef.current) {
      autoLoadedRef.current = true;
      // eslint-disable-next-line react-hooks/set-state-in-effect
      void fetchInsights(false);
    }
  }, [autoLoad, fetchInsights]);

  return (
    <div className="rounded-lg border border-border bg-surface/50">
      <div className="flex items-center justify-between border-b border-border px-4 py-3">
        <div className="flex items-center gap-2">
          <Sparkles size={16} className="text-accent" />
          <span className="text-sm font-semibold text-text-primary">AI Insights</span>
        </div>
        <div className="flex items-center gap-1">
          {state.status === "success" && (
            <button
              onClick={handleRefresh}
              className="rounded-md p-1.5 text-text-secondary transition-colors hover:bg-surface-hover hover:text-text-primary"
              title="Refresh insights"
            >
              <RefreshCw size={14} />
            </button>
          )}
          <button
            onClick={() => setCollapsed(!collapsed)}
            className="rounded-md p-1.5 text-text-secondary transition-colors hover:bg-surface-hover hover:text-text-primary"
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
              <Sparkles size={32} className="mx-auto mb-3 text-text-muted opacity-50" />
              <p className="mb-4 text-sm text-text-muted">
                Click to generate AI-powered insights for this tab
              </p>
              <button
                onClick={handleGenerate}
                className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary transition-colors hover:bg-accent-hover"
              >
                <Sparkles size={16} />
                Generate insights
              </button>
            </div>
          )}

          {state.status === "loading" && (
            <div className="px-4 py-8 text-center">
              <Loader2 size={32} className="mx-auto mb-3 animate-spin text-accent" />
              <p className="text-sm text-text-muted">Generating insights...</p>
            </div>
          )}

          {state.status === "error" && (
            <div className="px-4 py-6">
              <div className="mb-4 rounded-lg border border-danger/30 bg-danger/10 p-4 text-sm text-danger">
                <div className="flex items-start gap-2">
                  <AlertCircle size={16} className="mt-0.5 shrink-0" />
                  <span>{state.message}</span>
                </div>
              </div>
              <button
                onClick={handleGenerate}
                className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary transition-colors hover:bg-accent-hover"
              >
                Retry
              </button>
            </div>
          )}

          {state.status === "success" && (() => {
            const d = state.data;
            const charts: ChartSpec[] = (d.charts ?? []).filter((c) => c && chartHasData(c));
            const hasMdx = !!(d.mdx && d.mdx.trim());
            const hasHighlights = !!(d.highlights && d.highlights.length > 0);
            const hasFindings = !!(d.findings && d.findings.length > 0);
            const hasRecs = !!(d.recommendations && d.recommendations.length > 0);
            const hasVariances = !!(d.variances && d.variances.length > 0);
            const hasRationale = !!(d.rationale && d.rationale.trim());

            const isEmpty =
              !d.summary?.trim() &&
              !hasMdx &&
              !hasHighlights &&
              !hasFindings &&
              !hasRecs &&
              !hasVariances &&
              charts.length === 0;

            if (isEmpty) {
              return (
                <div className="px-4 py-8 text-center">
                  <Sparkles size={32} className="mx-auto mb-3 text-text-muted opacity-50" />
                  <p className="mb-1 text-sm text-text-primary">No insights available</p>
                  <p className="mb-4 text-xs text-text-muted">
                    The AI returned an empty response, or this tab has too little data to analyze.
                  </p>
                  <button
                    onClick={handleRefresh}
                    className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary transition-colors hover:bg-accent-hover"
                  >
                    <RefreshCw size={14} />
                    Try again
                  </button>
                </div>
              );
            }

            return (
              <div className="space-y-4 p-4">
                {/* KPI strip */}
                {hasHighlights && (
                  <div className="flex flex-wrap gap-1.5">
                    {d.highlights!.map((h, i) => (
                      <div
                        key={i}
                        className={`inline-flex items-center gap-1.5 rounded-md border px-2 py-1 text-xs font-medium ${severityClasses[h.severity]}`}
                      >
                        <span className="font-semibold">{h.label}</span>
                        <span className="text-text-primary">{h.value}</span>
                        <TrendIcon trend={h.trend} />
                      </div>
                    ))}
                  </div>
                )}

                {/* Chart grid — primary visual area */}
                {charts.length > 0 && (
                  <div className="grid gap-3 sm:grid-cols-1 md:grid-cols-2">
                    {charts.map((c) => (
                      <EChart key={c.id} spec={c} />
                    ))}
                  </div>
                )}

                {/* MDX narrative — small text below charts */}
                {hasMdx ? (
                  <InsightsMdx mdx={d.mdx} charts={charts} />
                ) : d.summary?.trim() ? (
                  <p className="text-sm leading-relaxed text-text-secondary">{d.summary}</p>
                ) : null}

                {/* Severity callouts: critical findings always visible, rest in accordion */}
                {hasFindings && (
                  <div className="flex flex-wrap gap-1.5">
                    {d.findings!
                      .filter((f) => f.severity === "critical" || f.severity === "warning")
                      .map((f, i) => (
                        <span
                          key={i}
                          className={`inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-xs ${severityClasses[f.severity]}`}
                          title={f.detail}
                        >
                          <span className="font-semibold uppercase tracking-wider">
                            {f.severity}
                          </span>
                          <span className="text-text-primary">{f.label}</span>
                        </span>
                      ))}
                  </div>
                )}

                {/* Details accordion */}
                {(hasFindings || hasRecs || hasVariances || hasRationale) && (
                  <div className="rounded-md border border-border">
                    <button
                      onClick={() => setDetailOpen(!detailOpen)}
                      className="flex w-full items-center justify-between px-3 py-2 text-xs font-medium text-text-secondary hover:bg-surface-hover"
                    >
                      <span>
                        {detailOpen ? "Hide" : "Show"} detailed analysis
                        {hasRecs && ` (${d.recommendations!.length} recs`}
                        {hasFindings && ` · ${d.findings!.length} findings`}
                        {hasVariances && ` · ${d.variances!.length} variances`}
                        {hasRecs && ")"}
                      </span>
                      {detailOpen ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                    </button>
                    {detailOpen && (
                      <div className="space-y-4 border-t border-border p-3">
                        {hasRecs && (
                          <div>
                            <h4 className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-text-muted">
                              Recommendations
                            </h4>
                            <div className="grid gap-2 sm:grid-cols-2">
                              {d.recommendations!.map((r, i) => (
                                <div
                                  key={i}
                                  className="rounded-md border border-border bg-surface/40 p-2.5"
                                >
                                  <div className="mb-1 flex items-center justify-between gap-2">
                                    <span className="text-xs font-semibold text-text-primary">
                                      {r.title}
                                    </span>
                                    <span
                                      className={`rounded-full px-1.5 py-0.5 text-[10px] font-medium capitalize ${priorityClasses[r.priority]}`}
                                    >
                                      {r.priority}
                                    </span>
                                  </div>
                                  <p className="text-xs text-text-primary">{r.action}</p>
                                  {r.rationale && (
                                    <p className="mt-1 text-[11px] text-text-muted">{r.rationale}</p>
                                  )}
                                </div>
                              ))}
                            </div>
                          </div>
                        )}

                        {hasFindings && (
                          <div>
                            <h4 className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-text-muted">
                              All Findings
                            </h4>
                            <div className="space-y-1.5">
                              {d.findings!.map((f, i) => (
                                <div
                                  key={i}
                                  className={`rounded-md border px-2 py-1.5 text-xs ${severityClasses[f.severity]}`}
                                >
                                  <span className="mr-2 font-semibold uppercase tracking-wider">
                                    {f.severity}
                                  </span>
                                  <span className="font-medium text-text-primary">{f.label}</span>
                                  {f.detail && (
                                    <span className="ml-1 text-text-secondary">— {f.detail}</span>
                                  )}
                                </div>
                              ))}
                            </div>
                          </div>
                        )}

                        {hasVariances && (
                          <div>
                            <h4 className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-text-muted">
                              Variances
                            </h4>
                            <div className="overflow-x-auto rounded-md border border-border">
                              <table className="w-full text-xs">
                                <thead className="border-b border-border bg-surface/80">
                                  <tr>
                                    <th className="px-2 py-1.5 text-left font-medium text-text-secondary">
                                      Name
                                    </th>
                                    <th className="px-2 py-1.5 text-left font-medium text-text-secondary">
                                      Delta
                                    </th>
                                    <th className="px-2 py-1.5 text-left font-medium text-text-secondary">
                                      Explanation
                                    </th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {d.variances!.map((v, i) => (
                                    <tr
                                      key={i}
                                      className="border-b border-border last:border-b-0 hover:bg-surface/80"
                                    >
                                      <td className="px-2 py-1.5 font-medium text-text-primary">
                                        {v.name}
                                      </td>
                                      <td className="px-2 py-1.5 text-text-primary">{v.delta}</td>
                                      <td className="px-2 py-1.5 text-text-secondary">
                                        {v.explanation}
                                      </td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </div>
                          </div>
                        )}

                        {hasRationale && (
                          <div>
                            <h4 className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-text-muted">
                              Rationale
                            </h4>
                            <p className="text-xs leading-relaxed text-text-secondary">
                              {d.rationale}
                            </p>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                )}

                {/* Footer */}
                <div className="flex items-center justify-between border-t border-border pt-2">
                  <span className="text-[11px] text-text-muted">
                    Updated: {state.lastUpdated.toLocaleString()}
                  </span>
                  <button
                    onClick={handleRefresh}
                    className="inline-flex items-center gap-1 rounded-md border border-border px-2 py-1 text-[11px] font-medium text-text-secondary transition-colors hover:bg-surface-hover"
                  >
                    <RefreshCw size={11} />
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
