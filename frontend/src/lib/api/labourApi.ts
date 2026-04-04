import { apiClient } from "./client";
import type { ApiResponse, PagedResponse } from "../types";

export interface LabourReturnResponse {
  id: string;
  projectId: string;
  contractorName: string;
  returnDate: string;
  skillCategory: "SKILLED" | "SEMI_SKILLED" | "UNSKILLED" | "SUPERVISOR" | "ENGINEER";
  headCount: number;
  manDays: number;
  wbsNodeId: string | null;
  siteLocation: string | null;
  remarks: string | null;
  createdAt: string;
  createdBy: string | null;
}

export interface CreateLabourReturnRequest {
  projectId: string;
  contractorName: string;
  returnDate: string;
  skillCategory: "SKILLED" | "SEMI_SKILLED" | "UNSKILLED" | "SUPERVISOR" | "ENGINEER";
  headCount: number;
  manDays: number;
  wbsNodeId?: string;
  siteLocation?: string;
  remarks?: string;
}

export interface DeploymentSummary {
  skillCategory: "SKILLED" | "SEMI_SKILLED" | "UNSKILLED" | "SUPERVISOR" | "ENGINEER";
  totalHeadCount: number;
  totalManDays: number;
}

export const labourApi = {
  createReturn: (projectId: string, data: CreateLabourReturnRequest) =>
    apiClient
      .post<ApiResponse<LabourReturnResponse>>(
        `/v1/projects/${projectId}/labour-returns`,
        data
      )
      .then((r) => r.data),

  getReturnsByProject: (
    projectId: string,
    page = 0,
    size = 20,
    filters?: {
      fromDate?: string;
      toDate?: string;
    }
  ) =>
    apiClient
      .get<ApiResponse<PagedResponse<LabourReturnResponse>>>(
        `/v1/projects/${projectId}/labour-returns`,
        {
          params: {
            page,
            size,
            ...filters,
          },
        }
      )
      .then((r) => r.data),

  getDeploymentSummary: (projectId: string) =>
    apiClient
      .get<ApiResponse<DeploymentSummary[]>>(
        `/v1/projects/${projectId}/labour-returns/summary`
      )
      .then((r) => r.data),
};
