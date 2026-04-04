import { apiClient } from "./client";

export interface FloatDistribution {
  zero: number;
  "1to5": number;
  "6to10": number;
  "10plus": number;
}

export interface ScheduleHealthResponse {
  id: string;
  projectId: string;
  scheduleResultId: string;
  totalActivities: number;
  criticalActivities: number;
  nearCriticalActivities: number;
  totalFloatAverage: number;
  healthScore: number;
  floatDistribution: FloatDistribution;
  riskLevel: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
}

export const scheduleHealthApi = {
  async getLatestHealth(projectId: string): Promise<ScheduleHealthResponse> {
    const response = await apiClient.get(
      `/v1/projects/${projectId}/schedule-health`
    );
    return response.data;
  },
};
