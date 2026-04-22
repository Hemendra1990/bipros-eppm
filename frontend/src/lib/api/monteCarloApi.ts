import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export type DistributionType =
  | "TRIANGULAR"
  | "BETA_PERT"
  | "UNIFORM"
  | "NORMAL"
  | "LOGNORMAL"
  | "TRIGEN"
  | "DISCRETE";

export interface MonteCarloResult {
  id: string;
  simulationId: string;
  iterationNumber: number;
  projectDuration: number;
  projectCost: string;
}

export interface MonteCarloSimulation {
  id: string;
  projectId: string;
  simulationName: string;
  iterations: number;

  p10Duration?: number | null;
  p25Duration?: number | null;
  confidenceP50Duration: number;
  p75Duration?: number | null;
  confidenceP80Duration: number;
  p90Duration?: number | null;
  p95Duration?: number | null;
  p99Duration?: number | null;
  meanDuration?: number | null;
  stddevDuration?: number | null;

  p10Cost?: string | null;
  p25Cost?: string | null;
  confidenceP50Cost: string;
  p75Cost?: string | null;
  confidenceP80Cost: string;
  p90Cost?: string | null;
  p95Cost?: string | null;
  p99Cost?: string | null;
  meanCost?: string | null;
  stddevCost?: string | null;

  baselineDuration: number;
  baselineCost: string;
  baselineId?: string | null;
  dataDate?: string | null;
  iterationsCompleted?: number | null;
  configJson?: string | null;
  errorMessage?: string | null;

  status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";
  completedAt: string | null;
  createdAt: string;
  results?: MonteCarloResult[];
}

export interface MonteCarloActivityStat {
  id: string;
  simulationId: string;
  activityId: string;
  activityCode?: string | null;
  activityName?: string | null;
  criticalityIndex: number;
  durationMean?: number | null;
  durationStddev?: number | null;
  durationP10?: number | null;
  durationP90?: number | null;
  durationSensitivity?: number | null;
  costSensitivity?: number | null;
  cruciality?: number | null;
}

export interface MonteCarloMilestoneStat {
  id: string;
  simulationId: string;
  activityId: string;
  activityCode?: string | null;
  activityName?: string | null;
  plannedFinishDate?: string | null;
  p50FinishDate?: string | null;
  p80FinishDate?: string | null;
  p90FinishDate?: string | null;
  cdfJson?: string | null;
}

export interface MonteCarloCashflowBucket {
  id: string;
  simulationId: string;
  periodEndDate: string;
  baselineCumulative?: string | null;
  p10Cumulative?: string | null;
  p50Cumulative?: string | null;
  p80Cumulative?: string | null;
  p90Cumulative?: string | null;
}

export interface MonteCarloRiskContribution {
  id: string;
  simulationId: string;
  riskId: string;
  riskCode?: string | null;
  riskTitle?: string | null;
  occurrences?: number | null;
  occurrenceRate?: number | null;
  meanDurationImpact?: number | null;
  meanCostImpact?: string | null;
  affectedActivityIds?: string | null;
}

export interface MonteCarloRunRequest {
  iterations?: number;
  defaultDistribution?: DistributionType;
  fallbackVariancePct?: number;
  enableRisks?: boolean;
  randomSeed?: number | null;
}

export const monteCarloApi = {
  runSimulation: (projectId: string, input: number | MonteCarloRunRequest = 10000) => {
    const body: MonteCarloRunRequest =
      typeof input === "number" ? { iterations: input } : input;
    return apiClient
      .post<ApiResponse<MonteCarloSimulation>>(
        `/v1/projects/${projectId}/monte-carlo/run`,
        body
      )
      .then((r) => r.data);
  },

  getLatestSimulation: (projectId: string) =>
    apiClient
      .get<ApiResponse<MonteCarloSimulation>>(
        `/v1/projects/${projectId}/monte-carlo/latest`
      )
      .then((r) => r.data),

  getSimulation: (projectId: string, simulationId: string) =>
    apiClient
      .get<ApiResponse<MonteCarloSimulation>>(
        `/v1/projects/${projectId}/monte-carlo/${simulationId}`
      )
      .then((r) => r.data),

  listSimulations: (projectId: string) =>
    apiClient
      .get<ApiResponse<MonteCarloSimulation[]>>(
        `/v1/projects/${projectId}/monte-carlo`
      )
      .then((r) => r.data),

  getActivityStats: (projectId: string, simulationId: string) =>
    apiClient
      .get<ApiResponse<MonteCarloActivityStat[]>>(
        `/v1/projects/${projectId}/monte-carlo/${simulationId}/activity-stats`
      )
      .then((r) => r.data),

  getCriticality: (projectId: string, simulationId: string) =>
    apiClient
      .get<ApiResponse<MonteCarloActivityStat[]>>(
        `/v1/projects/${projectId}/monte-carlo/${simulationId}/criticality`
      )
      .then((r) => r.data),

  getTornado: (projectId: string, simulationId: string, metric: "duration" | "cost" = "duration") =>
    apiClient
      .get<ApiResponse<MonteCarloActivityStat[]>>(
        `/v1/projects/${projectId}/monte-carlo/${simulationId}/sensitivity-tornado`,
        { params: { metric } }
      )
      .then((r) => r.data),

  getMilestoneStats: (projectId: string, simulationId: string) =>
    apiClient
      .get<ApiResponse<MonteCarloMilestoneStat[]>>(
        `/v1/projects/${projectId}/monte-carlo/${simulationId}/milestone-stats`
      )
      .then((r) => r.data),

  getCashflow: (projectId: string, simulationId: string) =>
    apiClient
      .get<ApiResponse<MonteCarloCashflowBucket[]>>(
        `/v1/projects/${projectId}/monte-carlo/${simulationId}/cashflow`
      )
      .then((r) => r.data),

  getRiskContributions: (projectId: string, simulationId: string) =>
    apiClient
      .get<ApiResponse<MonteCarloRiskContribution[]>>(
        `/v1/projects/${projectId}/monte-carlo/${simulationId}/risk-contributions`
      )
      .then((r) => r.data),
};
