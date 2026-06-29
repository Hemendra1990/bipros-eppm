import type { ActivityStatusRow } from "@/lib/api/projectInsightsApi";

export type HealthBucket = "onTrack" | "atRisk" | "delayed";

export function bucketActivityHealth(r: ActivityStatusRow): HealthBucket {
  if (r.pctComplete >= 100) return "onTrack";
  const variance = r.pctComplete - (r.expectedProgressPct ?? 0);
  if (variance <= -20) return "delayed";
  if (variance <= -5) return "atRisk";
  if (r.isCritical && variance < 0) return "atRisk";
  return "onTrack";
}

export function mapWorkPackageStatus(r: ActivityStatusRow): string {
  if (r.pctComplete >= 100) return "COMPLETED";
  const variance = r.pctComplete - (r.expectedProgressPct ?? 0);
  if (variance <= -20) return "DELAYED";
  if (r.pctComplete > 0) return "IN_PROGRESS";
  return r.status || "NOT_STARTED";
}

export interface DeltaPill {
  value: string;
  direction: "up" | "down" | "flat";
}

export function formatDelta(
  curr: number | null | undefined,
  delta: number | null | undefined,
  opts: { unit?: string; digits?: number; invertColor?: boolean } = {},
): DeltaPill | undefined {
  if (curr == null || delta == null || Number.isNaN(delta)) return undefined;
  const { unit = "", digits = 1, invertColor = false } = opts;
  if (Math.abs(delta) < 10 ** -digits / 2) {
    return { value: `${(0).toFixed(digits)}${unit}`, direction: "flat" };
  }
  const sign = delta > 0 ? "+" : "";
  const value = `${sign}${delta.toFixed(digits)}${unit}`;
  const rising = delta > 0;
  const direction = (invertColor ? !rising : rising) ? "up" : "down";
  return { value, direction };
}

export function bucketActivities(rows: ActivityStatusRow[]) {
  let onTrack = 0;
  let atRisk = 0;
  let delayed = 0;
  for (const r of rows) {
    const b = bucketActivityHealth(r);
    if (b === "onTrack") onTrack++;
    else if (b === "atRisk") atRisk++;
    else delayed++;
  }
  return { onTrack, atRisk, delayed, total: rows.length };
}

export function tasksCompleted(rows: ActivityStatusRow[]) {
  const done = rows.filter(
    (r) => r.pctComplete >= 100 || r.status === "DONE" || r.status === "COMPLETED",
  ).length;
  return { done, total: rows.length };
}

// Non-terminal ("open") issue statuses — the single source shared by the open count, the
// critical sub-count, and the drawer list, so the tile value, its "N critical" hint, and the
// drilled-in list can never disagree on what counts as open.
const OPEN_ISSUE_STATUSES = new Set(["OPEN", "IN_PROGRESS", "BLOCKED"]);

export function criticalIssueCount(
  issues: { severity: string; status: string }[],
): number {
  return issues.filter(
    (i) => i.severity === "CRITICAL" && OPEN_ISSUE_STATUSES.has(i.status),
  ).length;
}

export function completedActivities(rows: ActivityStatusRow[]): ActivityStatusRow[] {
  return rows.filter(
    (r) => r.pctComplete >= 100 || r.status === "DONE" || r.status === "COMPLETED",
  );
}

export function activitiesByHealth(
  rows: ActivityStatusRow[],
  bucket: HealthBucket,
): ActivityStatusRow[] {
  return rows.filter((r) => bucketActivityHealth(r) === bucket);
}

export function openIssueCount(issues: { status: string }[]): number {
  return issues.filter((i) => OPEN_ISSUE_STATUSES.has(i.status)).length;
}

const SEVERITY_ORDER: Record<string, number> = {
  CRITICAL: 0,
  HIGH: 1,
  MEDIUM: 2,
  LOW: 3,
};

export function openIssues<T extends { status: string; severity: string }>(
  issues: T[],
): T[] {
  return issues
    .filter((i) => OPEN_ISSUE_STATUSES.has(i.status))
    .sort(
      (a, b) =>
        (SEVERITY_ORDER[a.severity] ?? 99) - (SEVERITY_ORDER[b.severity] ?? 99),
    );
}
