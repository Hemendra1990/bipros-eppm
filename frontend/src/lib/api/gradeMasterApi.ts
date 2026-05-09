import { apiClient } from "./client";
import type { ApiResponse } from "../types";

/**
 * Grade Master — flat lookup of manpower grades (A, B, C, ...). Used as one
 * dimension of the Manpower Rate Master key (role + category + sub-category + grade).
 */
export interface GradeMaster {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  sortOrder: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface GradeMasterRequest {
  code: string;
  name: string;
  description?: string | null;
  sortOrder?: number | null;
  active?: boolean;
}

export const gradeMasterApi = {
  list: () =>
    apiClient
      .get<ApiResponse<GradeMaster[]>>("/v1/grade-master")
      .then((r) => r.data),

  get: (id: string) =>
    apiClient
      .get<ApiResponse<GradeMaster>>(`/v1/grade-master/${id}`)
      .then((r) => r.data),

  create: (request: GradeMasterRequest) =>
    apiClient
      .post<ApiResponse<GradeMaster>>("/v1/grade-master", request)
      .then((r) => r.data),

  update: (id: string, request: GradeMasterRequest) =>
    apiClient
      .put<ApiResponse<GradeMaster>>(`/v1/grade-master/${id}`, request)
      .then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/v1/grade-master/${id}`),
};
