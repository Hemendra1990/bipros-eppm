import React from "react";

type Tone = "default" | "success" | "warning" | "danger" | "accent";

interface KpiTileProps {
  label: string;
  value: string | number;
  hint?: string;
  tone?: Tone;
  icon?: React.ReactNode;
}

const toneCls: Record<Tone, string> = {
  default: "text-text-primary",
  success: "text-success",
  warning: "text-warning",
  danger: "text-danger",
  accent: "text-accent",
};

export function KpiTile({ label, value, hint, tone = "default", icon }: KpiTileProps) {
  return (
    <div className="rounded-lg border border-border bg-surface-hover/40 p-4">
      <div className="flex items-center justify-between">
        <div className="text-xs font-medium uppercase tracking-wide text-text-secondary">
          {label}
        </div>
        {icon && <div className="text-text-muted">{icon}</div>}
      </div>
      <div className={`mt-2 text-2xl font-bold ${toneCls[tone]}`}>{value}</div>
      {hint && <div className="mt-1 text-xs text-text-muted">{hint}</div>}
    </div>
  );
}
