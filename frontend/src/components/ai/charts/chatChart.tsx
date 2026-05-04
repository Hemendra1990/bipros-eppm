"use client";

import { useMemo } from "react";

import type { ChartSpec, ChartType } from "@/lib/types";

import { EChart } from "./EChart";

/**
 * Compact chart spec the chat LLM is taught to emit inside a fenced
 * ```chart``` code block. We deliberately keep this format much smaller and
 * higher-level than raw ECharts options, because the model is bad at
 * authoring verbose JSON reliably. Anything richer should go through the
 * deterministic insights path.
 */
export interface CompactChartSpec {
  title: string;
  type: "bar" | "line" | "pie" | "donut" | "area" | "horizontalBar";
  x?: (string | number)[];
  y?: number[];
  series?: { name: string; values: number[] }[];
  stacked?: boolean;
  note?: string;
}

function compactToEcharts(compact: CompactChartSpec): ChartSpec | null {
  const t = compact.type;
  if (!t) return null;

  // Build a list of series rows in {name, values} form, falling back to the
  // simpler x/y pair the model emits for single-series charts.
  let seriesRows: { name: string; values: number[] }[];
  if (Array.isArray(compact.series) && compact.series.length > 0) {
    seriesRows = compact.series;
  } else if (Array.isArray(compact.y)) {
    seriesRows = [{ name: compact.title, values: compact.y }];
  } else {
    return null;
  }

  const labels = (compact.x ?? []).map((v) => String(v));
  const isPie = t === "pie" || t === "donut";

  if (isPie) {
    const values = seriesRows[0].values;
    const radius = t === "donut" ? ["55%", "75%"] : "70%";
    const data = labels.map((name, i) => ({ name, value: values[i] ?? 0 }));
    return {
      id: cryptoRandomId(),
      title: compact.title,
      type: t,
      note: compact.note ?? null,
      option: {
        tooltip: { trigger: "item" },
        legend: { bottom: 0, type: "scroll" },
        series: [
          {
            type: "pie",
            radius,
            avoidLabelOverlap: true,
            label: { formatter: "{b}: {d}%", fontSize: 11 },
            data,
          },
        ],
      },
    };
  }

  // Cartesian charts: bar / horizontalBar / line / area.
  const isHorizontal = t === "horizontalBar";
  const echartsType: "bar" | "line" = t === "line" || t === "area" ? "line" : "bar";
  const isArea = t === "area";

  const singleSeriesBar = seriesRows.length === 1 && echartsType === "bar";
  const series = seriesRows.map((s) => {
    const base: Record<string, unknown> = {
      name: s.name,
      type: echartsType,
      data: s.values,
    };
    // Single-series bar: color each data point from the palette so the
    // breakdown reads at a glance. Multi-series uses the default per-series
    // cycle. Theme palette is injected at the top level by applyThemeToOption.
    if (singleSeriesBar) base.colorBy = "data";
    if (isArea) base.areaStyle = { opacity: 0.25 };
    if (compact.stacked) base.stack = "total";
    if (echartsType === "bar") base.barMaxWidth = 28;
    return base;
  });

  const valueAxis = { type: "value" as const };
  const categoryAxis = {
    type: "category" as const,
    data: labels,
    axisLabel: { fontSize: 11, interval: 0, overflow: "truncate", width: 90 },
  };

  return {
    id: cryptoRandomId(),
    title: compact.title,
    type: (echartsType === "line" ? (isArea ? "area" : "line") : "bar") as ChartType,
    note: compact.note ?? null,
    option: {
      tooltip: { trigger: "axis" },
      legend: seriesRows.length > 1 ? { bottom: 0, type: "scroll" } : undefined,
      grid: { left: 50, right: 16, top: 24, bottom: seriesRows.length > 1 ? 32 : 24 },
      xAxis: isHorizontal ? valueAxis : categoryAxis,
      yAxis: isHorizontal ? categoryAxis : valueAxis,
      series,
    },
  };
}

function cryptoRandomId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return Math.random().toString(36).slice(2);
}

export function ChatChart({ raw }: { raw: string }) {
  const spec = useMemo(() => {
    try {
      const compact = JSON.parse(raw) as CompactChartSpec;
      return compactToEcharts(compact);
    } catch {
      return null;
    }
  }, [raw]);

  if (!spec) return null;
  return <EChart spec={spec} height={220} />;
}
