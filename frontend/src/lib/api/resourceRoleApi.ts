import { apiClient } from "./client";
import type { ApiResponse } from "../types";

/**
 * Resource Role — labour / equipment / material role within a Resource Type. Drives
 * default rate, productivity unit, and is the FK target on Resource.roleId.
 */
export interface ResourceRole {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  resourceTypeId: string;
  resourceTypeCode: string;
  resourceTypeName: string;
  productivityUnit?: string | null;
  defaultRate?: number | null;
  sortOrder: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ResourceRoleRequest {
  code: string;
  name: string;
  description?: string | null;
  resourceTypeId: string;
  productivityUnit?: string | null;
  defaultRate?: number | null;
  sortOrder?: number | null;
  active?: boolean;
}

export const resourceRoleApi = {
  list: () =>
    apiClient
      .get<ApiResponse<ResourceRole[]>>("/v1/resource-roles")
      .then((r) => r.data),

  listByType: (typeId: string) =>
    apiClient
      .get<ApiResponse<ResourceRole[]>>(`/v1/resource-roles/by-type/${typeId}`)
      .then((r) => r.data),

  get: (id: string) =>
    apiClient
      .get<ApiResponse<ResourceRole>>(`/v1/resource-roles/${id}`)
      .then((r) => r.data),

  create: (request: ResourceRoleRequest) =>
    apiClient
      .post<ApiResponse<ResourceRole>>("/v1/resource-roles", request)
      .then((r) => r.data),

  update: (id: string, request: ResourceRoleRequest) =>
    apiClient
      .put<ApiResponse<ResourceRole>>(`/v1/resource-roles/${id}`, request)
      .then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/v1/resource-roles/${id}`),
};
