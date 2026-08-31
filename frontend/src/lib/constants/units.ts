/**
 * Single source of truth for the unit-of-measure dropdown shared by:
 * - DPR add/edit form (`DprActivityForm.tsx`)
 * - Work Activity admin form (`admin/work-activities/page.tsx`)
 * - Productivity Norm admin form (`admin/productivity-norms/page.tsx`)
 *
 * Keeping these aligned prevents the case where a user types `cum` (lower-case) or `Lm` on the
 * Work Activity master, and the DPR form's strict dropdown silently rejects it (or falls back to
 * the first option), leading to a unit mismatch and broken Capacity Utilization math.
 *
 * This is the client-approved 12-unit list (workbook "Web" sheet, DPR row 2, 01 Aug 2026:
 * "Keep Cum, Sqm, Rm, MT, Nr, LS, Kg, Hrs, Bag, Month, Week, Day. Rest all to be removed").
 * Removing a value never touches stored rows — units are free text in the DB; consumers render
 * via unitOptionsWithFallback so legacy values keep displaying with a "(legacy)" tag.
 * KEEP IN SYNC with backend/bipros-ai .../voice/dpr/DprUnits.java (voice DPR fill).
 */
export const STANDARD_UNITS = [
  "Cum",
  "Sqm",
  "Rm",
  "MT",
  "Nr",
  "LS",
  "Kg",
  "Hrs",
  "Bag",
  "Month",
  "Week",
  "Day",
] as const;

export type StandardUnit = (typeof STANDARD_UNITS)[number];

/**
 * Options for a `<select>` that includes the standard list plus the current value when it's a
 * legacy / typo'd entry not in the standard list. Lets existing data still display while
 * pushing new entries onto the canonical list.
 */
export function unitOptionsWithFallback(currentValue: string | null | undefined): string[] {
  if (!currentValue) return [...STANDARD_UNITS];
  const trimmed = currentValue.trim();
  if (!trimmed) return [...STANDARD_UNITS];
  if ((STANDARD_UNITS as readonly string[]).includes(trimmed)) return [...STANDARD_UNITS];
  return [...STANDARD_UNITS, trimmed];
}
