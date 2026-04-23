"use client";

import { useQuery } from "@tanstack/react-query";
import { Briefcase, Coins, Flame, ShieldAlert, Wallet, Zap } from "lucide-react";
import { KpiTile } from "@/components/common/KpiTile";
import { portfolioReportApi } from "@/lib/api/portfolioReportApi";
import { LoadingBlock, EmptyBlock, formatCrore } from "./chartPrimitives";

export function PortfolioKpiRow() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["portfolio-scorecard"],
    queryFn: () => portfolioReportApi.getScorecard(),
    staleTime: 60_000,
  });

  if (isLoading) return <LoadingBlock label="Loading KPIs…" />;
  if (isError || !data) return <EmptyBlock label="Portfolio KPIs unavailable" />;

  const active = data.byStatus?.ACTIVE ?? 0;
  const completed = data.byStatus?.COMPLETED ?? 0;
  const atRisk = data.activeProjectsWithCriticalActivities;

  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-6">
      <KpiTile
        label="Projects"
        value={data.totalProjects}
        hint={`${active} active · ${completed} completed`}
        icon={<Briefcase size={16} />}
      />
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
        value={atRisk}
        hint="active w/ critical activities"
        tone={atRisk > 0 ? "warning" : "default"}
        icon={<Zap size={16} />}
      />
      <KpiTile
        label="Open critical risks"
        value={data.openRisksCritical}
        tone={data.openRisksCritical > 0 ? "danger" : "success"}
        icon={data.openRisksCritical > 0 ? <Flame size={16} /> : <ShieldAlert size={16} />}
      />
    </div>
  );
}
