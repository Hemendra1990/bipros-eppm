"use client";

import { KpiTile } from "@/components/common/KpiTile";
import type { LabourMasterDashboardSummary } from "@/lib/api/labourMasterApi";
import { formatOMR } from "./labourMasterTokens";

type Props = { summary: LabourMasterDashboardSummary };

export function KpiTiles({ summary }: Props) {
  const mix = summary.nationalityMix;
  return (
    <div className="grid gap-4 grid-cols-2 md:grid-cols-3 lg:grid-cols-5">
      <KpiTile label="Total Designations" value={summary.totalDesignations} />
      <KpiTile label="Total Workforce"     value={summary.totalWorkforce} />
      <KpiTile
        label="Daily Payroll"
        value={formatOMR(summary.dailyPayroll)}
        hint={summary.currency}
        tone="accent"
      />
      <KpiTile label="Skill Categories" value={summary.skillCategoryCount} />
      <KpiTile
        label="Nationality Mix"
        value={`${mix.omani} / ${mix.expat} / ${mix.omaniOrExpat}`}
        hint="Omani / Expat / Either"
      />
    </div>
  );
}
