import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export type RiskRag = "CRIMSON" | "RED" | "AMBER" | "GREEN" | "OPPORTUNITY";

export type RiskTrend = "WORSENING" | "STABLE" | "IMPROVING";

export type RiskStatus =
  | "OPEN"
  | "OPEN_ESCALATED"
  | "OPEN_UNDER_ACTIVE_MANAGEMENT"
  | "OPEN_BEING_MANAGED"
  | "OPEN_MONITOR"
  | "OPEN_WATCH"
  | "OPEN_TARGET"
  | "OPEN_ASI_REVIEW"
  | "MITIGATED"
  | "CLOSED"
  | "REALISED"
  | "REALISED_PARTIALLY";

export type RiskCategory =
  | "TECHNICAL"
  | "COMMERCIAL"
  | "ENVIRONMENTAL"
  | "REGULATORY"
  | "FINANCIAL"
  | "SCHEDULE"
  | "SAFETY"
  | "POLITICAL"
  | "SOCIAL"
  | "LAND_ACQUISITION"
  | "SUPPLY_CHAIN"
  | "DESIGN"
  | "CONSTRUCTION"
  | "GEOTECHNICAL"
  | "MONSOON_WEATHER";

export type RiskResponseType = "AVOID" | "MITIGATE" | "TRANSFER" | "ACCEPT" | "EXPLOIT";

export interface RiskResponse {
  id: string;
  code: string;
  title: string;
  description: string;
  category: RiskCategory;
  probability: number;
  impact: number;
  impactCost: number;
  impactSchedule: number;
  riskScore: number;
  residualRiskScore: number;
  rag: RiskRag;
  trend: RiskTrend;
  isOpportunity: boolean;
  status: RiskStatus;
  owner: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRiskRequest {
  code: string;
  title: string;
  description?: string;
  category: RiskCategory;
  probability: number;
  impactCost: number;
  impactSchedule: number;
  owner?: string;
}

export interface UpdateRiskRequest {
  title?: string;
  description?: string;
  probability?: number;
  impactCost?: number;
  impactSchedule?: number;
  status?: RiskStatus;
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

  getRisksByProject: (projectId: string) =>
    apiClient
      .get<ApiResponse<RiskResponse[]>>(`/v1/projects/${projectId}/risks`)
      .then((r) => r.data),
};
