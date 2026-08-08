import { apiClient } from "./client";
import type { ApiResponse } from "../types";

/** List entry for a stored Daily DPR Report (no HTML body — that comes with the detail). */
export interface DprReportSummary {
  id: string;
  trigger: "SCHEDULED" | "ON_DEMAND" | string;
  windowFrom: string | null;
  windowTo: string | null;
  windowLabel: string | null;
  generatedAt: string;
  status: "SUCCESS" | "PARTIAL" | "FAILED" | string;
  deliveryStatus: "SENT" | "PREVIEW" | "FAILED" | string | null;
  deliveredTo: string | null;
}

export interface DprReportDetail {
  id: string;
  trigger: string;
  windowLabel: string | null;
  generatedAt: string;
  status: string;
  deliveryStatus: string | null;
  deliveredTo: string | null;
  summary: string | null;
  htmlBody: string | null;
  errorMessage: string | null;
}

export const dprReportApi = {
  list: (projectId: string) =>
    apiClient
      .get<ApiResponse<DprReportSummary[]>>(`/v1/projects/${projectId}/dpr-reports`)
      .then((r) => r.data),

  get: (projectId: string, reportId: string) =>
    apiClient
      .get<ApiResponse<DprReportDetail>>(`/v1/projects/${projectId}/dpr-reports/${reportId}`)
      .then((r) => r.data),

  /** Fire the engine immediately (ON_DEMAND) — used for testing; returns the delivery outcome. */
  testSend: (projectId: string, email?: string, window?: string) =>
    apiClient
      .post<ApiResponse<Record<string, unknown>>>(
        `/v1/projects/${projectId}/dpr-report/test-send`,
        undefined,
        { params: { ...(email ? { email } : {}), ...(window ? { window } : {}) } },
      )
      .then((r) => r.data),
};
