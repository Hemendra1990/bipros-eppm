import { apiClient } from "./client";
import type { ApiResponse } from "../types";

/**
 * Resource Role — pure metadata for a labour / equipment / material role within a Resource Type.
 * Drives productivity unit and is the FK target on Resource.roleId. Rate is NOT carried here —
 * it lives on the individual Resource (`costPerUnit`) because actual rates vary by experience,
 * skill, and project even within a single role.
 */
export interface ResourceRole {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  resourceTypeId: string;
  resourceTypeCode: string;
  resourceTypeName: string;
  /**
   * @deprecated Phase 8 hides this from the UI. The DB column still exists for the
   * assignment unit fallback used when a role-only / unstaffed slot has no resource yet,
   * but new roles never set it and the form no longer exposes it.
   */
  productivityUnit?: string | null;
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
