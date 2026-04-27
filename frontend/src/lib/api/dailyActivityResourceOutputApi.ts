import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface DailyActivityResourceOutputResponse {
  id: string;
  projectId: string;
  outputDate: string;
  activityId: string;
  resourceId: string;
  qtyExecuted: number;
  unit: string;
  hoursWorked: number | null;
  daysWorked: number | null;
  remarks: string | null;
  createdAt: string;
  updatedAt: string;
  createdBy: string | null;
  updatedBy: string | null;
}

export interface CreateDailyActivityResourceOutputRequest {
  outputDate: string;
  activityId: string;
  resourceId: string;
  qtyExecuted: number;
  /** Optional — server mirrors WorkActivity.defaultUnit when blank. */
  unit?: string;
  hoursWorked?: number | null;
  daysWorked?: number | null;
  remarks?: string | null;
}

export interface ListDailyOutputsParams {
  from?: string;
  to?: string;
  activityId?: string;
  resourceId?: string;
}

export const dailyActivityResourceOutputApi = {
  list: (projectId: string, params?: ListDailyOutputsParams) => {
    const qs: string[] = [];
    if (params?.from) qs.push(`from=${params.from}`);
    if (params?.to) qs.push(`to=${params.to}`);
    if (params?.activityId) qs.push(`activityId=${params.activityId}`);
    if (params?.resourceId) qs.push(`resourceId=${params.resourceId}`);
    const suffix = qs.length ? `?${qs.join("&")}` : "";
    return apiClient
      .get<ApiResponse<DailyActivityResourceOutputResponse[]>>(
        `/v1/projects/${projectId}/activity-resource-outputs${suffix}`,
      )
      .then((r) => r.data);
  },

  get: (projectId: string, id: string) =>
    apiClient
      .get<ApiResponse<DailyActivityResourceOutputResponse>>(
        `/v1/projects/${projectId}/activity-resource-outputs/${id}`,
      )
      .then((r) => r.data),

  create: (projectId: string, request: CreateDailyActivityResourceOutputRequest) =>
    apiClient
      .post<ApiResponse<DailyActivityResourceOutputResponse>>(
        `/v1/projects/${projectId}/activity-resource-outputs`,
        request,
      )
      .then((r) => r.data),

  createBulk: (projectId: string, requests: CreateDailyActivityResourceOutputRequest[]) =>
    apiClient
      .post<ApiResponse<DailyActivityResourceOutputResponse[]>>(
        `/v1/projects/${projectId}/activity-resource-outputs/bulk`,
        requests,
      )
      .then((r) => r.data),

  delete: (projectId: string, id: string) =>
    apiClient.delete(`/v1/projects/${projectId}/activity-resource-outputs/${id}`),
};
