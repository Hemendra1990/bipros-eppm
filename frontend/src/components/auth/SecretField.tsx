"use client";

import type { ReactNode } from "react";
import { useAuth } from "@/lib/auth/useAuth";

/**
 * Renders {@code children} only when the current user holds one of the supplied roles;
 * otherwise renders the {@code masked} placeholder (an em-dash by default).
 *
 * <p>For sensitive cells (budget, contract value, salary, payment terms). Use this so the UI
 * doesn't render an awkward "null" when the backend strips the field via {@code @JsonView}.
 * The backend remains the source of truth — never assume the value is hidden just because the
 * cell shows the mask.
 *
 * @example
 *   <SecretField visibleTo={["ROLE_FINANCE", "ROLE_ADMIN"]}>
 *     {formatCurrency(contract.contractValue)}
 *   </SecretField>
 */
export function SecretField({
  visibleTo,
  masked = "—",
  children,
}: {
  visibleTo: readonly string[];
  masked?: ReactNode;
  children: ReactNode;
}) {
  const { hasAnyRole } = useAuth();
  return <>{hasAnyRole(visibleTo) ? children : masked}</>;
}
