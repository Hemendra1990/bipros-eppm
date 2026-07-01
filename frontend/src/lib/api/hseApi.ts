import { apiClient } from "./client";
import type { ApiResponse } from "../types";

/**
 * Project HSE (Health, Safety & Environment) statistics. All figures are cumulative,
 * project-to-date. Numbers are plain counts / exposure metrics — never money, so callers
 * format them with thousands separators, not the project currency.
 */
export interface HseStatisticsResponse {
  manHoursWorked: number;
  manHoursWithoutLti: number;
  projectDaysWorked: number;
  projectDaysWithoutLti: number;
  kmDistanceDriven: number;
  mtcCount: number;
  propertyDamageCount: number;
  nearMissCount: number;
  fatalityCount: number;
  /** ISO date (yyyy-MM-dd) of the most recent Lost Time Injury, or null when none logged. */
  lastLtiDate: string | null;
  /** Calendar hours/day used as the man-hour fallback when a DPR row logs no working hours. */
  calendarHoursPerDay: number;
}

/** The manual, per-project HSE inputs (KM driven). */
export interface ProjectHseMetricsResponse {
  kmDistanceDriven: number;
}

/** Upsert payload for the gated HSE inputs editor. */
export interface UpdateProjectHseMetricsRequest {
  kmDistanceDriven: number;
}

export const hseApi = {
  statistics: (projectId: string) =>
    apiClient
      .get<ApiResponse<HseStatisticsResponse>>(
        `/v1/projects/${projectId}/hse/statistics`,
      )
      .then((r) => r.data),

  getMetrics: (projectId: string) =>
    apiClient
      .get<ApiResponse<ProjectHseMetricsResponse>>(
        `/v1/projects/${projectId}/hse/metrics`,
      )
      .then((r) => r.data),

  putMetrics: (projectId: string, body: UpdateProjectHseMetricsRequest) =>
    apiClient
      .put<ApiResponse<ProjectHseMetricsResponse>>(
        `/v1/projects/${projectId}/hse/metrics`,
        body,
      )
      .then((r) => r.data),
};
