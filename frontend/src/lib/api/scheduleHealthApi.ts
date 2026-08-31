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
  missingLogicPct: number | null;
  highFloatPct: number | null;
  deadlineSlipRatio: number | null;
  deadlineSlipDays: number | null;
  plannedFinish: string | null;
  scheduledFinish: string | null;
  computedAt: string | null;
  stale: boolean | null;
}

export const scheduleHealthApi = {
  async getLatestHealth(projectId: string): Promise<ScheduleHealthResponse> {
    const response = await apiClient.get(
      `/v1/projects/${projectId}/schedule-health`
    );
    return response.data;
  },
};
