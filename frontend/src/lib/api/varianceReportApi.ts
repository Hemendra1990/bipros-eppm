import { apiClient } from "./client";
import type { ApiResponse } from "../types";

// ───────────────── Schedule variance shapes ─────────────────

export interface ScheduleVarianceProjectInfo {
  id: string;
  code: string;
  name: string;
}

export interface ScheduleVarianceBaselineInfo {
  id: string;
  name: string;
  baselineDate: string | null;
}

export interface ScheduleVarianceSummary {
  totalActivities: number;
  slippedCount: number;
  aheadCount: number;
  onTrackCount: number;
  criticalSlippedCount: number;
  milestoneSlippedCount: number;
  avgStartVarianceDays: number;
  avgFinishVarianceDays: number;
  worstFinishVarianceDays: number;
  worstActivityCode: string | null;
  worstActivityName: string | null;
}

export type ActivityTypeName =
  | "TASK_DEPENDENT"
  | "RESOURCE_DEPENDENT"
  | "LEVEL_OF_EFFORT"
  | "START_MILESTONE"
  | "FINISH_MILESTONE"
  | "WBS_SUMMARY"
  | "UNKNOWN";

export type ActivityStatusName =
  | "NOT_STARTED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "ON_HOLD"
  | "CANCELLED"
  | "UNKNOWN";

export interface ScheduleVarianceRow {
  activityId: string;
  code: string;
  name: string;
  activityType: ActivityTypeName;
  status: ActivityStatusName;
  percentComplete: number | null;
  baselineStart: string | null;
  currentStart: string | null;
  startVarianceDays: number;
  baselineFinish: string | null;
  currentFinish: string | null;
  finishVarianceDays: number;
  baselineOriginalDuration: number | null;
  currentOriginalDuration: number | null;
  durationVarianceDays: number;
  totalFloat: number | null;
  isCritical: boolean | null;
  isMilestone: boolean;
}

export interface ScheduleVarianceReport {
  project: ScheduleVarianceProjectInfo;
  baseline: ScheduleVarianceBaselineInfo;
  dataDate: string | null;
  summary: ScheduleVarianceSummary;
  rows: ScheduleVarianceRow[];
}

// ───────────────── Cost variance shapes ─────────────────

export interface CostVarianceSummary {
  budgetAtCompletion: number | null;
  plannedValue: number | null;
  earnedValue: number | null;
  actualCost: number | null;
  scheduleVariance: number | null;
  costVariance: number | null;
  schedulePerformanceIndex: number | null;
  costPerformanceIndex: number | null;
  estimateAtCompletion: number | null;
  varianceAtCompletion: number | null;
  performancePercentComplete: number | null;
}

export interface CostVarianceWbsRow {
  wbsNodeId: string;
  wbsCode: string;
  wbsName: string;
  wbsLevel: number | null;
  budget: number | null;
  plannedValue: number | null;
  earnedValue: number | null;
  actualCost: number | null;
  costVariance: number | null;
  costPerformanceIndex: number | null;
}

export interface CostVarianceActivityRow {
  activityId: string;
  code: string;
  name: string;
  activityType: ActivityTypeName;
  status: ActivityStatusName;
  percentComplete: number | null;
  baselinePlannedCost: number | null;
  currentPlannedCost: number | null;
  actualCost: number | null;
  estimateVariance: number | null;
  burnVariance: number | null;
}

export interface CostVarianceReport {
  project: ScheduleVarianceProjectInfo;
  baseline: ScheduleVarianceBaselineInfo;
  dataDate: string | null;
  summary: CostVarianceSummary;
  wbsRows: CostVarianceWbsRow[];
  activityRows: CostVarianceActivityRow[];
}

// ───────────────── Client ─────────────────

export const varianceReportApi = {
  getScheduleVariance: (projectId: string, baselineId?: string) =>
    apiClient
      .get<ApiResponse<ScheduleVarianceReport>>(
        `/v1/projects/${projectId}/reports/schedule-variance`,
        { params: baselineId ? { baselineId } : undefined }
      )
      .then((r) => r.data),

  getCostVariance: (projectId: string, baselineId?: string) =>
    apiClient
      .get<ApiResponse<CostVarianceReport>>(
        `/v1/projects/${projectId}/reports/cost-variance`,
        { params: baselineId ? { baselineId } : undefined }
      )
      .then((r) => r.data),
};
