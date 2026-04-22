import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface ActivityCorrelation {
  id?: string;
  projectId?: string;
  activityAId: string;
  activityBId: string;
  coefficient: number;
}

export const activityCorrelationApi = {
  list: (projectId: string) =>
    apiClient
      .get<ApiResponse<ActivityCorrelation[]>>(
        `/v1/projects/${projectId}/activity-correlations`
      )
      .then((r) => r.data),

  upsert: (projectId: string, body: ActivityCorrelation) =>
    apiClient
      .put<ApiResponse<ActivityCorrelation>>(
        `/v1/projects/${projectId}/activity-correlations`,
        body
      )
      .then((r) => r.data),

  remove: (projectId: string, activityAId: string, activityBId: string) =>
    apiClient
      .delete<ApiResponse<void>>(
        `/v1/projects/${projectId}/activity-correlations/${activityAId}/${activityBId}`
      )
      .then((r) => r.data),
};
