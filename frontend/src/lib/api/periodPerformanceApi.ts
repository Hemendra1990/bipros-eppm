import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface FinancialPeriod {
  id: string;
  name: string;
  startDate: string;
  endDate: string;
  periodType: string | null;
  isClosed: boolean;
  sortOrder: number | null;
}

export interface StorePeriodPerformance {
  id: string;
  projectId: string;
  financialPeriodId: string;
  activityId: string | null;
  actualLaborCost: number | null;
  actualNonlaborCost: number | null;
  actualMaterialCost: number | null;
  actualExpenseCost: number | null;
  actualLaborUnits: number | null;
  actualNonlaborUnits: number | null;
  actualMaterialUnits: number | null;
  earnedValueCost: number | null;
  plannedValueCost: number | null;
}

export interface CreateStorePeriodPerformanceRequest {
  projectId: string;
  financialPeriodId: string;
  activityId?: string | null;
  actualLaborCost?: number | null;
  actualNonlaborCost?: number | null;
  actualMaterialCost?: number | null;
  actualExpenseCost?: number | null;
  actualLaborUnits?: number | null;
  actualNonlaborUnits?: number | null;
  actualMaterialUnits?: number | null;
  earnedValueCost?: number | null;
  plannedValueCost?: number | null;
}

export interface PeriodPerformanceRollup {
  periodId: string;
  periodName: string;
  periodType: string | null;
  startDate: string;
  endDate: string;
  actualCost: number;
  plannedValue: number;
  earnedValue: number;
  cv: number;
  sv: number;
  cpi: number | null;
  spi: number | null;
}

function triggerBlobDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

export const periodPerformanceApi = {
  /** Excel download for the Performance screen (Access-Output row 5). Server gate: COST.READ + REPORT.EXPORT. */
  downloadPerformanceExcel: async (projectId: string, cadence: string) => {
    const res = await apiClient.get<Blob>(
      `/v1/projects/${projectId}/performance/export.xlsx`,
      { params: { periodType: cadence }, responseType: "blob" },
    );
    triggerBlobDownload(res.data, `performance-${cadence}.xlsx`);
  },

  getAllFinancialPeriods: () =>
    apiClient
      .get<ApiResponse<FinancialPeriod[]>>("/v1/financial-periods")
      .then((r) => r.data),

  getOpenFinancialPeriods: () =>
    apiClient
      .get<ApiResponse<FinancialPeriod[]>>("/v1/financial-periods/open")
      .then((r) => r.data),

  getProjectPeriodPerformance: (projectId: string) =>
    apiClient
      .get<ApiResponse<StorePeriodPerformance[]>>(`/v1/projects/${projectId}/spp`)
      .then((r) => r.data),

  getProjectLevelPerformance: (projectId: string, periodId: string) =>
    apiClient
      .get<ApiResponse<StorePeriodPerformance>>(`/v1/projects/${projectId}/spp/${periodId}`)
      .then((r) => r.data),

  createStorePeriodPerformance: (
    projectId: string,
    data: CreateStorePeriodPerformanceRequest
  ) =>
    apiClient
      .post<ApiResponse<StorePeriodPerformance>>(`/v1/projects/${projectId}/spp`, data)
      .then((r) => r.data),

  deleteStorePeriodPerformance: (projectId: string, sppId: string) =>
    apiClient
      .delete<ApiResponse<void>>(`/v1/projects/${projectId}/spp/${sppId}`)
      .then((r) => r.data),

  getPerformanceRollup: (projectId: string, periodType: "D" | "W" | "M") =>
    apiClient
      .get<ApiResponse<PeriodPerformanceRollup[]>>(
        `/v1/projects/${projectId}/performance`,
        { params: { periodType } },
      )
      .then((r) => r.data),
};
