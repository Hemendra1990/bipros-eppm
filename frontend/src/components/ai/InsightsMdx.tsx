"use client";

import {
  Component,
  type ComponentType,
  type ReactNode,
  useEffect,
  useMemo,
  useState,
} from "react";
import * as runtime from "react/jsx-runtime";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

import { TrendingDown, TrendingUp, Minus, Info, AlertTriangle, AlertOctagon } from "lucide-react";

import type { ChartSpec } from "@/lib/types";
import { EChart, chartHasData } from "./charts/EChart";

interface InsightsMdxProps {
  mdx?: string | null;
  charts: ChartSpec[];
}

/**
 * Compiled MDX modules cached by source string. Compilation is expensive;
 * the same mdx text is often re-rendered on collapse/expand or refresh.
 */
const compileCache = new Map<string, ComponentType<Record<string, unknown>>>();

const trendIcons: Record<string, ReactNode> = {
  up: <TrendingUp size={12} className="text-success" />,
  down: <TrendingDown size={12} className="text-danger" />,
  flat: <Minus size={12} className="text-text-muted" />,
};

const noteSeverityIcon: Record<string, ReactNode> = {
  info: <Info size={14} className="text-blue-500" />,
  warning: <AlertTriangle size={14} className="text-yellow-500" />,
  critical: <AlertOctagon size={14} className="text-red-500" />,
};

function buildAllowedComponents(charts: ChartSpec[]) {
  const chartById = new Map(charts.map((c) => [c.id, c]));

  function Chart({ id }: { id?: string }) {
    if (!id) return null;
    const spec = chartById.get(id);
    if (!spec) {
      return (
        <span className="rounded bg-yellow-500/10 px-1 py-0.5 text-[10px] font-mono text-yellow-700 dark:text-yellow-400">
          chart:{id} (not found)
        </span>
      );
    }
    if (!chartHasData(spec)) return null;
    return (
      <div className="my-3">
        <EChart spec={spec} />
      </div>
    );
  }

  function Kpi({
    label,
    value,
    trend,
  }: {
    label?: string;
    value?: string;
    trend?: string;
  }) {
    return (
      <span className="mx-1 inline-flex items-center gap-1 rounded-md border border-border bg-surface px-2 py-0.5 text-xs">
        <span className="font-medium text-text-secondary">{label}:</span>
        <span className="font-semibold text-text-primary">{value}</span>
        {trend ? trendIcons[trend] ?? null : null}
      </span>
    );
  }

  function Note({
    severity = "info",
    children,
  }: {
    severity?: string;
    children?: ReactNode;
  }) {
    return (
      <span className="mx-1 inline-flex items-center gap-1 rounded-md border border-border bg-surface/60 px-2 py-0.5 text-xs">
        {noteSeverityIcon[severity] ?? noteSeverityIcon.info}
        <span className="text-text-primary">{children}</span>
      </span>
    );
  }

  // Render MDX paragraphs as <div> so block-level children (Chart wrappers,
  // EChart's outer <div>) don't produce invalid <div> in <p> nesting and the
  // hydration error that follows.
  function P({ children }: { children?: ReactNode }) {
    return <div className="mb-2 last:mb-0">{children}</div>;
  }

  return { Chart, Kpi, Note, p: P };
}

class MdxErrorBoundary extends Component<
  { fallback: ReactNode; children: ReactNode },
  { hasError: boolean }
> {
  state = { hasError: false };

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  render() {
    if (this.state.hasError) return this.props.fallback;
    return this.props.children;
  }
}

export function InsightsMdx({ mdx, charts }: InsightsMdxProps) {
  const [Component, setComponent] = useState<ComponentType<Record<string, unknown>> | null>(null);
  const [compileError, setCompileError] = useState(false);

  const components = useMemo(() => buildAllowedComponents(charts), [charts]);

  useEffect(() => {
    let cancelled = false;
    setCompileError(false);
    if (!mdx || !mdx.trim()) {
      setComponent(null);
      return;
    }

    const cached = compileCache.get(mdx);
    if (cached) {
      setComponent(() => cached);
      return;
    }

    (async () => {
      try {
        const { evaluate } = await import("@mdx-js/mdx");
        const { default: Compiled } = await evaluate(mdx, {
          ...(runtime as unknown as {
            Fragment: typeof runtime.Fragment;
            jsx: typeof runtime.jsx;
            jsxs: typeof runtime.jsxs;
          }),
        });
        compileCache.set(mdx, Compiled as ComponentType<Record<string, unknown>>);
        if (!cancelled) {
          setComponent(() => Compiled as ComponentType<Record<string, unknown>>);
        }
      } catch (err) {
        if (!cancelled) setCompileError(true);
        console.warn("[InsightsMdx] failed to compile MDX, falling back to markdown", err);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [mdx]);

  if (!mdx || !mdx.trim()) return null;

  const fallback = (
    <ReactMarkdown remarkPlugins={[remarkGfm]}>{mdx}</ReactMarkdown>
  );

  if (compileError || !Component) {
    return <div className="prose-sm text-sm text-text-secondary">{fallback}</div>;
  }

  return (
    <MdxErrorBoundary fallback={<div className="prose-sm text-sm text-text-secondary">{fallback}</div>}>
      <div className="prose-sm text-sm leading-relaxed text-text-primary">
        <Component components={components} />
      </div>
    </MdxErrorBoundary>
  );
}
