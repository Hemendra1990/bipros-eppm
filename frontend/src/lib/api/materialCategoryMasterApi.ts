import { apiClient } from "./client";
import type { ApiResponse } from "../types";

/**
 * Material Category Master — flat lookup of material families (Cement, Steel,
 * Aggregate, Sand, Bricks, ...). Used as one dimension of the Material Rate
 * Master key (category + spec/grade).
 */
export interface MaterialCategoryMaster {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  sortOrder: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface MaterialCategoryMasterRequest {
  code: string;
  name: string;
  description?: string | null;
  sortOrder?: number | null;
  active?: boolean;
}

export const materialCategoryMasterApi = {
  list: () =>
    apiClient
      .get<ApiResponse<MaterialCategoryMaster[]>>("/v1/material-category-master")
      .then((r) => r.data),

  get: (id: string) =>
    apiClient
      .get<ApiResponse<MaterialCategoryMaster>>(`/v1/material-category-master/${id}`)
      .then((r) => r.data),

  create: (request: MaterialCategoryMasterRequest) =>
    apiClient
      .post<ApiResponse<MaterialCategoryMaster>>("/v1/material-category-master", request)
      .then((r) => r.data),

  update: (id: string, request: MaterialCategoryMasterRequest) =>
    apiClient
      .put<ApiResponse<MaterialCategoryMaster>>(`/v1/material-category-master/${id}`, request)
      .then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/v1/material-category-master/${id}`),
};
