"use client";

import { useQuery } from "@tanstack/react-query";
import { Coins, Flame, ShieldAlert, Wallet, Zap } from "lucide-react";
import { KpiTile } from "@/components/common/KpiTile";
import { portfolioReportApi } from "@/lib/api/portfolioReportApi";
import {
  EmptyBlock,
  LoadingBlock,
  formatCrore,
} from "@/components/common/dashboard/primitives";

export function PortfolioKpiRow() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-scorecard"],
    queryFn: () => portfolioReportApi.getScorecard(),
    staleTime: 60_000,
  });

  if (isLoading) return <LoadingBlock label="Loading KPIs…" />;
  if (isError || !data) return <EmptyBlock label="Portfolio KPIs unavailable" />;

  const planned = data.byStatus?.PLANNED ?? 0;
  const active = data.byStatus?.ACTIVE ?? 0;
  const completed = data.byStatus?.COMPLETED ?? 0;
  const onHold = data.byStatus?.ON_HOLD ?? 0;
  const cancelled = data.byStatus?.CANCELLED ?? 0;

  return (
    <div className="space-y-4">
      {/* Row 1 — Projects & lifecycle status */}
      <div>
        <div className="mb-2 text-xs font-medium uppercase tracking-wide text-text-muted">
          Projects by status
        </div>
        <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-6">
          <KpiTile label="Total projects" value={data.totalProjects} tone="accent" />
          <KpiTile label="Planned" value={planned} />
          <KpiTile
            label="Active"
            value={active}
            tone={active > 0 ? "success" : "default"}
          />
          <KpiTile label="Completed" value={completed} />
          <KpiTile
            label="On Hold"
            value={onHold}
            tone={onHold > 0 ? "warning" : "default"}
          />
          <KpiTile
            label="Cancelled"
            value={cancelled}
            tone={cancelled > 0 ? "danger" : "default"}
          />
        </div>
      </div>

      {/* Row 2 — Financial & risk */}
      <div>
        <div className="mb-2 text-xs font-medium uppercase tracking-wide text-text-muted">
          Financial & risk
        </div>
        <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-5">
          <KpiTile
            label="Budget"
            value={formatCrore(data.totalBudgetCrores)}
            tone="accent"
            icon={<Wallet size={16} />}
          />
          <KpiTile
            label="Committed"
            value={formatCrore(data.totalCommittedCrores)}
            icon={<Coins size={16} />}
          />
          <KpiTile
            label="Spent"
            value={formatCrore(data.totalSpentCrores)}
            icon={<Coins size={16} />}
          />
          <KpiTile
            label="At-risk"
            value={data.activeProjectsWithCriticalActivities}
            hint="active w/ critical activities"
            tone={data.activeProjectsWithCriticalActivities > 0 ? "warning" : "default"}
            icon={<Zap size={16} />}
          />
          <KpiTile
            label="Open critical risks"
            value={data.openRisksCritical}
            tone={data.openRisksCritical > 0 ? "danger" : "success"}
            icon={data.openRisksCritical > 0 ? <Flame size={16} /> : <ShieldAlert size={16} />}
          />
        </div>
      </div>
    </div>
  );
}
