/**
 * "Schedule % Complete" — the P6 metric that says how much of an activity's planned work
 * SHOULD have been done by the data date, regardless of how much actually has been.
 *
 * Compare against the activity's own % Complete to see whether the activity is ahead,
 * on track, or behind schedule.
 *
 * Reference dates: caller decides which dates to feed in. The recommended precedence is
 * (a) active PRIMARY baseline's earlyStart/earlyFinish for the activity, (b) the
 * activity's plannedStartDate/plannedFinishDate as a fallback when no baseline exists.
 *
 * Returns null when any input is missing — the caller is expected to render "—".
 */
export function schedulePercentComplete(
  start: string | Date | null | undefined,
  finish: string | Date | null | undefined,
  dataDate: string | Date | null | undefined,
): number | null {
  const s = toDate(start);
  const f = toDate(finish);
  const d = toDate(dataDate);
  if (s == null || f == null || d == null) return null;
  if (f.getTime() <= s.getTime()) {
    // Zero or negative window — treat as instantaneous: 0 before, 100 on/after.
    return d.getTime() < s.getTime() ? 0 : 100;
  }
  if (d.getTime() <= s.getTime()) return 0;
  if (d.getTime() >= f.getTime()) return 100;
  const elapsed = d.getTime() - s.getTime();
  const total = f.getTime() - s.getTime();
  return Math.round((elapsed / total) * 100);
}

/**
 * Variance bucket for the Schedule % cell. Caller maps to a colour token —
 * we keep the buckets here so the threshold (5%) is in one place.
 */
export type ScheduleVariance = "ahead" | "on-track" | "behind" | "unknown";

export function scheduleVarianceBucket(
  schedulePct: number | null,
  activityPct: number | null | undefined,
): ScheduleVariance {
  if (schedulePct == null || activityPct == null) return "unknown";
  const delta = activityPct - schedulePct;
  if (delta > 5) return "ahead";
  if (delta < -5) return "behind";
  return "on-track";
}

function toDate(value: string | Date | null | undefined): Date | null {
  if (value == null || value === "") return null;
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value;
  }
  // ISO date strings ("2024-11-05") become local-midnight; ISO datetimes parsed as-is.
  // We want the comparison to be calendar-day-stable, so when given a bare date,
  // anchor to local midnight to avoid TZ drift.
  const isoDateOnly = /^\d{4}-\d{2}-\d{2}$/.test(value);
  const d = isoDateOnly ? new Date(value + "T00:00:00") : new Date(value);
  return Number.isNaN(d.getTime()) ? null : d;
}
