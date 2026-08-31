"use client";

import { useState, useMemo } from "react";
import { useQuery, useQueries } from "@tanstack/react-query";
import { dashboardApi } from "@/lib/api/dashboardApi";
import { projectApi } from "@/lib/api/projectApi";
import { activityApi } from "@/lib/api/activityApi";
import { organisationApi } from "@/lib/api/organisationApi";
import { analyticsApi, type ContractorPerformance } from "@/lib/api/analyticsApi";
import { portfolioReportApi, type PortfolioEvmRow } from "@/lib/api/portfolioReportApi";
import Link from "next/link";
import { ArrowLeft, Calendar, Flag, Gauge, Users } from "lucide-react";
import type {
  ProjectResponse,
  OrganisationResponse,
} from "@/lib/types";
import { Badge, type BadgeVariant } from "@/components/ui/badge";

interface Milestone {
  id: string;
  name: string;
  projectId: string;
  plannedDate: string;
  actualDate?: string;
  status: string;
}

interface ContractorScorecard {
  id: string;
  name: string;
  performanceScore: number | null;
  safetyScore: number | null;
  complianceScore: number | null;
}

function milestoneBadge(status: string): BadgeVariant {
  switch (status) {
    case "COMPLETED":
      return "success";
    case "IN_PROGRESS":
      return "info";
    case "PENDING":
      return "neutral";
    default:
      return "neutral";
  }
}

function scoreColor(score: number | null) {
  if (score == null) return "text-ash";
  if (score >= 90) return "text-emerald";
  if (score >= 80) return "text-steel";
  if (score >= 70) return "text-bronze-warn";
  return "text-burgundy";
}

function scoreBarColor(score: number | null) {
  if (score == null) return "bg-ash/40";
  if (score >= 90) return "bg-emerald";
  if (score >= 80) return "bg-steel";
  if (score >= 70) return "bg-bronze-warn";
  return "bg-burgundy";
}

function evmTone(value: number | undefined): {
  text: string;
  badge: BadgeVariant;
} {
  if (value == null) return { text: "text-ash", badge: "neutral" };
  if (value >= 0.95) return { text: "text-emerald", badge: "success" };
  if (value >= 0.9) return { text: "text-steel", badge: "info" };
  return { text: "text-burgundy", badge: "danger" };
}

// No EVM baseline (BAC/EV = 0) → SPI/CPI aren't meaningful (0 = no data, not poor
// performance). Render neutral rather than danger-red.
const NO_EVM_TONE = { text: "text-ash", badge: "neutral" as BadgeVariant };

const formatScore = (score: number | null) =>
  score == null ? "n/a" : `${score}%`;

