"use client";

import { cn } from "@/lib/utils/cn";
import type { PermitStatus } from "@/lib/api/permitApi";

const STYLES: Record<PermitStatus, { bg: string; text: string; ring: string; label: string }> = {
  DRAFT: { bg: "bg-divider/50", text: "text-slate", ring: "ring-divider", label: "Draft" },
  PENDING_SITE_ENGINEER: { bg: "bg-steel/10", text: "text-steel", ring: "ring-steel/30", label: "Pending Review" },
  PENDING_HSE: { bg: "bg-amber-flame/10", text: "text-amber-flame", ring: "ring-amber-flame/30", label: "Pending Safety" },
  AWAITING_GAS_TEST: { bg: "bg-amber-flame/15", text: "text-amber-flame", ring: "ring-amber-flame/40", label: "Awaiting Gas Test" },
  PENDING_PM: { bg: "bg-gold-tint", text: "text-gold-ink", ring: "ring-gold/30", label: "Pending PM" },
  APPROVED: { bg: "bg-emerald/10", text: "text-emerald", ring: "ring-emerald/30", label: "Approved" },
  ISSUED: { bg: "bg-emerald/15", text: "text-emerald", ring: "ring-emerald/40", label: "Issued" },
  IN_PROGRESS: { bg: "bg-emerald/20", text: "text-emerald", ring: "ring-emerald/50", label: "In Progress" },
  SUSPENDED: { bg: "bg-bronze-warn/10", text: "text-bronze-warn", ring: "ring-bronze-warn/30", label: "Suspended" },
  CLOSED: { bg: "bg-divider", text: "text-slate", ring: "ring-divider", label: "Closed" },
  REJECTED: { bg: "bg-burgundy/10", text: "text-burgundy", ring: "ring-burgundy/30", label: "Rejected" },
  EXPIRED: { bg: "bg-burgundy/15", text: "text-burgundy", ring: "ring-burgundy/40", label: "Expired" },
  REVOKED: { bg: "bg-burgundy/20", text: "text-burgundy", ring: "ring-burgundy/50", label: "Revoked" },
};

export function PermitStatusBadge({ status }: { status: PermitStatus }) {
  const s = STYLES[status];
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1",
        s.bg,
        s.text,
        s.ring
      )}
    >
      {s.label}
    </span>
  );
}
