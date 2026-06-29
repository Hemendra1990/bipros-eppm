import { describe, it, expect } from "vitest";
import { statusLabel, STATUS_OPTIONS } from "../IssueBadges";

describe("statusLabel", () => {
  it("renders BLOCKED as 'On Hold'", () => {
    expect(statusLabel("BLOCKED")).toBe("On Hold");
  });
  it("renders IN_PROGRESS as 'In progress'", () => {
    expect(statusLabel("IN_PROGRESS")).toBe("In progress");
  });
  it("falls back to '—' for null", () => {
    expect(statusLabel(null)).toBe("—");
  });
  it("STATUS_OPTIONS has no 'Blocked' label", () => {
    expect(STATUS_OPTIONS.find((o) => o.label === "Blocked")).toBeUndefined();
    expect(STATUS_OPTIONS.find((o) => o.value === "BLOCKED")?.label).toBe("On Hold");
  });
});
