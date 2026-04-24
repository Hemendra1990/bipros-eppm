import React from "react";
import { cn } from "@/lib/utils/cn";

type Variant = "flat" | "elevated" | "interactive" | "accent";

export interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: Variant;
}

const variantClass: Record<Variant, string> = {
  flat: "border border-hairline",
  elevated: "border border-hairline shadow-[0_1px_2px_rgba(28,28,28,0.04)]",
  interactive:
    "border border-hairline cursor-pointer transition-all duration-200 " +
    "hover:shadow-[0_4px_20px_rgba(28,28,28,0.05)] hover:-translate-y-0.5 hover:border-gold/40",
  accent: "border border-hairline border-l-[3px] border-l-gold",
};

export function Card({ className = "", variant = "flat", ...props }: CardProps) {
  return (
    <div
      className={cn(
        "rounded-xl bg-paper p-6",
        variantClass[variant],
        className
      )}
      {...props}
    />
  );
}

export function CardHeader({ className = "", ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("border-b border-hairline px-6 pb-4 -mx-6 -mt-2 mb-4", className)} {...props} />;
}

export function CardTitle({ className = "", ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return (
    <h3
      className={cn("font-display text-xl font-semibold text-charcoal tracking-tight", className)}
      {...props}
    />
  );
}

export function CardDescription({
  className = "",
  ...props
}: React.HTMLAttributes<HTMLParagraphElement>) {
  return <p className={cn("mt-1 text-sm text-slate", className)} {...props} />;
}

export function CardContent({ className = "", ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("", className)} {...props} />;
}
