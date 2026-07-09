"use client";

import { useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { Sparkles, Loader2, RefreshCw, ShieldAlert, Bell, Inbox } from "lucide-react";
import { PageHeader } from "@/components/common/PageHeader";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { agentApi } from "@/lib/api/agentApi";
import type { AgentFindingDto, AgentSeverity } from "@/lib/types";
import { FindingCard } from "@/components/ai/agents/FindingCard";
import { AgentActivityFeed } from "@/components/ai/agents/AgentActivityFeed";
import { InvestigatePanel } from "@/components/ai/agents/InvestigatePanel";
import { SiteWeatherPanel } from "@/components/ai/agents/SiteWeatherPanel";
import { FindingsTicker } from "@/components/ai/agents/FindingsTicker";
import { SEVERITY_META, severityMeta } from "@/components/ai/agents/agentMeta";

const SEVERITIES: AgentSeverity[] = ["CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"];
const STATUSES = ["ACTIVE", "RESOLVED_BY_USER", "SUPERSEDED", "EXPIRED"] as const;

function KpiTile({
  label,
  value,
  hue,
  icon,
}: {
  label: string;
  value: number | string;
  hue: string;
  icon: React.ReactNode;
}) {
  return (
    <Card variant="flat" className="p-4">
      <div className="flex items-center gap-3">
        <span
          className="inline-flex h-9 w-9 items-center justify-center rounded-lg"
          style={{ backgroundColor: `${hue}22`, color: hue }}
        >
          {icon}
        </span>
        <div>
          <div className="font-display text-2xl font-semibold tabular-nums text-charcoal">
            {value}
          </div>
          <div className="text-[11px] font-medium uppercase tracking-wide text-slate">
            {label}
          </div>
        </div>
      </div>
    </Card>
  );
}

const selectCls =
  "rounded-lg border border-hairline bg-paper px-3 py-1.5 text-sm text-text-primary focus:border-gold focus:outline-none focus:ring-1 focus:ring-gold";

export default function ProjectAiPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const qc = useQueryClient();

  const [severity, setSeverity] = useState<AgentSeverity | "ALL">("ALL");
  const [agentKey, setAgentKey] = useState<string>("ALL");
  const [status, setStatus] = useState<string>("ACTIVE");

  const { data: agentsRes } = useQuery({
    queryKey: ["agents", projectId],
    queryFn: () => agentApi.listAgents(projectId),
    enabled: !!projectId,
  });
  const agents = agentsRes?.data ?? [];
  const agentNames = useMemo(
    () => Object.fromEntries(agents.map((a) => [a.key, a.displayName])),
    [agents],
  );

  // KPI feed — always all ACTIVE findings, independent of the board filters.
  const { data: activeRes } = useQuery({
    queryKey: ["agent-findings", projectId, "kpi-active"],
    queryFn: () => agentApi.listFindings(projectId, { status: "ACTIVE", size: 200 }),
    enabled: !!projectId,
  });
  const activeFindings = activeRes?.data?.content ?? [];
  const kpis = useMemo(() => {
    const bySev = (s: AgentSeverity) => activeFindings.filter((f) => f.severity === s).length;
    return {
      total: activeRes?.data?.totalElements ?? activeFindings.length,
      critical: bySev("CRITICAL"),
      high: bySev("HIGH"),
      notifiable: activeFindings.filter((f) => f.notifiable).length,
    };
  }, [activeFindings, activeRes]);

  // The filtered board.
  const {
    data: boardRes,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ["agent-findings", projectId, "board", severity, agentKey, status],
    queryFn: () =>
      agentApi.listFindings(projectId, {
        size: 100,
        ...(severity !== "ALL" ? { severity } : {}),
        ...(agentKey !== "ALL" ? { agentKey } : {}),
        ...(status !== "ALL" ? { status } : {}),
      }),
    enabled: !!projectId,
  });
  const findings: AgentFindingDto[] = useMemo(() => {
    const list = boardRes?.data?.content ?? [];
    return [...list].sort(
      (a, b) =>
        severityMeta(b.severity).order - severityMeta(a.severity).order ||
        (b.confidence ?? 0) - (a.confidence ?? 0),
    );
  }, [boardRes]);

  const sweep = useMutation({
    mutationFn: async () => {
      const results = await Promise.allSettled(
        agents.map((a) => agentApi.runAgent(projectId, a.key)),
      );
      const ok = results.filter((r) => r.status === "fulfilled").length;
      return { ok, total: agents.length };
    },
    onSuccess: ({ ok, total }) => {
      toast.success(`Sweep started — ${ok}/${total} agents running`);
      // Give runs a head start, then refresh the feed + findings.
      setTimeout(() => {
        qc.invalidateQueries({ queryKey: ["agents", projectId] });
        qc.invalidateQueries({ queryKey: ["agent-findings", projectId] });
      }, 4000);
    },
    onError: () => toast.error("Couldn't start the sweep"),
  });

  return (
    <div className="px-6 pb-10">
      <PageHeader
        title="AI insights"
        description="What the agents found, why it matters, and what to do about it."
        actions={
          <Button
            onClick={() => sweep.mutate()}
            disabled={sweep.isPending || agents.length === 0}
          >
            {sweep.isPending ? (
              <Loader2 size={15} className="animate-spin" />
            ) : (
              <RefreshCw size={15} />
            )}
            Run sweep now
          </Button>
        }
      />

      {/* KPI strip */}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <KpiTile label="Active findings" value={kpis.total} hue="#B8962E" icon={<Sparkles size={16} />} />
        <KpiTile label="Critical" value={kpis.critical} hue={SEVERITY_META.CRITICAL.hue} icon={<ShieldAlert size={16} />} />
        <KpiTile label="High" value={kpis.high} hue={SEVERITY_META.HIGH.hue} icon={<ShieldAlert size={16} />} />
        <KpiTile label="Notifiable" value={kpis.notifiable} hue="#475569" icon={<Bell size={16} />} />
      </div>

      {/* Live site weather (only when a site location + monitoring are configured) */}
      <div className="mt-5">
        <SiteWeatherPanel projectId={projectId} />
      </div>

      {/* Investigate */}
      <div className="mt-5">
        <InvestigatePanel projectId={projectId} />
      </div>

      <div className="mt-5 grid gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        {/* Findings board */}
        <div>
          <FindingsTicker findings={findings} agentNames={agentNames} />
          <div className="mb-3 flex flex-wrap items-center gap-2">
            <span className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate">
              Filter
            </span>
            <select
              value={severity}
              onChange={(e) => setSeverity(e.target.value as AgentSeverity | "ALL")}
              className={selectCls}
            >
              <option value="ALL">All severities</option>
              {SEVERITIES.map((s) => (
                <option key={s} value={s}>
                  {SEVERITY_META[s].label}
                </option>
              ))}
            </select>
            <select
              value={agentKey}
              onChange={(e) => setAgentKey(e.target.value)}
              className={selectCls}
            >
              <option value="ALL">All agents</option>
              {agents.map((a) => (
                <option key={a.key} value={a.key}>
                  {a.displayName}
                </option>
              ))}
            </select>
            <select value={status} onChange={(e) => setStatus(e.target.value)} className={selectCls}>
              <option value="ALL">Any status</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s.replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())}
                </option>
              ))}
            </select>
          </div>

          {isLoading ? (
            <Card variant="flat" className="py-12 text-center text-sm text-text-muted">
              Loading findings…
            </Card>
          ) : isError ? (
            <Card variant="flat" className="py-12 text-center text-sm text-text-muted">
              Couldn&apos;t load findings. The agent platform may not be seeded for this project yet.
            </Card>
          ) : findings.length === 0 ? (
            <Card variant="flat" className="py-12 text-center">
              <Inbox size={28} className="mx-auto mb-3 text-ash" />
              <p className="text-sm text-text-secondary">No findings match these filters.</p>
              <p className="mt-1 text-xs text-text-muted">
                Run a sweep to have the agents analyse the latest project data.
              </p>
            </Card>
          ) : (
            <div className="space-y-4">
              {findings.map((f) => (
                <FindingCard
                  key={f.id}
                  finding={f}
                  agentName={agentNames[f.agentKey]}
                  projectId={projectId}
                />
              ))}
            </div>
          )}
        </div>

        {/* Live feed */}
        <div className="lg:sticky lg:top-20 lg:self-start">
          <AgentActivityFeed projectId={projectId} />
        </div>
      </div>
    </div>
  );
}
