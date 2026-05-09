import { STANDARD_UNITS } from "./units";

/**
 * Unit-of-measure list for Resource and Rate Master forms — the unit at which a
 * resource is consumed/charged (Day for a Mason paid daily, Hour for a JCB,
 * Bag for cement). This is distinct from {@link STANDARD_UNITS} which measures
 * activity output (Cum, Sqm, MT) for Work Activities / Productivity Norms / DPR.
 *
 * Lists overlap (MT, Cum, kg apply to both) — combining them here gives admins a
 * single dropdown that covers any rate they need without forcing a code change.
 */
export const RESOURCE_RATE_UNITS = [
  "Day",
  "Hour",
  "Month",
  "Shift",
  "Trip",
  "Bag",
  ...STANDARD_UNITS,
] as const;

export type ResourceRateUnit = (typeof RESOURCE_RATE_UNITS)[number];

/**
 * Returns the standard list, or the standard list plus the supplied value if it's a
 * legacy / typo'd entry not already in the list. Lets existing data still display.
 */
export function rateUnitOptionsWithFallback(currentValue: string | null | undefined): string[] {
  const baseList: string[] = Array.from(new Set<string>(RESOURCE_RATE_UNITS));
  if (!currentValue) return baseList;
  const trimmed = currentValue.trim();
  if (!trimmed) return baseList;
  if (baseList.includes(trimmed)) return baseList;
  return [...baseList, trimmed];
}
