"use client";

import type { LabourDesignationResponse } from "@/lib/api/labourMasterApi";
import { CATEGORY_ACCENT, GRADE_BADGE, formatOMR } from "./labourMasterTokens";

type Props = {
  designation: LabourDesignationResponse;
  onClick?: (d: LabourDesignationResponse) => void;
};

export function WorkerCard({ designation, onClick }: Props) {
  const accent = CATEGORY_ACCENT[designation.category];
  const grade = GRADE_BADGE[designation.grade];
  const workerCount = designation.deployment?.workerCount ?? 0;
  const dailyRate = designation.deployment?.effectiveRate ?? designation.defaultDailyRate;
  return (
    <button
      type="button"
      onClick={onClick ? () => onClick(designation) : undefined}
      className={`text-left rounded-lg border ring-1 ${accent.ring} ${accent.bg} p-4 hover:shadow transition`}
    >
      <div className="flex items-center justify-between">
        <span className={`text-xs font-mono ${accent.text}`}>{designation.code}</span>
        <span className={`text-xs px-2 py-0.5 rounded ${grade}`}>Grade {designation.grade}</span>
      </div>
      <div className="mt-1 font-semibold">{designation.designation}</div>
      <div className="text-xs text-muted-foreground">{designation.trade}</div>
      <div className="mt-2 flex flex-wrap gap-1">
        {designation.skills.slice(0, 5).map((s) => (
          <span key={s} className={`text-[10px] px-1.5 py-0.5 rounded ${accent.chip}`}>
            {s}
          </span>
        ))}
      </div>
      <div className="mt-3 flex items-center justify-between text-sm">
        <span>
          {workerCount} worker{workerCount === 1 ? "" : "s"}
        </span>
        <span className="font-medium">{formatOMR(dailyRate)}</span>
      </div>
      <div className="mt-1 text-xs text-muted-foreground">
        {designation.nationality.replace("_", " / ")} · {designation.experienceYearsMin}+ yrs
      </div>
    </button>
  );
}
