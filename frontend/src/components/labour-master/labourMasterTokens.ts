import type { LabourCategory, LabourGrade } from "@/lib/api/labourMasterApi";

export const CATEGORY_ACCENT: Record<
  LabourCategory,
  { bg: string; ring: string; text: string; chip: string }
> = {
  SITE_MANAGEMENT:    { bg: "bg-emerald-50", ring: "ring-emerald-200", text: "text-emerald-900", chip: "bg-emerald-100 text-emerald-800" },
  PLANT_EQUIPMENT:    { bg: "bg-amber-50",   ring: "ring-amber-200",   text: "text-amber-900",   chip: "bg-amber-100 text-amber-800" },
  SKILLED_LABOUR:     { bg: "bg-indigo-50",  ring: "ring-indigo-200",  text: "text-indigo-900",  chip: "bg-indigo-100 text-indigo-800" },
  SEMI_SKILLED_LABOUR:{ bg: "bg-purple-50",  ring: "ring-purple-200",  text: "text-purple-900",  chip: "bg-purple-100 text-purple-800" },
  GENERAL_UNSKILLED:  { bg: "bg-slate-50",   ring: "ring-slate-200",   text: "text-slate-900",   chip: "bg-slate-100 text-slate-800" },
};

export const GRADE_BADGE: Record<LabourGrade, string> = {
  A: "bg-rose-100 text-rose-800",
  B: "bg-orange-100 text-orange-800",
  C: "bg-amber-100 text-amber-800",
  D: "bg-sky-100 text-sky-800",
  E: "bg-slate-100 text-slate-800",
};

export const formatOMR = (value: number | null | undefined): string =>
  value == null
    ? "—"
    : `OMR ${value.toLocaleString("en-OM", { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
