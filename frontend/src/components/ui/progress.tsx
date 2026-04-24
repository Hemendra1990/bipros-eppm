import React from "react";
import { cn } from "@/lib/utils/cn";

export type ProgressVariant = "gold" | "success" | "warning" | "danger";

export interface ProgressProps extends React.HTMLAttributes<HTMLDivElement> {
  value?: number;
  max?: number;
  variant?: ProgressVariant;
  label?: string;
}

const fills: Record<ProgressVariant, string> = {
  gold: "linear-gradient(90deg,#D4AF37,#B8962E)",
  success: "linear-gradient(90deg,#2E7D5B,#256B4C)",
  warning: "linear-gradient(90deg,#C7882E,#A6701F)",
  danger: "linear-gradient(90deg,#9B2C2C,#7F2424)",
};

export function Progress({
  value = 0,
  max = 100,
  variant = "gold",
  label,
  className,
  ...props
}: ProgressProps) {
  const pct = Math.min(100, Math.max(0, (value / max) * 100));

  return (
    <div className={cn("flex flex-col gap-1.5", className)} {...props}>
      {label && (
        <div className="flex justify-between text-xs">
          <span className="text-slate">{label}</span>
          <span className="font-semibold text-charcoal font-mono tabular-nums">
            {Math.round(pct)}%
          </span>
        </div>
      )}
      <div
        className="h-1.5 w-full overflow-hidden rounded-full bg-parchment"
        role="progressbar"
        aria-valuenow={Math.round(pct)}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <div
          className="h-full rounded-full transition-[width] duration-300 ease-[cubic-bezier(.2,.7,.2,1)]"
          style={{ width: `${pct}%`, background: fills[variant] }}
        />
      </div>
    </div>
  );
}
