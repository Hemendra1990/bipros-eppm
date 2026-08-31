"use client";

import { useQuery } from "@tanstack/react-query";
import { MailCheck } from "lucide-react";
import { Card } from "@/components/ui/card";
import { agentApi, type NotificationLogEntry } from "@/lib/api/agentApi";
import { cn } from "@/lib/utils/cn";

/**
 * Notification Log — who was sent what, when, over which channel, and whether it truly left
 * (SENT) or was only rendered (PREVIEW — no SMTP configured). Visible to the project's PM and
 * admins only: the endpoint 403s for everyone else and the panel hides itself.
 */
export function NotificationLogPanel({ projectId }: { projectId: string }) {
  const { data, isError, isLoading } = useQuery({
    queryKey: ["notification-log", projectId],
    queryFn: () => agentApi.notificationLog(projectId),
    retry: false,
  });

  if (isLoading || isError) return null; // 403 (not PM/admin) or transport error — not this user's panel
  const entries = data?.data ?? [];

  return (
    <Card variant="flat" className="mt-6 p-5">
      <div className="mb-3 flex items-center gap-2">
        <MailCheck size={16} className="text-slate" />
        <h2 className="text-sm font-semibold text-charcoal">Notification log</h2>
        <span className="text-[11px] text-slate">
          who received what, over which channel · PREVIEW = rendered but not emailed (no SMTP configured)
        </span>
      </div>
      {entries.length === 0 ? (
        <p className="py-6 text-center text-sm text-slate">
          No notifications delivered yet — they appear here after a sweep routes a notifiable finding.
        </p>
      ) : (
        <NotificationLogTable entries={entries} showProject={false} />
      )}
      <p className="mt-2 text-[11px] text-slate">
        Rows recorded before 5 Aug 2026 may show SENT for what were preview-mode emails.
      </p>
    </Card>
  );
}

const STATUS_CLS: Record<string, string> = {
  SENT: "bg-emerald-100 text-emerald-700",
  PREVIEW: "bg-amber-100 text-amber-700",
  FAILED: "bg-red-100 text-red-700",
  SKIPPED: "bg-gray-100 text-gray-600",
  PENDING: "bg-gray-100 text-gray-600",
};

/** Plain table over log entries — shared by the project panel and the admin page. */
export function NotificationLogTable({
  entries,
  showProject,
}: {
  entries: NotificationLogEntry[];
  showProject: boolean;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-[11px] uppercase tracking-wide text-slate">
            <th className="pb-2 pr-3">When</th>
            <th className="pb-2 pr-3">Finding</th>
            <th className="pb-2 pr-3">Severity</th>
            <th className="pb-2 pr-3">Recipient</th>
            <th className="pb-2 pr-3">Channel</th>
            <th className="pb-2 pr-3">Status</th>
            {showProject && <th className="pb-2">Project</th>}
          </tr>
        </thead>
        <tbody>
          {entries.map((e, i) => (
            <tr key={`${e.findingId}-${e.channel}-${e.recipientUserId}-${i}`} className="border-t border-border/50 align-top">
              <td className="whitespace-nowrap py-2 pr-3 tabular-nums text-slate">
                {new Date(e.at).toLocaleString()}
              </td>
              <td className="max-w-[320px] py-2 pr-3">
                <span className="line-clamp-2 text-charcoal" title={e.findingTitle}>
                  {e.findingTitle}
                </span>
                <span className="text-[11px] text-slate">{e.agentKey}</span>
              </td>
              <td className="py-2 pr-3">{e.severity}</td>
              <td className="py-2 pr-3">{e.recipientName ?? "—"}</td>
              <td className="py-2 pr-3">
                <span className="rounded bg-gray-100 px-1.5 py-0.5 text-[11px] font-medium text-gray-700">
                  {e.channel}
                </span>
              </td>
              <td className="py-2 pr-3">
                <span
                  className={cn(
                    "rounded px-1.5 py-0.5 text-[11px] font-semibold",
                    STATUS_CLS[e.status] ?? STATUS_CLS.PENDING,
                  )}
                  title={e.detail ?? undefined}
                >
                  {e.status}
                </span>
              </td>
              {showProject && (
                <td className="py-2 text-[12px] text-slate">{e.projectId ?? "—"}</td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
