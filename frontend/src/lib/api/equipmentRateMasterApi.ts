import { apiClient } from "./client";
import type { ApiResponse } from "../types";

/**
 * Equipment Rate Master — one row per (Equipment Name + Make + Model). Carries unit
 * and cost-per-unit that snapshot onto every Equipment {@code Resource} pointing at
 * this row. Revising rate or unit cascades to all linked Resources.
 */
export interface EquipmentRateMaster {
  id: string;
  equipmentName: string;
  make: string;
  model: string;
  unit: string;
  rate: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface EquipmentRateMasterRequest {
  equipmentName: string;
  make: string;
  model: string;
  unit: string;
  rate: number;
  active?: boolean;
}

export const equipmentRateMasterApi = {
  list: () =>
    apiClient
      .get<ApiResponse<EquipmentRateMaster[]>>("/v1/equipment-rate-master")
      .then((r) => r.data),

  get: (id: string) =>
    apiClient
      .get<ApiResponse<EquipmentRateMaster>>(`/v1/equipment-rate-master/${id}`)
      .then((r) => r.data),

  create: (request: EquipmentRateMasterRequest) =>
    apiClient
      .post<ApiResponse<EquipmentRateMaster>>("/v1/equipment-rate-master", request)
      .then((r) => r.data),

  update: (id: string, request: EquipmentRateMasterRequest) =>
    apiClient
      .put<ApiResponse<EquipmentRateMaster>>(`/v1/equipment-rate-master/${id}`, request)
      .then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/v1/equipment-rate-master/${id}`),
};
