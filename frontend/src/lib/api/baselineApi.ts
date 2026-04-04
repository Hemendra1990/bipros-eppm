import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface BaselineResponse {
  id: string;
  code: string;
  name: string;
  projectId: string;
  baselineType: "PROJECT" | "PRIMARY" | "SECONDARY" | "TERTIARY";
  snapshotDate: string;
  activitiesCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface BaselineVarianceRow {
  activityCode: string;
  activityName: string;
  startVariance: number;
  finishVariance: number;
  durationVariance: number;
  costVariance: number;
}

export interface BaselineVarianceData {
  baselineId: string;
  baselineName: string;
  projectId: string;
  variance: BaselineVarianceRow[];
}

export interface CreateBaselineRequest {
  name: string;
  baselineType: "PROJECT" | "PRIMARY" | "SECONDARY" | "TERTIARY";
}

export interface BaselineActivityResponse {
  activityId: string;
  baselineStartDate: string | null;
  baselineFinishDate: string | null;
}

export const baselineApi = {
  listBaselines: (projectId: string) =>
    apiClient
      .get<ApiResponse<BaselineResponse[]>>(`/v1/projects/${projectId}/baselines`)
      .then((r) => r.data),

  createBaseline: (projectId: string, data: CreateBaselineRequest) =>
    apiClient
      .post<ApiResponse<BaselineResponse>>(
        `/v1/projects/${projectId}/baselines`,
        data
      )
      .then((r) => r.data),

  getVariance: (projectId: string, baselineId: string) =>
    apiClient
      .get<ApiResponse<BaselineVarianceData>>(
        `/v1/projects/${projectId}/baselines/${baselineId}/variance`
      )
      .then((r) => r.data),

  getBaselineActivities: (projectId: string, baselineId: string) =>
    apiClient
      .get<ApiResponse<BaselineActivityResponse[]>>(
        `/v1/projects/${projectId}/baselines/${baselineId}/activities`
      )
      .then((r) => r.data),

  deleteBaseline: (projectId: string, baselineId: string) =>
    apiClient.delete<ApiResponse<void>>(
      `/v1/projects/${projectId}/baselines/${baselineId}`
    ),
};
