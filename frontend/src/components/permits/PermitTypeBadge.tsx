"use client";

import { cn } from "@/lib/utils/cn";

interface PermitTypeBadgeProps {
  code?: string | null;
  name?: string | null;
  colorHex?: string | null;
  size?: "sm" | "md";
}

export function PermitTypeBadge({ code, name, colorHex, size = "sm" }: PermitTypeBadgeProps) {
  const text = name || code || "Unknown";
  const color = colorHex || "#6B7280";
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full font-semibold ring-1",
        size === "sm" ? "px-2.5 py-0.5 text-xs" : "px-3 py-1 text-sm"
      )}
      style={{
        backgroundColor: `${color}1A`,
        color,
        boxShadow: `inset 0 0 0 1px ${color}33`,
      }}
    >
      {text}
    </span>
  );
}
