import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export type CapacityGroupBy = "RESOURCE_TYPE" | "RESOURCE";
export type CapacityNormType = "MANPOWER" | "EQUIPMENT";
export type BudgetedSource =
  | "SPECIFIC_RESOURCE"
  | "RESOURCE_TYPE"
  | "WORK_ACTIVITY"
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
  supervisorResourceId?: string;
}

// ───────────────── Supervisor Performance (SC180-style rollup) ──────────────────

export interface TradeRollup {
  tradeKey: string;
  tradeLabel: string;
  mmRate: number | null;
  budgetedManDays: number | null;
  budgetedNos: number | null;
  actualManDays: number | null;
  actualNos: number | null;
  utilizationPct: number | null;
  costImplication: number | null;
  normSource: BudgetedSource;
}

export interface EquipmentRollup {
  equipmentKey: string;
  equipmentLabel: string;
  hourRate: number | null;
  budgetedDays: number | null;
  budgetedNos: number | null;
  actualDays: number | null;
  actualNos: number | null;
  utilizationPct: number | null;
  costImplication: number | null;
  normSource: BudgetedSource;
}

export interface ProductivityNorms {
  budget: number | null;
  projection: number | null;
  actualsFtm: number | null;
  normSource: BudgetedSource;
}

export interface PlannedActuals {
  qty: number | null;
  budgetDays: number | null;
  days: number | null;
  utilizationPct: number | null;
}

export interface ResourceLine {
  kind: "MANPOWER" | "EQUIPMENT";
  resourceKey: string;
  resourceLabel: string;
  norms: ProductivityNorms;
  planMonth: PlannedActuals;
  actualMonth: PlannedActuals;
}

export interface ActivityDrillDown {
  activityId: string;
  activityCode: string | null;
  activityName: string;
  unit: string | null;
  qtyForMonth: number | null;
  resources: ResourceLine[];
  remarks: string | null;
}

export interface SupervisorPerformanceReport {
  projectId: string;
  supervisorResourceId: string | null;
  supervisorName: string | null;
  fromDate: string;
  toDate: string;
  workDays: number;
  summary: {
    manpower: TradeRollup[];
    equipment: EquipmentRollup[];
  };
  activities: ActivityDrillDown[];
}

export interface SupervisorPerformanceComparison {
  projectId: string;
  fromDate: string;
  toDate: string;
  workDays: number;
  reports: SupervisorPerformanceReport[];
  tradeDeltas: Array<{
    tradeKey: string;
    tradeLabel: string;
    bySupervisor: Record<string, TradeRollup>;
    bestUtilizationPct: number | null;
    bestSupervisorId: string | null;
  }>;
  equipmentDeltas: Array<{
    equipmentKey: string;
    equipmentLabel: string;
    bySupervisor: Record<string, EquipmentRollup>;
    bestUtilizationPct: number | null;
    bestSupervisorId: string | null;
  }>;
}

export interface SupervisorOption {
  supervisorResourceId: string;
  supervisorCode: string | null;
  supervisorName: string;
  dprCount: number;
}

export interface GetSupervisorPerformanceParams {
  projectId: string;
  supervisorResourceId?: string;
  fromDate?: string;
  toDate?: string;
  workDays?: number;
}

export interface CompareSupervisorPerformanceParams {
  projectId: string;
  supervisorResourceIds: string[];
  fromDate?: string;
  toDate?: string;
  workDays?: number;
}

export const capacityUtilizationApi = {
  get: (params: GetCapacityUtilizationParams) => {
    const qs: string[] = [`projectId=${params.projectId}`];
    if (params.fromDate) qs.push(`fromDate=${params.fromDate}`);
    if (params.toDate) qs.push(`toDate=${params.toDate}`);
    if (params.groupBy) qs.push(`groupBy=${params.groupBy}`);
    if (params.normType) qs.push(`normType=${params.normType}`);
    if (params.supervisorResourceId)
      qs.push(`supervisorResourceId=${params.supervisorResourceId}`);
    return apiClient
      .get<ApiResponse<CapacityUtilizationReport>>(`/v1/reports/capacity-utilization?${qs.join("&")}`)
      .then((r) => r.data);
  },

  getSupervisorPerformance: (params: GetSupervisorPerformanceParams) => {
    const qs: string[] = [`projectId=${params.projectId}`];
    if (params.supervisorResourceId)
      qs.push(`supervisorResourceId=${params.supervisorResourceId}`);
    if (params.fromDate) qs.push(`fromDate=${params.fromDate}`);
    if (params.toDate) qs.push(`toDate=${params.toDate}`);
    if (params.workDays) qs.push(`workDays=${params.workDays}`);
    return apiClient
      .get<ApiResponse<SupervisorPerformanceReport>>(
        `/v1/reports/supervisor-performance?${qs.join("&")}`,
      )
      .then((r) => r.data);
  },

  compareSupervisorPerformance: (params: CompareSupervisorPerformanceParams) => {
    const qs: string[] = [
      `projectId=${params.projectId}`,
      `supervisorResourceIds=${params.supervisorResourceIds.join(",")}`,
    ];
    if (params.fromDate) qs.push(`fromDate=${params.fromDate}`);
    if (params.toDate) qs.push(`toDate=${params.toDate}`);
    if (params.workDays) qs.push(`workDays=${params.workDays}`);
    return apiClient
      .get<ApiResponse<SupervisorPerformanceComparison>>(
        `/v1/reports/supervisor-performance/compare?${qs.join("&")}`,
      )
      .then((r) => r.data);
  },

  getSupervisorsUsed: (params: {
    projectId: string;
    fromDate?: string;
    toDate?: string;
  }) => {
    const qs: string[] = [];
    if (params.fromDate) qs.push(`fromDate=${params.fromDate}`);
    if (params.toDate) qs.push(`toDate=${params.toDate}`);
    const tail = qs.length ? `?${qs.join("&")}` : "";
    return apiClient
      .get<ApiResponse<SupervisorOption[]>>(
        `/v1/projects/${params.projectId}/dpr/supervisors-used${tail}`,
      )
      .then((r) => r.data);
  },
};
