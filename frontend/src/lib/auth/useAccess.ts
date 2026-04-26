"use client";

import { create } from "zustand";
import { persist } from "zustand/middleware";
import { useEffect } from "react";
import type { IcpmsModule, ModuleAccessLevel } from "../types";
import { userApi, type UserAccessApiResponse } from "../api/userApi";
import { useAuthStore } from "../state/store";
import { canAccessCorridor, canAccessModule } from "./permissions";

/**
 * Cached module-access matrix for the current user. Loaded once after login and refreshed
 * on demand. The store survives page refreshes via Zustand persist (see {@code "bipros-access"}).
 */
interface AccessState {
  access: UserAccessApiResponse | null;
  loading: boolean;
  loadedForUserId: string | null;
  load: (userId: string, force?: boolean) => Promise<void>;
  clear: () => void;
}

export const useAccessStore = create<AccessState>()(
  persist(
    (set, get) => ({
      access: null,
      loading: false,
      loadedForUserId: null,
      load: async (userId, force = false) => {
        const state = get();
        if (state.loading) return;
        if (!force && state.loadedForUserId === userId && state.access) return;
        set({ loading: true });
        try {
          const res = await userApi.getAccess(userId);
          set({ access: res.data ?? null, loadedForUserId: userId, loading: false });
        } catch {
          // 403/404 here means the user has no module access matrix yet — treat as no access,
          // not as a hard error. Components fall back to role-only checks.
          set({ access: null, loadedForUserId: userId, loading: false });
        }
      },
      clear: () => set({ access: null, loadedForUserId: null, loading: false }),
    }),
    { name: "bipros-access" },
  ),
);

/**
 * Loads the access matrix for the currently authenticated user (idempotent) and returns
 * helpers that read from it.
 */
export function useAccess() {
  const { user } = useAuthStore();
  const { access, loading, load } = useAccessStore();

  useEffect(() => {
    if (user?.id) {
      void load(user.id);
    }
  }, [user?.id, load]);

  return {
    access,
    loading,
    canAccessModule: (m: IcpmsModule, level: ModuleAccessLevel = "VIEW") =>
      canAccessModule(user, access, m, level),
    canAccessCorridor: (wbsNodeId: string) => canAccessCorridor(user, access, wbsNodeId),
  };
}
