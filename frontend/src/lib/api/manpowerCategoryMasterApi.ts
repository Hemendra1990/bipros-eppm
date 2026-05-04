import { apiClient } from "./client";
import type { ApiResponse } from "../types";

/**
 * Manpower Category Master — supports parent-child hierarchy. Top-level rows have
 * `parentId = null` (Categories); rows with a non-null `parentId` are Sub-Categories of that
 * parent. Stored on resource records as the master row's `name` (string).
 */
export interface ManpowerCategoryMaster {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  parentId?: string | null;
  parentName?: string | null;
  sortOrder: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ManpowerCategoryMasterRequest {
  code: string;
  name: string;
  description?: string | null;
  parentId?: string | null;
  sortOrder?: number | null;
  active?: boolean;
}

export const manpowerCategoryMasterApi = {
  list: () =>
    apiClient
      .get<ApiResponse<ManpowerCategoryMaster[]>>("/v1/admin/manpower-categories")
      .then((r) => r.data),

  listTopLevel: () =>
    apiClient
      .get<ApiResponse<ManpowerCategoryMaster[]>>("/v1/admin/manpower-categories/top-level")
      .then((r) => r.data),

  listByParent: (parentId: string) =>
    apiClient
      .get<ApiResponse<ManpowerCategoryMaster[]>>(
        `/v1/admin/manpower-categories/by-parent/${parentId}`,
      )
      .then((r) => r.data),

  get: (id: string) =>
    apiClient
      .get<ApiResponse<ManpowerCategoryMaster>>(`/v1/admin/manpower-categories/${id}`)
      .then((r) => r.data),

  create: (request: ManpowerCategoryMasterRequest) =>
    apiClient
      .post<ApiResponse<ManpowerCategoryMaster>>("/v1/admin/manpower-categories", request)
      .then((r) => r.data),

  update: (id: string, request: ManpowerCategoryMasterRequest) =>
    apiClient
      .put<ApiResponse<ManpowerCategoryMaster>>(`/v1/admin/manpower-categories/${id}`, request)
      .then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/v1/admin/manpower-categories/${id}`),
};
