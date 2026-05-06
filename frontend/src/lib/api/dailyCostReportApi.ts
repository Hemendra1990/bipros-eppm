import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface DailyCostReportRow {
  dprId: string;
  date: string;
  activity: string;
  qtyExecuted: number;
  unit: string;
  boqItemNo: string | null;
  budgetedUnitRate: number | null;
  actualUnitRate: number | null;
  budgetedCost: number | null;
  actualCost: number | null;
  variance: number | null;
  variancePercent: number | null;
  supervisor: string;
}

export interface DailyCostReportResponse {
  from: string | null;
  to: string | null;
  rows: DailyCostReportRow[];
  periodBudgetedCost: number;
  periodActualCost: number;
  periodVariance: number;
  periodVariancePercent: number | null;
}

export interface DailyCostReportFilters {
  from?: string;
  to?: string;
}

export const dailyCostReportApi = {
  generate: (projectId: string, filters: DailyCostReportFilters = {}) => {
    const params = new URLSearchParams();
    if (filters.from) params.set("from", filters.from);
    if (filters.to) params.set("to", filters.to);
    const qs = params.toString() ? `?${params.toString()}` : "";
    return apiClient
      .get<ApiResponse<DailyCostReportResponse>>(`/v1/projects/${projectId}/daily-cost-report${qs}`)
      .then((r) => r.data);
  },
};
