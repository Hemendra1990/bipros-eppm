import { apiClient } from "./client";
import type { ApiResponse } from "../types";
import type { Industry, RiskCategoryMasterResponse } from "./riskCategoryApi";

export type { Industry };

export const INDUSTRY_LABEL: Record<Industry, string> = {
  ROAD: "Road",
  BRIDGE: "Bridge",
  BUILDING: "Building",
  CONSTRUCTION_GENERAL: "Construction (General)",
  REFINERY: "Refinery",
  OIL_GAS: "Oil & Gas",
  RAILWAY: "Railway",
  METRO: "Metro",
  POWER: "Power",
  WATER: "Water",
  MINING: "Mining",
  MANUFACTURING: "Manufacturing",
  PHARMA: "Pharma",
  IT: "Information Technology",
  TELECOM: "Telecom",
  BANKING_FINANCE: "Banking & Finance",
  HEALTHCARE: "Healthcare",
  AGRICULTURE: "Agriculture",
  AEROSPACE_DEFENSE: "Aerospace & Defense",
  MARITIME: "Maritime",
  MASS_EVENT: "Mass Event",
  GENERIC: "Generic",
};

export interface RiskTemplate {
  id: string;
  code: string;
  title: string;
  description: string | null;
  industry: Industry;
  applicableProjectCategories: string[];
  /** Embedded category summary; null if uncategorised. */
  category: RiskCategoryMasterResponse | null;
  defaultProbability: number | null;
  defaultImpactCost: number | null;
  defaultImpactSchedule: number | null;
  mitigationGuidance: string | null;
  isOpportunity: boolean;
  sortOrder: number | null;
  active: boolean;
  systemDefault: boolean;
  createdAt: string;
  updatedAt: string;
  createdBy: string | null;
  updatedBy: string | null;
}

export interface CreateRiskTemplateRequest {
  code: string;
  title: string;
  description?: string | null;
  industry: Industry;
  applicableProjectCategories?: string[];
  /** FK to risk_category_master.id. Optional — admins may create uncategorised templates. */
  categoryId?: string | null;
  defaultProbability?: number | null;
  defaultImpactCost?: number | null;
  defaultImpactSchedule?: number | null;
  mitigationGuidance?: string | null;
  isOpportunity?: boolean;
  sortOrder?: number | null;
  active?: boolean;
}

export type UpdateRiskTemplateRequest = CreateRiskTemplateRequest;

export interface ListTemplatesParams {
  industry?: Industry;
  projectCategory?: string;
  active?: boolean;
}

export const riskTemplateApi = {
  list: (params?: ListTemplatesParams) => {
    const qs: string[] = [];
    if (params?.industry) qs.push(`industry=${params.industry}`);
    if (params?.projectCategory) qs.push(`projectCategory=${encodeURIComponent(params.projectCategory)}`);
    if (params?.active !== undefined) qs.push(`active=${params.active}`);
    const suffix = qs.length ? `?${qs.join("&")}` : "";
    return apiClient
      .get<ApiResponse<RiskTemplate[]>>(`/v1/risk-templates${suffix}`)
      .then((r) => r.data);
  },

  get: (id: string) =>
    apiClient.get<ApiResponse<RiskTemplate>>(`/v1/risk-templates/${id}`).then((r) => r.data),

  create: (request: CreateRiskTemplateRequest) =>
    apiClient
      .post<ApiResponse<RiskTemplate>>("/v1/risk-templates", request)
      .then((r) => r.data),

  update: (id: string, request: UpdateRiskTemplateRequest) =>
    apiClient
      .put<ApiResponse<RiskTemplate>>(`/v1/risk-templates/${id}`, request)
      .then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/v1/risk-templates/${id}`),

  copyToProject: (projectId: string, templateIds: string[]) =>
    apiClient
      .post<ApiResponse<unknown[]>>(
        `/v1/projects/${projectId}/risks/copy-from-templates`,
        { templateIds })
      .then((r) => r.data),
};

/** Map a project's road-flavoured ProjectCategory to the broad Industry tag for filtering. */
export function deriveIndustryFromProjectCategory(category: string | null | undefined): Industry {
  if (!category) return "GENERIC";
  const c = category.toUpperCase();
  if (["HIGHWAY", "EXPRESSWAY", "RURAL_ROAD", "STATE_HIGHWAY", "URBAN_ROAD"].includes(c)) {
    return "ROAD";
  }
  return "GENERIC";
}
