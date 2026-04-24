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
  gold: "bg-gold-tint text-gold-ink border-[#E8D68A]",
  success: "bg-[#E5F1EB] text-emerald border-[#C8E0D3]",
  warning: "bg-[#FBEDD5] text-[#8B5E14] border-[#F0DDAE]",
  danger: "bg-[#F5E2E2] text-burgundy border-[#E5C4C4]",
  info: "bg-[#E8ECF1] text-steel border-[#CFD6DF]",
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
