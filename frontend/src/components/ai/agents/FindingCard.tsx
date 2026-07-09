"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import {
  Activity,
  BarChart3,
  Box,
  Check,
  CheckCircle2,
  ChevronDown,
  ExternalLink,
  Loader2,
  Wrench,
  AlertTriangle,
  HelpCircle,
  Target,
  Lightbulb,
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Button } from "@/components/ui/button";
import { agentApi } from "@/lib/api/agentApi";
import type { AgentFindingDto, EvidenceDto } from "@/lib/types";
import { cn } from "@/lib/utils/cn";
import { AgentAvatar } from "./AgentAvatar";
import { ConfidenceBadge } from "./ConfidenceBadge";
import { FindingVisual } from "./FindingVisual";
import { humanizeType, severityMeta } from "./agentMeta";

/** First sentence (or a hard-truncated clause) of a paragraph — the one-line "so what". */
function takeaway(finding: AgentFindingDto): string | null {
  const src = finding.businessImpact || finding.whatHappened;
  if (!src) return null;
  const dot = src.search(/\.\s|\.$/);
  const s = dot > 0 ? src.slice(0, dot + 1) : src;
  return s.length > 180 ? s.slice(0, 177).trimEnd() + "…" : s;
}

function evidenceIcon(type: string) {
  switch (type) {
    case "METRIC":
      return <Activity size={12} />;
    case "ENTITY":
      return <Box size={12} />;
    case "TOOL_RESULT":
      return <Wrench size={12} />;
    case "CHART":
      return <BarChart3 size={12} />;
    default:
      return <Activity size={12} />;
  }
}

function EvidenceChip({ ev }: { ev: EvidenceDto }) {
  const inner = (
    <span
      className={cn(
        "inline-flex max-w-full items-center gap-1.5 rounded-md border border-hairline bg-ivory px-2 py-1 text-[11px] text-text-secondary",
        ev.linkUrl && "transition-colors hover:border-gold/45 hover:text-text-primary",
      )}
    >
      <span className="text-slate">{evidenceIcon(ev.type)}</span>
      <span className="font-medium text-text-primary">{ev.label}</span>
      {ev.value != null && ev.value !== "" && (
        <span className="truncate font-mono tabular-nums text-slate">{ev.value}</span>
      )}
      {ev.linkUrl && <ExternalLink size={10} className="shrink-0 text-gold-deep" />}
    </span>
  );
  if (ev.linkUrl) {
    return (
      <Link href={ev.linkUrl} className="max-w-full">
        {inner}
      </Link>
    );
  }
  return inner;
}

function Section({
  icon,
  label,
  text,
}: {
  icon: React.ReactNode;
  label: string;
  text?: string | null;
}) {
  if (!text) return null;
  return (
    <div>
      <div className="mb-1 flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-slate">
        <span className="text-gold-deep">{icon}</span>
        {label}
      </div>
      <p className="text-sm leading-relaxed text-text-primary">{text}</p>
    </div>
  );
}

