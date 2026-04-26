"use client";

import type { ReactNode } from "react";
import type { IcpmsModule, ModuleAccessLevel } from "@/lib/types";
import { useAccess } from "@/lib/auth/useAccess";

/**
 * Renders {@code children} only when the current user has at least the requested access level
 * for the given IC-PMS module. Backed by the {@code /v1/users/{id}/access} matrix loaded by
 * {@link AccessProvider}.
 *
 * <p>UX gate only — backend enforces the same check via {@code @moduleAccess.check(...)}.
 *
 * @example
 *   <IfCanAccess module="M5_CONTRACTS" level="EDIT">
 *     <button>New Contract</button>
 *   </IfCanAccess>
 */
export function IfCanAccess({
  module,
  level = "VIEW",
  fallback = null,
  children,
}: {
  module: IcpmsModule;
  level?: ModuleAccessLevel;
  fallback?: ReactNode;
  children: ReactNode;
}) {
  const { canAccessModule } = useAccess();
  return <>{canAccessModule(module, level) ? children : fallback}</>;
}
