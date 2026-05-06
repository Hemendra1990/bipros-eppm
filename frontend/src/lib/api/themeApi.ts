import { apiClient } from "./client";
import type { ApiResponse } from "../types";
import type { SettingResponse } from "./settingsApi";
import type { ThemeDefinition } from "../themes/definitions";

export const themeApi = {
  getActiveThemeId: () =>
    apiClient
      .get<ApiResponse<SettingResponse>>("/v1/admin/settings/key/ui.active_theme")
      .then((r) => r.data)
      .catch(() =>
        apiClient
          .get<ApiResponse<SettingResponse[]>>("/v1/admin/settings")
          .then((r) => {
            const found = r.data.data?.find((s) => s.settingKey === "ui.active_theme");
            if (found) {
              return { data: found, error: null, meta: r.data.meta } as ApiResponse<SettingResponse>;
            }
            throw new Error("ui.active_theme not found");
          })
      ),

  setActiveThemeId: (themeId: string, existingId?: string) => {
    const payload = {
      settingKey: "ui.active_theme",
      settingValue: themeId,
      category: "THEME",
    };
    if (existingId) {
      return apiClient
        .put<ApiResponse<SettingResponse>>(`/v1/admin/settings/${existingId}`, payload)
        .then((r) => r.data);
    }
    return apiClient
      .post<ApiResponse<SettingResponse>>("/v1/admin/settings", payload)
      .then((r) => r.data);
  },

  getCustomThemes: () =>
    apiClient
      .get<ApiResponse<SettingResponse>>("/v1/admin/settings/key/ui.custom_themes")
      .then((r) => r.data)
      .catch(() =>
        apiClient
          .get<ApiResponse<SettingResponse[]>>("/v1/admin/settings")
          .then((r) => {
            const found = r.data.data?.find((s) => s.settingKey === "ui.custom_themes");
            if (found) {
              return { data: found, error: null, meta: r.data.meta } as ApiResponse<SettingResponse>;
            }
            throw new Error("ui.custom_themes not found");
          })
      ),

  saveCustomThemes: (themes: ThemeDefinition[], existingId?: string) => {
    const payload = {
      settingKey: "ui.custom_themes",
      settingValue: JSON.stringify(themes),
      category: "THEME",
    };
    if (existingId) {
      return apiClient
        .put<ApiResponse<SettingResponse>>(`/v1/admin/settings/${existingId}`, payload)
        .then((r) => r.data);
    }
    return apiClient
      .post<ApiResponse<SettingResponse>>("/v1/admin/settings", payload)
      .then((r) => r.data);
  },
};
