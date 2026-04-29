import { apiClient } from "./client";
import type { ApiResponse, PagedResponse } from "../types";

export type LabourCategory =
  | "SITE_MANAGEMENT"
  | "PLANT_EQUIPMENT"
  | "SKILLED_LABOUR"
  | "SEMI_SKILLED_LABOUR"
  | "GENERAL_UNSKILLED";

export type LabourGrade = "A" | "B" | "C" | "D" | "E";
export type NationalityType = "OMANI" | "EXPAT" | "OMANI_OR_EXPAT";
export type LabourStatus = "ACTIVE" | "INACTIVE";

export interface LabourDesignationResponse {
  id: string;
  code: string;
  designation: string;
  category: LabourCategory;
  categoryDisplay: string;
  codePrefix: string;
  trade: string;
  grade: LabourGrade;
  nationality: NationalityType;
  experienceYearsMin: number;
  defaultDailyRate: number;
  currency: string;
  skills: string[];
  certifications: string[];
  keyRoleSummary: string | null;
  status: LabourStatus;
  sortOrder: number;
  deployment?: {
    id: string;
    workerCount: number;
    actualDailyRate: number | null;
    effectiveRate: number;
    dailyCost: number;
    notes: string | null;
  } | null;
}

export interface LabourDesignationRequest {
  code: string;
  designation: string;
  category: LabourCategory;
  trade: string;
  grade: LabourGrade;
  nationality: NationalityType;
  experienceYearsMin: number;
  defaultDailyRate: number;
  currency?: string;
  skills?: string[];
  certifications?: string[];
  keyRoleSummary?: string;
  status?: LabourStatus;
  sortOrder?: number;
}

export interface ProjectLabourDeploymentRequest {
  designationId: string;
  workerCount: number;
  actualDailyRate?: number;
  notes?: string;
}

export interface ProjectLabourDeploymentResponse {
  id: string;
  projectId: string;
  designationId: string;
  workerCount: number;
  actualDailyRate: number | null;
  effectiveRate: number;
  dailyCost: number;
  notes: string | null;
  designation: LabourDesignationResponse;
}

export interface LabourCategorySummary {
  category: LabourCategory;
  categoryDisplay: string;
  codePrefix: string;
  designationCount: number;
  workerCount: number;
  dailyCost: number;
  gradeRange: string;
  dailyRateRange: string;
  keyRolesSummary: string;
}

export interface LabourMasterDashboardSummary {
  projectId: string;
  totalDesignations: number;
  totalWorkforce: number;
  dailyPayroll: number;
  currency: string;
  skillCategoryCount: number;
  nationalityMix: { omani: number; expat: number; omaniOrExpat: number };
  byCategory: LabourCategorySummary[];
}

export interface LabourGradeReference {
  grade: LabourGrade;
  classification: string;
  dailyRateRange: string;
  description: string;
}

export interface LabourCategoryReference {
  category: LabourCategory;
  codePrefix: string;
  displayName: string;
}

export const labourMasterApi = {
  designations: {
    list: (params?: {
      category?: LabourCategory;
      grade?: LabourGrade;
      status?: LabourStatus;
      q?: string;
      page?: number;
      size?: number;
    }) =>
      apiClient
        .get<ApiResponse<PagedResponse<LabourDesignationResponse>>>(
          "/v1/labour-designations",
          { params }
        )
        .then((r) => r.data),

    get: (id: string) =>
      apiClient
        .get<ApiResponse<LabourDesignationResponse>>(`/v1/labour-designations/${id}`)
        .then((r) => r.data),

    getByCode: (code: string) =>
      apiClient
        .get<ApiResponse<LabourDesignationResponse>>(`/v1/labour-designations/by-code/${code}`)
        .then((r) => r.data),

    create: (req: LabourDesignationRequest) =>
      apiClient
        .post<ApiResponse<LabourDesignationResponse>>("/v1/labour-designations", req)
        .then((r) => r.data),

    update: (id: string, req: LabourDesignationRequest) =>
      apiClient
        .put<ApiResponse<LabourDesignationResponse>>(`/v1/labour-designations/${id}`, req)
        .then((r) => r.data),

    remove: (id: string) =>
      apiClient
        .delete<ApiResponse<void>>(`/v1/labour-designations/${id}`)
        .then((r) => r.data),

    listCategories: () =>
      apiClient
        .get<ApiResponse<LabourCategoryReference[]>>("/v1/labour-designations/categories")
        .then((r) => r.data),

    listGrades: () =>
      apiClient
        .get<ApiResponse<LabourGradeReference[]>>("/v1/labour-designations/grades")
        .then((r) => r.data),
  },

  deployments: {
    listForProject: (projectId: string) =>
      apiClient
        .get<ApiResponse<ProjectLabourDeploymentResponse[]>>(
          `/v1/projects/${projectId}/labour-deployments`
        )
        .then((r) => r.data),

    getDashboard: (projectId: string) =>
      apiClient
        .get<ApiResponse<LabourMasterDashboardSummary>>(
          `/v1/projects/${projectId}/labour-deployments/dashboard`
        )
        .then((r) => r.data),

    getByCategory: (projectId: string) =>
      apiClient
        .get<ApiResponse<LabourCategorySummary[]>>(
          `/v1/projects/${projectId}/labour-deployments/by-category`
        )
        .then((r) => r.data),

    create: (projectId: string, req: ProjectLabourDeploymentRequest) =>
      apiClient
        .post<ApiResponse<ProjectLabourDeploymentResponse>>(
          `/v1/projects/${projectId}/labour-deployments`,
          req
        )
        .then((r) => r.data),

    update: (
      projectId: string,
      deploymentId: string,
      req: ProjectLabourDeploymentRequest
    ) =>
      apiClient
        .put<ApiResponse<ProjectLabourDeploymentResponse>>(
          `/v1/projects/${projectId}/labour-deployments/${deploymentId}`,
          req
        )
        .then((r) => r.data),

    remove: (projectId: string, deploymentId: string) =>
      apiClient
        .delete<ApiResponse<void>>(
          `/v1/projects/${projectId}/labour-deployments/${deploymentId}`
        )
        .then((r) => r.data),
  },
};
