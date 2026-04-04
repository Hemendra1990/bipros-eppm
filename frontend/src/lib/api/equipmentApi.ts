import { apiClient } from "./client";
import type { ApiResponse, PagedResponse } from "../types";

export interface EquipmentLogResponse {
  id: string;
  resourceId: string;
  projectId: string;
  logDate: string;
  deploymentSite: string | null;
  operatingHours: number | null;
  idleHours: number | null;
  breakdownHours: number | null;
  fuelConsumed: number | null;
  operatorName: string | null;
  remarks: string | null;
  status: "WORKING" | "IDLE" | "UNDER_MAINTENANCE" | "BREAKDOWN";
  createdAt: string;
  createdBy: string | null;
}

export interface CreateEquipmentLogRequest {
  resourceId: string;
  projectId: string;
  logDate: string;
  deploymentSite?: string;
  operatingHours?: number;
  idleHours?: number;
  breakdownHours?: number;
  fuelConsumed?: number;
  operatorName?: string;
  remarks?: string;
  status?: "WORKING" | "IDLE" | "UNDER_MAINTENANCE" | "BREAKDOWN";
}

export interface EquipmentUtilizationSummary {
  resourceId: string;
  resourceName: string;
  totalOperatingHours: number;
  totalIdleHours: number;
  totalBreakdownHours: number;
  utilizationPercentage: number;
  totalAvailableHours: number;
}

export const equipmentApi = {
  createLog: (projectId: string, data: CreateEquipmentLogRequest) =>
    apiClient
      .post<ApiResponse<EquipmentLogResponse>>(
        `/v1/projects/${projectId}/equipment-logs`,
        data
      )
      .then((r) => r.data),

  getLogsByProject: (
    projectId: string,
    page = 0,
    size = 20,
    filters?: {
      resourceId?: string;
      fromDate?: string;
      toDate?: string;
    }
  ) =>
    apiClient
      .get<ApiResponse<PagedResponse<EquipmentLogResponse>>>(
        `/v1/projects/${projectId}/equipment-logs`,
        {
          params: {
            page,
            size,
            ...filters,
          },
        }
      )
      .then((r) => r.data),

  getUtilizationSummary: (projectId: string) =>
    apiClient
      .get<ApiResponse<EquipmentUtilizationSummary[]>>(
        `/v1/projects/${projectId}/equipment-logs/utilization`
      )
      .then((r) => r.data),
};
