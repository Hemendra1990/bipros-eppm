import { apiClient } from "./client";
import type { ApiResponse, PagedResponse } from "../types";

export interface RiskResponse {
  id: string;
  code: string;
  title: string;
  description: string;
  category: string;
  probability: number;
  impact: number;
  score: number;
  status: "OPEN" | "MITIGATED" | "CLOSED";
  owner: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRiskRequest {
  code: string;
  title: string;
  description?: string;
  category: string;
  probability: number;
  impact: number;
  owner?: string;
}

export interface UpdateRiskRequest {
  title?: string;
  description?: string;
  probability?: number;
  impact?: number;
  status?: string;
  owner?: string;
}

export const riskApi = {
  listRisks: (projectId?: string, page = 0, size = 20) => {
    const url = projectId ? `/v1/projects/${projectId}/risks` : "/v1/projects/risks";
    return apiClient
      .get<ApiResponse<RiskResponse[]>>(url, { params: { page, size } })
      .then((r) => r.data);
  },

  getRisk: (id: string) =>
    apiClient.get<ApiResponse<RiskResponse>>(`/v1/risks/${id}`).then((r) => r.data),

  createRisk: (data: CreateRiskRequest) =>
    apiClient.post<ApiResponse<RiskResponse>>("/v1/risks", data).then((r) => r.data),

  updateRisk: (id: string, data: UpdateRiskRequest) =>
    apiClient.put<ApiResponse<RiskResponse>>(`/v1/risks/${id}`, data).then((r) => r.data),

  deleteRisk: (id: string) => apiClient.delete(`/v1/risks/${id}`),

  getRisksByProject: (projectId: string, page = 0, size = 20) =>
    apiClient
      .get<ApiResponse<PagedResponse<RiskResponse>>>(`/v1/projects/${projectId}/risks`, {
        params: { page, size },
      })
      .then((r) => r.data),
};
