import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export type CapacityGroupBy = "RESOURCE_TYPE" | "RESOURCE";
export type CapacityNormType = "MANPOWER" | "EQUIPMENT";
export type BudgetedSource =
  | "SPECIFIC_RESOURCE"
  | "RESOURCE_TYPE"
  | "RESOURCE_LEGACY"
  | "NONE";

export interface CapacityPeriod {
  qty: number | null;
  budgetedDays: number | null;
  actualDays: number | null;
  actualOutputPerDay: number | null;
  utilizationPct: number | null;
}

export interface CapacityUtilizationRow {
  groupKey: {
    resourceTypeDefId: string | null;
    resourceId: string | null;
    displayLabel: string;
  };
  workActivity: {
    id: string;
    code: string;
    name: string;
    defaultUnit: string | null;
  };
  budgeted: {
    outputPerDay: number | null;
    source: BudgetedSource;
  };
  forTheDay: CapacityPeriod;
  forTheMonth: CapacityPeriod;
  cumulative: CapacityPeriod;
}

export interface CapacityUtilizationReport {
  projectId: string;
  fromDate: string | null;
  toDate: string | null;
  groupBy: CapacityGroupBy;
  normType: CapacityNormType | null;
  rows: CapacityUtilizationRow[];
}

export interface GetCapacityUtilizationParams {
  projectId: string;
  fromDate?: string;
  toDate?: string;
  groupBy?: CapacityGroupBy;
  normType?: CapacityNormType;
}

export const capacityUtilizationApi = {
  get: (params: GetCapacityUtilizationParams) => {
    const qs: string[] = [`projectId=${params.projectId}`];
    if (params.fromDate) qs.push(`fromDate=${params.fromDate}`);
    if (params.toDate) qs.push(`toDate=${params.toDate}`);
    if (params.groupBy) qs.push(`groupBy=${params.groupBy}`);
    if (params.normType) qs.push(`normType=${params.normType}`);
    return apiClient
      .get<ApiResponse<CapacityUtilizationReport>>(`/v1/reports/capacity-utilization?${qs.join("&")}`)
      .then((r) => r.data);
  },
};
