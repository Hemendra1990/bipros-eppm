import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export const evmApi = {
  calculateEvm: (projectId: string, technique: string, etcMethod: string) =>
    apiClient
      .post<ApiResponse<any>>(`/v1/projects/${projectId}/evm/calculate`, {
        technique,
        etcMethod,
      })
      .then((r) => r.data),

  getLatest: (projectId: string) =>
    apiClient.get<ApiResponse<any>>(`/v1/projects/${projectId}/evm`).then((r) => r.data),

  getHistory: (projectId: string) =>
    apiClient
      .get<ApiResponse<any>>(`/v1/projects/${projectId}/evm/history`)
      .then((r) => r.data),
};
