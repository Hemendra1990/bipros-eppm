"use client";

import { type ReactNode, useEffect } from "react";
import { useAuthStore } from "../state/store";
import { useAccessStore } from "./useAccess";

/**
 * Pre-loads the current user's module-access matrix once after login. Render anywhere inside
 * the authenticated route group (e.g. {@code app/(app)/layout.tsx}) so children see populated
 * permissions without each one having to call {@code useAccess()} eagerly.
 *
 * <p>Also clears the cached access matrix when the user logs out, so the next user doesn't
 * inherit the previous user's permissions from {@code localStorage}.
 */
export function AccessProvider({ children }: { children: ReactNode }) {
  const userId = useAuthStore((s) => s.user?.id);
  const accessToken = useAuthStore((s) => s.accessToken);
  const { load, clear, loadedForUserId } = useAccessStore();

  useEffect(() => {
    if (userId) {
      void load(userId);
    } else {
      clear();
    }
  }, [userId, accessToken, load, clear]);

  // Clear stale access if the persisted matrix belongs to a different user.
  useEffect(() => {
    if (userId && loadedForUserId && loadedForUserId !== userId) {
      void load(userId, true);
    }
  }, [userId, loadedForUserId, load]);

  return <>{children}</>;
}
