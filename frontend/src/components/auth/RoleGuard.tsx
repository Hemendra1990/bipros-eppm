"use client";

import type { ReactNode } from "react";
import { useAuth } from "@/lib/auth/useAuth";

/**
 * Renders {@code children} only when the current user has at least one of the supplied roles.
 * ROLE_ prefix optional — handled by the underlying check.
 *
 * <p>This is a UX gate, not a security gate. The backend MUST also reject the request — never
 * rely on {@code <RoleGuard>} alone to keep an unauthorised user out of sensitive data.
 *
 * @example
 *   <RoleGuard roles={["ROLE_FINANCE", "ROLE_ADMIN"]} fallback={<NotAuthorisedHint />}>
 *     <BudgetEditor />
 *   </RoleGuard>
 */
export function RoleGuard({
  roles,
  fallback = null,
  children,
}: {
  roles: readonly string[];
  fallback?: ReactNode;
  children: ReactNode;
}) {
  const { hasAnyRole } = useAuth();
  return <>{hasAnyRole(roles) ? children : fallback}</>;
}
