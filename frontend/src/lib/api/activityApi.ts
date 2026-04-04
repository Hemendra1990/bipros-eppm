import { apiClient } from "./client";
import type { ApiResponse, PagedResponse } from "../types";

export interface ActivityResponse {
  id: string;
  code: string;
  name: string;
  description?: string;
  projectId: string;
  wbsNodeId: string;
  status: string;
  plannedStartDate: string | null;
  plannedFinishDate: string | null;
  actualStartDate: string | null;
  actualFinishDate: string | null;
  earlyStartDate?: string | null;
  earlyFinishDate?: string | null;
  lateStartDate?: string | null;
  lateFinishDate?: string | null;
  duration: number;
  percentComplete: number;
  slack: number;
  totalFloat: number;
  freeFloat?: number;
  remainingDuration: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateActivityRequest {
  code: string;
  name: string;
  wbsNodeId: string;
  duration: number;
  status?: string;
  plannedStartDate?: string;
}

export interface UpdateActivityRequest {
  name?: string;
  duration?: number;
  percentComplete?: number;
  actualStartDate?: string;
  actualFinishDate?: string;
}

export const activityApi = {
  listActivities: (projectId: string, page = 0, size = 20) =>
    apiClient
      .get<ApiResponse<PagedResponse<ActivityResponse>>>(`/v1/projects/${projectId}/activities`, {
        params: { page, size },
      })
      .then((r) => r.data),

  getActivity: (projectId: string, activityId: string) =>
    apiClient
      .get<ApiResponse<ActivityResponse>>(`/v1/projects/${projectId}/activities/${activityId}`)
      .then((r) => r.data),

  createActivity: (projectId: string, data: CreateActivityRequest) =>
    apiClient
      .post<ApiResponse<ActivityResponse>>(`/v1/projects/${projectId}/activities`, data)
      .then((r) => r.data),

  updateActivity: (projectId: string, activityId: string, data: UpdateActivityRequest) =>
    apiClient
      .put<ApiResponse<ActivityResponse>>(
        `/v1/projects/${projectId}/activities/${activityId}`,
        data
      )
      .then((r) => r.data),

  deleteActivity: (projectId: string, activityId: string) =>
    apiClient.delete(`/v1/projects/${projectId}/activities/${activityId}`),

  triggerSchedule: (projectId: string, option: string) =>
    apiClient
      .post<ApiResponse<{ success: boolean }>>(
        `/v1/projects/${projectId}/schedule/calculate`,
        { option }
      )
      .then((r) => r.data),

  getCriticalPath: (projectId: string) =>
    apiClient
      .get<ApiResponse<ActivityResponse[]>>(`/v1/projects/${projectId}/critical-path`)
      .then((r) => r.data),

  getRelationships: (projectId: string) =>
    apiClient
      .get<
        ApiResponse<
          Array<{
            predecessorActivityId: string;
            successorActivityId: string;
            relationshipType: string;
          }>
        >
      >(`/v1/projects/${projectId}/relationships`)
      .then((r) => r.data),
};
