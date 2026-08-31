import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface EvmSummary {
  budgetAtCompletion: number | null;
  plannedValue: number | null;
  earnedValue: number | null;
  actualCost: number | null;
  scheduleVariance: number | null;
  costVariance: number | null;
  schedulePerformanceIndex: number | null;
  costPerformanceIndex: number | null;
  toCompletePerformanceIndex: number | null;
  estimateAtCompletion: number | null;
  estimateToComplete: number | null;
  varianceAtCompletion: number | null;
  evmTechnique: string | null;
  etcMethod: string | null;
  performancePercentComplete: number | null;
}

export interface WbsEvmNode {
  id: string;
  name: string;
  code: string;
  budgetAtCompletion: number | null;
  plannedValue: number | null;
  earnedValue: number | null;
  actualCost: number | null;
  scheduleVariance: number | null;
  costVariance: number | null;
  schedulePerformanceIndex: number | null;
  costPerformanceIndex: number | null;
  estimateAtCompletion: number | null;
  estimateToComplete: number | null;
  varianceAtCompletion: number | null;
  children: WbsEvmNode[];
}

export const evmKpiApi = {
  getKpis: (projectId: string) =>
    apiClient
      .get<ApiResponse<EvmSummary>>(`/v1/projects/${projectId}/kpis/evm`)
      .then((r) => r.data),

  /**
   * Per-WBS EVM tree (KPI 10.1–10.10 per activity / per WBS leaf). Reuses the existing
   * EvmRollupService.calculateWbsTree endpoint exposed at /v1/projects/{id}/evm/wbs-tree.
   */
  getWbsTree: (projectId: string) =>
    apiClient
      .get<ApiResponse<WbsEvmNode[]>>(
        `/v1/projects/${projectId}/evm/wbs-tree`,
        { params: { technique: "ACTIVITY_PERCENT_COMPLETE", etcMethod: "CPI_BASED" } },
      )
      .then((r) => r.data),
};
