import { apiClient } from "./client";
import type { ApiResponse, PagedResponse, ExpenseResponse } from "../types";

export interface CostAccount {
  id: string;
  code: string;
  name: string;
  description?: string;
  parentId?: string | null;
}

export interface CreateCostAccountRequest {
  code: string;
  name: string;
  description?: string;
  parentId?: string | null;
}

export interface CostSummary {
  totalBudget: number;
  totalActual: number;
  totalRemaining: number;
  atCompletion: number;
  costVariance: number;
  costPerformanceIndex: number | null;
  expenseCount: number;
  // PMS MasterData procurement roll-ups (nullable when the project has no material activity).
  materialProcurementCost: number | null;
  openStockValue: number | null;
  materialIssuedCost: number | null;
}

export interface CashFlowForecastItem {
  id: string | null;
  projectId: string;
  period: string;
  plannedAmount: number;
  actualAmount: number;
  forecastAmount: number;
  cumulativePlanned: number;
  cumulativeActual: number;
  cumulativeForecast: number;
}

export interface PeriodCostAggregation {
  periodId: string;
  periodName: string;
  startDate: string;
  endDate: string;
  budget: number;
  actual: number;
  variance: number;
  earnedValue: number;
  plannedValue: number;
}

export type ForecastMethod = "LINEAR" | "CPI_BASED" | "SPI_CPI_COMPOSITE";

export interface CreateExpenseRequest {
  activityId?: string;
  description: string;
  amount: number;
  currency?: string;
  expenseDate: string;
  category: string;
}

export const costApi = {
  getExpensesByProject: (projectId: string, page = 0, size = 20) =>
    apiClient
      .get<ApiResponse<PagedResponse<ExpenseResponse>>>(`/v1/projects/${projectId}/expenses`, {
        params: { page, size },
      })
      .then((r) => r.data),

  getCostSummary: (projectId: string) =>
    apiClient
      .get<ApiResponse<CostSummary>>(`/v1/projects/${projectId}/cost-summary`)
      .then((r) => r.data),

  getCostAccountTree: () =>
    apiClient.get<ApiResponse<CostAccount[]>>("/v1/cost-accounts").then((r) => r.data),

  getCashFlowForecast: (projectId: string) =>
    apiClient
      .get<ApiResponse<CashFlowForecastItem[]>>(`/v1/projects/${projectId}/cash-flow`)
      .then((r) => r.data),

  generateForecast: (projectId: string, method: ForecastMethod = "LINEAR") =>
    apiClient
      .get<ApiResponse<CashFlowForecastItem[]>>(`/v1/projects/${projectId}/cost-forecast`, {
        params: { method },
      })
      .then((r) => r.data),

  getCostPeriods: (projectId: string) =>
    apiClient
      .get<ApiResponse<PeriodCostAggregation[]>>(`/v1/projects/${projectId}/cost-periods`)
      .then((r) => r.data),

  listCostAccounts: () =>
    apiClient.get<ApiResponse<CostAccount[]>>("/v1/cost-accounts").then((r) => r.data),

  createCostAccount: (data: CreateCostAccountRequest) =>
    apiClient.post<ApiResponse<CostAccount>>("/v1/cost-accounts", data).then((r) => r.data),

  deleteCostAccount: (id: string) =>
    apiClient.delete(`/v1/cost-accounts/${id}`),

  createExpense: (projectId: string, data: CreateExpenseRequest) =>
    apiClient
      .post<ApiResponse<ExpenseResponse>>(`/v1/projects/${projectId}/expenses`, data)
      .then((r) => r.data),
};
