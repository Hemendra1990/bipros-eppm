import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface ActivityStepResponse {
  id: string;
  activityId: string;
  name: string;
  description: string | null;
  weight: number | null;
  weightPercent: number | null;
  isCompleted: boolean;
  sortOrder: number | null;
}

export interface CreateActivityStepRequest {
  name: string;
  description?: string;
  weight?: number;
}

export const activityStepApi = {
  listSteps: (activityId: string) =>
    apiClient
      .get<ApiResponse<ActivityStepResponse[]>>(`/v1/activities/${activityId}/steps`)
      .then((r) => r.data),

  createStep: (activityId: string, request: CreateActivityStepRequest) =>
    apiClient
      .post<ApiResponse<ActivityStepResponse>>(`/v1/activities/${activityId}/steps`, request)
      .then((r) => r.data),

  updateStep: (
    activityId: string,
    stepId: string,
    name: string,
    weight: number,
    description?: string
  ) =>
    apiClient
      .put<ApiResponse<ActivityStepResponse>>(
        `/v1/activities/${activityId}/steps/${stepId}`,
        null,
        { params: { name, weight, ...(description !== undefined ? { description } : {}) } }
      )
      .then((r) => r.data),

  completeStep: (activityId: string, stepId: string) =>
    apiClient
      .put<ApiResponse<ActivityStepResponse>>(
        `/v1/activities/${activityId}/steps/${stepId}/complete`
      )
      .then((r) => r.data),

  uncompleteStep: (activityId: string, stepId: string) =>
    apiClient
      .put<ApiResponse<ActivityStepResponse>>(
        `/v1/activities/${activityId}/steps/${stepId}/uncomplete`
      )
      .then((r) => r.data),

  deleteStep: (activityId: string, stepId: string) =>
    apiClient.delete(`/v1/activities/${activityId}/steps/${stepId}`),
};
