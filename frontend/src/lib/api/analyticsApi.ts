import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface PredictionDto {
  id: string;
  projectId: string;
  predictionType: string;
  predictedValue: number;
  confidenceLevel: number;
  baselineValue?: number;
  variance?: number;
  factors: string;
  modelVersion: string;
  calculatedAt: string;
}

export interface AnalyticsQueryDto {
  id: string;
  userId: string;
  queryText: string;
  queryType: string;
  responseText: string;
  responseData?: string;
  responseTimeMs?: number;
  createdAt: string;
}

export interface AnalyticsQueryRequest {
  queryText: string;
  projectId?: string;
}

/**
 * Live response from POST /v1/analytics/query — backend record
 * `AnalyticsAssistantResponse`. Tabular fields are null when the answer
 * is purely narrative (e.g. polite refusals); SQL/tokens/cost are null
 * when no provider call happened (e.g. local refusal before the LLM).
 */
export interface AnalyticsAssistantResponse {
  narrative: string | null;
  toolUsed: string | null;
  columns: string[] | null;
  rows: Array<Record<string, unknown>> | null;
  sqlExecuted: string | null;
  tokensInput: number | null;
  tokensOutput: number | null;
  costMicros: number | null;
  status: string;
}

export function isLlmNotConfigured(err: unknown): boolean {
  const code = (
    err as { response?: { data?: { error?: { code?: string } } } }
  )?.response?.data?.error?.code;
  return code === "LLM_NOT_CONFIGURED";
}

/**
 * Per-contractor performance/compliance rollup.
 * Populated by `/v1/analytics/contractor-performance`.
 * `safetyScore` is `null` because safety incident ingestion isn't wired yet
 * — the UI should render "n/a" rather than fabricate a value.
 */
export interface ContractorPerformance {
  orgId: string;
  orgCode: string;
  orgName: string;
  performanceScore: number | null;
  safetyScore: number | null;
  complianceScore: number | null;
  activeContracts: number;
  totalContractValueCr: number;
}

export const analyticsApi = {
  runPredictions: (projectId: string) =>
    apiClient
      .post<ApiResponse<PredictionDto[]>>(
        `/v1/projects/${projectId}/predictions/run`
      )
      .then((r) => ({ data: r.data.data ?? [] })),

  getPredictions: (projectId: string) =>
    apiClient
      .get<ApiResponse<PredictionDto[]>>(
        `/v1/projects/${projectId}/predictions`
      )
      .then((r) => ({ data: r.data.data ?? [] })),

  getPredictionByType: (projectId: string, type: string) =>
    apiClient
      .get<ApiResponse<PredictionDto>>(
        `/v1/projects/${projectId}/predictions/${type}`
      )
      .then((r) => ({ data: r.data.data })),

  submitQuery: (request: AnalyticsQueryRequest) =>
    apiClient
      .post<ApiResponse<AnalyticsAssistantResponse>>(
        `/v1/analytics/query`,
        request
      )
      .then((r) => r.data.data!),

  getQueryHistory: (limit: number = 20) =>
    apiClient
      .get<ApiResponse<AnalyticsQueryDto[]>>(
        `/v1/analytics/queries`,
        { params: { limit } }
      )
      .then((r) => ({ data: r.data.data ?? [] })),

  getContractorPerformance: () =>
    apiClient
      .get<ApiResponse<ContractorPerformance[]>>(
        `/v1/analytics/contractor-performance`
      )
      .then((r) => r.data.data ?? []),
};
