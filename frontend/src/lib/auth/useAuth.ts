import { useAuthStore } from "../state/store";
import { hasAnyRole, hasRole, isAdmin } from "./permissions";

/**
 * Thin React hook over the existing Zustand auth store. Exposes the current user, roles, and
 * three small role helpers so call sites don't have to re-import {@link permissions} util.
 */
export function useAuth() {
  const { user, accessToken, clearAuth } = useAuthStore();
  const roles = user?.roles ?? [];
  return {
    user,
    accessToken,
    roles,
    isAuthenticated: accessToken !== null,
    isAdmin: isAdmin(user),
    hasRole: (r: string) => hasRole(user, r),
    hasAnyRole: (rs: readonly string[]) => hasAnyRole(user, rs),
    logout: clearAuth,
  };
}
