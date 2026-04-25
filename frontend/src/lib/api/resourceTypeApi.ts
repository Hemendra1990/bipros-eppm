import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export type ResourceTypeBaseCategory = "LABOR" | "NONLABOR" | "MATERIAL";

export interface ResourceTypeDef {
  id: string;
  code: string;
  name: string;
  baseCategory: ResourceTypeBaseCategory;
  codePrefix: string | null;
  sortOrder: number | null;
  active: boolean;
  systemDefault: boolean;
  createdAt: string;
  updatedAt: string;
  createdBy: string | null;
  updatedBy: string | null;
}

export interface CreateResourceTypeDefRequest {
  code: string;
  name: string;
  baseCategory: ResourceTypeBaseCategory;
  codePrefix?: string | null;
  sortOrder?: number | null;
  active?: boolean;
}

export interface UpdateResourceTypeDefRequest {
  code: string;
  name: string;
  baseCategory: ResourceTypeBaseCategory;
  codePrefix?: string | null;
  sortOrder?: number | null;
  active?: boolean;
}

export const BASE_CATEGORY_LABEL: Record<ResourceTypeBaseCategory, string> = {
  LABOR: "Manpower",
  NONLABOR: "Machine",
  MATERIAL: "Material",
};

export const resourceTypeApi = {
  list: (params?: { active?: boolean; baseCategory?: ResourceTypeBaseCategory }) => {
    const qs: string[] = [];
    if (params?.active !== undefined) qs.push(`active=${params.active}`);
    if (params?.baseCategory) qs.push(`baseCategory=${params.baseCategory}`);
    const suffix = qs.length ? `?${qs.join("&")}` : "";
    return apiClient
      .get<ApiResponse<ResourceTypeDef[]>>(`/v1/resource-types${suffix}`)
      .then((r) => r.data);
  },

  get: (id: string) =>
    apiClient.get<ApiResponse<ResourceTypeDef>>(`/v1/resource-types/${id}`).then((r) => r.data),

  create: (request: CreateResourceTypeDefRequest) =>
    apiClient
      .post<ApiResponse<ResourceTypeDef>>("/v1/resource-types", request)
      .then((r) => r.data),

  update: (id: string, request: UpdateResourceTypeDefRequest) =>
    apiClient
      .put<ApiResponse<ResourceTypeDef>>(`/v1/resource-types/${id}`, request)
      .then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/v1/resource-types/${id}`),
};
