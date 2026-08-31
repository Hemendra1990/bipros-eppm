// Dedicated client for DPR-issue mutations outside the parent DPR drawer. Create / bulk-edit
// rides along on dprApi.create / dprApi.update (parent payload carries `issues: DprIssueRow[]`);
// this client only handles post-save mutations (PATCH / DELETE / GET) so a future Issues
// dashboard or the AI tool can update status without re-saving the whole DPR.

import { apiClient } from "./client";
import type { ApiResponse } from "../types";
import type {
  CreateDprIssueRequest,
  DprIssueRow,
  DprIssueStatusHistoryRow,
  HseIncidentType,
  IssueCategory,
  IssueSeverity,
  IssueStatus,
} from "../types/dpr";

export type {
  CreateDprIssueRequest,
  DprIssueRow,
  DprIssueStatusHistoryRow,
  HseIncidentType,
  IssueCategory,
  IssueSeverity,
  IssueStatus,
} from "../types/dpr";

export interface DprIssueFilters {
  status?: IssueStatus;
  severity?: IssueSeverity;
  category?: IssueCategory;
  supervisorUserId?: string;
  activityId?: string;
  dateFrom?: string;
  dateTo?: string;
  q?: string;
  /** true = only intervention-flagged issues. */
  interventionRequired?: boolean;
}

/**
 * Partial-update request. Only non-null fields are applied server-side, with ONE EXCEPTION:
 * `hseIncidentType` is applied UNCONDITIONALLY (null explicitly clears the HSE classification).
 * Any partial-PATCH caller that omits `hseIncidentType` will silently wipe the classification —
 * always pass `hseIncidentType: row.hseIncidentType ?? null` when sending a status-only or
 * other partial body.
 */
export interface UpdateDprIssueRequest {
  title?: string;
  description?: string | null;
  category?: IssueCategory;
  severity?: IssueSeverity;
  status?: IssueStatus;
  supervisorUserId?: string | null;
  supervisorName?: string | null;
  assignedToUserId?: string | null;
  assignedToName?: string | null;
  resolutionNotes?: string | null;
  activityId?: string | null;
  activityName?: string | null;
  hseIncidentType?: HseIncidentType | null;
  /** Free-text reason recorded on the status-change history row (non-terminal moves). */
  statusChangeReason?: string | null;
  interventionRequired?: boolean | null;
  /** Act-by date; ISO date, null = leave unchanged. */
  dueDate?: string | null;
}

function toQuery(filters: DprIssueFilters): string {
  const params = new URLSearchParams();
  if (filters.status) params.set("status", filters.status);
  if (filters.severity) params.set("severity", filters.severity);
  if (filters.category) params.set("category", filters.category);
  if (filters.supervisorUserId) params.set("supervisorUserId", filters.supervisorUserId);
  if (filters.activityId) params.set("activityId", filters.activityId);
  if (filters.dateFrom) params.set("dateFrom", filters.dateFrom);
  if (filters.dateTo) params.set("dateTo", filters.dateTo);
  if (filters.q) params.set("q", filters.q);
  if (filters.interventionRequired != null) {
    params.set("interventionRequired", String(filters.interventionRequired));
  }
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

export const dprIssueApi = {
  create: (projectId: string, body: CreateDprIssueRequest) =>
    apiClient
      .post<ApiResponse<DprIssueRow>>(`/v1/projects/${projectId}/dpr-issues`, body)
      .then((r) => r.data),

  list: (projectId: string, filters: DprIssueFilters = {}) =>
    apiClient
      .get<ApiResponse<DprIssueRow[]>>(`/v1/projects/${projectId}/dpr-issues${toQuery(filters)}`)
      .then((r) => r.data),

  get: (projectId: string, id: string) =>
    apiClient
      .get<ApiResponse<DprIssueRow>>(`/v1/projects/${projectId}/dpr-issues/${id}`)
      .then((r) => r.data),

  history: (projectId: string, id: string) =>
    apiClient
      .get<ApiResponse<DprIssueStatusHistoryRow[]>>(`/v1/projects/${projectId}/dpr-issues/${id}/history`)
      .then((r) => r.data),

  patch: (projectId: string, id: string, body: UpdateDprIssueRequest) =>
    apiClient
      .patch<ApiResponse<DprIssueRow>>(`/v1/projects/${projectId}/dpr-issues/${id}`, body)
      .then((r) => r.data),

  remove: (projectId: string, id: string) =>
    apiClient
      .delete<ApiResponse<void>>(`/v1/projects/${projectId}/dpr-issues/${id}`)
      .then((r) => r.data),
};
