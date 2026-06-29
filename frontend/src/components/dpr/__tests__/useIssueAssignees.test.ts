import { describe, it, expect } from "vitest";
import { memberDisplayName, assigneeOption } from "../useIssueAssignees";
import type { ProjectTeamMember } from "@/lib/api/projectTeamApi";
import type { UserSummary } from "@/lib/api/userApi";

function m(over: Partial<ProjectTeamMember>): ProjectTeamMember {
  return {
    id: "mem1", projectId: "p1", userId: "u1", role: "ENGINEER",
    reportsToUserId: null, activeFrom: null, activeTo: null,
    createdAt: null, updatedAt: null, ...over,
  };
}

function roster(entries: Array<Partial<UserSummary> & { id: string }>): Map<string, UserSummary> {
  const map = new Map<string, UserSummary>();
  for (const e of entries) {
    map.set(e.id, {
      id: e.id,
      username: e.username ?? "user",
      name: e.name ?? "",
      email: e.email ?? "",
      employeeCode: e.employeeCode ?? null,
    });
  }
  return map;
}

describe("memberDisplayName", () => {
  it("prefers first + last name", () => {
    expect(memberDisplayName(m({ firstName: "Sara", lastName: "Khan" }))).toBe("Sara Khan");
  });
  it("falls back to the user roster name when team fields are absent", () => {
    const r = roster([{ id: "u1", name: "Vijay Kumar", username: "vijaykumar" }]);
    expect(
      memberDisplayName(m({ firstName: null, lastName: null, username: null }), r),
    ).toBe("Vijay Kumar");
  });
  it("falls back to username when no roster name", () => {
    expect(
      memberDisplayName(m({ firstName: null, lastName: null, username: "skhan" })),
    ).toBe("skhan");
  });
  it("falls back to a short userId, never a raw full UUID", () => {
    const out = memberDisplayName(
      m({ firstName: null, lastName: null, username: null, userId: "7652c2b0-3c04-471a-9d5d-453753d083d6" }),
    );
    expect(out).toBe("7652c2b0…");
  });
});

describe("assigneeOption", () => {
  it("value is userId, label is name only (no role suffix)", () => {
    const o = assigneeOption(m({ firstName: "Sara", lastName: "Khan", role: "SUPERVISOR" }));
    expect(o.value).toBe("u1");
    expect(o.label).toBe("Sara Khan");
  });
  it("uses the roster name in the label when team fields are absent", () => {
    const r = roster([{ id: "u1", name: "Vijay Kumar" }]);
    const o = assigneeOption(m({ firstName: null, lastName: null, username: null }), r);
    expect(o.label).toBe("Vijay Kumar");
  });
});
