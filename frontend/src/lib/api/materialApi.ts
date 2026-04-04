import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface MaterialReconciliationResponse {
  id: string;
  resourceId: string;
  projectId: string;
  wbsNodeId: string | null;
  period: string;
  openingBalance: number;
  received: number;
  consumed: number;
  wastage: number;
  closingBalance: number;
  unit: string | null;
  remarks: string | null;
  createdAt: string;
  createdBy: string | null;
}

export interface CreateMaterialReconciliationRequest {
  resourceId: string;
  projectId: string;
  wbsNodeId?: string;
  period: string;
  openingBalance: number;
  received: number;
  consumed: number;
  wastage?: number;
  unit?: string;
  remarks?: string;
}

export const materialApi = {
  createReconciliation: (projectId: string, data: CreateMaterialReconciliationRequest) =>
    apiClient
      .post<ApiResponse<MaterialReconciliationResponse>>(
        `/v1/projects/${projectId}/material-reconciliation`,
        data
      )
      .then((r) => r.data),

  getReconciliations: (projectId: string, period?: string) =>
    apiClient
      .get<ApiResponse<MaterialReconciliationResponse[]>>(
        `/v1/projects/${projectId}/material-reconciliation`,
        {
          params: {
            ...(period ? { period } : {}),
          },
        }
      )
      .then((r) => r.data),

  getByResource: (projectId: string, resourceId: string) =>
    apiClient
      .get<ApiResponse<MaterialReconciliationResponse[]>>(
        `/v1/projects/${projectId}/material-reconciliation/resource/${resourceId}`
      )
      .then((r) => r.data),

  getById: (projectId: string, id: string) =>
    apiClient
      .get<ApiResponse<MaterialReconciliationResponse>>(
        `/v1/projects/${projectId}/material-reconciliation/${id}`
      )
      .then((r) => r.data),
};
