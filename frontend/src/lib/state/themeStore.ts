import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { ThemeDefinition } from "../themes/definitions";

interface ThemeState {
  activeThemeId: string;
  customThemes: ThemeDefinition[];
  activeSettingRecordId: string | null;
  customSettingRecordId: string | null;

  setActiveThemeId: (id: string) => void;
  setCustomThemes: (themes: ThemeDefinition[]) => void;
  setSettingRecordIds: (activeId: string | null, customId: string | null) => void;
  addOrUpdateCustomTheme: (theme: ThemeDefinition) => void;
  deleteCustomTheme: (id: string) => void;
  clearBackendIds: () => void;
}

export const useThemeStore = create<ThemeState>()(
  persist(
    (set) => ({
      activeThemeId: "classic-gold",
      customThemes: [],
      activeSettingRecordId: null,
      customSettingRecordId: null,

      setActiveThemeId: (id) => set({ activeThemeId: id }),
      setCustomThemes: (themes) => set({ customThemes: themes }),
      setSettingRecordIds: (activeId, customId) =>
        set({ activeSettingRecordId: activeId, customSettingRecordId: customId }),
      addOrUpdateCustomTheme: (theme) =>
        set((state) => {
          const exists = state.customThemes.find((t) => t.id === theme.id);
          if (exists) {
            return {
              customThemes: state.customThemes.map((t) =>
                t.id === theme.id ? theme : t
              ),
            };
          }
          return { customThemes: [...state.customThemes, theme] };
        }),
      deleteCustomTheme: (id) =>
        set((state) => ({
          customThemes: state.customThemes.filter((t) => t.id !== id),
        })),
      clearBackendIds: () =>
        set({ activeSettingRecordId: null, customSettingRecordId: null }),
    }),
    {
      name: "bipros-theme",
      partialize: (state) => ({
        activeThemeId: state.activeThemeId,
        customThemes: state.customThemes,
        activeSettingRecordId: state.activeSettingRecordId,
        customSettingRecordId: state.customSettingRecordId,
      }),
    }
  )
);
