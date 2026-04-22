import React from "react";

type StatusType = "PLANNED" | "ACTIVE" | "INACTIVE" | "COMPLETED" | "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

interface StatusBadgeProps {
  status: StatusType | string;
  variant?: "default" | "compact";
}

const statusStyles: Record<string, string> = {
  PLANNED: "bg-surface-hover text-text-secondary ring-1 ring-border",
  ACTIVE: "bg-success/10 text-success ring-1 ring-success/20",
  INACTIVE: "bg-warning/10 text-warning ring-1 ring-warning/20",
  COMPLETED: "bg-accent/10 text-accent ring-1 ring-accent/20",
  LOW: "bg-success/10 text-success ring-1 ring-success/20",
  MEDIUM: "bg-warning/10 text-warning ring-1 ring-warning/20",
  HIGH: "bg-danger/10 text-danger ring-1 ring-danger/20",
  CRITICAL: "bg-danger/15 text-danger ring-1 ring-danger/30",
};

export function StatusBadge({ status, variant = "default" }: StatusBadgeProps) {
  const style = statusStyles[status] ?? "bg-surface-hover/30 text-text-secondary ring-1 ring-border";
  const size = variant === "compact" ? "px-2 py-0.5 text-xs" : "px-2.5 py-1 text-xs";

  return (
    <span className={`inline-flex rounded-md font-medium ${size} ${style}`}>
      {status}
    </span>
  );
}
