import { apiClient } from "./client";
import type { ApiResponse } from "../types";

/**
 * Resource Type — top-level taxonomy node for resources. Three rows are seeded as system
 * defaults (codes LABOR / EQUIPMENT / MATERIAL); admins may add custom rows.
 */
export interface ResourceType {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  sortOrder: number;
  active: boolean;
  systemDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ResourceTypeRequest {
  code: string;
  name: string;
  description?: string | null;
  sortOrder?: number | null;
  active?: boolean;
}

export const resourceTypeApi = {
  list: () =>
    apiClient
      .get<ApiResponse<ResourceType[]>>("/v1/resource-types")
      .then((r) => r.data),

  get: (id: string) =>
    apiClient
      .get<ApiResponse<ResourceType>>(`/v1/resource-types/${id}`)
      .then((r) => r.data),

  create: (request: ResourceTypeRequest) =>
    apiClient
      .post<ApiResponse<ResourceType>>("/v1/resource-types", request)
      .then((r) => r.data),

  update: (id: string, request: ResourceTypeRequest) =>
    apiClient
      .put<ApiResponse<ResourceType>>(`/v1/resource-types/${id}`, request)
      .then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/v1/resource-types/${id}`),
};
