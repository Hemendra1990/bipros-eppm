import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface ReportData {
  name: string;
  description: string;
  type: string;
  data: unknown;
  generatedAt: string;
}

export interface SCurveData {
  periods: string[];
  plangedCumulativeValue: number[];
  earnedCumulativeValue: number[];
  actualCumulativeValue: number[];
}

export interface ResourceHistogramData {
  resources: string[];
  allocations: { date: string; allocated: Record<string, number> }[];
}

export interface CashFlowData {
  periods: string[];
  outflows: number[];
  inflows: number[];
  netCashFlow: number[];
}

export interface ReportExecutionResponse {
  id: string;
  format: "EXCEL" | "PDF";
  status: "PENDING" | "COMPLETED" | "FAILED";
  createdAt: string;
}

export const reportApi = {
  generateSCurve: (projectId: string) =>
    apiClient
      .post<ApiResponse<SCurveData>>(`/v1/projects/${projectId}/reports/s-curve`)
      .then((r) => r.data),

  generateResourceHistogram: (projectId: string, resourceId?: string) =>
    apiClient
      .post<ApiResponse<ResourceHistogramData>>(
        `/v1/projects/${projectId}/reports/resource-histogram`,
        resourceId ? { resourceId } : {}
      )
      .then((r) => r.data),

  generateCashFlow: (projectId: string) =>
    apiClient
      .post<ApiResponse<CashFlowData>>(`/v1/projects/${projectId}/reports/cash-flow`)
      .then((r) => r.data),

  generateScheduleComparison: (projectId: string, baselineId?: string) =>
    apiClient
      .post<ApiResponse<unknown>>(
        `/v1/projects/${projectId}/reports/schedule-comparison`,
        { baselineId }
      )
      .then((r) => r.data),

  listCustomReports: (projectId: string) =>
    apiClient
      .get<ApiResponse<ReportData[]>>(`/v1/projects/${projectId}/reports/custom`)
      .then((r) => r.data),

  executeReport: (format: "EXCEL" | "PDF", reportType?: string) =>
    apiClient
      .post<ApiResponse<ReportExecutionResponse>>(`/v1/reports/execute`, {
        format,
        reportType,
      })
      .then((r) => r.data),

  downloadReport: (executionId: string) =>
    apiClient.get(`/v1/reports/executions/${executionId}/download`, {
      responseType: "blob",
    }),
};
