"use client";

import { useEffect } from "react";
import type { LabourDesignationResponse } from "@/lib/api/labourMasterApi";
import { CATEGORY_ACCENT, GRADE_BADGE, formatOMR } from "./labourMasterTokens";

type Props = {
  designation: LabourDesignationResponse | null;
  onClose: () => void;
};

export function WorkerDetailModal({ designation, onClose }: Props) {
  useEffect(() => {
    if (!designation) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [designation, onClose]);

  if (!designation) return null;
  const accent = CATEGORY_ACCENT[designation.category];
  const workerCount = designation.deployment?.workerCount ?? 0;
  const dailyRate = designation.deployment?.effectiveRate ?? designation.defaultDailyRate;
  const totalDailyCost = designation.deployment?.dailyCost ?? workerCount * dailyRate;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-paper/80 p-4 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      onClick={onClose}
    >
      <div
        className="relative w-full max-w-2xl overflow-hidden rounded-2xl border border-hairline bg-paper shadow-[0_24px_48px_-12px_rgba(0,0,0,0.5)]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className={`pointer-events-none absolute inset-x-0 top-0 h-[3px] ${accent.stripe}`} />

        <header className="flex items-start justify-between gap-3 px-6 pt-5 pb-4">
          <div>
            <div className="font-mono text-[11px] tracking-[0.06em] text-gold-ink">{designation.code}</div>
            <h3 className="mt-1 font-display text-[24px] font-semibold leading-tight text-charcoal">
              {designation.designation}
            </h3>
            <div className="mt-1 flex items-center gap-2 text-[12px] text-slate">
              <span className={`inline-flex items-center rounded-md border px-2 py-0.5 text-[11px] font-medium ${accent.chip}`}>
                {designation.categoryDisplay}
              </span>
              <span>·</span>
              <span>{designation.trade}</span>
            </div>
          </div>
          <button
            onClick={onClose}
            aria-label="Close"
            className="rounded-md p-1.5 text-slate hover:bg-ivory hover:text-charcoal"
          >
            ✕
          </button>
        </header>

        <div className="grid grid-cols-2 gap-x-6 gap-y-4 border-y border-hairline bg-ivory/40 px-6 py-5">
          <Field label="Grade">
            <span className={`inline-flex items-center rounded border px-2 py-0.5 text-[11px] font-semibold ${GRADE_BADGE[designation.grade]}`}>
              Grade {designation.grade}
            </span>
          </Field>
          <Field label="Nationality">
            <span className="text-[13px] text-charcoal">{designation.nationality.replace(/_/g, " / ")}</span>
          </Field>
          <Field label="Experience">
            <span className="font-display text-[18px] font-semibold text-charcoal">{designation.experienceYearsMin}+ yrs</span>
          </Field>
          <Field label="Workers on this project">
            <span className="font-display text-[18px] font-semibold text-charcoal">{workerCount}</span>
          </Field>
          <Field label="Daily rate">
            <span className="font-display text-[18px] font-semibold text-gold-deep">{formatOMR(dailyRate)}</span>
          </Field>
          <Field label="Total daily cost">
            <span className="font-display text-[18px] font-semibold text-gold-deep">{formatOMR(totalDailyCost)}</span>
          </Field>
        </div>

        <Section title="Skills">
          <div className="flex flex-wrap gap-1.5">
            {designation.skills.map((s) => (
              <span key={s} className={`inline-flex rounded-md border px-2 py-0.5 text-[11px] ${accent.chip}`}>
                {s}
              </span>
            ))}
          </div>
        </Section>

        <Section title="Required certifications">
          <ul className="grid gap-1 sm:grid-cols-2">
            {designation.certifications.map((c) => (
              <li key={c} className="flex items-start gap-2 text-[13px] text-charcoal">
                <span className={`mt-1 h-1.5 w-1.5 rounded-full ${accent.stripe}`} />
                {c}
              </li>
            ))}
          </ul>
        </Section>
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="text-[10px] font-semibold uppercase tracking-[0.12em] text-slate">{label}</div>
      <div className="mt-1">{children}</div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="px-6 py-4">
      <h4 className="mb-2 text-[10px] font-semibold uppercase tracking-[0.12em] text-slate">{title}</h4>
      {children}
    </div>
  );
}
