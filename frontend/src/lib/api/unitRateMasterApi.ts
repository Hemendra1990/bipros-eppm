import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export type UnitRateCategory = "Equipment" | "Manpower" | "Material" | "Sub-Contract";
export type UnitRateSource = "RESOURCE" | "RESOURCE_ROLE";

export interface UnitRateMasterRow {
  id: string;
  source: UnitRateSource;
  category: UnitRateCategory | string;
  description: string;
  unit: string | null;
  budgetedRate: number | null;
  actualRate: number | null;
  variance: number | null;
  variancePercent: number | null;
  remarks: string | null;
}

export type UnitRateCategoryFilter = "EQUIPMENT" | "MANPOWER" | "MATERIAL" | "SUB_CONTRACT";

export interface ResourceRateRow {
  id: string;
  resourceId: string;
  rateType: string;
  pricePerUnit: number | null;
  budgetedRate: number | null;
  actualRate: number | null;
  variance: number | null;
  variancePercent: number | null;
  effectiveDate: string;
  effectiveTo: string | null;
  category: UnitRateCategoryFilter | null;
  approvedByUserId: string | null;
  approvedByName: string | null;
  maxUnitsPerTime: number | null;
}

export interface CreateResourceRateRequest {
  rateType?: string | null;
  pricePerUnit?: number | null;
  budgetedRate?: number | null;
  actualRate?: number | null;
  overtimeRate?: number | null;
  effectiveDate: string;
  effectiveTo?: string | null;
  category?: UnitRateCategoryFilter | null;
  approvedByUserId?: string | null;
  approvedByName?: string | null;
  maxUnitsPerTime?: number | null;
}

export const unitRateMasterApi = {
  list: (category?: UnitRateCategoryFilter) => {
    const qs = category ? `?category=${category}` : "";
    return apiClient
      .get<ApiResponse<UnitRateMasterRow[]>>(`/v1/unit-rate-master${qs}`)
      .then((r) => r.data);
  },

  listForResource: (resourceId: string) =>
    apiClient
      .get<ApiResponse<ResourceRateRow[]>>(`/v1/unit-rate-master/resources/${resourceId}/rates`)
      .then((r) => r.data),

  createForResource: (resourceId: string, body: CreateResourceRateRequest) =>
    apiClient
      .post<ApiResponse<ResourceRateRow>>(
        `/v1/unit-rate-master/resources/${resourceId}/rates`,
        body,
      )
      .then((r) => r.data),

  updateRate: (rateId: string, body: CreateResourceRateRequest) =>
    apiClient
      .put<ApiResponse<ResourceRateRow>>(`/v1/unit-rate-master/rates/${rateId}`, body)
      .then((r) => r.data),

  deleteRate: (rateId: string) =>
    apiClient
      .delete<ApiResponse<void>>(`/v1/unit-rate-master/rates/${rateId}`)
      .then((r) => r.data),
};
