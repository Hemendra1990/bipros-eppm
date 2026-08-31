"use client";

import Link from "next/link";
import { ExternalLink, Inbox, Settings2 } from "lucide-react";
import { Card } from "@/components/ui/card";
import { AgentAvatar } from "@/components/ai/agents/AgentAvatar";
import type { AgentSummaryDto } from "@/lib/types";
import { catalogFor, type CoverageStatus } from "./agentCatalog";

/**
 * Same visual language as {@link FindingCard} (top band + padded body + agent avatar), but for an
 * agent that currently has no data to analyse — it explains why it's blank and links to the tab
 * where the data is maintained, instead of leaving the feed silent.
 */
export function NoDataCard({
  agent,
  status,
  projectId,
}: {
  agent: AgentSummaryDto;
  status: CoverageStatus; // NO_DATA | NOT_CONFIGURED
  projectId: string;
}) {
  const entry = catalogFor(agent.key);
  const notConfigured = status === "NOT_CONFIGURED";
  const hue = "#8A6D1F"; // muted amber — "nothing wrong, just nothing to show yet"

  return (
    <Card variant="flat" className="overflow-hidden p-0">
      <div className="h-1 w-full" style={{ backgroundColor: `${hue}66` }} />
      <div className="p-5">
        <div className="flex items-center gap-2">
          <AgentAvatar displayName={agent.displayName} agentKey={agent.key} size="sm" />
          <span className="text-sm font-semibold text-text-primary">{agent.displayName}</span>
          <span
            className="ml-auto inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide"
            style={{ backgroundColor: `${hue}1A`, color: hue }}
          >
            {notConfigured ? <Settings2 size={12} /> : <Inbox size={12} />}
            {notConfigured ? "Not configured" : "No data"}
          </span>
        </div>

        <p className="mt-3 text-sm text-text-secondary">{entry.gap.reason}</p>
        {entry.gap.suggestion && (
          <p className="mt-1 text-xs text-text-muted">{entry.gap.suggestion}</p>
        )}

        <Link
          href={entry.route(projectId)}
          className="mt-3 inline-flex items-center gap-1 text-xs font-medium text-gold hover:underline"
        >
          Maintain data <ExternalLink size={12} />
        </Link>
      </div>
    </Card>
  );
}
