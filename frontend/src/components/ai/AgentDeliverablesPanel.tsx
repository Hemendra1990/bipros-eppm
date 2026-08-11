"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ChevronDown, ChevronRight, Mail } from "lucide-react";

import {
  agentApi,
  type AgentDeliverablesResponse,
  type AgentMailRow,
} from "@/lib/api/agentApi";

/**
 * "Agent deliverables" — what the scheduled senders actually sent, to whom, over which
 * channel, with content preview. Sections mirror the AI Agent sheet rows: the daily report,
 * the supervisor summary mails (capacity + DBS), the missing-DPR alerts and DPR rejections.
 * Data: ai.agent_mail_log (per-recipient rows) + the two schedulers' status. Mail bodies
 * render in a sandboxed iframe; daily-report rows link to the stored report instead.
 */
const CATEGORY_ORDER: Array<{ key: AgentMailRow["category"]; label: string }> = [
  { key: "DPR_REPORT", label: "Daily Project Report" },
  { key: "SUPERVISOR_SUMMARY", label: "Supervisor summaries (capacity + DBS)" },
  { key: "MISSING_DPR", label: "Missing-DPR alerts" },
  { key: "DPR_REJECTION", label: "DPR rejections" },
  { key: "ISSUE_ASSIGNMENT", label: "Issue assignments" },
  { key: "OUTSTANDING_ISSUES", label: "Outstanding-issues digest" },
  { key: "MATERIAL_SHORT_SUPPLY", label: "Material short-supply digest" },
];

export function AgentDeliverablesPanel({ projectId }: { projectId: string }) {
  const [open, setOpen] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ["agent-deliverables", projectId],
    queryFn: () => agentApi.getDeliverables(projectId),
    enabled: !!projectId && open,
  });
  const d: AgentDeliverablesResponse | undefined = data?.data ?? undefined;

  return (
    <div className="rounded-lg border border-border bg-surface/50">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center gap-2 px-4 py-2.5 text-left"
      >
        {open ? (
          <ChevronDown size={16} className="text-text-secondary" />
        ) : (
          <ChevronRight size={16} className="text-text-secondary" />
        )}
        <Mail size={16} className="text-accent" />
        <span className="text-sm font-semibold text-text-primary">Agent deliverables</span>
        <span className="text-xs text-text-muted">
          what was sent, to whom, over which channel
        </span>
      </button>

      {open && (
        <div className="space-y-4 border-t border-border px-4 py-4">
          {isLoading || !d ? (
            <p className="text-sm text-text-muted">Loading deliveries…</p>
          ) : (
            <>
              <div className="space-y-1 text-xs text-text-secondary">
                <p>
                  <span className="font-semibold text-text-primary">Daily report</span>{" "}
                  {d.reportSchedule.enabled
                    ? `— ${d.reportSchedule.cadence.toLowerCase()} at ${d.reportSchedule.sendTime} ${d.reportSchedule.timezone}`
                    : "— disabled"}
                  {d.reportSchedule.lastGeneratedAt && (
                    <>
                      {" · last "}
                      {new Date(d.reportSchedule.lastGeneratedAt).toLocaleString()}
                      {" · "}
                      <StatusChip status={d.reportSchedule.lastDeliveryStatus ?? "—"} />
                      {d.reportSchedule.lastDeliveredTo && ` → ${d.reportSchedule.lastDeliveredTo}`}
                    </>
                  )}
                </p>
                <p>
                  <span className="font-semibold text-text-primary">Missing-DPR alert</span>{" "}
                  {d.missingAlert.enabled ? `— daily at ${d.missingAlert.alertTime}` : "— off"}
                  {d.missingAlert.lastCheckedDate && (
                    <>
                      {" · last check "}
                      {d.missingAlert.lastCheckedDate}
                      {": "}
                      {d.missingAlert.lastSkippedNonWorking
                        ? "skipped — non-working day"
                        : `${d.missingAlert.lastMissingCount ?? 0} missing · ${d.missingAlert.lastEmailsSent ?? 0} emails`}
                    </>
                  )}
                  {" · channel "}
                  {d.alertChannel}
                </p>
              </div>

              {d.mails.length === 0 ? (
                <p className="text-sm text-text-muted">
                  Nothing sent yet — deliveries appear here from the next scheduled send.
                </p>
              ) : (
                CATEGORY_ORDER.map(({ key, label }) => {
                  const rows = d.mails.filter((m) => m.category === key);
                  if (rows.length === 0) return null;
                  return (
                    <div key={key}>
                      <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-text-secondary">
                        {label}
                      </p>
                      <div className="divide-y divide-border/60 rounded-md border border-border">
                        {rows.map((m) => (
                          <MailEntry key={m.id} row={m} projectId={projectId} />
                        ))}
                      </div>
                    </div>
                  );
                })
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}

function MailEntry({ row, projectId }: { row: AgentMailRow; projectId: string }) {
  const [expanded, setExpanded] = useState(false);
  const recipient =
    row.recipientName || row.recipientEmail || (row.recipientUserId ?? "").slice(0, 8) + "…";
  const expandable = !!row.bodyHtml || !!row.reportId;
  return (
    <div className="px-3 py-2 text-sm">
      <button
        type="button"
        onClick={expandable ? () => setExpanded((v) => !v) : undefined}
        className={`flex w-full flex-wrap items-center gap-2 text-left ${expandable ? "" : "cursor-default"}`}
      >
        <span
          className={`rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${
            row.channel === "EMAIL"
              ? "bg-info/10 text-info"
              : "bg-surface-hover text-text-secondary"
          }`}
        >
          {row.channel === "IN_APP" ? "In-app" : row.channel.toLowerCase()}
        </span>
        <span className="font-medium text-text-primary">{recipient}</span>
        {row.recipientEmail && row.recipientName && (
          <span className="text-xs text-text-muted">{row.recipientEmail}</span>
        )}
        <span className="ml-auto flex items-center gap-2">
          <StatusChip status={row.status} detail={row.detail} />
          <span className="text-xs text-text-muted">
            {new Date(row.sentAt).toLocaleString()}
          </span>
        </span>
      </button>
      {row.subject && <p className="mt-0.5 text-xs text-text-secondary">{row.subject}</p>}
      {expanded && row.bodyHtml && (
        <iframe
          sandbox=""
          srcDoc={row.bodyHtml}
          title={row.subject ?? "Mail preview"}
          className="mt-2 h-72 w-full rounded border border-border bg-white"
        />
      )}
      {expanded && !row.bodyHtml && row.reportId && (
        <Link
          href={`/projects/${projectId}/dpr-reports?report=${row.reportId}`}
          prefetch={false}
          className="mt-2 inline-block text-xs font-medium text-accent hover:underline"
        >
          Open the stored report →
        </Link>
      )}
    </div>
  );
}

function StatusChip({ status, detail }: { status: string; detail?: string | null }) {
  const ok = status === "SENT";
  const warn = status === "PREVIEW";
  return (
    <span
      title={detail ?? undefined}
      className={`rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${
        ok
          ? "bg-success/10 text-success"
          : warn
            ? "bg-warning/10 text-warning"
            : "bg-danger/10 text-danger"
      }`}
    >
      {status}
    </span>
  );
}
