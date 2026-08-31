import { apiClient } from "./client";
import type { ApiResponse } from "../types";

/**
 * Material Rate Master — one row per (Material Category + Spec/Grade). Carries unit
 * and cost-per-unit that snapshot onto every Material {@code Resource} pointing at
 * this row. Revising rate or unit cascades to all linked Resources.
 */
export interface MaterialRateMaster {
  id: string;
  categoryId: string;
  categoryCode?: string | null;
  categoryName?: string | null;
  specGrade: string;
  unit: string;
  rate: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface MaterialRateMasterRequest {
  categoryId: string;
  specGrade: string;
  unit: string;
  rate: number;
  active?: boolean;
}

export const materialRateMasterApi = {
  list: () =>
    apiClient
      .get<ApiResponse<MaterialRateMaster[]>>("/v1/material-rate-master")
      .then((r) => r.data),

  get: (id: string) =>
    apiClient
      .get<ApiResponse<MaterialRateMaster>>(`/v1/material-rate-master/${id}`)
      .then((r) => r.data),

  create: (request: MaterialRateMasterRequest) =>
    apiClient
      .post<ApiResponse<MaterialRateMaster>>("/v1/material-rate-master", request)
      .then((r) => r.data),

  update: (id: string, request: MaterialRateMasterRequest) =>
    apiClient
      .put<ApiResponse<MaterialRateMaster>>(`/v1/material-rate-master/${id}`, request)
      .then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/v1/material-rate-master/${id}`),
};
