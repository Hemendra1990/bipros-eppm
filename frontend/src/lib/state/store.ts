import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { UserResponse } from "../types";

interface AuthState {
  user: UserResponse | null;
  accessToken: string | null;
  refreshToken: string | null;
  setAuth: (user: UserResponse, accessToken: string, refreshToken: string) => void;
  clearAuth: () => void;
  isAuthenticated: () => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      accessToken: null,
      refreshToken: null,
      setAuth: (user, accessToken, refreshToken) => {
        localStorage.setItem("access_token", accessToken);
        localStorage.setItem("refresh_token", refreshToken);
        set({ user, accessToken, refreshToken });
      },
      clearAuth: () => {
        localStorage.removeItem("access_token");
        localStorage.removeItem("refresh_token");
        set({ user: null, accessToken: null, refreshToken: null });
      },
      isAuthenticated: () => get().accessToken !== null,
    }),
    { name: "bipros-auth" }
  )
);

interface AppState {
  currentProjectId: string | null;
  sidebarCollapsed: boolean;
  setCurrentProjectId: (id: string | null) => void;
  toggleSidebar: () => void;
}

export const useAppStore = create<AppState>()(
  persist(
    (set) => ({
      currentProjectId: null,
      sidebarCollapsed: false,
      setCurrentProjectId: (id) => set({ currentProjectId: id }),
      toggleSidebar: () => set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),
    }),
    { name: "bipros-app" }
  )
);

interface AiState {
  open: boolean;
  currentConversationId: string | null;
  draft: string;
  toggle: () => void;
  setOpen: (v: boolean) => void;
  setConversationId: (id: string | null) => void;
  setDraft: (text: string) => void;
}

export const useAiStore = create<AiState>()(
  persist(
    (set) => ({
      open: false,
      currentConversationId: null,
      draft: "",
      toggle: () => set((s) => ({ open: !s.open })),
      setOpen: (v) => set({ open: v }),
      setConversationId: (id) => set({ currentConversationId: id }),
      setDraft: (text) => set({ draft: text }),
    }),
    { name: "bipros-ai" }
  )
);
