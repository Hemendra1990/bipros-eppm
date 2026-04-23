import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface NextDayPlanResponse {
  id: string;
  projectId: string;
  reportDate: string;
  nextDayActivity: string;
  chainageFromM: number | null;
  chainageToM: number | null;
  targetQty: number | null;
  unit: string | null;
  concerns: string | null;
  actionBy: string | null;
  dueDate: string | null;
}

export interface CreateNextDayPlanRequest {
  reportDate: string;
  nextDayActivity: string;
  chainageFromM?: number | null;
  chainageToM?: number | null;
  targetQty?: number | null;
  unit?: string | null;
  concerns?: string | null;
  actionBy?: string | null;
  dueDate?: string | null;
}

export interface NextDayPlanFilters {
  from?: string;
  to?: string;
}

export const nextDayPlanApi = {
  list: (projectId: string, filters: NextDayPlanFilters = {}) => {
    const params = new URLSearchParams();
    if (filters.from) params.set("from", filters.from);
    if (filters.to) params.set("to", filters.to);
    const qs = params.toString() ? `?${params.toString()}` : "";
    return apiClient
      .get<ApiResponse<NextDayPlanResponse[]>>(`/v1/projects/${projectId}/next-day-plan${qs}`)
      .then((r) => r.data);
  },

  create: (projectId: string, request: CreateNextDayPlanRequest) =>
    apiClient
      .post<ApiResponse<NextDayPlanResponse>>(`/v1/projects/${projectId}/next-day-plan`, request)
      .then((r) => r.data),

  delete: (projectId: string, id: string) =>
    apiClient.delete(`/v1/projects/${projectId}/next-day-plan/${id}`),
};
