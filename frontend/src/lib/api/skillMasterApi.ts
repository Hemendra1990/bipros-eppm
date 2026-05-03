import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface SkillMaster {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  sortOrder: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SkillMasterRequest {
  code: string;
  name: string;
  description?: string | null;
  sortOrder?: number | null;
  active?: boolean;
}

export const skillMasterApi = {
  list: () =>
    apiClient.get<ApiResponse<SkillMaster[]>>("/v1/admin/skills").then((r) => r.data),

  get: (id: string) =>
    apiClient.get<ApiResponse<SkillMaster>>(`/v1/admin/skills/${id}`).then((r) => r.data),

  create: (request: SkillMasterRequest) =>
    apiClient
      .post<ApiResponse<SkillMaster>>("/v1/admin/skills", request)
      .then((r) => r.data),

  update: (id: string, request: SkillMasterRequest) =>
    apiClient
      .put<ApiResponse<SkillMaster>>(`/v1/admin/skills/${id}`, request)
      .then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/v1/admin/skills/${id}`),
};
