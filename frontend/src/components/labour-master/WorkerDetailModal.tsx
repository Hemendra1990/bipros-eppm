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
      className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      onClick={onClose}
    >
      <div className="bg-white rounded-xl max-w-2xl w-full p-6 shadow-xl" onClick={(e) => e.stopPropagation()}>
        <header className="flex items-start justify-between">
          <div>
            <div className="text-xs font-mono text-muted-foreground">{designation.code}</div>
            <h3 className="text-xl font-semibold">{designation.designation}</h3>
            <p className="text-sm text-muted-foreground">
              {designation.categoryDisplay} · {designation.trade}
            </p>
          </div>
          <button onClick={onClose} aria-label="Close" className="text-slate-500 hover:text-slate-900">
            ✕
          </button>
        </header>
        <div className={`mt-4 grid grid-cols-2 gap-3 rounded ${accent.bg} ring-1 ${accent.ring} p-4`}>
          <Field label="Grade">
            <span className={`px-2 py-0.5 rounded text-xs ${GRADE_BADGE[designation.grade]}`}>
              Grade {designation.grade}
            </span>
          </Field>
          <Field label="Nationality">{designation.nationality.replace("_", " / ")}</Field>
          <Field label="Experience">{designation.experienceYearsMin}+ yrs</Field>
          <Field label="Worker Count">{workerCount}</Field>
          <Field label="Daily Rate">{formatOMR(dailyRate)}</Field>
          <Field label="Total Daily Cost">{formatOMR(totalDailyCost)}</Field>
        </div>
        <Section title="Skills">
          <div className="flex flex-wrap gap-1">
            {designation.skills.map((s) => (
              <span key={s} className={`text-xs px-2 py-0.5 rounded ${accent.chip}`}>
                {s}
              </span>
            ))}
          </div>
        </Section>
        <Section title="Required Certifications">
          <ul className="list-disc pl-5 text-sm">
            {designation.certifications.map((c) => (
              <li key={c}>{c}</li>
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
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="text-sm font-medium">{children}</div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="mt-5">
      <h4 className="text-sm font-semibold mb-2">{title}</h4>
      {children}
    </div>
  );
}
