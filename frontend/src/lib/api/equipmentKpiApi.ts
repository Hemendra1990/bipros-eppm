import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface UtilizationRow {
  resourceId: string;
  resourceCode: string;
  resourceName: string;
  operatingHours: number;
  idleHours: number;
  breakdownHours: number;
  utilizationPct: number;
}

export interface IdleAlertRow {
  resourceId: string;
  resourceCode: string;
  resourceName: string;
  logDate: string;
  idleHours: number;
}

export interface FuelPerOutputRow {
  resourceId: string;
  resourceCode: string;
  resourceName: string;
  fuelConsumed: number;
  qtyExecuted: number;
  fuelPerOutput: number;
}

export interface AvailabilityPerformanceRow {
  resourceId: string;
  resourceCode: string;
  resourceName: string;
  availability: number;
  performance: number;
}

export interface OwnedRentedSlice {
  ownershipType: string;
  operatingHours: number;
  cost: number;
}

export interface ServiceDueRow {
  resourceId: string;
  resourceCode: string;
  resourceName: string;
  nextServiceDate: string;
  daysUntilService: number;
}

export interface EquipmentKpiResponse {
  projectId: string;
  from: string;
  to: string;
  utilization: UtilizationRow[];
  idleAlerts: IdleAlertRow[];
  fuelPerOutput: FuelPerOutputRow[];
  availabilityPerformance: AvailabilityPerformanceRow[];
  ownedVsRented: OwnedRentedSlice[];
  serviceDue: ServiceDueRow[];
}

export const equipmentKpiApi = {
  getKpis: (projectId: string, from: string, to: string) =>
    apiClient
      .get<ApiResponse<EquipmentKpiResponse>>(
        `/v1/projects/${projectId}/kpis/equipment`,
        { params: { from, to } },
      )
      .then((r) => r.data),
};
