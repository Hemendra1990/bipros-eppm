import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export type DeploymentResourceType = "MANPOWER" | "EQUIPMENT";

export interface DailyResourceDeploymentResponse {
  id: string;
  projectId: string;
  logDate: string;
  resourceType: DeploymentResourceType;
  resourceDescription: string;
  resourceId: string | null;
  resourceRoleId: string | null;
  nosPlanned: number | null;
  nosDeployed: number | null;
  hoursWorked: number | null;
  idleHours: number | null;
  remarks: string | null;
}

export interface CreateDailyResourceDeploymentRequest {
  logDate: string;
  resourceType: DeploymentResourceType;
  resourceDescription: string;
  resourceId?: string | null;
  resourceRoleId?: string | null;
  nosPlanned?: number | null;
  nosDeployed?: number | null;
  hoursWorked?: number | null;
  idleHours?: number | null;
  remarks?: string | null;
}

export interface ResourceDeploymentFilters {
  from?: string;
  to?: string;
  resourceType?: DeploymentResourceType;
}

export const resourceDeploymentApi = {
  list: (projectId: string, filters: ResourceDeploymentFilters = {}) => {
    const params = new URLSearchParams();
    if (filters.from) params.set("from", filters.from);
    if (filters.to) params.set("to", filters.to);
    if (filters.resourceType) params.set("resourceType", filters.resourceType);
    const qs = params.toString() ? `?${params.toString()}` : "";
    return apiClient
      .get<ApiResponse<DailyResourceDeploymentResponse[]>>(
        `/v1/projects/${projectId}/resource-deployment${qs}`,
      )
      .then((r) => r.data);
  },

  create: (projectId: string, request: CreateDailyResourceDeploymentRequest) =>
    apiClient
      .post<ApiResponse<DailyResourceDeploymentResponse>>(
        `/v1/projects/${projectId}/resource-deployment`,
        request,
      )
      .then((r) => r.data),

  delete: (projectId: string, id: string) =>
    apiClient.delete(`/v1/projects/${projectId}/resource-deployment/${id}`),
};
