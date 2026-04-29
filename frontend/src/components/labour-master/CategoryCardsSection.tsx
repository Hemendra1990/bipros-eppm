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
      <header className={`relative overflow-hidden rounded-xl border ${accent.section} px-4 py-3`}>
        <div className={`pointer-events-none absolute inset-y-0 left-0 w-[3px] ${accent.stripe}`} />
        <div className="flex flex-wrap items-end justify-between gap-3 pl-2">
          <div>
            <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-slate">
              {summary.codePrefix} · Category
            </div>
            <h2 className={`mt-0.5 font-display text-[22px] font-semibold leading-tight ${accent.title}`}>
              {summary.categoryDisplay}
            </h2>
          </div>
          <dl className="flex items-center gap-5 text-[12px] text-slate">
            <div>
              <dt className="text-[10px] uppercase tracking-[0.12em] text-slate">Designations</dt>
              <dd className="font-display text-[16px] font-semibold text-charcoal">{summary.designationCount}</dd>
            </div>
            <div>
              <dt className="text-[10px] uppercase tracking-[0.12em] text-slate">Workers</dt>
              <dd className="font-display text-[16px] font-semibold text-charcoal">{summary.workerCount}</dd>
            </div>
            <div>
              <dt className="text-[10px] uppercase tracking-[0.12em] text-slate">Daily cost</dt>
              <dd className="font-display text-[16px] font-semibold text-gold-deep">{formatOMR(summary.dailyCost)}</dd>
            </div>
          </dl>
        </div>
      </header>

      <div className="grid gap-3 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {designations.map((d) => (
          <WorkerCard key={d.id} designation={d} onClick={onCardClick} />
        ))}
      </div>
    </section>
  );
}
