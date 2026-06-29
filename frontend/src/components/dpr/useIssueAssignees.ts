import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { projectTeamApi, type ProjectTeamMember } from "@/lib/api/projectTeamApi";
import { userApi, type UserSummary } from "@/lib/api/userApi";
import type { SelectOption } from "@/components/common/SearchableSelect";

/**
 * System roles whose users can own a field issue — mirrors the Team page picker
 * roster so the assignee dropdown shows the same people the org chart is built from.
 */
const ASSIGNEE_ROLES = [
  "ADMIN",
  "PROJECT_MANAGER",
  "SITE_MANAGER",
  "SUPERVISOR",
  "FOREMAN",
  "SITE_ENGINEER",
  "QUANTITY_SURVEYOR",
  "SAFETY_OFFICER",
  "ENGINEER",
  "PLANNER",
];

/**
 * Resolve a human display name for a team member. `projectTeamApi` only projects
 * name fields on a best-effort basis, so when they're absent we fall back to the
 * user roster (`userApi.listByRoles`), then username, then a short id — never an
 * empty cell or a raw UUID.
 */
export function memberDisplayName(
  m: ProjectTeamMember,
  usersById?: Map<string, UserSummary>,
): string {
  const full = [m.firstName, m.lastName].filter(Boolean).join(" ").trim();
  if (full) return full;
  const rosterName = usersById?.get(m.userId)?.name?.trim();
  if (rosterName) return rosterName;
  if (m.username) return m.username;
  return `${m.userId.slice(0, 8)}…`;
}

/** Picker option: value = userId, label = resolved name only (no role suffix). */
export function assigneeOption(
  m: ProjectTeamMember,
  usersById?: Map<string, UserSummary>,
): SelectOption {
  return { value: m.userId, label: memberDisplayName(m, usersById) };
}

/**
 * One project-team fetch + one user-roster fetch, combined so a member's name
 * resolves even when the team API omits it. Feeds both the Assigned-To picker
 * (`options`) and the status-history timeline (`nameByUserId`). Members holding
 * multiple roles are de-duped by `userId` (first occurrence wins).
 */
export function useIssueAssignees(projectId: string) {
  const { data: teamData, isLoading: teamLoading } = useQuery({
    queryKey: ["project-team", projectId],
    queryFn: () => projectTeamApi.list(projectId),
    enabled: !!projectId,
  });

  const { data: roster, isLoading: rosterLoading } = useQuery({
    queryKey: ["users", "by-roles", ASSIGNEE_ROLES],
    queryFn: () => userApi.listByRoles(ASSIGNEE_ROLES),
  });

  const members = useMemo(() => teamData?.data ?? [], [teamData]);

  const usersById = useMemo(() => {
    const map = new Map<string, UserSummary>();
    for (const u of roster ?? []) map.set(u.id, u);
    return map;
  }, [roster]);

  const options = useMemo(() => {
    const seen = new Set<string>();
    const out: SelectOption[] = [];
    for (const m of members) {
      if (seen.has(m.userId)) continue;
      seen.add(m.userId);
      out.push(assigneeOption(m, usersById));
    }
    return out;
  }, [members, usersById]);

  const nameByUserId = useMemo(() => {
    const map = new Map<string, string>();
    for (const m of members) map.set(m.userId, memberDisplayName(m, usersById));
    return map;
  }, [members, usersById]);

  return { options, nameByUserId, isLoading: teamLoading || rosterLoading };
}
