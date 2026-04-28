import { apiClient } from "./client";
import type { AnalyticsHealthResponse, ApiResponse } from "@/lib/types";

/**
 * Admin-only observability for the analytics assistant.
 * Backend: GET /v1/admin/analytics/health?windowHours=N (default 24, capped 1..168).
 */
export const analyticsHealthApi = {
  fetchHealth: (windowHours = 24) =>
    apiClient
      .get<ApiResponse<AnalyticsHealthResponse>>(
        `/v1/admin/analytics/health?windowHours=${windowHours}`,
      )
      .then((r) => r.data),
};
