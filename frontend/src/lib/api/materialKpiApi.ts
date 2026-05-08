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
}

export const materialKpiApi = {
  getKpis: (projectId: string, from: string, to: string) =>
    apiClient
      .get<ApiResponse<MaterialKpiResponse>>(
        `/v1/projects/${projectId}/kpis/material`,
        { params: { from, to } },
      )
      .then((r) => r.data),
};
