import { apiClient } from "./client";
import type { ApiResponse } from "../types";

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
  confidenceP50Duration: number;
  confidenceP80Duration: number;
  confidenceP50Cost: string;
  confidenceP80Cost: string;
  baselineDuration: number;
  baselineCost: string;
  status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";
  completedAt: string | null;
  createdAt: string;
  results?: MonteCarloResult[];
}

export const monteCarloApi = {
  runSimulation: (projectId: string, iterations: number = 10000) =>
    apiClient
      .post<ApiResponse<MonteCarloSimulation>>(
        `/v1/projects/${projectId}/monte-carlo/run`,
        null,
        { params: { iterations } }
      )
      .then((r) => r.data),

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
};
