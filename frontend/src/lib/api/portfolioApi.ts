import { apiClient } from "./client";
import type {
  ApiResponse,
  CreatePortfolioRequest,
  PagedResponse,
  PortfolioResponse,
  PortfolioProjectResponse,
  PortfolioScoringCriterion,
  PortfolioScenarioResponse,
} from "../types";

export const portfolioApi = {
  // Portfolios
  listPortfolios: (page = 0, size = 20) =>
    apiClient
      .get<ApiResponse<PagedResponse<PortfolioResponse>>>("/v1/portfolios", {
        params: { page, size },
      })
      .then((r) => r.data),

  getPortfolio: (id: string) =>
    apiClient
      .get<ApiResponse<PortfolioResponse>>(`/v1/portfolios/${id}`)
      .then((r) => r.data),

  createPortfolio: (data: CreatePortfolioRequest) =>
    apiClient
      .post<ApiResponse<PortfolioResponse>>("/v1/portfolios", data)
      .then((r) => r.data),

  updatePortfolio: (id: string, data: Partial<CreatePortfolioRequest>) =>
    apiClient
      .put<ApiResponse<PortfolioResponse>>(`/v1/portfolios/${id}`, data)
      .then((r) => r.data),

  deletePortfolio: (id: string) =>
    apiClient.delete(`/v1/portfolios/${id}`),

  // Portfolio Projects
  getPortfolioProjects: (portfolioId: string) =>
    apiClient
      .get<ApiResponse<PortfolioProjectResponse[]>>(`/v1/portfolios/${portfolioId}/projects`)
      .then((r) => r.data),

  addProjectToPortfolio: (portfolioId: string, projectId: string) =>
    apiClient
      .post(`/v1/portfolios/${portfolioId}/projects`, null, { params: { projectId } })
      .then((r) => r.data),

  removeProjectFromPortfolio: (portfolioId: string, projectId: string) =>
    apiClient.delete(`/v1/portfolios/${portfolioId}/projects/${projectId}`),

  // Portfolio Scoring
  getPortfolioScoringCriteria: (portfolioId: string) =>
    apiClient
      .get<ApiResponse<PortfolioScoringCriterion[]>>(
        `/v1/portfolios/${portfolioId}/scoring-criteria`
      )
      .then((r) => r.data),

  calculatePortfolioRanking: (portfolioId: string) =>
    apiClient
      .post(`/v1/portfolios/${portfolioId}/calculate-ranking`, {})
      .then((r) => r.data),

  // Portfolio Scenarios
  getPortfolioScenarios: (portfolioId: string) =>
    apiClient
      .get<ApiResponse<PortfolioScenarioResponse[]>>(`/v1/portfolios/${portfolioId}/scenarios`)
      .then((r) => r.data),

  createPortfolioScenario: (portfolioId: string, data: { name: string; description?: string }) =>
    apiClient
      .post<ApiResponse<PortfolioScenarioResponse>>(
        `/v1/portfolios/${portfolioId}/scenarios`,
        data
      )
      .then((r) => r.data),

  compareScenarios: (portfolioId: string, scenarioIds: string[]) =>
    apiClient
      .post(`/v1/portfolios/${portfolioId}/scenarios/compare`, { scenarioIds })
      .then((r) => r.data),

  // Portfolio Optimization
  optimizePortfolio: (
    portfolioId: string,
    data: { budgetLimit?: number; mandatoryProjectIds?: string[] }
  ) =>
    apiClient
      .post<ApiResponse<OptimizationResultResponse>>(
        `/v1/portfolios/${portfolioId}/optimize`,
        data
      )
      .then((r) => r.data),

  whatIfAnalysis: (
    portfolioId: string,
    data: { projectId: string; addProject: boolean; budgetLimit?: number }
  ) =>
    apiClient
      .post<ApiResponse<WhatIfResponse>>(`/v1/portfolios/${portfolioId}/what-if`, data)
      .then((r) => r.data),
};

export interface OptimizationResultResponse {
  selectedProjectIds: string[];
  excludedProjectIds: string[];
  totalScore: number;
  totalBudget: number;
  remainingBudget: number | null;
  totalSelected: number;
  totalExcluded: number;
  messages: string[];
}

export interface WhatIfResponse {
  projectId: string;
  projectName: string;
  action: string;
  scoreBefore: number;
  scoreAfter: number;
  scoreDelta: number;
  budgetBefore: number;
  budgetAfter: number;
  budgetDelta: number;
  withinBudget: boolean;
  remainingBudget: number | null;
}
