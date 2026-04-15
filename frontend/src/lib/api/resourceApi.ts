import { apiClient } from "./client";
import type { ApiResponse, PagedResponse } from "../types";

export type ResourceCategory =
  | "SITE_ENGINEER"
  | "FOREMAN"
  | "SKILLED_LABOUR"
  | "UNSKILLED_LABOUR"
  | "OPERATOR"
  | "DRIVER"
  | "WELDER"
  | "ELECTRICIAN"
  | "EARTH_MOVING"
  | "CRANES_LIFTING"
  | "CONCRETE_EQUIPMENT"
  | "PAVING_EQUIPMENT"
  | "TRANSPORT_VEHICLES"
  | "PILING_RIG"
  | "SURVEY_EQUIPMENT"
  | "CEMENT"
  | "STEEL_REBAR"
  | "AGGREGATE"
  | "BITUMEN"
  | "READY_MIX_CONCRETE"
  | "BRICKS_BLOCKS"
  | "ELECTRICAL_CABLE"
  | "FORMWORK"
  | "OTHER";

export type ResourceUnit =
  | "PER_DAY"
  | "MT"
  | "CU_M"
  | "RMT"
  | "NOS"
  | "KG"
  | "LITRE";

export type UtilisationStatus =
  | "ACTIVE"
  | "OVER_90"
  | "CRITICAL_100"
  | "ON_HOLD_NOT_MOBILISED"
  | "PROCUREMENT"
  | "DELIVERY_ONGOING"
  | "LAYING";

export interface ResourceResponse {
  id: string;
  code: string;
  name: string;
  resourceType: "LABOR" | "NONLABOR" | "MATERIAL";
  resourceCategory?: ResourceCategory | null;
  unit?: ResourceUnit | null;
  status: string;
  maxUnitsPerDay?: number;
  poolMaxAvailable?: number | null;
  plannedUnitsToday?: number | null;
  actualUnitsToday?: number | null;
  utilisationPercent?: number | null;
  utilisationStatus?: UtilisationStatus | null;
  dailyCostLakh?: number | null;
  cumulativeCostCrores?: number | null;
  wbsAssignmentId?: string | null;
  calendarId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateResourceRequest {
  code: string;
  name: string;
  type: "LABOR" | "NONLABOR" | "MATERIAL";
  maxUnits?: number;
  calendarId?: string;
}

export interface UpdateResourceRequest {
  name?: string;
  status?: string;
  maxUnits?: number;
  calendarId?: string;
}

export type LevelingMode = "LEVEL_WITHIN_FLOAT" | "LEVEL_ALL" | "SMOOTH";

export interface ResourceLevelingRequest {
  mode: LevelingMode;
  resourceIds?: string[];
}

export interface ShiftedActivity {
  activityId: string;
  originalStart: string;
  newStart: string;
  delayDays: number;
}

export interface ResourceLevelingResponse {
  mode: LevelingMode;
  activitiesShifted: number;
  iterationsUsed: number;
  peakUtilizationBefore: number;
  peakUtilizationAfter: number;
  shiftedActivities: ShiftedActivity[];
  messages: string[];
}

export interface UtilizationProfileEntry {
  date: string;
  resourceId: string;
  resourceName: string;
  demand: number;
  capacity: number;
  utilization: number;
}

export interface ResourceAssignmentResponse {
  id: string;
  activityId: string;
  resourceId: string;
  assignmentStartDate: string | null;
  assignmentFinishDate: string | null;
  plannedUnits: number;
  actualUnits: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProjectResourceAssignmentRequest {
  activityId: string;
  resourceId: string;
  assignmentStartDate?: string;
  assignmentFinishDate?: string;
  plannedUnits: number;
}

export const resourceApi = {
  listResources: (page = 0, size = 20) =>
    apiClient
      .get<ApiResponse<PagedResponse<ResourceResponse>>>("/v1/resources", {
        params: { page, size },
      })
      .then((r) => r.data),

  getResource: (id: string) =>
    apiClient.get<ApiResponse<ResourceResponse>>(`/v1/resources/${id}`).then((r) => r.data),

  createResource: (data: CreateResourceRequest) =>
    apiClient.post<ApiResponse<ResourceResponse>>("/v1/resources", {
      code: data.code,
      name: data.name,
      resourceType: data.type,
      maxUnitsPerDay: data.maxUnits ?? 8,
      calendarId: data.calendarId,
    }).then((r) => r.data),

  updateResource: (id: string, data: UpdateResourceRequest) =>
    apiClient.put<ApiResponse<ResourceResponse>>(`/v1/resources/${id}`, data).then((r) => r.data),

  deleteResource: (id: string) => apiClient.delete(`/v1/resources/${id}`),

  getResourceAssignments: (resourceId: string, page = 0, size = 20) =>
    apiClient
      .get<ApiResponse<PagedResponse<ResourceAssignmentResponse>>>(
        `/v1/resources/${resourceId}/assignments`,
        { params: { page, size } }
      )
      .then((r) => r.data),

  assignResourceToActivity: (activityId: string, resourceId: string, units: number) =>
    apiClient
      .post<ApiResponse<ResourceAssignmentResponse>>(
        `/v1/activities/${activityId}/assign-resource`,
        { resourceId, plannedUnits: units }
      )
      .then((r) => r.data),

  getProjectResourceAssignments: (projectId: string, page = 0, size = 20) =>
    apiClient
      .get<ApiResponse<PagedResponse<ResourceAssignmentResponse>>>(
        `/v1/projects/${projectId}/resource-assignments`,
        { params: { page, size } }
      )
      .then((r) => r.data),

  createProjectResourceAssignment: (projectId: string, data: CreateProjectResourceAssignmentRequest) =>
    apiClient
      .post<ApiResponse<ResourceAssignmentResponse>>(
        `/v1/projects/${projectId}/resource-assignments`,
        data
      )
      .then((r) => r.data),

  levelResources: (projectId: string, request: ResourceLevelingRequest) =>
    apiClient
      .post<ApiResponse<ResourceLevelingResponse>>(
        `/v1/projects/${projectId}/resource-assignments/level`,
        request
      )
      .then((r) => r.data),

  getUtilizationProfile: (projectId: string) =>
    apiClient
      .get<ApiResponse<UtilizationProfileEntry[]>>(
        `/v1/projects/${projectId}/resource-assignments/utilization-profile`
      )
      .then((r) => r.data),
};
