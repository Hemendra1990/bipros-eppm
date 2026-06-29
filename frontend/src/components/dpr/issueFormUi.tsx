"use client";

import type { ReactNode } from "react";
import { AlertCircle } from "lucide-react";
import { cn } from "@/lib/utils/cn";
import type { IssueSeverity, IssueStatus } from "@/lib/types/dpr";

/**
 * Shared visual primitives for the Issue create/edit surfaces. Everything is
 * driven by the app's design tokens (--surface, --border, --text-*, severity
 * accents) so the forms adapt to both the light (cream) and dark themes with no
 * hard-coded glyph colours.
 */

export const fieldInput =
  "mt-1.5 block w-full rounded-lg border border-border bg-surface-hover/70 px-3.5 py-2.5 " +
  "text-sm text-text-primary shadow-sm transition-colors placeholder:text-text-muted " +
  "focus:border-accent focus:bg-surface focus:outline-none focus:ring-2 focus:ring-accent/20";

export const fieldLabel = "block text-[13px] font-medium text-text-secondary";

/** Severity → accent colour (used for the rail and the severity pill ring). */
export const SEVERITY_ACCENT: Record<IssueSeverity, string> = {
  LOW: "var(--ash)",
  MEDIUM: "var(--steel)",
  HIGH: "var(--amber-flame)",
  CRITICAL: "var(--burgundy)",
};

/** Status → timeline-node / dot colour. */
export const STATUS_DOT: Record<IssueStatus, string> = {
  OPEN: "var(--bronze-warn)",
  IN_PROGRESS: "var(--steel)",
  BLOCKED: "var(--amber-flame)",
  RESOLVED: "var(--emerald)",
  CLOSED: "var(--ash)",
  CANCELLED: "var(--ash)",
};

export function FieldLabel({
  children,
  required,
}: {
  children: ReactNode;
  required?: boolean;
}) {
  return (
    <label className={fieldLabel}>
      {children}
      {required && <span className="ml-0.5 text-accent">*</span>}
    </label>
  );
}

export function InlineError({ message }: { message?: string }) {
  if (!message) return null;
  return (
    <p className="mt-1.5 flex items-center gap-1.5 text-xs font-medium text-danger">
      <AlertCircle className="h-3.5 w-3.5 shrink-0" />
      {message}
    </p>
  );
}

/** Quiet uppercase section heading with a hairline rule. */
export function SectionHeading({ children }: { children: ReactNode }) {
  return (
    <div className="flex items-center gap-3 pb-1">
      <span className="text-[11px] font-semibold uppercase tracking-[0.14em] text-text-muted">
        {children}
      </span>
      <span className="h-px flex-1 bg-border" />
    </div>
  );
}

/** One labelled stat in the read-only context strip (logged by / dates). */
export function MetaStat({
  icon,
  label,
  value,
}: {
  icon: ReactNode;
  label: string;
  value: ReactNode;
}) {
  return (
    <div className="flex items-start gap-2.5">
      <span className="mt-0.5 text-text-muted">{icon}</span>
      <div className="min-w-0">
        <div className="text-[10px] font-semibold uppercase tracking-[0.12em] text-text-muted">
          {label}
        </div>
        <div className="truncate text-sm font-medium text-text-primary">{value}</div>
      </div>
    </div>
  );
}

/**
 * The form shell: an elevated card with a severity-coloured left rail and a
 * header band carrying the page kicker/title plus live status + severity pills.
 */
export function IssueFormShell({
  severity,
  kicker,
  title,
  pills,
  children,
}: {
  severity: IssueSeverity;
  kicker: string;
  title: string;
  pills?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="overflow-hidden rounded-2xl border border-border bg-surface shadow-[0_1px_2px_rgba(0,0,0,0.04),0_8px_24px_-12px_rgba(0,0,0,0.12)]">
      <div className="flex">
        <div
          className="w-1.5 shrink-0"
          style={{ backgroundColor: SEVERITY_ACCENT[severity] }}
          aria-hidden
        />
        <div className="min-w-0 flex-1">
          <div className="flex items-start justify-between gap-4 border-b border-border px-6 py-5 sm:px-8">
            <div>
              <div className="text-[11px] font-semibold uppercase tracking-[0.16em] text-accent">
                {kicker}
              </div>
              <h1 className="mt-1 text-xl font-semibold tracking-tight text-text-primary">
                {title}
              </h1>
            </div>
            {pills && <div className="flex shrink-0 items-center gap-2">{pills}</div>}
          </div>
          {children}
        </div>
      </div>
    </div>
  );
}

/** A subtle accent panel used for the conditional Resolution block. */
export function AccentPanel({
  tone = "gold",
  children,
}: {
  tone?: "gold" | "emerald";
  children: ReactNode;
}) {
  return (
    <div
      className={cn(
        "rounded-xl border p-4",
        tone === "emerald"
          ? "border-emerald/30 bg-emerald/[0.06]"
          : "border-gold/30 bg-gold-tint/40",
      )}
    >
      {children}
    </div>
  );
}
