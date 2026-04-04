import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface ResourceHistogramEntry {
  period: string;
  planned: number;
  actual: number;
}

export const resourceHistogramApi = {
  getHistogram: (
    projectId: string,
    resourceId: string,
    filters?: {
      fromDate?: string;
      toDate?: string;
    }
  ) =>
    apiClient
      .get<ApiResponse<ResourceHistogramEntry[]>>(
        `/v1/projects/${projectId}/resource-histogram`,
        {
          params: {
            resourceId,
            ...filters,
          },
        }
      )
      .then((r) => r.data),
};
