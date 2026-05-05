import { describe, expect, it } from "vitest";
import { schedulePercentComplete, scheduleVarianceBucket } from "./schedulePercent";

describe("schedulePercentComplete", () => {
  it("returns null when any input is missing", () => {
    expect(schedulePercentComplete(null, "2024-12-10", "2024-12-05")).toBeNull();
    expect(schedulePercentComplete("2024-12-01", null, "2024-12-05")).toBeNull();
    expect(schedulePercentComplete("2024-12-01", "2024-12-10", null)).toBeNull();
    expect(schedulePercentComplete(undefined, undefined, undefined)).toBeNull();
    expect(schedulePercentComplete("", "2024-12-10", "2024-12-05")).toBeNull();
  });

  it("returns 0 when data date is before the start", () => {
    expect(schedulePercentComplete("2024-12-01", "2024-12-11", "2024-11-15")).toBe(0);
  });

  it("returns 0 when data date equals the start", () => {
    expect(schedulePercentComplete("2024-12-01", "2024-12-11", "2024-12-01")).toBe(0);
  });

  it("returns 100 when data date is after the finish", () => {
    expect(schedulePercentComplete("2024-12-01", "2024-12-11", "2025-01-01")).toBe(100);
  });

  it("returns 100 when data date equals the finish", () => {
    expect(schedulePercentComplete("2024-12-01", "2024-12-11", "2024-12-11")).toBe(100);
  });

  it("computes mid-window percentage rounded to nearest integer", () => {
    // 10-day window, 5 days elapsed → 50
    expect(schedulePercentComplete("2024-12-01", "2024-12-11", "2024-12-06")).toBe(50);
    // 10-day window, 1 day elapsed → 10
    expect(schedulePercentComplete("2024-12-01", "2024-12-11", "2024-12-02")).toBe(10);
    // 4-day window, 1 day elapsed → 25
    expect(schedulePercentComplete("2024-12-01", "2024-12-05", "2024-12-02")).toBe(25);
  });

  it("accepts Date objects as well as ISO strings", () => {
    const start = new Date("2024-12-01T00:00:00");
    const finish = new Date("2024-12-11T00:00:00");
    const dataDate = new Date("2024-12-06T00:00:00");
    expect(schedulePercentComplete(start, finish, dataDate)).toBe(50);
  });

  it("treats a zero-duration window as instantaneous (0 before, 100 on/after)", () => {
    expect(schedulePercentComplete("2024-12-05", "2024-12-05", "2024-12-04")).toBe(0);
    expect(schedulePercentComplete("2024-12-05", "2024-12-05", "2024-12-05")).toBe(100);
    expect(schedulePercentComplete("2024-12-05", "2024-12-05", "2024-12-06")).toBe(100);
  });

  it("returns null for unparseable date strings", () => {
    expect(schedulePercentComplete("not-a-date", "2024-12-11", "2024-12-05")).toBeNull();
    expect(schedulePercentComplete("2024-12-01", "also-not-a-date", "2024-12-05")).toBeNull();
  });
});

describe("scheduleVarianceBucket", () => {
  it("returns 'unknown' when either input is null/undefined", () => {
    expect(scheduleVarianceBucket(null, 50)).toBe("unknown");
    expect(scheduleVarianceBucket(50, null)).toBe("unknown");
    expect(scheduleVarianceBucket(50, undefined)).toBe("unknown");
  });

  it("returns 'ahead' when activity % exceeds schedule % by more than 5", () => {
    expect(scheduleVarianceBucket(50, 56)).toBe("ahead");
    expect(scheduleVarianceBucket(0, 100)).toBe("ahead");
  });

  it("returns 'behind' when activity % trails schedule % by more than 5", () => {
    expect(scheduleVarianceBucket(50, 44)).toBe("behind");
    expect(scheduleVarianceBucket(80, 0)).toBe("behind");
  });

  it("returns 'on-track' for deltas within ±5", () => {
    expect(scheduleVarianceBucket(50, 50)).toBe("on-track");
    expect(scheduleVarianceBucket(50, 55)).toBe("on-track");
    expect(scheduleVarianceBucket(50, 45)).toBe("on-track");
  });
});
