import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface DailyWeatherResponse {
  id: string;
  projectId: string;
  logDate: string;
  tempMaxC: number | null;
  tempMinC: number | null;
  rainfallMm: number | null;
  windKmh: number | null;
  weatherCondition: string | null;
  workingHours: number | null;
  remarks: string | null;
}

export interface CreateDailyWeatherRequest {
  logDate: string;
  tempMaxC?: number | null;
  tempMinC?: number | null;
  rainfallMm?: number | null;
  windKmh?: number | null;
  weatherCondition?: string | null;
  workingHours?: number | null;
  remarks?: string | null;
}

export interface WeatherFilters {
  from?: string;
  to?: string;
}

export const dailyWeatherApi = {
  list: (projectId: string, filters: WeatherFilters = {}) => {
    const params = new URLSearchParams();
    if (filters.from) params.set("from", filters.from);
    if (filters.to) params.set("to", filters.to);
    const qs = params.toString() ? `?${params.toString()}` : "";
    return apiClient
      .get<ApiResponse<DailyWeatherResponse[]>>(`/v1/projects/${projectId}/weather${qs}`)
      .then((r) => r.data);
  },

  upsert: (projectId: string, request: CreateDailyWeatherRequest) =>
    apiClient
      .post<ApiResponse<DailyWeatherResponse>>(`/v1/projects/${projectId}/weather`, request)
      .then((r) => r.data),

  delete: (projectId: string, id: string) =>
    apiClient.delete(`/v1/projects/${projectId}/weather/${id}`),
};