export function FindingCard({
  finding,
  agentName,
  projectId,
  onChanged,
}: {
  finding: AgentFindingDto;
  agentName?: string;
  projectId?: string;
  onChanged?: (updated: AgentFindingDto) => void;
}) {
  const qc = useQueryClient();
  const sev = severityMeta(finding.severity);
  const [local, setLocal] = useState<AgentFindingDto>(finding);
  const [expanded, setExpanded] = useState(false);
  const displayName = agentName ?? humanizeType(finding.agentKey);
  const resolved = local.status === "RESOLVED_BY_USER" || !!local.resolvedAt;
  const acknowledged = !!local.acknowledgedAt;
  const line = takeaway(finding);
  const entityChips = (finding.evidence ?? []).filter((e) => e.type === "ENTITY");

  const invalidate = (updated: AgentFindingDto) => {
    setLocal(updated);
    onChanged?.(updated);
    const pid = projectId ?? finding.projectId;
    qc.invalidateQueries({ queryKey: ["agent-findings", pid] });
  };

  const ack = useMutation({
    mutationFn: () => agentApi.acknowledgeFinding(local.id),
    onSuccess: (res) => res.data && invalidate(res.data),
  });
  const resolve = useMutation({
    mutationFn: () => agentApi.resolveFinding(local.id),
    onSuccess: (res) => res.data && invalidate(res.data),
  });

  return (
    <Card
      variant="elevated"
      className={cn("border-l-[3px]", resolved && "opacity-70")}
      style={{ borderLeftColor: sev.hue }}
    >
      {/* Header */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3">
          <AgentAvatar agentKey={finding.agentKey} displayName={displayName} size="md" />
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-[11px] font-semibold uppercase tracking-wide text-slate">
                {displayName}
              </span>
              <Badge variant="neutral">{humanizeType(finding.findingType)}</Badge>
              <StatusBadge status={finding.severity} variant="compact" />
              {finding.notifiable && (
                <Badge variant="gold" withDot>
                  Notifiable
                </Badge>
              )}
              {resolved && (
                <Badge variant="success" withDot>
                  Resolved
                </Badge>
              )}
            </div>
            <h4 className="mt-1.5 font-display text-base font-semibold leading-snug tracking-tight text-charcoal">
              {finding.title}
            </h4>
          </div>
        </div>
        <div className="shrink-0">
          <ConfidenceBadge confidence={finding.confidence} basis={finding.confidenceBasis} />
        </div>
      </div>

      {/* One-line takeaway */}
      {line && <p className="mt-2.5 text-sm leading-relaxed text-text-secondary">{line}</p>}

      {/* Chart from the finding's numbers */}
      <div className="mt-3">
        <FindingVisual finding={finding} />
      </div>

      {/* Entity deep-links (kept visible — they're navigation, not prose) */}
      {entityChips.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {entityChips.map((ev, i) => (
            <EvidenceChip key={`${ev.label}-${i}`} ev={ev} />
          ))}
        </div>
      )}

      {/* Details toggle — full narrative + all evidence, collapsed by default */}
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        className="mt-3 inline-flex items-center gap-1 text-[11px] font-semibold uppercase tracking-[0.14em] text-gold-deep transition-colors hover:text-gold"
      >
        <ChevronDown size={13} className={cn("transition-transform", expanded && "rotate-180")} />
        {expanded ? "Hide details" : "Why & what to do"}
      </button>

      {expanded && (
        <div className="mt-3 space-y-4 border-t border-hairline pt-3">
          <div className="grid gap-4 sm:grid-cols-2">
            <Section icon={<AlertTriangle size={12} />} label="What happened" text={finding.whatHappened} />
            <Section icon={<HelpCircle size={12} />} label="Why it happened" text={finding.whyItHappened} />
            <Section icon={<Target size={12} />} label="Business impact" text={finding.businessImpact} />
            <Section icon={<Lightbulb size={12} />} label="Recommended action" text={finding.recommendedAction} />
          </div>
          {finding.evidence?.length > 0 && (
            <div>
              <div className="mb-1.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-slate">
                Evidence
              </div>
              <div className="flex flex-wrap gap-1.5">
                {finding.evidence.map((ev, i) => (
                  <EvidenceChip key={`${ev.label}-${i}`} ev={ev} />
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Actions */}
      <div className="mt-4 flex items-center justify-between gap-3 border-t border-hairline pt-3">
        <div className="text-[11px] text-text-muted">
          {resolved && local.resolvedBy ? (
            <span className="inline-flex items-center gap-1 text-emerald">
              <CheckCircle2 size={12} /> Resolved by {local.resolvedBy}
            </span>
          ) : acknowledged && local.acknowledgedBy ? (
            <span className="inline-flex items-center gap-1">
              <Check size={12} /> Acknowledged by {local.acknowledgedBy}
            </span>
          ) : local.lastSeenAt ? (
            <span>Last seen {new Date(local.lastSeenAt).toLocaleString()}</span>
          ) : (
            <span>Detected {new Date(local.createdAt).toLocaleString()}</span>
          )}
        </div>
        {!resolved && (
          <div className="flex items-center gap-2">
            {!acknowledged && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => ack.mutate()}
                disabled={ack.isPending}
              >
                {ack.isPending ? <Loader2 size={13} className="animate-spin" /> : <Check size={13} />}
                Acknowledge
              </Button>
            )}
            <Button
              variant="secondary"
              size="sm"
              onClick={() => resolve.mutate()}
              disabled={resolve.isPending}
            >
              {resolve.isPending ? (
                <Loader2 size={13} className="animate-spin" />
              ) : (
                <CheckCircle2 size={13} />
              )}
              Resolve
            </Button>
          </div>
        )}
      </div>
    </Card>
  );
}
