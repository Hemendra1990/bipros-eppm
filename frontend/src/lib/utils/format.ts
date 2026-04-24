/**
 * Format an ISO date string (e.g. "2026-01-15") to a readable format (e.g. "Jan 15, 2026").
 * Returns "—" for null/undefined/empty values.
 */
export function formatDate(date: string | null | undefined): string {
  if (!date) return "—";
  const d = new Date(date + "T00:00:00");
  if (isNaN(d.getTime())) return date;
  return d.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

/**
 * Priority is a 1-100 integer (1 = highest). Display labels are derived from bucketing the value
 * so both data seeded on a 1-10 scale and projects created on the 1-100 scale produce the same
 * six labels everywhere in the UI.
 */
export function getPriorityInfo(priority: number | null | undefined): { label: string; color: string } {
  if (priority == null || Number.isNaN(priority)) {
    return { label: "—", color: "text-slate-500" };
  }
  // Clamp into 1-100 for bucketing, but keep the raw value visible for out-of-range data.
  const p = Math.max(1, Math.min(100, Math.round(priority)));
  if (p <= 10) return { label: "Critical", color: "text-red-400" };
  if (p <= 25) return { label: "Very High", color: "text-red-400" };
  if (p <= 40) return { label: "High", color: "text-orange-400" };
  if (p <= 60) return { label: "Medium", color: "text-yellow-400" };
  if (p <= 80) return { label: "Low", color: "text-slate-400" };
  return { label: "Very Low", color: "text-slate-500" };
}
