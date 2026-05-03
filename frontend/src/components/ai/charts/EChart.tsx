"use client";

import { Component, useEffect, useMemo, useState, type ReactNode } from "react";
import dynamic from "next/dynamic";
import { useTheme } from "next-themes";

import type { ChartSpec } from "@/lib/types";
import { applyThemeToOption, readTokens, type ResolvedTokens } from "./chartTheme";

const ReactECharts = dynamic(() => import("echarts-for-react"), {
  ssr: false,
  loading: () => (
    <div className="h-[260px] w-full animate-pulse rounded-md bg-surface/40" />
  ),
});

interface EChartProps {
  spec: ChartSpec;
  height?: number;
}

/**
 * Returns true when the spec contains at least one series with non-empty data.
 * Used to skip rendering charts the LLM emitted with an empty/placeholder option.
 */
export function chartHasData(spec: ChartSpec | null | undefined): boolean {
  if (!spec?.option) return false;
  const opt = spec.option as { series?: unknown };
  const series = opt.series;
  if (series == null) return false;
  const list = Array.isArray(series) ? series : [series];
  if (list.length === 0) return false;
  return list.some((s) => {
    if (!s || typeof s !== "object") return false;
    const data = (s as { data?: unknown }).data;
    if (Array.isArray(data)) return data.length > 0;
    if (data && typeof data === "object") return true;
    if (typeof (s as { value?: unknown }).value === "number") return true;
    return false;
  });
}

class ChartErrorBoundary extends Component<
  { children: ReactNode },
  { hasError: boolean }
> {
  state = { hasError: false };
  static getDerivedStateFromError() {
    return { hasError: true };
  }
  componentDidCatch(err: unknown) {
    console.warn("[EChart] render error, hiding chart", err);
  }
  render() {
    if (this.state.hasError) return null;
    return this.props.children;
  }
}

export function EChart({ spec, height = 260 }: EChartProps) {
  const { resolvedTheme } = useTheme();
  const [tokens, setTokens] = useState<ResolvedTokens | null>(null);

  useEffect(() => {
    // Defer one frame so the .dark class swap has flushed to computed style.
    const id = requestAnimationFrame(() => setTokens(readTokens()));
    return () => cancelAnimationFrame(id);
  }, [resolvedTheme]);

  const themedOption = useMemo(() => {
    const raw = spec.option as Record<string, unknown> | null;
    if (!raw) return raw;
    if (!tokens) return raw;
    try {
      return applyThemeToOption(spec.type, raw, tokens);
    } catch (err) {
      console.warn("[EChart] theming failed, using raw option", err);
      return raw;
    }
  }, [spec.type, spec.option, tokens]);

  if (!chartHasData(spec)) return null;

  return (
    <ChartErrorBoundary>
      <div className="rounded-md border border-border bg-surface/30 p-3">
        <div className="mb-1 flex items-baseline justify-between gap-2">
          <span className="text-xs font-semibold uppercase tracking-wider text-text-secondary">
            {spec.title}
          </span>
          {spec.note && (
            <span className="truncate text-[10px] text-text-muted">{spec.note}</span>
          )}
        </div>
        {tokens ? (
          <ReactECharts
            option={themedOption as Record<string, unknown>}
            style={{ height, width: "100%" }}
            opts={{ renderer: "canvas" }}
            notMerge
            lazyUpdate
            autoResize
          />
        ) : (
          // Hold the slot until tokens resolve so echarts.init runs once after
          // layout — gauges (canvas-relative radius/center) stick at a tiny size
          // if init happens before the container has been measured.
          <div
            style={{ height }}
            className="w-full animate-pulse rounded-md bg-surface/40"
          />
        )}
      </div>
    </ChartErrorBoundary>
  );
}
