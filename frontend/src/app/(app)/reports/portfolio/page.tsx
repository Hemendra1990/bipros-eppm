"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { PageHeader } from "@/components/common/PageHeader";
import { portfolioReportApi } from "@/lib/api/portfolioReportApi";

type TabId =
  | "scorecard"
  | "delayed"
  | "cost-overrun"
  | "evm-rollup"
  | "funding"
  | "contractor"
  | "risk-heatmap"
  | "cash-flow"
  | "compliance"
  | "schedule-health";

const TABS: { id: TabId; label: string }[] = [
  { id: "scorecard", label: "O1 Scorecard" },
  { id: "delayed", label: "O2 Delayed" },
  { id: "cost-overrun", label: "O3 Cost Overrun" },
  { id: "evm-rollup", label: "O4 EVM Rollup" },
  { id: "funding", label: "O5 Funding" },
  { id: "contractor", label: "O6 Contractors" },
  { id: "risk-heatmap", label: "O7 Risk Heatmap" },
  { id: "cash-flow", label: "O8 Cash Flow" },
  { id: "compliance", label: "O9 Compliance" },
  { id: "schedule-health", label: "O10 Schedule" },
];

export default function PortfolioReportsPage() {
  const [tab, setTab] = useState<TabId>("scorecard");

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Link href="/reports">
          <button className="rounded p-1 hover:bg-surface-hover">
            <ArrowLeft size={20} />
          </button>
        </Link>
        <PageHeader
          title="Portfolio Reports"
          description="Organization-level reports: scorecard, delays, cost overruns, funding, risks, compliance"
        />
      </div>

      <div className="flex flex-wrap gap-1 border-b border-border">
        {TABS.map((t) => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={`px-3 py-2 text-sm font-medium ${
              tab === t.id
                ? "border-b-2 border-accent text-accent"
                : "text-text-secondary hover:text-text-primary"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === "scorecard" && <ScorecardPanel />}
      {tab === "delayed" && <DelayedPanel />}
      {tab === "cost-overrun" && <CostOverrunPanel />}
      {tab === "evm-rollup" && <EvmRollupPanel />}
      {tab === "funding" && <FundingPanel />}
      {tab === "contractor" && <ContractorPanel />}
      {tab === "risk-heatmap" && <RiskHeatmapPanel />}
      {tab === "cash-flow" && <CashFlowPanel />}
      {tab === "compliance" && <CompliancePanel />}
      {tab === "schedule-health" && <ScheduleHealthPanel />}
    </div>
  );
}

function formatCrore(n: number | null | undefined): string {
  if (n == null) return "—";
  return `₹ ${n.toLocaleString("en-IN", { maximumFractionDigits: 2 })} Cr`;
}

function ragBadge(rag: string) {
  const cls =
    rag === "GREEN"
      ? "bg-success/20 text-success"
      : rag === "AMBER"
        ? "bg-warning/20 text-warning"
        : rag === "RED" || rag === "CRIMSON"
          ? "bg-danger/20 text-danger"
          : "bg-surface-hover text-text-secondary";
  return <span className={`rounded px-2 py-0.5 text-xs font-semibold ${cls}`}>{rag}</span>;
}

function Tile({
  label,
  value,
  tone = "default",
}: {
  label: string;
  value: string | number;
  tone?: "default" | "success" | "warning" | "danger";
}) {
  const toneCls =
    tone === "success"
      ? "text-success"
      : tone === "warning"
        ? "text-warning"
        : tone === "danger"
          ? "text-danger"
          : "text-text-primary";
  return (
    <div className="rounded-lg border border-border bg-surface-hover/40 p-4">
      <div className="text-xs font-medium text-text-secondary">{label}</div>
      <div className={`mt-1 text-2xl font-bold ${toneCls}`}>{value}</div>
    </div>
  );
}

// ─────────── O1 ───────────
function ScorecardPanel() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-scorecard"],
    queryFn: () => portfolioReportApi.getScorecard(),
  });
  if (isLoading) return <LoadingBlock />;
  if (isError || !data) return <EmptyBlock label="Scorecard unavailable" />;
  const s = data;
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4 lg:grid-cols-5">
        <Tile label="Total projects" value={s.totalProjects} />
        <Tile label="Active" value={s.byStatus?.ACTIVE ?? 0} />
        <Tile label="Planned" value={s.byStatus?.PLANNED ?? 0} />
        <Tile label="Completed" value={s.byStatus?.COMPLETED ?? 0} />
        <Tile label="On Hold" value={s.byStatus?.ON_HOLD ?? 0} />
      </div>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <Tile label="Total budget" value={formatCrore(s.totalBudgetCrores)} />
        <Tile label="Committed" value={formatCrore(s.totalCommittedCrores)} />
        <Tile label="Spent" value={formatCrore(s.totalSpentCrores)} />
      </div>
      <div className="grid grid-cols-2 gap-4 md:grid-cols-5">
        <Tile label="RAG Green" value={s.rag.green} tone="success" />
        <Tile label="RAG Amber" value={s.rag.amber} tone="warning" />
        <Tile label="RAG Red" value={s.rag.red} tone="danger" />
        <Tile
          label="Active w/ critical activities"
          value={s.activeProjectsWithCriticalActivities}
          tone="warning"
        />
        <Tile label="Open critical risks" value={s.openRisksCritical} tone="danger" />
      </div>
    </div>
  );
}

// ─────────── O2 ───────────
function DelayedPanel() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-delayed"],
    queryFn: () => portfolioReportApi.getDelayedProjects(10),
  });
  if (isLoading) return <LoadingBlock />;
  if (isError) return <EmptyBlock label="Delayed projects unavailable" />;
  const rows = data ?? [];
  if (rows.length === 0) return <EmptyBlock label="No delayed projects" />;
  return (
    <Table
      head={["Code", "Name", "Planned Finish", "Forecast Finish", "Days Delay", "SPI", "RAG"]}
      rows={rows.map((r) => [
        r.projectCode,
        r.projectName,
        r.plannedFinish ?? "—",
        r.forecastFinish ?? "—",
        r.daysDelayed,
        r.spi.toFixed(2),
        ragBadge(r.rag),
      ])}
    />
  );
}

// ─────────── O3 ───────────
function CostOverrunPanel() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-cost-overrun"],
    queryFn: () => portfolioReportApi.getCostOverrunProjects(10),
  });
  if (isLoading) return <LoadingBlock />;
  if (isError) return <EmptyBlock label="Cost overrun unavailable" />;
  const rows = data ?? [];
  if (rows.length === 0) return <EmptyBlock label="No cost overruns" />;
  return (
    <Table
      head={["Code", "Name", "BAC", "EAC", "Variance", "CPI"]}
      rows={rows.map((r) => [
        r.projectCode,
        r.projectName,
        formatCrore(r.bacCrores),
        formatCrore(r.eacCrores),
        formatCrore(r.varianceCrores),
        r.cpi.toFixed(2),
      ])}
    />
  );
}

// ─────────── O4 ───────────
function EvmRollupPanel() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-evm-rollup"],
    queryFn: () => portfolioReportApi.getEvmRollup(),
  });
  if (isLoading) return <LoadingBlock />;
  if (isError) return <EmptyBlock label="EVM rollup unavailable" />;
  const rows = data?.data ?? [];
  if (rows.length === 0) return <EmptyBlock label="No EVM data" />;
  const totalBac = rows.reduce((s, r) => s + (r.bac ?? 0), 0);
  const totalEv = rows.reduce((s, r) => s + (r.ev ?? 0), 0);
  const totalAc = rows.reduce((s, r) => s + (r.ac ?? 0), 0);
  const totalPv = rows.reduce((s, r) => s + (r.pv ?? 0), 0);
  const wCpi = totalAc > 0 ? totalEv / totalAc : 0;
  const wSpi = totalPv > 0 ? totalEv / totalPv : 0;
  return (
    <div className="space-y-4">
      <Table
        head={["Code", "Name", "PV", "EV", "AC", "CPI", "SPI", "EAC", "BAC"]}
        rows={rows.map((r) => [
          r.projectCode,
          r.projectName,
          (r.pv ?? 0).toFixed(0),
          (r.ev ?? 0).toFixed(0),
          (r.ac ?? 0).toFixed(0),
          (r.cpi ?? 0).toFixed(2),
          (r.spi ?? 0).toFixed(2),
          (r.eac ?? 0).toFixed(0),
          (r.bac ?? 0).toFixed(0),
        ])}
        footer={[
          "Σ Portfolio",
          `${rows.length} projects`,
          totalPv.toFixed(0),
          totalEv.toFixed(0),
          totalAc.toFixed(0),
          wCpi.toFixed(3),
          wSpi.toFixed(3),
          "—",
          totalBac.toFixed(0),
        ]}
      />
    </div>
  );
}

// ─────────── O5 ───────────
function FundingPanel() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-funding"],
    queryFn: () => portfolioReportApi.getFundingUtilization(),
  });
  if (isLoading) return <LoadingBlock />;
  if (isError) return <EmptyBlock label="Funding data unavailable" />;
  const rows = data ?? [];
  if (rows.length === 0) return <EmptyBlock label="No funding rows" />;
  const stuck = rows.filter((r) => r.utilizationPct < 50);
  return (
    <div className="space-y-4">
      {stuck.length > 0 && (
        <div className="rounded border border-warning/30 bg-warning/10 p-3 text-sm text-warning">
          {stuck.length} project(s) have utilization below 50%. Funds released but not spent.
        </div>
      )}
      <Table
        head={["Project", "Sanctioned", "Released", "Utilized", "Release %", "Util %", "Status"]}
        rows={rows.map((r) => [
          r.projectName,
          formatCrore(r.totalSanctionedCrores),
          formatCrore(r.totalReleasedCrores),
          formatCrore(r.totalUtilizedCrores),
          `${r.releasePct.toFixed(1)}%`,
          `${r.utilizationPct.toFixed(1)}%`,
          r.fundingStatus,
        ])}
      />
    </div>
  );
}

// ─────────── O6 ───────────
function ContractorPanel() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-contractor-league"],
    queryFn: () => portfolioReportApi.getContractorLeague(),
  });
  if (isLoading) return <LoadingBlock />;
  if (isError) return <EmptyBlock label="Contractor data unavailable" />;
  const rows = data ?? [];
  if (rows.length === 0) return <EmptyBlock label="No contractors" />;
  return (
    <Table
      head={["Code", "Contractor", "Active Projects", "Avg Perf", "Avg SPI", "Avg CPI", "Contract Value", "RA Bills"]}
      rows={rows.map((r) => [
        r.contractorCode,
        r.contractorName,
        r.activeProjects,
        r.avgPerformance.toFixed(1),
        r.avgSpi.toFixed(2),
        r.avgCpi.toFixed(2),
        formatCrore(r.totalContractValueCrores),
        formatCrore(r.totalRaBillsCrores),
      ])}
    />
  );
}

// ─────────── O7 ───────────
function RiskHeatmapPanel() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-risk-heatmap"],
    queryFn: () => portfolioReportApi.getRiskHeatmap(),
  });
  if (isLoading) return <LoadingBlock />;
  if (isError || !data) return <EmptyBlock label="Risk heatmap unavailable" />;
  // Build a 5×5 grid lookup
  const grid = new Map<string, number>();
  data.cells.forEach((c) => grid.set(`${c.probability}-${c.impact}`, c.count));
  const cellColor = (p: number, i: number) => {
    const score = p * i;
    if (score >= 15) return "bg-danger/30 text-danger";
    if (score >= 9) return "bg-warning/30 text-warning";
    if (score >= 5) return "bg-accent/20 text-accent";
    return "bg-success/20 text-success";
  };
  return (
    <div className="space-y-6">
      <div>
        <h3 className="mb-2 text-sm font-semibold text-text-secondary">
          5×5 Probability × Impact (open risks)
        </h3>
        <table className="border-collapse">
          <thead>
            <tr>
              <th className="p-2 text-xs text-text-muted">P\I</th>
              {[1, 2, 3, 4, 5].map((i) => (
                <th key={i} className="w-20 p-2 text-center text-xs text-text-muted">
                  {i}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {[5, 4, 3, 2, 1].map((p) => (
              <tr key={p}>
                <td className="p-2 text-xs text-text-muted">{p}</td>
                {[1, 2, 3, 4, 5].map((i) => {
                  const n = grid.get(`${p}-${i}`) ?? 0;
                  return (
                    <td
                      key={i}
                      className={`h-16 w-20 border border-border text-center align-middle ${cellColor(p, i)}`}
                    >
                      {n > 0 ? (
                        <span className="text-lg font-bold">{n}</span>
                      ) : (
                        <span className="text-xs">·</span>
                      )}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div>
        <h3 className="mb-2 text-sm font-semibold text-text-secondary">Top 5 exposure</h3>
        <Table
          head={["Code", "Project", "Title", "Prob", "Impact", "Score", "RAG"]}
          rows={data.topExposureRisks.map((r) => [
            r.code,
            r.projectCode,
            r.title,
            r.probability,
            r.impact,
            r.score.toFixed(1),
            ragBadge(r.rag),
          ])}
        />
      </div>
    </div>
  );
}

// ─────────── O8 ───────────
function CashFlowPanel() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-cash-flow"],
    queryFn: () => portfolioReportApi.getCashFlowOutlook(12),
  });
  if (isLoading) return <LoadingBlock />;
  if (isError) return <EmptyBlock label="Cash flow unavailable" />;
  const rows = data ?? [];
  if (rows.every((r) => r.plannedOutflowCrores === 0 && r.plannedInflowCrores === 0))
    return <EmptyBlock label="No cash flow forecast data seeded" />;
  return (
    <Table
      head={["Month", "Outflow", "Inflow", "Net", "Cumulative"]}
      rows={rows.map((r) => [
        r.yearMonth,
        formatCrore(r.plannedOutflowCrores),
        formatCrore(r.plannedInflowCrores),
        formatCrore(r.netCrores),
        formatCrore(r.cumulativeCrores),
      ])}
    />
  );
}

// ─────────── O9 ───────────
function CompliancePanel() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-compliance"],
    queryFn: () => portfolioReportApi.getCompliance(),
  });
  if (isLoading) return <LoadingBlock />;
  if (isError) return <EmptyBlock label="Compliance unavailable" />;
  const rows = data ?? [];
  if (rows.length === 0) return <EmptyBlock label="No projects" />;
  const tick = (b: boolean | null) =>
    b === true ? (
      <span className="text-success">✓</span>
    ) : b === false ? (
      <span className="text-danger">✗</span>
    ) : (
      <span className="text-text-muted">—</span>
    );
  return (
    <Table
      head={["Code", "Project", "PFMS", "GSTN", "GeM", "CPPP", "PARIVESH", "Score"]}
      rows={rows.map((r) => [
        r.projectCode,
        r.projectName,
        tick(r.pfmsSanctionOk),
        tick(r.gstnCheckOk),
        tick(r.gemLinkedOk),
        tick(r.cpppPublishedOk),
        tick(r.pariveshClearanceOk),
        `${r.overallScore.toFixed(0)}%`,
      ])}
    />
  );
}

// ─────────── O10 ───────────
function ScheduleHealthPanel() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-schedule-health"],
    queryFn: () => portfolioReportApi.getScheduleHealth(),
  });
  if (isLoading) return <LoadingBlock />;
  if (isError) return <EmptyBlock label="Schedule health unavailable" />;
  const rows = data ?? [];
  if (rows.length === 0) return <EmptyBlock label="No projects" />;
  return (
    <Table
      head={[
        "Code",
        "Missing Logic",
        "Leads",
        "Lags",
        "FS %",
        "Hard Const.",
        "High Float",
        "Neg Float",
        "Missed",
        "CP Length",
        "BEI",
        "Health",
      ]}
      rows={rows.map((r) => [
        r.projectCode,
        r.missingLogicCount,
        r.leadRelationshipsCount,
        r.lagsCount,
        `${r.fsRelationshipPct.toFixed(0)}%`,
        r.hardConstraintsCount,
        r.highFloatCount,
        r.negativeFloatCount,
        r.missedTasksCount,
        r.criticalPathLength,
        r.beiActual.toFixed(2),
        `${r.overallHealthPct.toFixed(0)}%`,
      ])}
    />
  );
}

// ─────────── Shared ───────────
function Table({
  head,
  rows,
  footer,
}: {
  head: string[];
  rows: (string | number | React.ReactNode)[][];
  footer?: (string | number)[];
}) {
  return (
    <div className="overflow-x-auto rounded-lg border border-border">
      <table className="w-full text-sm">
        <thead className="bg-surface-hover/50">
          <tr>
            {head.map((h) => (
              <th key={h} className="px-3 py-2 text-left font-semibold text-text-secondary">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} className="border-t border-border">
              {r.map((c, j) => (
                <td key={j} className="px-3 py-2 text-text-primary">
                  {c}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
        {footer && (
          <tfoot className="bg-surface-hover/50">
            <tr>
              {footer.map((c, j) => (
                <td key={j} className="px-3 py-2 font-semibold text-text-primary">
                  {c}
                </td>
              ))}
            </tr>
          </tfoot>
        )}
      </table>
    </div>
  );
}

function LoadingBlock() {
  return (
    <div className="rounded-lg border border-border bg-surface/50 p-6 text-center text-text-muted">
      Loading…
    </div>
  );
}

function EmptyBlock({ label }: { label: string }) {
  return (
    <div className="rounded-lg border border-dashed border-border bg-surface/30 p-6 text-center text-text-muted">
      {label}
    </div>
  );
}
