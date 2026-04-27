import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface ProjectCategoryMasterResponse {
  id: string;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
  sortOrder: number;
}

export interface CreateProjectCategoryMasterRequest {
  code: string;
  name: string;
  description?: string;
  active: boolean;
  sortOrder: number;
}

export interface UpdateProjectCategoryMasterRequest {
  name: string;
  description?: string;
  active: boolean;
  sortOrder: number;
}

export const projectCategoryApi = {
  listActive: () =>
    apiClient.get<ApiResponse<ProjectCategoryMasterResponse[]>>("/v1/project-categories").then((r) => r.data),

  listAll: () =>
    apiClient.get<ApiResponse<ProjectCategoryMasterResponse[]>>("/v1/project-categories/all").then((r) => r.data),

  getById: (id: string) =>
    apiClient.get<ApiResponse<ProjectCategoryMasterResponse>>(`/v1/project-categories/${id}`).then((r) => r.data),

  create: (data: CreateProjectCategoryMasterRequest) =>
    apiClient.post<ApiResponse<ProjectCategoryMasterResponse>>("/v1/project-categories", data).then((r) => r.data),

  update: (id: string, data: UpdateProjectCategoryMasterRequest) =>
    apiClient.put<ApiResponse<ProjectCategoryMasterResponse>>(`/v1/project-categories/${id}`, data).then((r) => r.data),

  delete: (id: string) =>
    apiClient.delete(`/v1/project-categories/${id}`),
};
