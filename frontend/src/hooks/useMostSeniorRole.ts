import { useAuthStore } from "@/lib/state/store";

const ROLE_HIERARCHY = [
  "ROLE_ADMIN",
  "ROLE_EXECUTIVE",
  "ROLE_PMO",
  "ROLE_FINANCE",
  "ROLE_PROJECT_MANAGER",
  "ROLE_HSE_OFFICER",
  "ROLE_SITE_ENGINEER",
  "ROLE_FOREMAN",
  "ROLE_SCHEDULER",
  "ROLE_RESOURCE_MANAGER",
  "ROLE_TEAM_MEMBER",
  "ROLE_CLIENT",
  "ROLE_VIEWER",
] as const;

export type SeniorRole = (typeof ROLE_HIERARCHY)[number] | string | null;

const norm = (r: string) => (r.startsWith("ROLE_") ? r : `ROLE_${r}`);

function toLabel(role: string | null): string {
  if (!role) return "User";
  const stripped = role.replace(/^ROLE_/, "").replace(/_/g, " ").toLowerCase();
  return stripped.replace(/\b\w/g, (c) => c.toUpperCase());
}

export interface MostSeniorRole {
  role: SeniorRole;
  label: string;
  raw: string[];
}

export function useMostSeniorRole(): MostSeniorRole {
  const user = useAuthStore((s) => s.user);
  const raw = (user?.roles ?? []).map(norm);
  const role = ROLE_HIERARCHY.find((r) => raw.includes(r)) ?? raw[0] ?? null;
  return { role, label: toLabel(role), raw };
}
