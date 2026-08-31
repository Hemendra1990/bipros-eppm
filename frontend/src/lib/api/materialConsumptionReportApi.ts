import { apiClient } from "./client";
import type { ApiResponse } from "../types";

/**
 * Material Consumption Report (Phase E). Read-only — wraps the
 * {@code MaterialConsumptionReportController} backend endpoint plus an Excel export.
 * Mirrors {@code MaterialConsumptionRow} and {@code MaterialConsumptionReportResponse}
 * from {@code bipros-reporting/.../materialconsumption/}.
 */
export type MaterialConsumptionGroupBy =
  | "DAY"
  | "MATERIAL"
  | "ACTIVITY"
  | "SUPERVISOR";

export type MaterialConsumptionAlertCode =
  | "NEGATIVE_BALANCE"
  | "MISSING_UNIT_RATE";

export interface MaterialConsumptionRow {
  projectId: string;
  fromDate: string | null;
  toDate: string | null;
  wbsNodeId: string | null;
  wbsName: string | null;
  activityId: string | null;
  activityName: string | null;
  supervisorUserId: string | null;
  supervisorName: string | null;
  storekeeperUserId: string | null;
  storekeeperName: string | null;
  materialRateMasterId: string | null;
  materialName: string | null;
  unit: string | null;
  issuedQty: number | null;
  consumedQty: number | null;
  balanceQty: number | null;
  wastagePercent: number | null;
  unitRate: number | null;
  actualCost: number | null;
  alerts: string[];
}

export interface MaterialConsumptionSupervisor {
  userId: string;
  name: string | null;
}

export interface MaterialConsumptionReportResponse {
  from: string | null;
  to: string | null;
  groupBy: MaterialConsumptionGroupBy | null;
  rows: MaterialConsumptionRow[];
  totals: Record<string, number>;
  alertCounts: Record<string, number>;
  supervisors: MaterialConsumptionSupervisor[];
}

export interface MaterialConsumptionFilters {
  from?: string;
  to?: string;
  wbsNodeId?: string;
  activityId?: string;
  supervisorUserId?: string;
  storekeeperUserId?: string;
  materialRateMasterId?: string;
  groupBy?: MaterialConsumptionGroupBy | "";
}

function buildQuery(filters: MaterialConsumptionFilters): string {
  const params = new URLSearchParams();
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  if (filters.wbsNodeId) params.set("wbsNodeId", filters.wbsNodeId);
  if (filters.activityId) params.set("activityId", filters.activityId);
  if (filters.supervisorUserId) params.set("supervisorUserId", filters.supervisorUserId);
  if (filters.storekeeperUserId) params.set("storekeeperUserId", filters.storekeeperUserId);
  if (filters.materialRateMasterId)
    params.set("materialRateMasterId", filters.materialRateMasterId);
  if (filters.groupBy) params.set("groupBy", filters.groupBy);
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

/** Mirrors MaterialBalanceRow (bipros-resource) — the availability engine's per-material line. */
export interface MaterialBalanceRow {
  materialKey: string;
  materialName: string | null;
  unit: string | null;
  receivedWindow: number | null;
  issuedWindow: number | null;
  consumedWindow: number | null;
  receivedToDate: number | null;
  issuedToDate: number | null;
  consumedToDate: number | null;
  storeClosing: number | null;
  siteBalance: number | null;
  minStockLevel: number | null;
  avgDailyConsumption: number | null;
  daysOfCover: number | null;
  /** Ageing over siteBalance: days since the earliest issue, as of the To date. */
  daysHeld: number | null;
  alerts: string[];
}

export interface MaterialAvailabilityResult {
  tracked: boolean;
  rows: MaterialBalanceRow[];
}

/** Mirrors SupervisorMaterialRow — issued vs DPR-reported per (supervisor × material). */
export interface SupervisorMaterialRow {
  supervisorKey: string;
  supervisorName: string | null;
  materialName: string | null;
  unit: string | null;
  issuedToDate: number;
  reportedToDate: number;
  varianceQty: number;
  varianceValue: number | null;
  wastageQty: number | null;
  issuedWindow: number | null;
  reportedWindow: number | null;
}

export const materialConsumptionReportApi = {
  generate: (projectId: string, filters: MaterialConsumptionFilters = {}) =>
    apiClient
      .get<ApiResponse<MaterialConsumptionReportResponse>>(
        `/v1/projects/${projectId}/reports/material-consumption${buildQuery(filters)}`,
      )
      .then((r) => r.data),

  availability: (projectId: string, from?: string, to?: string) => {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    const qs = params.toString();
    return apiClient
      .get<ApiResponse<MaterialAvailabilityResult>>(
        `/v1/projects/${projectId}/reports/material-consumption/availability${qs ? `?${qs}` : ""}`,
      )
      .then((r) => r.data);
  },

  supervisorComparison: (projectId: string, asOf?: string, windowFrom?: string) => {
    const params = new URLSearchParams();
    if (asOf) params.set("asOf", asOf);
    if (windowFrom) params.set("windowFrom", windowFrom);
    const qs = params.toString();
    return apiClient
      .get<ApiResponse<SupervisorMaterialRow[]>>(
        `/v1/projects/${projectId}/reports/material-consumption/supervisor-comparison${qs ? `?${qs}` : ""}`,
      )
      .then((r) => r.data);
  },

  downloadExcel: async (projectId: string, filters: MaterialConsumptionFilters = {}) => {
    const response = await apiClient.get<Blob>(
      `/v1/projects/${projectId}/reports/material-consumption/export.xlsx${buildQuery(filters)}`,
      { responseType: "blob" },
    );
    return response.data;
  },
};
