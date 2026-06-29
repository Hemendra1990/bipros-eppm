import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  projectTeamApi,
  PROJECT_TEAM_ROLE_LABELS,
  type ProjectTeamMember,
} from "@/lib/api/projectTeamApi";
import type { SelectOption } from "@/components/common/SearchableSelect";

/** "First Last" || username || userId. */
export function memberDisplayName(m: ProjectTeamMember): string {
  const full = [m.firstName, m.lastName].filter(Boolean).join(" ").trim();
  return full || m.username || m.userId;
}

/** SearchableSelect option: value = userId, label = "<name> · <RoleLabel>". */
export function assigneeOption(m: ProjectTeamMember): SelectOption {
  return {
    value: m.userId,
    label: `${memberDisplayName(m)} · ${PROJECT_TEAM_ROLE_LABELS[m.role]}`,
  };
}

/**
 * One project-team fetch feeding both the Assigned-To picker (options) and the
 * status-history timeline (nameByUserId). De-dupes members that hold multiple roles
 * by userId, keeping the first occurrence.
 */
export function useIssueAssignees(projectId: string) {
  const { data, isLoading } = useQuery({
    queryKey: ["project-team", projectId],
    queryFn: () => projectTeamApi.list(projectId),
    enabled: !!projectId,
  });

  const members = useMemo(() => data?.data ?? [], [data]);

  const options = useMemo(() => {
    const seen = new Set<string>();
    const out: SelectOption[] = [];
    for (const m of members) {
      if (seen.has(m.userId)) continue;
      seen.add(m.userId);
      out.push(assigneeOption(m));
    }
    return out;
  }, [members]);

  const nameByUserId = useMemo(() => {
    const map = new Map<string, string>();
    for (const m of members) map.set(m.userId, memberDisplayName(m));
    return map;
  }, [members]);

  return { options, nameByUserId, isLoading };
}
