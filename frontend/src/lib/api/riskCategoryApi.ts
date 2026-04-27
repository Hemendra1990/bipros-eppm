import { apiClient } from "./client";
import type { ApiResponse } from "../types";

/** Backend Industry enum (mirror of bipros-risk Industry.java). */
export type Industry =
  | "ROAD" | "BRIDGE" | "BUILDING" | "CONSTRUCTION_GENERAL"
  | "REFINERY" | "OIL_GAS" | "RAILWAY" | "METRO" | "POWER" | "WATER"
  | "MINING" | "MANUFACTURING" | "PHARMA" | "IT" | "TELECOM"
  | "BANKING_FINANCE" | "HEALTHCARE" | "AGRICULTURE" | "AEROSPACE_DEFENSE"
  | "MARITIME" | "MASS_EVENT" | "GENERIC";

export const INDUSTRIES: Industry[] = [
  "ROAD","BRIDGE","BUILDING","CONSTRUCTION_GENERAL","REFINERY","OIL_GAS",
  "RAILWAY","METRO","POWER","WATER","MINING","MANUFACTURING","PHARMA","IT",
  "TELECOM","BANKING_FINANCE","HEALTHCARE","AGRICULTURE","AEROSPACE_DEFENSE",
  "MARITIME","MASS_EVENT","GENERIC",
];

export interface RiskCategoryTypeSummary {
  id: string;
  code: string;
  name: string;
}

export interface RiskCategoryTypeResponse {
  id: string;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
  sortOrder: number;
  systemDefault: boolean;
  childCount: number;
}

export interface RiskCategoryMasterResponse {
  id: string;
  code: string;
  name: string;
  description: string | null;
  industry: Industry;
  type: RiskCategoryTypeSummary;
  active: boolean;
  sortOrder: number;
  systemDefault: boolean;
}

export interface CreateRiskCategoryTypeRequest {
  code: string;
  name: string;
  description?: string;
  active: boolean;
  sortOrder: number;
}

export interface UpdateRiskCategoryTypeRequest {
  name: string;
  description?: string;
  active: boolean;
  sortOrder: number;
}

export interface CreateRiskCategoryMasterRequest {
  code: string;
  name: string;
  description?: string;
  typeId: string;
  industry: Industry;
  active: boolean;
  sortOrder: number;
}

export interface UpdateRiskCategoryMasterRequest {
  name: string;
  description?: string;
  typeId: string;
  industry: Industry;
  active: boolean;
  sortOrder: number;
}

export const riskCategoryApi = {
  // ── Types ──
  listTypes: () =>
    apiClient.get<ApiResponse<RiskCategoryTypeResponse[]>>("/v1/risk-category-types").then((r) => r.data),

  listAllTypes: () =>
    apiClient.get<ApiResponse<RiskCategoryTypeResponse[]>>("/v1/risk-category-types/all").then((r) => r.data),

  getType: (id: string) =>
    apiClient.get<ApiResponse<RiskCategoryTypeResponse>>(`/v1/risk-category-types/${id}`).then((r) => r.data),

  createType: (data: CreateRiskCategoryTypeRequest) =>
    apiClient.post<ApiResponse<RiskCategoryTypeResponse>>("/v1/risk-category-types", data).then((r) => r.data),

  updateType: (id: string, data: UpdateRiskCategoryTypeRequest) =>
    apiClient.put<ApiResponse<RiskCategoryTypeResponse>>(`/v1/risk-category-types/${id}`, data).then((r) => r.data),

  deleteType: (id: string) =>
    apiClient.delete(`/v1/risk-category-types/${id}`),

  // ── Categories ──
  listCategories: (params?: { typeId?: string; industry?: Industry }) => {
    const query: Record<string, string> = {};
    if (params?.typeId) query.typeId = params.typeId;
    if (params?.industry) query.industry = params.industry;
    return apiClient
      .get<ApiResponse<RiskCategoryMasterResponse[]>>("/v1/risk-categories", { params: query })
      .then((r) => r.data);
  },

  listAllCategories: () =>
    apiClient.get<ApiResponse<RiskCategoryMasterResponse[]>>("/v1/risk-categories/all").then((r) => r.data),

  getCategory: (id: string) =>
    apiClient.get<ApiResponse<RiskCategoryMasterResponse>>(`/v1/risk-categories/${id}`).then((r) => r.data),

  getCategoryByCode: (code: string) =>
    apiClient
      .get<ApiResponse<RiskCategoryMasterResponse>>(`/v1/risk-categories/by-code/${encodeURIComponent(code)}`)
      .then((r) => r.data),

  createCategory: (data: CreateRiskCategoryMasterRequest) =>
    apiClient.post<ApiResponse<RiskCategoryMasterResponse>>("/v1/risk-categories", data).then((r) => r.data),

  updateCategory: (id: string, data: UpdateRiskCategoryMasterRequest) =>
    apiClient.put<ApiResponse<RiskCategoryMasterResponse>>(`/v1/risk-categories/${id}`, data).then((r) => r.data),

  deleteCategory: (id: string) =>
    apiClient.delete(`/v1/risk-categories/${id}`),
};
