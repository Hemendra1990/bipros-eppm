import { describe, it, expect } from "vitest";
import {
  completedActivities,
  activitiesByHealth,
  openIssueCount,
  openIssues,
  criticalIssueCount,
} from "../dashboardDerivations";
import type { ActivityStatusRow } from "@/lib/api/projectInsightsApi";

// Minimal factory — only the fields the helpers touch
function makeRow(
  overrides: Partial<ActivityStatusRow> & { pctComplete: number },
): ActivityStatusRow {
  return {
    activityId: "1",
    code: "A1",
    name: "Test Activity",
    wbsCode: "1.1",
    wbsName: "WBS",
    status: "IN_PROGRESS",
    activityType: "TASK",
    plannedStart: null,
    plannedFinish: null,
    actualStart: null,
    actualFinish: null,
    earlyStart: null,
    earlyFinish: null,
    totalFloat: null,
    freeFloat: null,
    isCritical: false,
    expectedProgressPct: 0,
    daysDelay: 0,
    daysRemaining: 0,
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// completedActivities
// ---------------------------------------------------------------------------
describe("completedActivities", () => {
  it("includes rows where pctComplete >= 100", () => {
    const rows = [makeRow({ pctComplete: 100 }), makeRow({ pctComplete: 50 })];
    expect(completedActivities(rows)).toHaveLength(1);
    expect(completedActivities(rows)[0].pctComplete).toBe(100);
  });

  it("includes rows with status DONE", () => {
    const rows = [makeRow({ pctComplete: 80, status: "DONE" }), makeRow({ pctComplete: 80 })];
    expect(completedActivities(rows)).toHaveLength(1);
    expect(completedActivities(rows)[0].status).toBe("DONE");
  });

  it("includes rows with status COMPLETED", () => {
    const rows = [makeRow({ pctComplete: 0, status: "COMPLETED" })];
    expect(completedActivities(rows)).toHaveLength(1);
  });

  it("excludes rows that are in-progress or not started", () => {
    const rows = [
      makeRow({ pctComplete: 50, status: "IN_PROGRESS" }),
      makeRow({ pctComplete: 0, status: "NOT_STARTED" }),
      makeRow({ pctComplete: 99, status: "IN_PROGRESS" }),
    ];
    expect(completedActivities(rows)).toHaveLength(0);
  });

  it("returns empty array for empty input", () => {
    expect(completedActivities([])).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// activitiesByHealth
// ---------------------------------------------------------------------------
describe("activitiesByHealth", () => {
  // delayed: variance <= -20
  const delayed = makeRow({ pctComplete: 10, expectedProgressPct: 40 }); // variance = -30
  // atRisk: variance <= -5 (but > -20)
  const atRisk = makeRow({ pctComplete: 30, expectedProgressPct: 40 }); // variance = -10
  // onTrack: variance within -5
  const onTrack = makeRow({ pctComplete: 38, expectedProgressPct: 40 }); // variance = -2
  // onTrack: 100% complete
  const done = makeRow({ pctComplete: 100 });

  it("returns only delayed activities for bucket 'delayed'", () => {
    const result = activitiesByHealth([delayed, atRisk, onTrack, done], "delayed");
    expect(result).toHaveLength(1);
    expect(result[0]).toBe(delayed);
  });

  it("returns only atRisk activities for bucket 'atRisk'", () => {
    const result = activitiesByHealth([delayed, atRisk, onTrack, done], "atRisk");
    expect(result).toHaveLength(1);
    expect(result[0]).toBe(atRisk);
  });

  it("returns onTrack activities for bucket 'onTrack'", () => {
    const result = activitiesByHealth([delayed, atRisk, onTrack, done], "onTrack");
    expect(result).toHaveLength(2);
    expect(result).toContain(onTrack);
    expect(result).toContain(done);
  });

  it("returns empty array when no activities match the bucket", () => {
    const result = activitiesByHealth([onTrack, done], "delayed");
    expect(result).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// openIssueCount
// ---------------------------------------------------------------------------
describe("openIssueCount", () => {
  it("counts OPEN, IN_PROGRESS, and BLOCKED statuses", () => {
    const issues = [
      { status: "OPEN" },
      { status: "IN_PROGRESS" },
      { status: "BLOCKED" },
      { status: "RESOLVED" },
      { status: "CLOSED" },
      { status: "CANCELLED" },
    ];
    expect(openIssueCount(issues)).toBe(3);
  });

  it("returns 0 when all issues are closed/resolved/cancelled", () => {
    const issues = [
      { status: "RESOLVED" },
      { status: "CLOSED" },
      { status: "CANCELLED" },
    ];
    expect(openIssueCount(issues)).toBe(0);
  });

  it("returns 0 for empty input", () => {
    expect(openIssueCount([])).toBe(0);
  });

  it("counts all open regardless of case sensitivity (exact match)", () => {
    // Statuses are stored in UPPER_CASE; lowercase should NOT match
    const issues = [{ status: "open" }, { status: "OPEN" }];
    expect(openIssueCount(issues)).toBe(1);
  });
});

// ---------------------------------------------------------------------------
// openIssues
// ---------------------------------------------------------------------------
describe("openIssues", () => {
  it("excludes non-open statuses", () => {
    const issues = [
      { status: "OPEN", severity: "HIGH" },
      { status: "RESOLVED", severity: "CRITICAL" },
      { status: "CLOSED", severity: "LOW" },
    ];
    const result = openIssues(issues);
    expect(result).toHaveLength(1);
    expect(result[0].status).toBe("OPEN");
  });

  it("sorts by severity CRITICAL → HIGH → MEDIUM → LOW", () => {
    const issues = [
      { status: "OPEN", severity: "LOW" },
      { status: "IN_PROGRESS", severity: "CRITICAL" },
      { status: "BLOCKED", severity: "MEDIUM" },
      { status: "OPEN", severity: "HIGH" },
    ];
    const result = openIssues(issues);
    expect(result.map((i) => i.severity)).toEqual(["CRITICAL", "HIGH", "MEDIUM", "LOW"]);
  });

  it("places unknown severity at the end", () => {
    const issues = [
      { status: "OPEN", severity: "UNKNOWN" },
      { status: "OPEN", severity: "CRITICAL" },
    ];
    const result = openIssues(issues);
    expect(result[0].severity).toBe("CRITICAL");
    expect(result[1].severity).toBe("UNKNOWN");
  });

  it("returns empty array for empty input", () => {
    expect(openIssues([])).toEqual([]);
  });

  it("preserves all fields of the generic type", () => {
    const issues = [
      { status: "OPEN", severity: "HIGH", title: "Issue A", id: 1 },
      { status: "RESOLVED", severity: "CRITICAL", title: "Issue B", id: 2 },
    ];
    const result = openIssues(issues);
    expect(result).toHaveLength(1);
    expect(result[0].title).toBe("Issue A");
    expect(result[0].id).toBe(1);
  });
});

// ---------------------------------------------------------------------------
// criticalIssueCount — counts CRITICAL among the SAME open set as the tile value,
// so the "N critical" hint can never undercount the visible drawer list.
// ---------------------------------------------------------------------------
describe("criticalIssueCount", () => {
  it("counts CRITICAL issues across all open statuses incl. BLOCKED", () => {
    const issues = [
      { severity: "CRITICAL", status: "OPEN" },
      { severity: "CRITICAL", status: "IN_PROGRESS" },
      { severity: "CRITICAL", status: "BLOCKED" },
    ];
    expect(criticalIssueCount(issues)).toBe(3);
  });

  it("excludes CRITICAL issues that are resolved/closed/cancelled", () => {
    const issues = [
      { severity: "CRITICAL", status: "RESOLVED" },
      { severity: "CRITICAL", status: "CLOSED" },
      { severity: "CRITICAL", status: "CANCELLED" },
    ];
    expect(criticalIssueCount(issues)).toBe(0);
  });

  it("excludes non-CRITICAL open issues", () => {
    const issues = [
      { severity: "HIGH", status: "OPEN" },
      { severity: "MEDIUM", status: "BLOCKED" },
    ];
    expect(criticalIssueCount(issues)).toBe(0);
  });
});
