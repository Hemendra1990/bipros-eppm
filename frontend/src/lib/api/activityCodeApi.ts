import { apiClient } from "./client";
import type { ApiResponse, PagedResponse } from "../types";

export interface ActivityCodeResponse {
  id: string;
  name: string;
  description?: string;
  scope: "GLOBAL" | "EPS" | "PROJECT";
  epsNodeId?: string;
  projectId: string;
  parentId?: string;
  sortOrder?: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateActivityCodeRequest {
  name: string;
  description?: string;
  scope: "GLOBAL" | "EPS" | "PROJECT";
  parentId?: string;
}

export interface UpdateActivityCodeRequest {
  name: string;
  description?: string;
  scope: "GLOBAL" | "EPS" | "PROJECT";
  parentId?: string;
}

export interface ActivityCodeAssignmentRequest {
  activityId: string;
  activityCodeId: string;
}

export const activityCodeApi = {
  listActivityCodes: (projectId: string, page = 0, size = 20) =>
    apiClient
      .get<ApiResponse<PagedResponse<ActivityCodeResponse>>>(
        `/v1/projects/${projectId}/activity-codes`,
        { params: { page, size } }
      )
      .then((r) => r.data),

  getActivityCode: (projectId: string, codeId: string) =>
    apiClient
      .get<ApiResponse<ActivityCodeResponse>>(
        `/v1/projects/${projectId}/activity-codes/${codeId}`
      )
      .then((r) => r.data),

  createActivityCode: (projectId: string, data: CreateActivityCodeRequest) =>
    apiClient
      .post<ApiResponse<ActivityCodeResponse>>(
        `/v1/projects/${projectId}/activity-codes`,
        data
      )
      .then((r) => r.data),

  updateActivityCode: (projectId: string, codeId: string, data: UpdateActivityCodeRequest) =>
    apiClient
      .put<ApiResponse<ActivityCodeResponse>>(
        `/v1/projects/${projectId}/activity-codes/${codeId}`,
        data
      )
      .then((r) => r.data),

  deleteActivityCode: (projectId: string, codeId: string) =>
    apiClient.delete(`/v1/projects/${projectId}/activity-codes/${codeId}`),

  assignActivityCode: (projectId: string, data: ActivityCodeAssignmentRequest) =>
    apiClient
      .post<ApiResponse<void>>(
        `/v1/projects/${projectId}/activity-code-assignments`,
        data
      )
      .then((r) => r.data),

  unassignActivityCode: (projectId: string, activityId: string, codeId: string) =>
    apiClient.delete(
      `/v1/projects/${projectId}/activities/${activityId}/activity-codes/${codeId}`
    ),
};
