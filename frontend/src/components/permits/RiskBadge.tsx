"use client";

import { cn } from "@/lib/utils/cn";
import type { RiskLevel } from "@/lib/api/permitApi";

const STYLES: Record<RiskLevel, string> = {
  LOW: "bg-emerald/10 text-emerald ring-emerald/30",
  MEDIUM: "bg-bronze-warn/10 text-bronze-warn ring-bronze-warn/30",
  HIGH: "bg-burgundy/10 text-burgundy ring-burgundy/30",
};

export function RiskBadge({ level }: { level: RiskLevel }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-bold uppercase tracking-wider ring-1",
        STYLES[level]
      )}
    >
      {level}
    </span>
  );
}
