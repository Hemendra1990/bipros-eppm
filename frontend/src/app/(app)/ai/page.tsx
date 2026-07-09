"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Sparkles, ShieldAlert, Layers, Bot, Inbox } from "lucide-react";
import { PageHeader } from "@/components/common/PageHeader";
import { Card } from "@/components/ui/card";
import { agentApi } from "@/lib/api/agentApi";
import { projectApi } from "@/lib/api/projectApi";
import type { AgentFindingDto, AgentSeverity } from "@/lib/types";
import { FindingCard } from "@/components/ai/agents/FindingCard";
import {
  SEVERITY_META,
  severityMeta,
  agentHue,
  humanizeType,
} from "@/components/ai/agents/agentMeta";

// Cross-project fan-out is bounded so the portfolio view never issues hundreds of
// requests. Covers the most recently-listed projects; deeper coverage is a Phase 2
// dedicated /v1/agents portfolio endpoint.
const MAX_PROJECTS = 15;
const PER_PROJECT = 25;
const BOARD_LIMIT = 40;

type PortfolioFinding = AgentFindingDto & { projectName: string };

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

// NOTE: This is a PORTFOLIO surface aggregating many projects with potentially
// different currencies — per the currency scope boundary it MUST NOT call
// useProjectCurrency(). Finding values arrive pre-formatted from the backend.
export default function PortfolioAiPage() {
  const { data: projectsRes } = useQuery({
    queryKey: ["ai-portfolio", "projects"],
    queryFn: () => projectApi.listAccessible(),
  });
  const projects = useMemo(
    () => (projectsRes?.data ?? []).slice(0, MAX_PROJECTS),
    [projectsRes],
  );

  const { data: findings = [], isLoading } = useQuery<PortfolioFinding[]>({
    queryKey: ["ai-portfolio", "findings", projects.map((p) => p.id)],
    enabled: projects.length > 0,
    queryFn: async () => {
      const results = await Promise.allSettled(
        projects.map((p) =>
          agentApi
            .listFindings(p.id, { status: "ACTIVE", size: PER_PROJECT })
            .then((res) =>
              (res.data?.content ?? []).map(
                (f): PortfolioFinding => ({ ...f, projectName: p.name }),
              ),
            ),
        ),
      );
      return results
        .filter(
          (r): r is PromiseFulfilledResult<PortfolioFinding[]> =>
            r.status === "fulfilled",
        )
        .flatMap((r) => r.value);
    },
  });

  const ranked = useMemo(
    () =>
      [...findings].sort(
        (a, b) =>
          severityMeta(b.severity).order - severityMeta(a.severity).order ||
          (b.confidence ?? 0) - (a.confidence ?? 0),
      ),
    [findings],
  );

  const kpis = useMemo(() => {
    const bySev = (s: AgentSeverity) => findings.filter((f) => f.severity === s).length;
    return {
      total: findings.length,
      critical: bySev("CRITICAL"),
      high: bySev("HIGH"),
      projects: new Set(findings.map((f) => f.projectId)).size,
    };
  }, [findings]);

  // Per-agent health: how many active findings each agent is currently raising.
  const agentHealth = useMemo(() => {
    const map = new Map<string, { count: number; critical: number }>();
    for (const f of findings) {
      const entry = map.get(f.agentKey) ?? { count: 0, critical: 0 };
      entry.count += 1;
      if (f.severity === "CRITICAL") entry.critical += 1;
      map.set(f.agentKey, entry);
    }
    return [...map.entries()].sort((a, b) => b[1].count - a[1].count);
  }, [findings]);

  return (
    <div className="px-6 pb-10">
      <PageHeader
        title="Portfolio AI"
        description="Every agent's active findings across all the projects you can see, ranked by urgency."
      />

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <KpiTile label="Active findings" value={kpis.total} hue="#B8962E" icon={<Sparkles size={16} />} />
        <KpiTile label="Critical" value={kpis.critical} hue={SEVERITY_META.CRITICAL.hue} icon={<ShieldAlert size={16} />} />
        <KpiTile label="High" value={kpis.high} hue={SEVERITY_META.HIGH.hue} icon={<ShieldAlert size={16} />} />
        <KpiTile label="Projects flagged" value={kpis.projects} hue="#475569" icon={<Layers size={16} />} />
      </div>

      <div className="mt-5 grid gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        {/* Cross-project ranked findings */}
        <div>
          {isLoading ? (
            <Card variant="flat" className="py-12 text-center text-sm text-text-muted">
              Aggregating findings across projects…
            </Card>
          ) : ranked.length === 0 ? (
            <Card variant="flat" className="py-12 text-center">
              <Inbox size={28} className="mx-auto mb-3 text-ash" />
              <p className="text-sm text-text-secondary">No active findings across your projects.</p>
              <p className="mt-1 text-xs text-text-muted">
                Agents publish here automatically as they analyse each project.
              </p>
            </Card>
          ) : (
            <div className="space-y-4">
              {ranked.slice(0, BOARD_LIMIT).map((f) => (
                <div key={f.id}>
                  <div className="mb-1 text-[11px] font-semibold uppercase tracking-wide text-slate">
                    {f.projectName}
                  </div>
                  <FindingCard
                    finding={f}
                    agentName={humanizeType(f.agentKey)}
                    projectId={f.projectId}
                  />
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Per-agent health */}
        <div className="lg:sticky lg:top-20 lg:self-start">
          <Card variant="flat" className="p-4">
            <div className="mb-3 flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.16em] text-slate">
              <Bot size={14} /> Agent health
            </div>
            {agentHealth.length === 0 ? (
              <p className="text-sm text-text-muted">No agent activity yet.</p>
            ) : (
              <ul className="space-y-2">
                {agentHealth.map(([key, h]) => (
                  <li key={key} className="flex items-center justify-between gap-3">
                    <span className="flex items-center gap-2 text-sm text-text-primary">
                      <span
                        className="inline-block h-2.5 w-2.5 rounded-full"
                        style={{ backgroundColor: agentHue(key) }}
                      />
                      {humanizeType(key)}
                    </span>
                    <span className="tabular-nums text-xs text-text-muted">
                      {h.count} active
                      {h.critical > 0 && (
                        <span className="ml-1 font-semibold text-burgundy">· {h.critical} critical</span>
                      )}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}