export default function ProgrammeDashboardPage() {
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null);

  const { isLoading: isLoadingConfig } = useQuery({
    queryKey: ["dashboard-config", "PROGRAMME"],
    queryFn: () => dashboardApi.getDashboardByTier("PROGRAMME"),
  });

  const { data: projectsData } = useQuery({
    queryKey: ["projects"],
    queryFn: () => projectApi.listProjects(),
  });
  const projects = projectsData?.data?.content ?? [];

  // Activities for every project so the milestone tracker spans all programmes.
  // Schedule milestones are activities of type START_MILESTONE / FINISH_MILESTONE —
  // a small subset, and there's no cross-project milestone endpoint yet, so we page a
  // large window per project and filter client-side. Fine at portfolio scale; a
  // dedicated endpoint is the clean long-term fix.
  const activitiesQueries = useQueries({
    queries: projects.map((p: ProjectResponse) => ({
      queryKey: ["activities", "milestones", p.id],
      queryFn: () => activityApi.listActivities(p.id, 0, 500),
      staleTime: 60_000,
    })),
  });

  const { data: epcContractorsData } = useQuery({
    queryKey: ["organisations", "EPC_CONTRACTOR"],
    queryFn: () => organisationApi.listByType("EPC_CONTRACTOR"),
    staleTime: 5 * 60_000,
  });

  const { data: contractorPerfData } = useQuery({
    queryKey: ["analytics", "contractor-performance"],
    queryFn: () => analyticsApi.getContractorPerformance(),
    staleTime: 60_000,
  });

  const { data: evmRollupResponse } = useQuery({
    queryKey: ["portfolio", "evm-rollup"],
    queryFn: () => portfolioReportApi.getEvmRollup(),
    staleTime: 60_000,
  });
  const evmByProjectId = useMemo(() => {
    const map = new Map<string, PortfolioEvmRow>();
    (evmRollupResponse?.data ?? []).forEach((row) => map.set(row.projectId, row));
    return map;
  }, [evmRollupResponse]);

  const epcContractors: OrganisationResponse[] = epcContractorsData?.data ?? [];
  const contractorPerfByCode = useMemo(() => {
    const map = new Map<string, ContractorPerformance>();
    (contractorPerfData ?? []).forEach((p) => map.set(p.orgCode, p));
    return map;
  }, [contractorPerfData]);

  // Real schedule milestones across all programmes: activities typed START/FINISH_MILESTONE.
  // START milestones track their start dates; FINISH milestones track their finish dates.
  const MILESTONE_TYPES = new Set(["START_MILESTONE", "FINISH_MILESTONE"]);
  const allMilestones: Milestone[] = activitiesQueries
    .flatMap((q) => q.data?.data?.content ?? [])
    .filter((a) => MILESTONE_TYPES.has(a.activityType ?? ""))
    .map((a) => {
      const isFinish = a.activityType === "FINISH_MILESTONE";
      return {
        id: a.id,
        name: a.name,
        projectId: a.projectId,
        plannedDate: (isFinish ? a.plannedFinishDate : a.plannedStartDate) ?? "",
        actualDate: (isFinish ? a.actualFinishDate : a.actualStartDate) ?? undefined,
        status: a.status || "PENDING",
      };
    })
    .sort((a, b) => (a.plannedDate || "").localeCompare(b.plannedDate || ""));

  // Selecting a project card drills the tracker into that project; otherwise show all.
  const displayedMilestones = selectedProjectId
    ? allMilestones.filter((m) => m.projectId === selectedProjectId)
    : allMilestones;
  const projectNameById = new Map(
    projects.map((p: ProjectResponse) => [p.id, p.name] as const),
  );

  const contractors: ContractorScorecard[] = epcContractors.map((o) => {
    const perf = contractorPerfByCode.get(o.code);
    return {
      id: o.id,
      name: o.shortName || o.name,
      performanceScore: perf?.performanceScore ?? null,
      safetyScore: perf?.safetyScore ?? null,
      complianceScore: perf?.complianceScore ?? null,
    };
  });

  // Aggregate KPI strip. Average only EVM-active projects (a cost baseline AND earned
  // work) so projects with no EVM don't dilute the mean toward zero.
  const evmRows = Array.from(evmByProjectId.values());
  const evmActiveRows = evmRows.filter((r) => (r.bac ?? 0) > 0 && (r.ev ?? 0) > 0);
  const avgSpi = evmActiveRows.length
    ? evmActiveRows.reduce((s, r) => s + (r.spi ?? 0), 0) / evmActiveRows.length
    : null;
  const avgCpi = evmActiveRows.length
    ? evmActiveRows.reduce((s, r) => s + (r.cpi ?? 0), 0) / evmActiveRows.length
    : null;
  const activeProjectCount = projects.filter(
    (p: ProjectResponse) => p.status === "ACTIVE"
  ).length;
  const milestoneCompleted = allMilestones.filter(
    (m) => m.status === "COMPLETED"
  ).length;
  const milestoneTotal = allMilestones.length;

  if (isLoadingConfig) {
    return (
      <div className="flex items-center justify-center p-12">
        <div className="text-sm text-slate">Loading dashboard…</div>
      </div>
    );
  }

  return (
    <div>
      {/* Page head */}
      <div className="mb-7 flex items-start gap-4">
        <Link
          href="/dashboards"
          aria-label="Back to dashboards"
          className="mt-1.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-hairline bg-paper text-slate transition-all duration-200 hover:border-gold/50 hover:text-gold-deep"
        >
          <ArrowLeft size={16} strokeWidth={1.75} />
        </Link>
        <div className="flex-1">
          <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep mb-1.5">
            Programme · earned-value
          </div>
          <h1
            className="font-display text-[34px] font-semibold leading-[1.08] tracking-tight text-charcoal"
            style={{ fontVariationSettings: "'opsz' 144" }}
          >
            Programme dashboard
          </h1>
          <p className="mt-2 max-w-[640px] text-sm leading-relaxed text-slate">
            Earned-value performance, milestone slippage and contractor scorecards across active programmes.
          </p>
        </div>
      </div>

      {/* KPI strip */}
      <div className="mb-7 grid grid-cols-2 gap-3.5 lg:grid-cols-4">
        <KpiCard label="Active programmes" value={activeProjectCount} />
        <KpiCard
          label="Avg SPI"
          value={avgSpi != null ? avgSpi.toFixed(2) : "—"}
          accent={avgSpi != null && avgSpi < 0.95 ? "burgundy" : "emerald"}
        />
        <KpiCard
          label="Avg CPI"
          value={avgCpi != null ? avgCpi.toFixed(2) : "—"}
          accent={avgCpi != null && avgCpi < 0.95 ? "burgundy" : "emerald"}
        />
        <KpiCard
          label="Milestones met"
          value={milestoneTotal > 0 ? `${milestoneCompleted}/${milestoneTotal}` : "—"}
          accent="gold"
        />
      </div>

      {/* EVM cards */}
      <section className="mb-6">
        <SectionHeading
          kicker="Earned value"
          title="EVM by project"
          icon={<Gauge size={14} strokeWidth={1.75} />}
        />
        <div className="grid grid-cols-1 gap-3.5 md:grid-cols-2 xl:grid-cols-4">
          {projects.length === 0 ? (
            <div className="md:col-span-2 xl:col-span-4">
              <EmptyState label="No projects available" />
            </div>
          ) : (
            projects.map((project: ProjectResponse) => {
              const evm = evmByProjectId.get(project.id);
              // "EVM-active" = has a cost baseline AND earned work. Otherwise SPI/CPI are
              // not meaningful (0 = no baseline, not poor performance) → render neutral —.
              const hasEvm = !!evm && (evm.bac ?? 0) > 0 && (evm.ev ?? 0) > 0;
              const spiTone = hasEvm ? evmTone(evm!.spi) : NO_EVM_TONE;
              const cpiTone = hasEvm ? evmTone(evm!.cpi) : NO_EVM_TONE;
              const isSelected = selectedProjectId === project.id;
              return (
                <button
                  key={project.id}
                  type="button"
                  onClick={() => setSelectedProjectId(project.id)}
                  className={`group rounded-2xl border bg-paper p-5 text-left transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_4px_20px_rgba(28,28,28,0.05)] ${
                    isSelected
                      ? "border-gold/50 ring-1 ring-gold/15"
                      : "border-hairline hover:border-gold/40"
                  }`}
                >
                  <div className="mb-4 flex items-start justify-between gap-2">
                    <h3 className="line-clamp-2 font-display text-base font-semibold leading-snug tracking-tight text-charcoal">
                      {project.name}
                    </h3>
                    {isSelected && (
                      <span className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">
                        ●
                      </span>
                    )}
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <EvmStat
                      label="SPI"
                      value={hasEvm ? evm!.spi.toFixed(2) : "—"}
                      tone={spiTone.text}
                    />
                    <EvmStat
                      label="CPI"
                      value={hasEvm ? evm!.cpi.toFixed(2) : "—"}
                      tone={cpiTone.text}
                    />
                  </div>
                </button>
              );
            })
          )}
        </div>
      </section>

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
        {/* Milestones */}
        <section>
          <SectionHeading
            kicker="Schedule"
            title="Milestone tracker"
            icon={<Flag size={14} strokeWidth={1.75} />}
          />
          <div className="overflow-hidden rounded-2xl border border-hairline bg-paper">
            {displayedMilestones.length === 0 ? (
              <EmptyState label="No milestones tracked yet" />
            ) : (
            <ol className="relative divide-y divide-hairline">
              {displayedMilestones.map((milestone, i) => {
                const planned = milestone.plannedDate
                  ? new Date(milestone.plannedDate).toLocaleDateString()
                  : "—";
                const actual = milestone.actualDate
                  ? new Date(milestone.actualDate).toLocaleDateString()
                  : null;
                const projectName = projectNameById.get(milestone.projectId);
                return (
                  <li key={milestone.id} className="relative p-5 pl-12">
                    {/* Timeline dot + line */}
                    <span
                      aria-hidden
                      className={`absolute left-5 top-6 h-2.5 w-2.5 rounded-full ring-4 ring-paper ${
                        milestone.status === "COMPLETED"
                          ? "bg-emerald"
                          : milestone.status === "IN_PROGRESS"
                            ? "bg-gold"
                            : "bg-ash/60"
                      }`}
                    />
                    {i < displayedMilestones.length - 1 && (
                      <span
                        aria-hidden
                        className="absolute left-[24px] top-9 bottom-0 w-px bg-hairline"
                      />
                    )}
                    <div className="mb-1.5 flex items-start justify-between gap-2">
                      <div>
                        <h3 className="font-semibold text-charcoal">{milestone.name}</h3>
                        {projectName && (
                          <div className="text-[11px] text-slate">{projectName}</div>
                        )}
                      </div>
                      <Badge variant={milestoneBadge(milestone.status)} withDot>
                        {milestone.status.replace("_", " ")}
                      </Badge>
                    </div>
                    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate">
                      <span className="inline-flex items-center gap-1">
                        <Calendar size={12} className="text-ash" />
                        Planned: <span className="font-medium text-charcoal">{planned}</span>
                      </span>
                      {actual && (
                        <span className="inline-flex items-center gap-1">
                          Actual: <span className="font-medium text-charcoal">{actual}</span>
                        </span>
                      )}
                    </div>
                  </li>
                );
              })}
            </ol>
            )}
          </div>
        </section>

        {/* Contractor Scorecards */}
        <section>
          <SectionHeading
            kicker="Vendors"
            title="Contractor performance"
            icon={<Users size={14} strokeWidth={1.75} />}
          />
          <div className="overflow-hidden rounded-2xl border border-hairline bg-paper">
            {contractors.length === 0 ? (
              <EmptyState label="No EPC contractors registered" />
            ) : (
              <ul className="divide-y divide-hairline">
                {contractors.map((contractor) => (
                  <li key={contractor.id} className="p-5">
                    <h3 className="mb-3 font-semibold text-charcoal">{contractor.name}</h3>
                    <div className="space-y-2.5">
                      <ScoreRow
                        label="Performance"
                        score={contractor.performanceScore}
                      />
                      <ScoreRow label="Safety" score={contractor.safetyScore} />
                      <ScoreRow
                        label="Compliance"
                        score={contractor.complianceScore}
                      />
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}

// ----------------------- shared local primitives -----------------------

function KpiCard({
  label,
  value,
  accent = "default",
}: {
  label: string;
  value: number | string;
  accent?: "default" | "emerald" | "burgundy" | "gold";
}) {
  const rail =
    accent === "emerald"
      ? "border-l-[3px] border-l-emerald"
      : accent === "burgundy"
        ? "border-l-[3px] border-l-burgundy"
        : accent === "gold"
          ? "border-l-[3px] border-l-gold"
          : "";
  return (
    <div
      className={`rounded-xl border border-hairline bg-paper p-5 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_4px_20px_rgba(28,28,28,0.05)] ${rail}`}
    >
      <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep mb-2">
        {label}
      </div>
      <div
        className="font-display text-[32px] font-semibold leading-none tracking-tight text-charcoal"
        style={{ fontVariationSettings: "'opsz' 144" }}
      >
        {value}
      </div>
    </div>
  );
}

function SectionHeading({
  kicker,
  title,
  subtitle,
  icon,
}: {
  kicker: string;
  title: string;
  subtitle?: string;
  icon?: React.ReactNode;
}) {
  return (
    <div className="mb-3.5 flex items-baseline justify-between gap-3">
      <div>
        <div className="flex items-center gap-2 text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">
          {icon && <span>{icon}</span>}
          {kicker}
        </div>
        <h2 className="mt-0.5 font-display text-xl font-semibold tracking-tight text-charcoal">
          {title}
        </h2>
        {subtitle && <p className="mt-0.5 text-xs text-slate">{subtitle}</p>}
      </div>
    </div>
  );
}

function EvmStat({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone: string;
}) {
  return (
    <div className="rounded-lg border border-hairline bg-ivory/60 p-3">
      <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-slate">
        {label}
      </div>
      <div
        className={`mt-1 font-display text-2xl font-semibold leading-none tracking-tight ${tone}`}
        style={{ fontVariationSettings: "'opsz' 144" }}
      >
        {value}
      </div>
    </div>
  );
}

function ScoreRow({ label, score }: { label: string; score: number | null }) {
  const pct = score ?? 0;
  return (
    <div>
      <div className="mb-1 flex items-center justify-between">
        <span className="text-[11px] font-medium uppercase tracking-wide text-slate">
          {label}
        </span>
        <span className={`text-sm font-semibold ${scoreColor(score)}`}>
          {formatScore(score)}
        </span>
      </div>
      <div className="h-1.5 w-full overflow-hidden rounded-full bg-parchment">
        <div
          className={`h-full rounded-full transition-all duration-500 ${scoreBarColor(score)}`}
          style={{ width: `${Math.min(100, Math.max(0, pct))}%` }}
        />
      </div>
    </div>
  );
}

function EmptyState({ label }: { label: string }) {
  return (
    <div className="flex items-center justify-center rounded-xl border border-dashed border-hairline bg-ivory/50 p-8 text-sm text-slate">
      {label}
    </div>
  );
}
