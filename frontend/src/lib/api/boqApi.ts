import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface BoqItemResponse {
  id: string;
  projectId: string;
  itemNo: string;
  description: string;
  unit: string;
  wbsNodeId: string | null;
  boqQty: number | null;
  boqRate: number | null;
  boqAmount: number | null;
  budgetedRate: number | null;
  budgetedAmount: number | null;
  qtyExecutedToDate: number | null;
  actualRate: number | null;
  actualAmount: number | null;
  percentComplete: number | null;
  costVariance: number | null;
  costVariancePercent: number | null;
}

export interface BoqSummaryResponse {
  items: BoqItemResponse[];
  boqGrandTotal: number;
  budgetedGrandTotal: number;
  actualGrandTotal: number;
  grandCostVariance: number;
  grandCostVariancePercent: number | null;
  overallPercentComplete: number | null;
}

export interface CreateBoqItemRequest {
  itemNo: string;
  description: string;
  unit: string;
  wbsNodeId?: string | null;
  boqQty?: number;
  boqRate?: number;
  budgetedRate?: number;
  qtyExecutedToDate?: number;
  actualRate?: number;
}

export interface UpdateBoqItemRequest {
  description?: string | null;
  unit?: string | null;
  wbsNodeId?: string | null;
  boqQty?: number | null;
  boqRate?: number | null;
  budgetedRate?: number | null;
  qtyExecutedToDate?: number | null;
  actualRate?: number | null;
}

export const boqApi = {
  list: (projectId: string) =>
    apiClient
      .get<ApiResponse<BoqSummaryResponse>>(`/v1/projects/${projectId}/boq`)
      .then((r) => r.data),

  get: (projectId: string, itemId: string) =>
    apiClient
      .get<ApiResponse<BoqItemResponse>>(`/v1/projects/${projectId}/boq/${itemId}`)
      .then((r) => r.data),

  create: (projectId: string, request: CreateBoqItemRequest) =>
    apiClient
      .post<ApiResponse<BoqItemResponse>>(`/v1/projects/${projectId}/boq`, request)
      .then((r) => r.data),

  createBulk: (projectId: string, requests: CreateBoqItemRequest[]) =>
    apiClient
      .post<ApiResponse<BoqItemResponse[]>>(`/v1/projects/${projectId}/boq/bulk`, requests)
      .then((r) => r.data),

  update: (projectId: string, itemId: string, request: UpdateBoqItemRequest) =>
    apiClient
      .patch<ApiResponse<BoqItemResponse>>(`/v1/projects/${projectId}/boq/${itemId}`, request)
      .then((r) => r.data),

  delete: (projectId: string, itemId: string) =>
    apiClient.delete(`/v1/projects/${projectId}/boq/${itemId}`),
};
