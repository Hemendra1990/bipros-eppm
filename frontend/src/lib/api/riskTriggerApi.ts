import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface RiskTrigger {
  id: string;
  riskId: string;
  projectId: string;
  triggerCondition: string;
  triggerType: "SCHEDULE_DELAY" | "COST_OVERRUN" | "MILESTONE_MISSED" | "MANUAL";
  thresholdValue: number;
  currentValue: number | null;
  isTriggered: boolean;
  triggeredAt: string | null;
  escalationLevel: "GREEN" | "AMBER" | "RED";
  notifyRoles: string | null;
  createdAt: string;
}

export interface CreateRiskTriggerRequest {
  riskId: string;
  triggerCondition: string;
  triggerType: "SCHEDULE_DELAY" | "COST_OVERRUN" | "MILESTONE_MISSED" | "MANUAL";
  thresholdValue: number;
  escalationLevel: "GREEN" | "AMBER" | "RED";
  notifyRoles?: string;
}

export const riskTriggerApi = {
  createTrigger: (projectId: string, data: CreateRiskTriggerRequest) =>
    apiClient
      .post<ApiResponse<RiskTrigger>>(
        `/v1/projects/${projectId}/risk-triggers`,
        data
      )
      .then((r) => r.data),

  getTrigger: (projectId: string, triggerId: string) =>
    apiClient
      .get<ApiResponse<RiskTrigger>>(
        `/v1/projects/${projectId}/risk-triggers/${triggerId}`
      )
      .then((r) => r.data),

  listTriggers: (projectId: string) =>
    apiClient
      .get<ApiResponse<RiskTrigger[]>>(
        `/v1/projects/${projectId}/risk-triggers`
      )
      .then((r) => r.data),

  listTriggeredRisks: (projectId: string) =>
    apiClient
      .get<ApiResponse<RiskTrigger[]>>(
        `/v1/projects/${projectId}/risk-triggers/triggered`
      )
      .then((r) => r.data),

  evaluateTriggers: (projectId: string) =>
    apiClient
      .post<ApiResponse<RiskTrigger[]>>(
        `/v1/projects/${projectId}/risk-triggers/evaluate`,
        null
      )
      .then((r) => r.data),

  updateTrigger: (projectId: string, triggerId: string, data: CreateRiskTriggerRequest) =>
    apiClient
      .put<ApiResponse<RiskTrigger>>(
        `/v1/projects/${projectId}/risk-triggers/${triggerId}`,
        data
      )
      .then((r) => r.data),

  deleteTrigger: (projectId: string, triggerId: string) =>
    apiClient.delete(`/v1/projects/${projectId}/risk-triggers/${triggerId}`),
};
