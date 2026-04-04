import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface SettingResponse {
  key: string;
  value: string;
  description?: string;
  category: string;
}

export interface SettingsResponse {
  settings: SettingResponse[];
}

export const settingsApi = {
  listSettings: () =>
    apiClient.get<ApiResponse<SettingsResponse>>("/v1/settings").then((r) => r.data),

  getSetting: (key: string) =>
    apiClient.get<ApiResponse<SettingResponse>>(`/v1/settings/${key}`).then((r) => r.data),

  updateSetting: (key: string, value: string) =>
    apiClient
      .put<ApiResponse<SettingResponse>>(`/v1/settings/${key}`, { value })
      .then((r) => r.data),

  bulkUpdateSettings: (settings: Array<{ key: string; value: string }>) =>
    apiClient.put<ApiResponse<SettingsResponse>>("/v1/settings/bulk", { settings }).then((r) => r.data),
};
