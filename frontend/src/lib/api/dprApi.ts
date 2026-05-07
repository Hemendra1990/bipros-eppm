import { apiClient } from "./client";
import type { ApiResponse } from "../types";
import type {
  CreateDailyProgressReportRequest,
  DailyProgressReportResponse,
  UpdateDailyProgressReportRequest,
} from "../types/dpr";

// Re-export so existing call sites (`import ... from "@/lib/api/dprApi"`) keep working.
export type {
  CreateDailyProgressReportRequest,
  DailyProgressReportResponse,
  UpdateDailyProgressReportRequest,
} from "../types/dpr";

export interface DprListFilters {
  from?: string;
  to?: string;
  activity?: string;
}

export const dprApi = {
  list: (projectId: string, filters: DprListFilters = {}) => {
    const params = new URLSearchParams();
    if (filters.from) params.set("from", filters.from);
    if (filters.to) params.set("to", filters.to);
    if (filters.activity) params.set("activity", filters.activity);
    const qs = params.toString() ? `?${params.toString()}` : "";
    return apiClient
      .get<ApiResponse<DailyProgressReportResponse[]>>(`/v1/projects/${projectId}/dpr${qs}`)
      .then((r) => r.data);
  },

  get: (projectId: string, id: string) =>
    apiClient
      .get<ApiResponse<DailyProgressReportResponse>>(`/v1/projects/${projectId}/dpr/${id}`)
      .then((r) => r.data),

  create: (projectId: string, request: CreateDailyProgressReportRequest) =>
    apiClient
      .post<ApiResponse<DailyProgressReportResponse>>(`/v1/projects/${projectId}/dpr`, request)
      .then((r) => r.data),

  update: (projectId: string, id: string, request: UpdateDailyProgressReportRequest) =>
    apiClient
      .put<ApiResponse<DailyProgressReportResponse>>(`/v1/projects/${projectId}/dpr/${id}`, request)
      .then((r) => r.data),

  delete: (projectId: string, id: string) =>
    apiClient.delete(`/v1/projects/${projectId}/dpr/${id}`),
};
