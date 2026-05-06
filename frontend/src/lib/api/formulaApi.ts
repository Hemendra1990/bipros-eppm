import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export type FormulaCategory =
  | "EVM"
  | "COST"
  | "SCHEDULING"
  | "RESOURCE"
  | "REPORTING"
  | "PORTFOLIO"
  | "BASELINE"
  | "PREDICTION"
  | "BOQ"
  | "GENERAL";

export type FormulaOutputType = "NUMBER" | "PERCENTAGE" | "CURRENCY" | "BOOLEAN" | "INTEGER";

export interface FormulaDto {
  id: string;
  code: string;
  name: string;
  category: FormulaCategory;
  description?: string | null;
  defaultExpression: string;
  inputVariablesJson?: string | null;
  outputType: FormulaOutputType;
  scale: number;
  roundingMode: string;
  zeroDefault?: string | null;
  isActive: boolean;
  isEditable: boolean;
  sortOrder?: number | null;
  moduleSource?: string | null;
  formulaVersion?: number | null;
}

export interface FormulaOverrideDto {
  id: string;
  formulaCode: string;
  projectId: string;
  overrideExpression: string;
  isActive: boolean;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
  overrideReason?: string | null;
  overrideVersion?: number | null;
}

export interface FormulaResultDto {
  formulaCode: string;
  expressionUsed: string;
  value: string;
  formatted: string;
  error: boolean;
  errorMessage?: string | null;
}

export interface CreateFormulaRequest {
  code: string;
  name: string;
  category: FormulaCategory;
  description?: string | null;
  defaultExpression: string;
  inputVariablesJson?: string | null;
  outputType: FormulaOutputType;
  scale?: number | null;
  roundingMode?: string | null;
  zeroDefault?: string | null;
  isActive?: boolean | null;
  isEditable?: boolean | null;
  sortOrder?: number | null;
  moduleSource?: string | null;
}

export interface CreateFormulaOverrideRequest {
  formulaCode: string;
  projectId: string;
  overrideExpression: string;
  isActive?: boolean | null;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
  overrideReason?: string | null;
}

export interface EvaluateFormulaRequest {
  formulaCode: string;
  projectId?: string | null;
  variables: Record<string, string>;
}

export const formulaApi = {
  list: () =>
    apiClient.get<ApiResponse<FormulaDto[]>>("/v1/formulas").then((r) => r.data),

  listByCategory: () =>
    apiClient
      .get<ApiResponse<{ code: string; name: string; description?: string | null; formulas: FormulaDto[] }[]>>(
        "/v1/formulas/by-category"
      )
      .then((r) => r.data),

  getByCode: (code: string) =>
    apiClient.get<ApiResponse<FormulaDto>>(`/v1/formulas/code/${code}`).then((r) => r.data),

  create: (request: CreateFormulaRequest) =>
    apiClient.post<ApiResponse<FormulaDto>>("/v1/formulas", request).then((r) => r.data),

  update: (id: string, request: CreateFormulaRequest) =>
    apiClient.put<ApiResponse<FormulaDto>>(`/v1/formulas/${id}`, request).then((r) => r.data),

  evaluate: (request: EvaluateFormulaRequest) =>
    apiClient.post<ApiResponse<FormulaResultDto>>("/v1/formulas/evaluate", request).then((r) => r.data),

  // Overrides
  listOverridesByProject: (projectId: string) =>
    apiClient
      .get<ApiResponse<FormulaOverrideDto[]>>(`/v1/formulas/overrides/project/${projectId}`)
      .then((r) => r.data),

  listOverridesByFormula: (formulaCode: string) =>
    apiClient
      .get<ApiResponse<FormulaOverrideDto[]>>(`/v1/formulas/overrides/formula/${formulaCode}`)
      .then((r) => r.data),

  createOverride: (request: CreateFormulaOverrideRequest) =>
    apiClient
      .post<ApiResponse<FormulaOverrideDto>>("/v1/formulas/overrides", request)
      .then((r) => r.data),

  updateOverride: (id: string, request: CreateFormulaOverrideRequest) =>
    apiClient
      .put<ApiResponse<FormulaOverrideDto>>(`/v1/formulas/overrides/${id}`, request)
      .then((r) => r.data),

  deleteOverride: (id: string) => apiClient.delete(`/v1/formulas/overrides/${id}`),
};
