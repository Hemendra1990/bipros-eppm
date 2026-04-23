import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface DailyProgressReportResponse {
  id: string;
  projectId: string;
  reportDate: string;
  supervisorName: string;
  chainageFromM: number | null;
  chainageToM: number | null;
  activityName: string;
  wbsNodeId: string | null;
  boqItemNo: string | null;
  unit: string;
  qtyExecuted: number;
  cumulativeQty: number | null;
  weatherCondition: string | null;
  remarks: string | null;
}

export interface CreateDailyProgressReportRequest {
  reportDate: string;
  supervisorName: string;
  chainageFromM?: number | null;
  chainageToM?: number | null;
  activityName: string;
  wbsNodeId?: string | null;
  boqItemNo?: string | null;
  unit: string;
  qtyExecuted: number;
  weatherCondition?: string | null;
  remarks?: string | null;
}

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

  delete: (projectId: string, id: string) =>
    apiClient.delete(`/v1/projects/${projectId}/dpr/${id}`),
};
