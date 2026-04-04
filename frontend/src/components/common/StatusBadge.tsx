import React from "react";

type StatusType = "PLANNED" | "ACTIVE" | "INACTIVE" | "COMPLETED" | "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

interface StatusBadgeProps {
  status: StatusType | string;
  variant?: "default" | "compact";
}

const statusStyles: Record<string, string> = {
  PLANNED: "bg-gray-100 text-gray-700",
  ACTIVE: "bg-green-100 text-green-700",
  INACTIVE: "bg-yellow-100 text-yellow-700",
  COMPLETED: "bg-blue-100 text-blue-700",
  LOW: "bg-green-100 text-green-700",
  MEDIUM: "bg-yellow-100 text-yellow-700",
  HIGH: "bg-orange-100 text-orange-700",
  CRITICAL: "bg-red-100 text-red-700",
};

export function StatusBadge({ status, variant = "default" }: StatusBadgeProps) {
  const style = statusStyles[status] ?? "bg-gray-100 text-gray-700";
  const size = variant === "compact" ? "px-2 py-0.5 text-xs" : "px-2 py-1 text-xs";

  return (
    <span className={`inline-flex rounded-full font-medium ${size} ${style}`}>
      {status}
    </span>
  );
}
