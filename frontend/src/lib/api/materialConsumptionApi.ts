import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface MaterialConsumptionLogResponse {
  id: string;
  projectId: string;
  logDate: string;
  resourceId: string | null;
  materialName: string;
  unit: string;
  openingStock: number;
  received: number;
  consumed: number;
  closingStock: number;
  wastagePercent: number | null;
  issuedBy: string | null;
  receivedBy: string | null;
  wbsNodeId: string | null;
  remarks: string | null;
}

export interface CreateMaterialConsumptionLogRequest {
  logDate: string;
  resourceId?: string | null;
  materialName: string;
  unit: string;
  openingStock: number;
  received: number;
  consumed: number;
  wastagePercent?: number | null;
  issuedBy?: string | null;
  receivedBy?: string | null;
  wbsNodeId?: string | null;
  remarks?: string | null;
}

export interface MaterialConsumptionFilters {
  from?: string;
  to?: string;
}

export const materialConsumptionApi = {
  list: (projectId: string, filters: MaterialConsumptionFilters = {}) => {
    const params = new URLSearchParams();
    if (filters.from) params.set("from", filters.from);
    if (filters.to) params.set("to", filters.to);
    const qs = params.toString() ? `?${params.toString()}` : "";
    return apiClient
      .get<ApiResponse<MaterialConsumptionLogResponse[]>>(`/v1/projects/${projectId}/material-consumption${qs}`)
      .then((r) => r.data);
  },

  create: (projectId: string, request: CreateMaterialConsumptionLogRequest) =>
    apiClient
      .post<ApiResponse<MaterialConsumptionLogResponse>>(
        `/v1/projects/${projectId}/material-consumption`,
        request,
      )
      .then((r) => r.data),

  delete: (projectId: string, id: string) =>
    apiClient.delete(`/v1/projects/${projectId}/material-consumption/${id}`),
};
