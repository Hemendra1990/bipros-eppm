import { describe, it, expect } from "vitest";
import { memberDisplayName, assigneeOption } from "../useIssueAssignees";
import type { ProjectTeamMember } from "@/lib/api/projectTeamApi";

function m(over: Partial<ProjectTeamMember>): ProjectTeamMember {
  return {
    id: "mem1", projectId: "p1", userId: "u1", role: "ENGINEER",
    reportsToUserId: null, activeFrom: null, activeTo: null,
    createdAt: null, updatedAt: null, ...over,
  };
}

describe("memberDisplayName", () => {
  it("prefers first + last name", () => {
    expect(memberDisplayName(m({ firstName: "Sara", lastName: "Khan" }))).toBe("Sara Khan");
  });
  it("falls back to username", () => {
    expect(memberDisplayName(m({ firstName: null, lastName: null, username: "skhan" }))).toBe("skhan");
  });
  it("falls back to userId", () => {
    expect(memberDisplayName(m({ firstName: null, lastName: null, username: null, userId: "u9" }))).toBe("u9");
  });
});

describe("assigneeOption", () => {
  it("value is userId, label has role", () => {
    const o = assigneeOption(m({ firstName: "Sara", lastName: "Khan", role: "SUPERVISOR" }));
    expect(o.value).toBe("u1");
    expect(o.label).toBe("Sara Khan · Supervisor");
  });
});
