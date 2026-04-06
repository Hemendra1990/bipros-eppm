import { apiClient } from "./client";
import type { ApiResponse, ActivityResponse } from "../types";

export interface ScheduleResultResponse {
  id: string;
  projectId: string;
  dataDate: string;
  projectStartDate: string;
  projectFinishDate: string;
  criticalPathLength: number | null;
  totalActivities: number;
  criticalActivities: number;
  schedulingOption: string;
  calculatedAt: string;
  durationSeconds: number | null;
  status: string;
  warnings: string[];
}

export const scheduleApi = {
  runSchedule: (projectId: string, option = "RETAINED_LOGIC") =>
    apiClient.post<ApiResponse<ScheduleResultResponse>>(`/v1/projects/${projectId}/schedule`, { projectId, option }).then(r => r.data),

  getLatestSchedule: (projectId: string) =>
    apiClient.get<ApiResponse<ScheduleResultResponse>>(`/v1/projects/${projectId}/schedule`).then(r => r.data),

  getCriticalPath: (projectId: string) =>
    apiClient.get<ApiResponse<ActivityResponse[]>>(`/v1/projects/${projectId}/schedule/critical-path`).then(r => r.data),

  getFloatPaths: (projectId: string) =>
    apiClient.get<ApiResponse<ActivityResponse[]>>(`/v1/projects/${projectId}/schedule/float-paths`).then(r => r.data),

  getAllScheduledActivities: (projectId: string) =>
    apiClient.get<ApiResponse<ActivityResponse[]>>(`/v1/projects/${projectId}/schedule/activities`).then(r => r.data),
};
