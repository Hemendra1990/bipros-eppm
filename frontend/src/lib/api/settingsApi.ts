import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface SettingResponse {
  id: string;
  settingKey: string;
  settingValue: string;
  description?: string;
  category: string;
}

export interface CurrencyResponse {
  id: string;
  code: string;
  name: string;
  symbol: string;
  exchangeRate: number;
  isBaseCurrency: boolean;
  decimalPlaces: number;
}

export const settingsApi = {
  listSettings: () =>
    apiClient.get<ApiResponse<SettingResponse[]>>("/v1/admin/settings").then((r) => r.data),

  updateSetting: (id: string, data: { settingKey: string; settingValue: string; description?: string; category?: string }) =>
    apiClient.put<ApiResponse<SettingResponse>>(`/v1/admin/settings/${id}`, data).then((r) => r.data),

  createSetting: (data: { settingKey: string; settingValue: string; description?: string; category?: string }) =>
    apiClient.post<ApiResponse<SettingResponse>>("/v1/admin/settings", data).then((r) => r.data),

  deleteSetting: (id: string) =>
    apiClient.delete(`/v1/admin/settings/${id}`),

  listCurrencies: () =>
    apiClient.get<ApiResponse<CurrencyResponse[]>>("/v1/admin/currencies").then((r) => r.data),

  createCurrency: (data: { code: string; name: string; symbol: string; exchangeRate: number; isBaseCurrency?: boolean; decimalPlaces?: number }) =>
    apiClient.post<ApiResponse<CurrencyResponse>>("/v1/admin/currencies", data).then((r) => r.data),

  deleteCurrency: (id: string) =>
    apiClient.delete(`/v1/admin/currencies/${id}`),
};
