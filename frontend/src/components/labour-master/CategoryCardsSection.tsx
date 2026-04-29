"use client";

import type {
  LabourCategorySummary,
  LabourDesignationResponse,
} from "@/lib/api/labourMasterApi";
import { WorkerCard } from "./WorkerCard";
import { CATEGORY_ACCENT, formatOMR } from "./labourMasterTokens";

type Props = {
  summary: LabourCategorySummary;
  designations: LabourDesignationResponse[];
  onCardClick?: (d: LabourDesignationResponse) => void;
};

export function CategoryCardsSection({ summary, designations, onCardClick }: Props) {
  const accent = CATEGORY_ACCENT[summary.category];
  return (
    <section className="space-y-3">
      <header className={`rounded-md ${accent.bg} ring-1 ${accent.ring} px-4 py-3`}>
        <h2 className={`font-semibold ${accent.text}`}>{summary.categoryDisplay}</h2>
        <p className="text-xs text-muted-foreground">
          {summary.designationCount} designations · {summary.workerCount} workers ·
          Daily cost {formatOMR(summary.dailyCost)}
        </p>
      </header>
      <div className="grid gap-3 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {designations.map((d) => (
          <WorkerCard key={d.id} designation={d} onClick={onCardClick} />
        ))}
      </div>
    </section>
  );
}
