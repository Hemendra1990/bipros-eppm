"use client";

import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { PageHeader } from "@/components/common/PageHeader";
import { CashFlowOutlookChart } from "@/components/dashboards/portfolio/CashFlowOutlookChart";
import { CompliancePanel } from "@/components/dashboards/portfolio/CompliancePanel";
import { ContractorLeagueChart } from "@/components/dashboards/portfolio/ContractorLeagueChart";
import { CostOverrunChart } from "@/components/dashboards/portfolio/CostOverrunChart";
import { DelayedProjectsChart } from "@/components/dashboards/portfolio/DelayedProjectsChart";
import { EvmRollupChart } from "@/components/dashboards/portfolio/EvmRollupChart";
import { FundingUtilizationChart } from "@/components/dashboards/portfolio/FundingUtilizationChart";
import { PortfolioKpiRow } from "@/components/dashboards/portfolio/PortfolioKpiRow";
import {
  PortfolioSectionNav,
  type SectionNavItem,
} from "@/components/dashboards/portfolio/PortfolioSectionNav";
import { PortfolioStatusMix } from "@/components/dashboards/portfolio/PortfolioStatusMix";
import { RiskHeatmapPanel } from "@/components/dashboards/portfolio/RiskHeatmapPanel";
import { ScheduleHealthChart } from "@/components/dashboards/portfolio/ScheduleHealthChart";

const sections: SectionNavItem[] = [
  { id: "overview", label: "Overview" },
  { id: "schedule", label: "Schedule" },
  { id: "cost", label: "Cost" },
  { id: "cash-flow", label: "Cash Flow" },
  { id: "funding", label: "Funding" },
  { id: "risks", label: "Risks" },
  { id: "vendors", label: "Vendors" },
  { id: "compliance", label: "Compliance" },
];

export default function PortfolioDashboardPage() {
  return (
    <div className="p-6">
      <div className="mb-4 flex items-center gap-4">
        <Link
          href="/dashboards"
          aria-label="Back to dashboards"
          className="rounded p-1 hover:bg-surface-hover"
        >
          <ArrowLeft size={20} />
        </Link>
        <PageHeader
          title="Portfolio Dashboard"
          description="Cross-project performance, schedule, cost, funding, risks and compliance — at a glance."
        />
      </div>

      <PortfolioSectionNav sections={sections} />

      <div className="space-y-6">
        <section id="overview" className="scroll-mt-20 space-y-6">
          <PortfolioKpiRow />
          <PortfolioStatusMix />
        </section>

        <section id="schedule" className="scroll-mt-20">
          <DelayedProjectsChart />
        </section>

        <section id="cost" className="scroll-mt-20 grid grid-cols-1 gap-6 xl:grid-cols-2">
          <CostOverrunChart />
          <EvmRollupChart />
        </section>

        <section id="cash-flow" className="scroll-mt-20">
          <CashFlowOutlookChart />
        </section>

        <section id="funding" className="scroll-mt-20">
          <FundingUtilizationChart />
        </section>

        <section id="risks" className="scroll-mt-20">
          <RiskHeatmapPanel />
        </section>

        <section id="vendors" className="scroll-mt-20">
          <ContractorLeagueChart />
        </section>

        <section
          id="compliance"
          className="scroll-mt-20 grid grid-cols-1 gap-6 xl:grid-cols-2"
        >
          <CompliancePanel />
          <ScheduleHealthChart />
        </section>
      </div>
    </div>
  );
}
