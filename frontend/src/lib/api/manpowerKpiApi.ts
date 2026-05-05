import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface WorkforceUtilization {
  actualHours: number;
  availableHours: number;
  utilizationPct: number;
  laborResourceCount: number;
}

export interface ProductivityFactorRow {
  activityId: string;
  activityName: string;
  actualOutputPerManPerDay: number;
  normOutputPerManPerDay: number;
  factor: number;
}

export interface LabourCostPerUnitRow {
  boqItemId: string;
  itemNo: string;
  description: string;
  unit: string | null;
  labourCost: number;
  qtyExecuted: number;
  costPerUnit: number;
}

export interface CrewOutputRow {
  activityId: string;
  activityName: string;
  crewSize: number | null;
  actualOutputPerDay: number;
  normOutputPerDay: number;
  deviationPct: number;
}

export interface ManpowerKpiResponse {
  projectId: string;
  from: string;
  to: string;
  workforceUtilization: WorkforceUtilization;
  productivityFactor: ProductivityFactorRow[];
  labourCostPerUnit: LabourCostPerUnitRow[];
  crewOutput: CrewOutputRow[];
}

export const manpowerKpiApi = {
  /**
   * Composite manpower KPIs for the period [from..to]. Single network call returns all
   * the metrics the dashboards need; the components pick which slices to render.
   */
  getKpis: (projectId: string, from: string, to: string) =>
    apiClient
      .get<ApiResponse<ManpowerKpiResponse>>(
        `/v1/projects/${projectId}/kpis/manpower`,
        { params: { from, to } },
      )
      .then((r) => r.data),
};
