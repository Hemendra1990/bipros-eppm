import { apiClient } from "./client";
import type { ApiResponse, PagedResponse } from "../types";

export interface ActivityResponse {
  id: string;
  code: string;
  name: string;
  description?: string;
  projectId: string;
  wbsNodeId: string;
  status: string;
  plannedStartDate: string | null;
  plannedFinishDate: string | null;
  actualStartDate: string | null;
  actualFinishDate: string | null;
  earlyStartDate?: string | null;
  earlyFinishDate?: string | null;
  lateStartDate?: string | null;
  lateFinishDate?: string | null;
  duration: number;
  percentComplete: number;
  slack: number;
  totalFloat: number;
  freeFloat?: number;
  remainingDuration: number;
  isCritical?: boolean;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateActivityRequest {
  code: string;
  name: string;
  projectId: string;
  wbsNodeId: string;
  originalDuration?: number;
  activityType?: string;
  durationType?: string;
  plannedStartDate?: string;
  plannedFinishDate?: string;
  calendarId?: string;
}

export interface UpdateActivityRequest {
  name?: string;
  duration?: number;
  originalDuration?: number;
  percentComplete?: number;
  actualStartDate?: string;
  actualFinishDate?: string;
  notes?: string;
  calendarId?: string;
  activityType?: string;
  durationType?: string;
  plannedStartDate?: string;
  plannedFinishDate?: string;
}

export const activityApi = {
  listActivities: (projectId: string, page = 0, size = 20) =>
    apiClient
      .get<ApiResponse<PagedResponse<ActivityResponse>>>(`/v1/projects/${projectId}/activities`, {
        params: { page, size },
      })
      .then((r) => r.data),

  getActivity: (projectId: string, activityId: string) =>
    apiClient
      .get<ApiResponse<ActivityResponse>>(`/v1/projects/${projectId}/activities/${activityId}`)
      .then((r) => r.data),

  createActivity: (projectId: string, data: CreateActivityRequest) =>
    apiClient
      .post<ApiResponse<ActivityResponse>>(`/v1/projects/${projectId}/activities`, data)
      .then((r) => r.data),

  updateActivity: (projectId: string, activityId: string, data: UpdateActivityRequest) =>
    apiClient
      .put<ApiResponse<ActivityResponse>>(
        `/v1/projects/${projectId}/activities/${activityId}`,
        data
      )
      .then((r) => r.data),

  deleteActivity: (projectId: string, activityId: string) =>
    apiClient.delete(`/v1/projects/${projectId}/activities/${activityId}`),

  triggerSchedule: (projectId: string, option: string) =>
    apiClient
      .post<ApiResponse<{ success: boolean }>>(
        `/v1/projects/${projectId}/schedule`,
        { projectId, option }
      )
      .then((r) => r.data),

  getCriticalPath: (projectId: string) =>
    apiClient
      .get<ApiResponse<ActivityResponse[]>>(`/v1/projects/${projectId}/critical-path`)
      .then((r) => r.data),

  getRelationships: (projectId: string) =>
    apiClient
      .get<ApiResponse<RelationshipResponse[]>>(`/v1/projects/${projectId}/relationships`)
      .then((r) => r.data),

  getPredecessors: (projectId: string, activityId: string) =>
    apiClient
      .get<ApiResponse<RelationshipResponse[]>>(
        `/v1/projects/${projectId}/relationships/predecessors/${activityId}`
      )
      .then((r) => r.data),

  getSuccessors: (projectId: string, activityId: string) =>
    apiClient
      .get<ApiResponse<RelationshipResponse[]>>(
        `/v1/projects/${projectId}/relationships/successors/${activityId}`
      )
      .then((r) => r.data),

  createRelationship: (projectId: string, data: CreateRelationshipRequest) =>
    apiClient
      .post<ApiResponse<RelationshipResponse>>(
        `/v1/projects/${projectId}/relationships`,
        data
      )
      .then((r) => r.data),

  updateRelationship: (projectId: string, relationshipId: string, data: UpdateRelationshipRequest) =>
    apiClient
      .put<ApiResponse<RelationshipResponse>>(
        `/v1/projects/${projectId}/relationships/${relationshipId}`,
        data
      )
      .then((r) => r.data),

  deleteRelationship: (projectId: string, relationshipId: string) =>
    apiClient.delete(`/v1/projects/${projectId}/relationships/${relationshipId}`),

  applyActuals: (projectId: string, dataDate: string) =>
    apiClient
      .put<ApiResponse<void>>(
        `/v1/projects/${projectId}/activities/apply-actuals`,
        { dataDate }
      )
      .then((r) => r.data),

  applyGlobalChange: (projectId: string, request: GlobalChangeRequest) =>
    apiClient
      .post<ApiResponse<{ updatedCount: number }>>(
        `/v1/projects/${projectId}/activities/global-change`,
        request
      )
      .then((r) => r.data),

  updateProgress: (projectId: string, activityId: string, percentComplete: number, actualStartDate?: string, actualFinishDate?: string) =>
    apiClient
      .put<ApiResponse<ActivityResponse>>(
        `/v1/projects/${projectId}/activities/${activityId}/progress`,
        null,
        { params: { percentComplete, actualStartDate, actualFinishDate } }
      )
      .then((r) => r.data),
};

export interface GlobalChangeRequest {
  filterField: string;
  filterValue: string;
  updateField: string;
  updateValue: string;
  operation: "SET" | "ADD" | "SUBTRACT";
}

// === Relationships ===

export type RelationshipType = "FINISH_TO_START" | "FINISH_TO_FINISH" | "START_TO_START" | "START_TO_FINISH";

export interface RelationshipResponse {
  id: string;
  predecessorActivityId: string;
  successorActivityId: string;
  relationshipType: RelationshipType;
  lag: number;
  isExternal: boolean;
}

export interface CreateRelationshipRequest {
  predecessorActivityId: string;
  successorActivityId: string;
  relationshipType?: RelationshipType;
  lag?: number;
}

export interface UpdateRelationshipRequest {
  relationshipType: string;
  lag?: number;
}
