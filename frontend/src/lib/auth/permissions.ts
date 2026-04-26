import type { IcpmsModule, ModuleAccessLevel, UserResponse } from "../types";
import type { UserAccessApiResponse } from "../api/userApi";

/**
 * Pure permission helpers — usable from React components, axios interceptors, server
 * components, anywhere. Backend is the source of truth for security; these are UX helpers
 * that decide what to render. Never gate writes purely on the client side.
 */

const ROLE_PREFIX = "ROLE_";

const norm = (r: string) => (r.startsWith(ROLE_PREFIX) ? r : `${ROLE_PREFIX}${r}`);

const ACCESS_LEVEL_RANK: Record<ModuleAccessLevel, number> = {
  NONE: 0,
  VIEW: 1,
  EDIT: 2,
  CERTIFY: 3,
  APPROVE: 4,
  FULL: 5,
};

export function hasRole(user: UserResponse | null, role: string): boolean {
  if (!user?.roles) return false;
  const target = norm(role);
  return user.roles.some((r) => norm(r) === target);
}

export function hasAnyRole(user: UserResponse | null, roles: readonly string[]): boolean {
  if (!user?.roles?.length) return false;
  const targets = new Set(roles.map(norm));
  return user.roles.some((r) => targets.has(norm(r)));
}

export function isAdmin(user: UserResponse | null): boolean {
  return hasRole(user, "ADMIN");
}

/**
 * Module-access check. ADMIN short-circuits to true. Otherwise we look up the user's level
 * for the requested module and require it to be ≥ the level the caller asked for.
 */
export function canAccessModule(
  user: UserResponse | null,
  access: UserAccessApiResponse | null,
  module: IcpmsModule,
  level: ModuleAccessLevel = "VIEW",
): boolean {
  if (!user) return false;
  if (isAdmin(user)) return true;
  if (!access) return false;
  const userLevel = access.moduleAccess[module];
  if (!userLevel || userLevel === "NONE") return false;
  return ACCESS_LEVEL_RANK[userLevel] >= ACCESS_LEVEL_RANK[level];
}

export function canAccessCorridor(
  user: UserResponse | null,
  access: UserAccessApiResponse | null,
  wbsNodeId: string,
): boolean {
  if (!user) return false;
  if (isAdmin(user)) return true;
  if (!access) return false;
  return access.allCorridors || access.corridorScopes.includes(wbsNodeId);
}
