import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface ActivitySubContractorAssignment {
  id: string;
  activityId: string;
  projectId: string;
  subContractorMasterId: string;
  subContractorName: string;
  subContractorCode: string;
  subContractorLocation?: string | null;
  units: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateActivitySubContractorAssignmentRequest {
  activityId: string;
  subContractorMasterId: string;
  units: number;
}

export const activitySubContractorApi = {
  listForActivity: (projectId: string, activityId: string) =>
    apiClient
      .get<ApiResponse<ActivitySubContractorAssignment[]>>(
        `/v1/projects/${projectId}/activities/${activityId}/sub-contractor-assignments`,
      )
      .then((r) => r.data),

  create: (projectId: string, req: CreateActivitySubContractorAssignmentRequest) =>
    apiClient
      .post<ApiResponse<ActivitySubContractorAssignment>>(
        `/v1/projects/${projectId}/sub-contractor-assignments`,
        req,
      )
      .then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/v1/sub-contractor-assignments/${id}`),
};
