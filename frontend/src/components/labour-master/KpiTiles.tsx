"use client";

import type { LabourMasterDashboardSummary } from "@/lib/api/labourMasterApi";
import { formatOMR } from "./labourMasterTokens";

type Props = { summary: LabourMasterDashboardSummary };

export function KpiTiles({ summary }: Props) {
  const tiles: Array<{ label: string; value: string; sub?: string }> = [
    { label: "Total Designations", value: String(summary.totalDesignations) },
    { label: "Total Workforce",     value: String(summary.totalWorkforce) },
    { label: "Daily Payroll",       value: formatOMR(summary.dailyPayroll), sub: summary.currency },
    { label: "Skill Categories",    value: String(summary.skillCategoryCount) },
    {
      label: "Nationality Mix",
      value: `${summary.nationalityMix.omani} / ${summary.nationalityMix.expat} / ${summary.nationalityMix.omaniOrExpat}`,
      sub: "Omani / Expat / Either",
    },
  ];
  return (
    <div className="grid gap-4 grid-cols-2 md:grid-cols-3 lg:grid-cols-5">
      {tiles.map((t) => (
        <div key={t.label} className="rounded-lg border bg-white p-4 shadow-sm">
          <div className="text-sm text-muted-foreground">{t.label}</div>
          <div className="mt-1 text-2xl font-semibold">{t.value}</div>
          {t.sub ? <div className="mt-1 text-xs text-muted-foreground">{t.sub}</div> : null}
        </div>
      ))}
    </div>
  );
}
