"use client";

import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeSanitize from "rehype-sanitize";
import { Wrench } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import type { AnalyticsAssistantResponse } from "@/lib/api/analyticsApi";

interface AssistantMessageProps {
  response: AnalyticsAssistantResponse;
}

export function AssistantMessage({ response }: AssistantMessageProps) {
  const {
    narrative,
    toolUsed,
    columns,
    rows,
    sqlExecuted,
    tokensInput,
    tokensOutput,
    costMicros,
  } = response;

  const hasTable = !!(columns && columns.length && rows && rows.length);
  const hasFooter =
    tokensInput != null || tokensOutput != null || costMicros != null;

  return (
    <div className="space-y-3">
      {toolUsed && (
        <div className="flex justify-end">
          <Badge variant="info" withDot>
            <Wrench size={11} aria-hidden /> Tool: {toolUsed}
          </Badge>
        </div>
      )}

      {narrative && narrative.trim().length > 0 && (
        <div className="text-sm leading-relaxed text-text-primary">
          <ReactMarkdown
            remarkPlugins={[remarkGfm]}
            rehypePlugins={[rehypeSanitize]}
            components={MARKDOWN_COMPONENTS}
          >
            {narrative}
          </ReactMarkdown>
        </div>
      )}

      {hasTable && (
        <DataTable<Record<string, unknown>>
          columns={buildColumnDefs(columns!)}
          data={rows!}
          rowKey={(_, idx) => String(idx)}
          pageSize={10}
          searchable={rows!.length > 10}
          searchPlaceholder="Filter rows…"
        />
      )}

      {sqlExecuted && (
        <details className="rounded-md border border-border bg-surface/40 px-3 py-2 text-xs">
          <summary className="cursor-pointer select-none text-text-secondary hover:text-text-primary">
            Show SQL
          </summary>
          <pre className="mt-2 overflow-x-auto whitespace-pre-wrap rounded bg-ivory p-3 font-mono text-[11px] text-charcoal">
            {sqlExecuted}
          </pre>
        </details>
      )}

      {hasFooter && (
        <div className="text-[11px] text-text-muted">
          Tokens: {fmtNum(tokensInput)} in / {fmtNum(tokensOutput)} out
          {" — "}
          Cost: {fmtCost(costMicros)}
        </div>
      )}
    </div>
  );
}

// ---------- helpers ----------

function buildColumnDefs(
  columns: string[]
): ColumnDef<Record<string, unknown>>[] {
  return columns.map((key) => ({
    key,
    label: humanize(key),
    sortable: true,
    render: (value) => formatCell(value),
  }));
}

function humanize(key: string): string {
  return key
    .replace(/[_-]+/g, " ")
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

function formatCell(value: unknown): React.ReactNode {
  if (value === null || value === undefined) return "—";
  if (typeof value === "number") {
    return Number.isFinite(value) ? value.toLocaleString() : "—";
  }
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (typeof value === "string") {
    // ISO date heuristic — keep raw for everything else
    if (/^\d{4}-\d{2}-\d{2}(T\d{2}:\d{2}:\d{2}.*)?$/.test(value)) {
      const d = new Date(value);
      if (!isNaN(d.getTime())) return d.toLocaleDateString();
    }
    return value;
  }
  return JSON.stringify(value);
}

function fmtNum(n: number | null): string {
  return n == null ? "—" : n.toLocaleString();
}

function fmtCost(micros: number | null): string {
  if (micros == null) return "—";
  return `$${(micros / 1_000_000).toFixed(4)}`;
}

// Manual markdown element styling — matches existing palette
// (charcoal/slate/ivory/gold) without pulling in @tailwindcss/typography.
const MARKDOWN_COMPONENTS = {
  p: (p: React.HTMLAttributes<HTMLParagraphElement>) => (
    <p className="mb-2 last:mb-0" {...p} />
  ),
  h1: (p: React.HTMLAttributes<HTMLHeadingElement>) => (
    <h1 className="mb-2 mt-2 text-base font-semibold text-charcoal" {...p} />
  ),
  h2: (p: React.HTMLAttributes<HTMLHeadingElement>) => (
    <h2 className="mb-2 mt-2 text-sm font-semibold text-charcoal" {...p} />
  ),
  h3: (p: React.HTMLAttributes<HTMLHeadingElement>) => (
    <h3 className="mb-1 mt-2 text-sm font-semibold text-charcoal" {...p} />
  ),
  ul: (p: React.HTMLAttributes<HTMLUListElement>) => (
    <ul className="mb-2 list-disc pl-5" {...p} />
  ),
  ol: (p: React.HTMLAttributes<HTMLOListElement>) => (
    <ol className="mb-2 list-decimal pl-5" {...p} />
  ),
  li: (p: React.HTMLAttributes<HTMLLIElement>) => (
    <li className="mb-0.5" {...p} />
  ),
  code: (
    p: React.HTMLAttributes<HTMLElement> & { inline?: boolean }
  ) =>
    p.inline ? (
      <code
        className="rounded bg-ivory px-1 py-0.5 font-mono text-[12px] text-charcoal"
        {...p}
      />
    ) : (
      <code className="font-mono text-[12px]" {...p} />
    ),
  pre: (p: React.HTMLAttributes<HTMLPreElement>) => (
    <pre
      className="mb-2 overflow-x-auto rounded bg-ivory p-3 font-mono text-[12px] text-charcoal"
      {...p}
    />
  ),
  a: (p: React.AnchorHTMLAttributes<HTMLAnchorElement>) => (
    <a
      className="text-gold-deep underline-offset-2 hover:underline"
      target="_blank"
      rel="noopener noreferrer"
      {...p}
    />
  ),
  blockquote: (p: React.HTMLAttributes<HTMLQuoteElement>) => (
    <blockquote
      className="mb-2 border-l-2 border-hairline pl-3 text-slate"
      {...p}
    />
  ),
  table: (p: React.HTMLAttributes<HTMLTableElement>) => (
    <div className="mb-2 overflow-x-auto">
      <table className="w-full border-collapse text-xs" {...p} />
    </div>
  ),
  th: (p: React.ThHTMLAttributes<HTMLTableCellElement>) => (
    <th
      className="border-b border-hairline px-2 py-1 text-left font-semibold text-charcoal"
      {...p}
    />
  ),
  td: (p: React.TdHTMLAttributes<HTMLTableCellElement>) => (
    <td className="border-b border-hairline px-2 py-1 text-text-secondary" {...p} />
  ),
};
