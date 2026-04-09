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

const PRIORITY_LABELS: Record<number, { label: string; color: string }> = {
  1: { label: "Critical", color: "text-red-400" },
  2: { label: "Very High", color: "text-red-400" },
  3: { label: "High", color: "text-orange-400" },
  4: { label: "Medium-High", color: "text-orange-400" },
  5: { label: "Medium", color: "text-yellow-400" },
  6: { label: "Medium-Low", color: "text-yellow-400" },
  7: { label: "Low", color: "text-slate-400" },
  8: { label: "Very Low", color: "text-slate-400" },
  9: { label: "Minimal", color: "text-slate-500" },
  10: { label: "None", color: "text-slate-500" },
};

export function getPriorityInfo(priority: number): { label: string; color: string } {
  return PRIORITY_LABELS[priority] ?? { label: `P${priority}`, color: "text-slate-400" };
}
