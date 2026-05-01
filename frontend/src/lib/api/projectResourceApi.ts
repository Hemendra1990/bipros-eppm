import { apiClient } from "./client";
import type { ApiResponse } from "../types";
import type { ResourceResponse } from "./resourceApi";

export interface ProjectResourceResponse {
  id: string;
  projectId: string;
  resourceId: string;
  resourceCode: string | null;
  resourceName: string | null;
  resourceTypeName: string | null;
  roleName: string | null;
  masterRate: number | null;
  rateOverride: number | null;
  availabilityOverride: number | null;
  customUnit: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  createdBy: string | null;
  updatedBy: string | null;
}

export interface PoolEntryInput {
  resourceId: string;
  rateOverride?: number;
  availabilityOverride?: number;
  customUnit?: string;
  notes?: string;
}

export interface UpdatePoolEntryRequest {
  rateOverride?: number;
  availabilityOverride?: number;
  customUnit?: string;
  notes?: string;
}

export const projectResourceApi = {
  listPool: (projectId: string) =>
    apiClient
      .get<ApiResponse<ProjectResourceResponse[]>>(`/v1/projects/${projectId}/resource-pool`)
      .then((r) => r.data),

  listAvailable: (
    projectId: string,
    params?: { typeCode?: string; roleId?: string; q?: string }
  ) =>
    apiClient
      .get<ApiResponse<ResourceResponse[]>>(
        `/v1/projects/${projectId}/resource-pool/available`,
        { params }
      )
      .then((r) => r.data),

  listPoolByRole: (projectId: string, roleId: string) =>
    apiClient
      .get<ApiResponse<ProjectResourceResponse[]>>(
        `/v1/projects/${projectId}/resource-pool/by-role/${roleId}`
      )
      .then((r) => r.data),

  addToPool: (projectId: string, entries: PoolEntryInput[]) =>
    apiClient
      .post<ApiResponse<ProjectResourceResponse[]>>(
        `/v1/projects/${projectId}/resource-pool`,
        { entries }
      )
      .then((r) => r.data),

  updatePoolEntry: (
    projectId: string,
    id: string,
    patch: UpdatePoolEntryRequest
  ) =>
    apiClient
      .put<ApiResponse<ProjectResourceResponse>>(
        `/v1/projects/${projectId}/resource-pool/${id}`,
        patch
      )
      .then((r) => r.data),

  removeFromPool: (projectId: string, id: string) =>
    apiClient
      .delete<ApiResponse<void>>(`/v1/projects/${projectId}/resource-pool/${id}`)
      .then((r) => r.data),
};
