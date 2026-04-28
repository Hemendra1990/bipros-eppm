"use client";

import { Check, X, Minus, Clock } from "lucide-react";
import { cn } from "@/lib/utils/cn";
import type { ApprovalDto } from "@/lib/api/permitApi";

const ICON: Record<ApprovalDto["status"], { Icon: typeof Check; bg: string; text: string }> = {
  APPROVED: { Icon: Check, bg: "bg-emerald", text: "text-white" },
  REJECTED: { Icon: X, bg: "bg-burgundy", text: "text-white" },
  PENDING: { Icon: Clock, bg: "bg-steel", text: "text-white" },
  SKIPPED: { Icon: Minus, bg: "bg-divider", text: "text-slate" },
};

export function ApprovalTimeline({ approvals }: { approvals: ApprovalDto[] }) {
  if (approvals.length === 0) {
    return <p className="text-sm text-slate">No approval steps yet.</p>;
  }
  return (
    <ol className="space-y-3">
      {approvals.map((a) => {
        const { Icon, bg, text } = ICON[a.status];
        return (
          <li key={a.id} className="flex items-start gap-3">
            <div
              className={cn(
                "mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full",
                bg,
                text
              )}
            >
              <Icon size={14} strokeWidth={2.5} />
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-sm font-semibold text-charcoal">{a.label}</div>
              <div className="text-xs text-slate">{a.role.replace("ROLE_", "").replace(/_/g, " ")}</div>
              {a.reviewedAt && (
                <div className="text-[11px] text-ash mt-0.5">
                  {new Date(a.reviewedAt).toLocaleString()}
                  {a.remarks ? ` — ${a.remarks}` : ""}
                </div>
              )}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
