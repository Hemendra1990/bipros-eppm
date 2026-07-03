import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface BaselineResponse {
  id: string;
  projectId: string;
  name: string;
  description: string | null;
  baselineType: "PROJECT" | "PRIMARY" | "SECONDARY" | "TERTIARY";
  baselineDate: string;
  isActive: boolean;
  totalActivities: number;
  totalCost: number;
  projectDuration: number;
  projectStartDate: string | null;
  projectFinishDate: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface BaselineActivityResponse {
  id: string;
  baselineId: string;
  activityId: string;
  earlyStart: string | null;
  earlyFinish: string | null;
  lateStart: string | null;
  lateFinish: string | null;
  originalDuration: number | null;
  remainingDuration: number | null;
  totalFloat: number | null;
  freeFloat: number | null;
  plannedCost: number | null;
  actualCost: number | null;
  percentComplete: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface BaselineDetailResponse {
  baseline: BaselineResponse;
  activities: BaselineActivityResponse[];
}

export interface BaselineVarianceRow {
  activityId: string;
  activityName: string;
  startVarianceDays: number;
  finishVarianceDays: number;
  durationVariance: number;
  costVariance: number;
  comparable: boolean;
}

export interface CreateBaselineRequest {
  name: string;
  baselineType: "PROJECT" | "PRIMARY" | "SECONDARY" | "TERTIARY";
  description?: string;
  /**
   * Phase 4.3: P6's second creation option — pick another existing project as the snapshot
   * source. Variance comparison only matches when activity IDs overlap (true for "saved as
   * copy" workflows). Omit for the default "snapshot the current project" behaviour.
   */
  sourceProjectId?: string;
}

/**
 * Phase 4.2 selective-update filter. Every field is optional; defaults are permissive on the
 * server (everything updates unless the planner narrows the scope).
 */
export interface UpdateBaselineRequest {
  activityIds?: string[];
  criticalOnly?: boolean;
  milestonesOnly?: boolean;
  statuses?: string[];
  plannedStartFrom?: string;
  plannedStartTo?: string;
  updateDates?: boolean;
  updateDurations?: boolean;
  updateRelationships?: boolean;
  updateResourceCosts?: boolean;
  updateExpenseCosts?: boolean;
}

export interface ScheduleComparisonRow {
  activityId: string;
  activityName: string;
  currentStart: string | null;
  baselineStart: string | null;
  startVarianceDays: number | null;
  currentFinish: string | null;
  baselineFinish: string | null;
  finishVarianceDays: number | null;
  status: "ADDED" | "DELETED" | "CHANGED" | "UNCHANGED" | "NOT_COMPARABLE";
}

export type ImportFormat = "EXCEL" | "XER" | "P6XML" | "MSP_XML" | "CSV";

export interface ImportPreview {
  activitiesInFile: number;
  matched: number;
  newActivities: number;
  missingInFile: number;
  wbsNodes: number;
  relationships: number;
  resourceAssignments: number;
  dateRangeStart: string | null;
  dateRangeFinish: string | null;
  totalPlannedCost: number;
  missingActivityCodes: string[];
  warnings: string[];
  resources?: {
    manpowerRows: number;
    manpowerApplied: number;
    equipmentRows: number;
    equipmentApplied: number;
    materialRows: number;
    materialApplied: number;
    subContractorRows: number;
    subContractorApplied: number;
    warnings: string[];
  } | null;
}

export interface ApplySummary {
  activitiesCreated: number;
  activitiesUpdated: number;
  wbsCreated: number;
  wbsUpdated: number;
  relationshipsCreated: number;
  assignmentsUpserted: number;
  missingActivityCodes: string[];
}

export interface BaselineImportResult {
  baseline: BaselineResponse;
  summary: ApplySummary;
}

export const baselineApi = {
  listBaselines: (projectId: string) =>
    apiClient
      .get<ApiResponse<BaselineResponse[]>>(`/v1/projects/${projectId}/baselines`)
      .then((r) => r.data),

  createBaseline: (projectId: string, data: CreateBaselineRequest) =>
    apiClient
      .post<ApiResponse<BaselineResponse>>(
        `/v1/projects/${projectId}/baselines`,
        data
      )
      .then((r) => r.data),

  getBaseline: (projectId: string, baselineId: string) =>
    apiClient
      .get<ApiResponse<BaselineDetailResponse>>(
        `/v1/projects/${projectId}/baselines/${baselineId}`
      )
      .then((r) => r.data),

  getVariance: (projectId: string, baselineId: string) =>
    apiClient
      .get<ApiResponse<BaselineVarianceRow[]>>(
        `/v1/projects/${projectId}/baselines/${baselineId}/variance`
      )
      .then((r) => r.data),

  deleteBaseline: (projectId: string, baselineId: string) =>
    apiClient.delete<ApiResponse<void>>(
      `/v1/projects/${projectId}/baselines/${baselineId}`
    ),

  getScheduleComparison: (projectId: string, baselineId: string) =>
    apiClient
      .get<ApiResponse<ScheduleComparisonRow[]>>(
        `/v1/projects/${projectId}/baselines/${baselineId}/schedule-comparison`
      )
      .then((r) => r.data),

  setActiveBaseline: (projectId: string, baselineId: string) =>
    apiClient
      .post<ApiResponse<BaselineResponse>>(
        `/v1/projects/${projectId}/baselines/${baselineId}/activate`
      )
      .then((r) => r.data),

  /**
   * Phase 3: assign a baseline to one of three P6 slots (PRIMARY / SECONDARY / TERTIARY).
   * Slots are independent — assigning to SECONDARY does not unset PRIMARY.
   */
  assignBaselineToSlot: (
    projectId: string,
    baselineId: string,
    slot: "PRIMARY" | "SECONDARY" | "TERTIARY"
  ) =>
    apiClient
      .post<ApiResponse<BaselineResponse>>(
        `/v1/projects/${projectId}/baselines/${baselineId}/assign/${slot}`
      )
      .then((r) => r.data),

  /** Phase 3: detach the baseline currently in the given slot. Idempotent. */
  clearBaselineSlot: (projectId: string, slot: "PRIMARY" | "SECONDARY" | "TERTIARY") =>
    apiClient
      .delete<ApiResponse<void>>(`/v1/projects/${projectId}/baselines/slots/${slot}`)
      .then((r) => r.data),

  /**
   * Phase 4.1: P6-style "Restore Baseline". Overwrites planned dates, durations, and
   * relationships on the live project from the snapshot. Actuals are preserved.
   */
  restoreBaseline: (projectId: string, baselineId: string) =>
    apiClient
      .post<ApiResponse<BaselineResponse>>(
        `/v1/projects/${projectId}/baselines/${baselineId}/restore`
      )
      .then((r) => r.data),

  /** Phase 4.2: Selective Update Baseline with filters. */
  updateBaseline: (projectId: string, baselineId: string, request: UpdateBaselineRequest) =>
    apiClient
      .put<ApiResponse<BaselineResponse>>(
        `/v1/projects/${projectId}/baselines/${baselineId}/update`,
        request
      )
      .then((r) => r.data),

  previewImport: (projectId: string, file: File, format: ImportFormat) => {
    const form = new FormData();
    form.append("file", file);
    form.append("format", format);
    return apiClient
      .post<ApiResponse<ImportPreview>>(
        `/v1/projects/${projectId}/baselines/import/preview`,
        form,
        { headers: { "Content-Type": "multipart/form-data" } }
      )
      .then((r) => r.data);
  },

  importBaseline: (
    projectId: string,
    args: { file: File; format: ImportFormat; name: string; type?: string; description?: string }
  ) => {
    const form = new FormData();
    form.append("file", args.file);
    form.append("format", args.format);
    form.append("name", args.name);
    if (args.type) form.append("type", args.type);
    if (args.description) form.append("description", args.description);
    return apiClient
      .post<ApiResponse<BaselineImportResult>>(
        `/v1/projects/${projectId}/baselines/import`,
        form,
        { headers: { "Content-Type": "multipart/form-data" } }
      )
      .then((r) => r.data);
  },

  /** Downloadable import template whose headers match what the parser reads (Excel only, so far). */
  downloadTemplate: (projectId: string, format: ImportFormat) =>
    apiClient
      .get(`/v1/projects/${projectId}/baselines/import/template`, {
        responseType: "blob",
        params: { format },
      })
      .then((r) => r.data),
};
