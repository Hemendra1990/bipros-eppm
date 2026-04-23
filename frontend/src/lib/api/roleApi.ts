import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export type RoleResourceType = "LABOR" | "NONLABOR" | "MATERIAL";

export interface RoleResponse {
  id: string;
  code: string;
  name: string;
  description: string | null;
  resourceType: RoleResourceType;
  defaultRate: number | null;
  defaultUnitsPerTime: number | null;
  sortOrder: number;
  rateUnit: string | null;
  budgetedRate: number | null;
  actualRate: number | null;
  rateVariance: number | null;
  rateVariancePercent: number | null;
  rateRemarks: string | null;
  createdAt: string;
  updatedAt: string;
  createdBy: string | null;
  updatedBy: string | null;
}

export interface CreateRoleRequest {
  code: string;
  name: string;
  description?: string | null;
  resourceType: RoleResourceType;
  defaultRate?: number | null;
  rateUnit?: string | null;
  budgetedRate?: number | null;
  actualRate?: number | null;
  rateRemarks?: string | null;
}

export interface UserResourceRoleResponse {
  id: string;
  userId: string;
  resourceRoleId: string;
  assignedFrom: string | null;
  assignedTo: string | null;
  remarks: string | null;
}

export interface AssignUserToRoleRequest {
  userId: string;
  assignedFrom?: string | null;
  assignedTo?: string | null;
  remarks?: string | null;
}

export const roleApi = {
  list: (resourceType?: RoleResourceType) => {
    const qs = resourceType ? `?resourceType=${resourceType}` : "";
    return apiClient.get<ApiResponse<RoleResponse[]>>(`/v1/roles${qs}`).then((r) => r.data);
  },

  get: (id: string) =>
    apiClient.get<ApiResponse<RoleResponse>>(`/v1/roles/${id}`).then((r) => r.data),

  create: (request: CreateRoleRequest) =>
    apiClient.post<ApiResponse<RoleResponse>>("/v1/roles", request).then((r) => r.data),

  update: (id: string, request: CreateRoleRequest) =>
    apiClient.put<ApiResponse<RoleResponse>>(`/v1/roles/${id}`, request).then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/v1/roles/${id}`),

  listUsers: (roleId: string) =>
    apiClient
      .get<ApiResponse<UserResourceRoleResponse[]>>(`/v1/roles/${roleId}/users`)
      .then((r) => r.data),

  assignUser: (roleId: string, request: AssignUserToRoleRequest) =>
    apiClient
      .post<ApiResponse<UserResourceRoleResponse>>(`/v1/roles/${roleId}/users`, request)
      .then((r) => r.data),

  unassignUser: (roleId: string, assignmentId: string) =>
    apiClient.delete(`/v1/roles/${roleId}/users/${assignmentId}`),
};
