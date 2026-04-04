import { apiClient } from "./client";
import type { ApiResponse, PagedResponse } from "../types";

export const costApi = {
  getExpensesByProject: (projectId: string, page = 0, size = 20) =>
    apiClient
      .get<ApiResponse<PagedResponse<any>>>(`/v1/projects/${projectId}/expenses`, {
        params: { page, size },
      })
      .then((r) => r.data),

  getCostSummary: (projectId: string) =>
    apiClient
      .get<ApiResponse<any>>(`/v1/projects/${projectId}/cost-summary`)
      .then((r) => r.data),

  getCostAccountTree: () =>
    apiClient.get<ApiResponse<any>>("/v1/cost-accounts").then((r) => r.data),

  getCashFlowForecast: (projectId: string) =>
    apiClient
      .get<ApiResponse<any>>(`/v1/projects/${projectId}/cash-flow`)
      .then((r) => r.data),
};
