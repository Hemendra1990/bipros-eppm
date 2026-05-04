import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface NationalityMaster {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  sortOrder: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface NationalityMasterRequest {
  code: string;
  name: string;
  description?: string | null;
  sortOrder?: number | null;
  active?: boolean;
}

export const nationalityMasterApi = {
  list: () =>
    apiClient
      .get<ApiResponse<NationalityMaster[]>>("/v1/admin/nationalities")
      .then((r) => r.data),

  get: (id: string) =>
    apiClient
      .get<ApiResponse<NationalityMaster>>(`/v1/admin/nationalities/${id}`)
      .then((r) => r.data),

  create: (request: NationalityMasterRequest) =>
    apiClient
      .post<ApiResponse<NationalityMaster>>("/v1/admin/nationalities", request)
      .then((r) => r.data),

  update: (id: string, request: NationalityMasterRequest) =>
    apiClient
      .put<ApiResponse<NationalityMaster>>(`/v1/admin/nationalities/${id}`, request)
      .then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/v1/admin/nationalities/${id}`),
};
