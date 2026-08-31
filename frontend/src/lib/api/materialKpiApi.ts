import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface MaterialBreakdownRow {
  materialName: string;
  issuedQty: number;
  consumedQty: number;
  wastageQty: number;
  utilizationPct: number;
  avgUnitRate: number;
}

/**
 * KPI 9.5 — Material Cost / Unit Finished Work, per activity.
 * varianceVsBoqPct positive = under budget (favourable).
 */
export interface CostPerUnitRow {
  activityId: string;
  activityName: string;
  materialCost: number;
  qtyFinished: number;
  costPerUnit: number;
  boqBudgetedRate: number | null;
  varianceVsBoqPct: number | null;
}

export interface MaterialKpiResponse {
  projectId: string;
  from: string;
  to: string;
  issuedQty: number;
  consumedQty: number;
  wastageQty: number;
  materialUtilizationPct: number;
  wastagePct: number;
  reconciliationBalance: number;
  materialPriceVariance: number | null;
  materialUsageVariance: number | null;
  totalMaterialCostVariance: number | null;
  byMaterial: MaterialBreakdownRow[];
  weightedAvgCostPerUnitFinished: number;
  costPerUnitByActivity: CostPerUnitRow[];
}

export const materialKpiApi = {
  getKpis: (projectId: string, from?: string, to?: string) =>
    apiClient
      .get<ApiResponse<MaterialKpiResponse>>(
        `/v1/projects/${projectId}/kpis/material`,
        { params: from && to ? { from, to } : {} },
      )
      .then((r) => r.data),
};
