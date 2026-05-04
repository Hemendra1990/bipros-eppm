import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface SkillLevelMaster {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  sortOrder: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SkillLevelMasterRequest {
  code: string;
  name: string;
  description?: string | null;
  sortOrder?: number | null;
  active?: boolean;
}

export const skillLevelMasterApi = {
  list: () =>
    apiClient
      .get<ApiResponse<SkillLevelMaster[]>>("/v1/admin/skill-levels")
      .then((r) => r.data),

  get: (id: string) =>
    apiClient
      .get<ApiResponse<SkillLevelMaster>>(`/v1/admin/skill-levels/${id}`)
      .then((r) => r.data),

  create: (request: SkillLevelMasterRequest) =>
    apiClient
      .post<ApiResponse<SkillLevelMaster>>("/v1/admin/skill-levels", request)
      .then((r) => r.data),

  update: (id: string, request: SkillLevelMasterRequest) =>
    apiClient
      .put<ApiResponse<SkillLevelMaster>>(`/v1/admin/skill-levels/${id}`, request)
      .then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/v1/admin/skill-levels/${id}`),
};
