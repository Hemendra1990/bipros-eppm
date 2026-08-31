import { apiClient } from "./client";
import type { ApiResponse } from "../types";

/**
 * Manpower Rate Master — one row per priceable (Role + Category + Sub-Category + Grade)
 * combination. Carries the productivity unit and cost per unit that flow into every
 * Manpower {@code Resource} pointing at this row. Revising rate or unit cascades to all
 * linked Resources.
 */
export interface ManpowerRateMaster {
  id: string;
  roleId: string;
  roleCode?: string | null;
  roleName?: string | null;
  categoryId: string;
  categoryName?: string | null;
  gradeId: string;
  gradeCode?: string | null;
  gradeName?: string | null;
  unit: string;
  rate: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ManpowerRateMasterRequest {
  roleId: string;
  categoryId: string;
  gradeId: string;
  unit: string;
  rate: number;
  active?: boolean;
}

export const manpowerRateMasterApi = {
  list: () =>
    apiClient
      .get<ApiResponse<ManpowerRateMaster[]>>("/v1/manpower-rate-master")
      .then((r) => r.data),

  get: (id: string) =>
    apiClient
      .get<ApiResponse<ManpowerRateMaster>>(`/v1/manpower-rate-master/${id}`)
      .then((r) => r.data),

  create: (request: ManpowerRateMasterRequest) =>
    apiClient
      .post<ApiResponse<ManpowerRateMaster>>("/v1/manpower-rate-master", request)
      .then((r) => r.data),

  update: (id: string, request: ManpowerRateMasterRequest) =>
    apiClient
      .put<ApiResponse<ManpowerRateMaster>>(`/v1/manpower-rate-master/${id}`, request)
      .then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/v1/manpower-rate-master/${id}`),
};
