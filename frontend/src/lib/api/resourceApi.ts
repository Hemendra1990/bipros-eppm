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
  /** Base category derived from the chosen Resource Type def. */
  resourceType: "LABOR" | "NONLABOR" | "MATERIAL";
  /** The admin-managed Resource Type def this resource references. */
  resourceTypeDefId?: string | null;
  resourceTypeCode?: string | null;
  resourceTypeName?: string | null;
  resourceCategory?: ResourceCategory | null;
  unit?: ResourceUnit | null;
  status: string;
  maxUnitsPerDay: number;
  hourlyRate: number;
  costPerUse: number;
  overtimeRate: number;
  poolMaxAvailable?: number | null;
  plannedUnitsToday?: number | null;
  actualUnitsToday?: number | null;
  utilisationPercent?: number | null;
  utilisationStatus?: UtilisationStatus | null;
  dailyCostLakh?: number | null;
  cumulativeCostCrores?: number | null;
  wbsAssignmentId?: string | null;
  calendarId: string | null;
  userId?: string | null;
  createdAt: string;
  updatedAt: string;
}

export type ResourceOwnership = "OWNED" | "HIRED" | "SUB_CONTRACTOR_PROVIDED";

export interface CreateResourceRequest {
  code?: string;
  name: string;
  /** Preferred: id of the admin-managed Resource Type def. */
  resourceTypeDefId?: string;
  /** Legacy: base-category enum. Used when resourceTypeDefId is absent. */
  type?: "LABOR" | "NONLABOR" | "MATERIAL";
  maxUnits?: number;
  hourlyRate?: number;
  costPerUse?: number;
  overtimeRate?: number;
  calendarId?: string;
  // PMS MasterData Screen 04 equipment fields
  capacitySpec?: string;
  makeModel?: string;
  quantityAvailable?: number;
  ownershipType?: ResourceOwnership;
  standardOutputPerDay?: number;
  standardOutputUnit?: string;
  fuelLitresPerHour?: number;
}

export interface UpdateResourceRequest {
  code: string;
  name: string;
  /** Preferred: id of the admin-managed Resource Type def. */
  resourceTypeDefId?: string;
  /** Legacy: base-category enum. Used when resourceTypeDefId is absent. */
  resourceType?: "LABOR" | "NONLABOR" | "MATERIAL";
  maxUnitsPerDay?: number;
  status?: string;
  hourlyRate?: number;
  costPerUse?: number;
  overtimeRate?: number;
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
  activityName: string | null;
  resourceId: string | null;
  resourceName: string | null;
  roleId: string | null;
  roleName: string | null;
  projectId: string | null;
  plannedUnits: number;
  actualUnits: number;
  remainingUnits: number | null;
  atCompletionUnits: number | null;
  plannedCost: number | null;
  actualCost: number | null;
  remainingCost: number | null;
  atCompletionCost: number | null;
  rateType: string | null;
  plannedStartDate: string | null;
  plannedFinishDate: string | null;
  actualStartDate: string | null;
  actualFinishDate: string | null;
  staffed: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProjectResourceAssignmentRequest {
  activityId: string;
  resourceId?: string;
  roleId?: string;
  projectId: string;
  assignmentStartDate?: string;
  assignmentFinishDate?: string;
  plannedUnits: number;
  rateType?: string;
}

export interface StaffAssignmentRequest {
  resourceId: string;
  override?: boolean;
}

export interface SwapResourceRequest {
  resourceId: string;
  override?: boolean;
}

export const resourceApi = {
  // The backend `ResourceController.listResources` returns
  // `ApiResponse<List<ResourceResponse>>` (a flat array), NOT a Spring Page.
  // It only accepts optional `type` / `status` filters. We keep the legacy
  // `page`/`size` positional signature for backwards compatibility with
  // existing callers, but the parameters are ignored by the server.
  listResources: (_page = 0, _size = 20) =>
    apiClient
      .get<ApiResponse<ResourceResponse[]>>("/v1/resources")
      .then((r) => r.data),

  getResource: (id: string) =>
    apiClient.get<ApiResponse<ResourceResponse>>(`/v1/resources/${id}`).then((r) => r.data),

  createResource: (data: CreateResourceRequest) =>
    apiClient.post<ApiResponse<ResourceResponse>>("/v1/resources", {
      code: data.code,
      name: data.name,
      resourceTypeDefId: data.resourceTypeDefId,
      resourceType: data.type,
      maxUnitsPerDay: data.maxUnits ?? 8,
      hourlyRate: data.hourlyRate ?? 0,
      costPerUse: data.costPerUse ?? 0,
      overtimeRate: data.overtimeRate ?? 0,
      calendarId: data.calendarId,
      capacitySpec: data.capacitySpec,
      makeModel: data.makeModel,
      quantityAvailable: data.quantityAvailable,
      ownershipType: data.ownershipType,
      standardOutputPerDay: data.standardOutputPerDay,
      standardOutputUnit: data.standardOutputUnit,
      fuelLitresPerHour: data.fuelLitresPerHour,
    }).then((r) => r.data),

  updateResource: (id: string, data: UpdateResourceRequest) =>
    apiClient.put<ApiResponse<ResourceResponse>>(`/v1/resources/${id}`, data).then((r) => r.data),

  deleteResource: (id: string) => apiClient.delete(`/v1/resources/${id}`),

  deleteAllResources: () => apiClient.delete("/v1/resources"),

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

  getAssignmentsByActivity: (projectId: string, activityId: string) =>
    apiClient
      .get<ApiResponse<ResourceAssignmentResponse[]>>(
        `/v1/projects/${projectId}/resource-assignments/activity/${activityId}`
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

  staffAssignment: (projectId: string, id: string, data: StaffAssignmentRequest) =>
    apiClient
      .post<ApiResponse<ResourceAssignmentResponse>>(
        `/v1/projects/${projectId}/resource-assignments/${id}/staff`,
        data
      )
      .then((r) => r.data),

  swapResource: (projectId: string, id: string, data: SwapResourceRequest) =>
    apiClient
      .post<ApiResponse<ResourceAssignmentResponse>>(
        `/v1/projects/${projectId}/resource-assignments/${id}/swap`,
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
