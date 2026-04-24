import React from "react";
import { cn } from "@/lib/utils/cn";

export type BadgeVariant =
  | "neutral"
  | "gold"
  | "success"
  | "warning"
  | "danger"
  | "info";

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant;
  withDot?: boolean;
}

const variants: Record<BadgeVariant, string> = {
  neutral: "bg-ivory text-charcoal border-hairline",
  gold: "bg-gold-tint text-gold-ink border-gold/40",
  success: "bg-emerald/10 text-emerald border-emerald/30",
  warning: "bg-bronze-warn/15 text-bronze-warn border-bronze-warn/30",
  danger: "bg-burgundy/10 text-burgundy border-burgundy/30",
  info: "bg-steel/10 text-steel border-steel/30",
};

export function Badge({
  variant = "neutral",
  withDot = false,
  className,
  children,
  ...props
}: BadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-md border px-2.5 py-0.5 text-[11px] font-semibold tracking-wide",
        variants[variant],
        className
      )}
      {...props}
    >
      {withDot && (
        <span
          className="h-1.5 w-1.5 rounded-full"
          style={{ background: "currentColor" }}
          aria-hidden
        />
      )}
      {children}
    </span>
  );
}
