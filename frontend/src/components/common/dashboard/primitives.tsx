import React from "react";

export function SectionCard({
  title,
  subtitle,
  actions,
  children,
}: {
  title: string;
  subtitle?: string;
  actions?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <section className="rounded-lg border border-border bg-surface/50 p-6">
      <div className="mb-4 flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold text-text-primary">{title}</h2>
          {subtitle && <p className="mt-1 text-sm text-text-secondary">{subtitle}</p>}
        </div>
        {actions && <div>{actions}</div>}
      </div>
      {children}
    </section>
  );
}

export function LoadingBlock({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="flex items-center justify-center rounded-md border border-dashed border-border bg-surface-hover/20 p-6 text-sm text-text-muted">
      {label}
    </div>
  );
}

export function EmptyBlock({ label }: { label: string }) {
  return (
    <div className="flex items-center justify-center rounded-md border border-dashed border-border bg-surface-hover/20 p-6 text-sm text-text-muted">
      {label}
    </div>
  );
}

export const CHART_TOOLTIP_STYLE = {
  backgroundColor: "#1e293b",
  border: "1px solid #334155",
  borderRadius: "0.5rem",
  color: "#e2e8f0",
};

export const CHART_COLORS = {
  pv: "#3b82f6",
  ev: "#10b981",
  ac: "#ef4444",
  planned: "#3b82f6",
  actual: "#10b981",
  forecast: "#f59e0b",
  warning: "#f59e0b",
  danger: "#ef4444",
  committed: "#a855f7",
  good: "#10b981",
  amber: "#f59e0b",
  red: "#ef4444",
  muted: "#64748b",
};

export function formatCrore(n: number | null | undefined, digits = 2): string {
  if (n == null || Number.isNaN(n)) return "—";
  return `₹ ${n.toLocaleString("en-IN", { maximumFractionDigits: digits })} Cr`;
}

export function formatPct(n: number | null | undefined, digits = 1): string {
  if (n == null || Number.isNaN(n)) return "—";
  return `${n.toFixed(digits)}%`;
}

export function truncate(s: string | null | undefined, n: number): string {
  if (!s) return "";
  return s.length > n ? `${s.slice(0, n - 1)}…` : s;
}
