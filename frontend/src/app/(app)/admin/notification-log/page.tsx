"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { BellRing } from "lucide-react";
import { PageHeader } from "@/components/common/PageHeader";
import { Card } from "@/components/ui/card";
import { agentApi } from "@/lib/api/agentApi";
import { NotificationLogTable } from "@/components/ai/agents/NotificationLogPanel";

/**
 * Admin-wide Notification Log — every AI notification delivery across all projects: recipient,
 * channel, and honest status (SENT / PREVIEW / FAILED / SKIPPED). Project PMs see their own
 * project's log on the project AI tab instead.
 */
export default function AdminNotificationLogPage() {
  const [limit, setLimit] = useState(200);
  const { data, isLoading, isError } = useQuery({
    queryKey: ["admin-notification-log", limit],
    queryFn: () => agentApi.adminNotificationLog(undefined, limit),
    retry: false,
  });
  const entries = data?.data ?? [];

  return (
    <div className="px-6 pb-10">
      <PageHeader
        title="Notification log"
        description="Every AI notification delivery across all projects — who received what, over which channel, and whether it truly sent (PREVIEW = rendered but no SMTP configured)."
      />
      <Card variant="flat" className="p-5">
        {isLoading ? (
          <p className="py-8 text-center text-sm text-slate">Loading…</p>
        ) : isError ? (
          <p className="py-8 text-center text-sm text-slate">
            Couldn&apos;t load the log — admin access is required.
          </p>
        ) : entries.length === 0 ? (
          <div className="py-10 text-center">
            <BellRing size={24} className="mx-auto mb-3 text-slate" />
            <p className="text-sm text-slate">
              No deliveries recorded yet — rows appear after an agent sweep routes a notifiable finding.
            </p>
          </div>
        ) : (
          <>
            <NotificationLogTable entries={entries} showProject />
            {entries.length >= limit && (
              <button
                type="button"
                onClick={() => setLimit((l) => l + 200)}
                className="mt-3 text-sm text-accent hover:underline"
              >
                Load more
              </button>
            )}
          </>
        )}
        <p className="mt-3 text-[11px] text-slate">
          Rows recorded before 5 Aug 2026 may show SENT for what were preview-mode emails.
        </p>
      </Card>
    </div>
  );
}
