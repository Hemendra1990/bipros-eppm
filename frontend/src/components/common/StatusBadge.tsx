import React from "react";

type StatusType = "PLANNED" | "ACTIVE" | "INACTIVE" | "COMPLETED" | "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

interface StatusBadgeProps {
  status: StatusType | string;
  variant?: "default" | "compact";
}

const statusStyles: Record<string, string> = {
  PLANNED: "bg-slate-700/50 text-slate-300 ring-1 ring-slate-600/50",
  ACTIVE: "bg-emerald-500/10 text-emerald-400 ring-1 ring-emerald-500/20",
  INACTIVE: "bg-amber-500/10 text-amber-400 ring-1 ring-amber-500/20",
  COMPLETED: "bg-blue-500/10 text-blue-400 ring-1 ring-blue-500/20",
  LOW: "bg-emerald-500/10 text-green-400 ring-1 ring-green-500/20",
  MEDIUM: "bg-orange-500/10 text-orange-400 ring-1 ring-orange-500/20",
  HIGH: "bg-red-500/10 text-red-400 ring-1 ring-red-500/20",
  CRITICAL: "bg-red-500/15 text-red-300 ring-1 ring-red-500/30",
};

export function StatusBadge({ status, variant = "default" }: StatusBadgeProps) {
  const style = statusStyles[status] ?? "bg-slate-700/30 text-slate-400 ring-1 ring-slate-600/30";
  const size = variant === "compact" ? "px-2 py-0.5 text-xs" : "px-2.5 py-1 text-xs";

  return (
    <span className={`inline-flex rounded-md font-medium ${size} ${style}`}>
      {status}
    </span>
  );
}
