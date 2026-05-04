import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface EmploymentTypeMaster {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  sortOrder: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface EmploymentTypeMasterRequest {
  code: string;
  name: string;
  description?: string | null;
  sortOrder?: number | null;
  active?: boolean;
}

export const employmentTypeMasterApi = {
  list: () =>
    apiClient
      .get<ApiResponse<EmploymentTypeMaster[]>>("/v1/admin/employment-types")
      .then((r) => r.data),

  get: (id: string) =>
    apiClient
      .get<ApiResponse<EmploymentTypeMaster>>(`/v1/admin/employment-types/${id}`)
      .then((r) => r.data),

  create: (request: EmploymentTypeMasterRequest) =>
    apiClient
      .post<ApiResponse<EmploymentTypeMaster>>("/v1/admin/employment-types", request)
      .then((r) => r.data),

  update: (id: string, request: EmploymentTypeMasterRequest) =>
    apiClient
      .put<ApiResponse<EmploymentTypeMaster>>(`/v1/admin/employment-types/${id}`, request)
      .then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/v1/admin/employment-types/${id}`),
};
