import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export type ProductivityNormType = "MANPOWER" | "EQUIPMENT";

export interface ProductivityNormResponse {
  id: string;
  normType: ProductivityNormType;
  activityName: string;
  unit: string;
  outputPerManPerDay: number | null;
  outputPerHour: number | null;
  crewSize: number | null;
  outputPerDay: number | null;
  workingHoursPerDay: number | null;
  fuelLitresPerHour: number | null;
  equipmentSpec: string | null;
  remarks: string | null;
}

export interface CreateProductivityNormRequest {
  normType: ProductivityNormType;
  activityName: string;
  unit: string;
  outputPerManPerDay?: number | null;
  outputPerHour?: number | null;
  crewSize?: number | null;
  outputPerDay?: number | null;
  workingHoursPerDay?: number | null;
  fuelLitresPerHour?: number | null;
  equipmentSpec?: string | null;
  remarks?: string | null;
}

export const productivityNormApi = {
  list: (normType?: ProductivityNormType) => {
    const qs = normType ? `?normType=${normType}` : "";
    return apiClient
      .get<ApiResponse<ProductivityNormResponse[]>>(`/v1/productivity-norms${qs}`)
      .then((r) => r.data);
  },

  get: (id: string) =>
    apiClient
      .get<ApiResponse<ProductivityNormResponse>>(`/v1/productivity-norms/${id}`)
      .then((r) => r.data),

  create: (request: CreateProductivityNormRequest) =>
    apiClient
      .post<ApiResponse<ProductivityNormResponse>>(`/v1/productivity-norms`, request)
      .then((r) => r.data),

  createBulk: (requests: CreateProductivityNormRequest[]) =>
    apiClient
      .post<ApiResponse<ProductivityNormResponse[]>>(`/v1/productivity-norms/bulk`, requests)
      .then((r) => r.data),

  update: (id: string, request: CreateProductivityNormRequest) =>
    apiClient
      .put<ApiResponse<ProductivityNormResponse>>(`/v1/productivity-norms/${id}`, request)
      .then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/v1/productivity-norms/${id}`),
};
