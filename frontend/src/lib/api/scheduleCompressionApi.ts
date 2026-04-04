import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface CompressionRecommendation {
  activityId: string;
  activityCode: string;
  originalDuration: number;
  newDuration: number;
  durationSaved: number;
  additionalCost?: number;
  reason: string;
}

export interface CompressionAnalysisResponse {
  id: string;
  projectId: string;
  scenarioId?: string;
  analysisType: "FAST_TRACK" | "CRASH";
  originalDuration: number;
  compressedDuration: number;
  durationSaved: number;
  additionalCost?: number;
  recommendations: CompressionRecommendation[];
  createdAt: string;
  updatedAt: string;
}

export interface ScheduleScenarioResponse {
  id: string;
  projectId: string;
  scenarioName: string;
  description?: string;
  scenarioType: "BASELINE" | "FAST_TRACK" | "CRASH" | "WHAT_IF" | "CUSTOM";
  projectDuration: number;
  criticalPathLength: number;
  totalCost?: number;
  modifiedActivities?: string;
  status: "DRAFT" | "CALCULATED" | "ARCHIVED";
  createdAt: string;
  updatedAt: string;
}

export interface CreateScenarioRequest {
  scenarioName: string;
  description?: string;
  scenarioType: "BASELINE" | "FAST_TRACK" | "CRASH" | "WHAT_IF" | "CUSTOM";
}

export interface ScenarioComparisonResponse {
  scenario1Id: string;
  scenario2Id: string;
  scenario1Name: string;
  scenario2Name: string;
  duration1: number;
  duration2: number;
  durationDifference: number;
  cost1?: number;
  cost2?: number;
  costDifference?: number;
  activitiesChanged: number;
  changedActivities: Array<{
    activityId: string;
    activityCode: string;
    duration1: number;
    duration2: number;
    dateChange1?: string;
    dateChange2?: string;
  }>;
}

export const scheduleCompressionApi = {
  analyzeFastTrack: (projectId: string) =>
    apiClient
      .post<ApiResponse<CompressionAnalysisResponse>>(
        `/v1/projects/${projectId}/schedule-compression/fast-track`
      )
      .then((r) => r.data),

  analyzeCrashing: (projectId: string) =>
    apiClient
      .post<ApiResponse<CompressionAnalysisResponse>>(
        `/v1/projects/${projectId}/schedule-compression/crash`
      )
      .then((r) => r.data),

  // Scenario endpoints
  createScenario: (projectId: string, data: CreateScenarioRequest) =>
    apiClient
      .post<ApiResponse<ScheduleScenarioResponse>>(
        `/v1/projects/${projectId}/schedule-scenarios`,
        data
      )
      .then((r) => r.data),

  listScenarios: (projectId: string) =>
    apiClient
      .get<ApiResponse<ScheduleScenarioResponse[]>>(
        `/v1/projects/${projectId}/schedule-scenarios`
      )
      .then((r) => r.data),

  getScenario: (projectId: string, scenarioId: string) =>
    apiClient
      .get<ApiResponse<ScheduleScenarioResponse>>(
        `/v1/projects/${projectId}/schedule-scenarios/${scenarioId}`
      )
      .then((r) => r.data),

  compareScenarios: (projectId: string, scenario1Id: string, scenario2Id: string) =>
    apiClient
      .post<ApiResponse<ScenarioComparisonResponse>>(
        `/v1/projects/${projectId}/schedule-scenarios/compare`,
        null,
        {
          params: { scenario1: scenario1Id, scenario2: scenario2Id },
        }
      )
      .then((r) => r.data),
};
