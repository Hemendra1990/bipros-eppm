import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface EvmCalculationResult {
  id: string;
  projectId: string;
  wbsNodeId: string | null;
  dataDate: string;
  budgetAtCompletion: number;
  plannedValue: number;
  earnedValue: number;
  actualCost: number;
  scheduleVariance: number;
  costVariance: number;
  schedulePerformanceIndex: number;
  costPerformanceIndex: number;
  toCompletePerformanceIndex: number;
  estimateAtCompletion: number;
  estimateToComplete: number;
  varianceAtCompletion: number;
  evmTechnique: string;
  etcMethod: string;
  performancePercentComplete: number;
}

export interface WbsEvmNode {
  wbsNodeId: string;
  name: string;
  code: string;
  budgetAtCompletion: number;
  plannedValue: number;
  earnedValue: number;
  actualCost: number;
  scheduleVariance: number;
  costVariance: number;
  schedulePerformanceIndex: number;
  costPerformanceIndex: number;
  estimateAtCompletion: number;
  estimateToComplete: number;
  varianceAtCompletion: number;
  children: WbsEvmNode[];
}

export type EvmTechnique =
  | "ACTIVITY_PERCENT_COMPLETE"
  | "ZERO_ONE_HUNDRED"
  | "FIFTY_FIFTY"
  | "WEIGHTED_STEPS"
  | "LEVEL_OF_EFFORT";

export type EtcMethod =
  | "MANUAL"
  | "CPI_BASED"
  | "SPI_BASED"
  | "CPI_SPI_COMPOSITE"
  | "MANAGEMENT_OVERRIDE";

export const evmApi = {
  calculateEvm: (projectId: string, technique: EvmTechnique, etcMethod: EtcMethod) =>
    apiClient
      .post<ApiResponse<EvmCalculationResult>>(`/v1/projects/${projectId}/evm/calculate`, {
        technique,
        etcMethod,
      })
      .then((r) => r.data),

  getLatest: (projectId: string) =>
    apiClient
      .get<ApiResponse<EvmCalculationResult>>(`/v1/projects/${projectId}/evm`)
      .then((r) => r.data),

  getHistory: (projectId: string) =>
    apiClient
      .get<ApiResponse<EvmCalculationResult[]>>(`/v1/projects/${projectId}/evm/history`)
      .then((r) => r.data),

  getWbsTree: (projectId: string, technique: EvmTechnique, etcMethod: EtcMethod) =>
    apiClient
      .get<ApiResponse<WbsEvmNode[]>>(`/v1/projects/${projectId}/evm/wbs-tree`, {
        params: { technique, etcMethod },
      })
      .then((r) => r.data),

  calculateWbsEvm: (projectId: string, technique: EvmTechnique, etcMethod: EtcMethod) =>
    apiClient
      .post<ApiResponse<WbsEvmNode[]>>(`/v1/projects/${projectId}/evm/calculate-wbs`, {
        technique,
        etcMethod,
      })
      .then((r) => r.data),
};
