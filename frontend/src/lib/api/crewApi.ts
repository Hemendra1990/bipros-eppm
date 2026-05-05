import { apiClient } from "./client";
import type { ApiResponse } from "../types";
import type { ResourceStatus } from "./resourceApi";

export interface CrewMemberRequest {
  resourceId: string;
  roleInCrew?: string | null;
  startDate?: string | null;
  endDate?: string | null;
}

export interface CrewMemberResponse {
  id: string;
  crewId: string;
  resourceId: string;
  resourceCode: string | null;
  resourceName: string | null;
  resourceTypeCode: string | null;
  roleInCrew: string | null;
  startDate: string | null;
  endDate: string | null;
}

export interface CrewRequest {
  code?: string | null;
  name: string;
  description?: string | null;
  crewLeadResourceId: string;
  projectId?: string | null;
  status?: ResourceStatus | null;
  sortOrder?: number | null;
  members?: CrewMemberRequest[] | null;
}

export interface CrewResponse {
  id: string;
  code: string | null;
  name: string;
  description: string | null;
  crewLeadResourceId: string;
  crewLeadCode: string | null;
  crewLeadName: string | null;
  projectId: string | null;
  status: ResourceStatus;
  sortOrder: number | null;
  createdAt: string;
  updatedAt: string;
  memberCount: number;
  members: CrewMemberResponse[];
}

export const crewApi = {
  list: () =>
    apiClient.get<ApiResponse<CrewResponse[]>>("/v1/crews").then((r) => r.data),

  get: (id: string) =>
    apiClient
      .get<ApiResponse<CrewResponse>>(`/v1/crews/${id}`)
      .then((r) => r.data),

  create: (data: CrewRequest) =>
    apiClient.post<ApiResponse<CrewResponse>>("/v1/crews", data).then((r) => r.data),

  update: (id: string, data: CrewRequest) =>
    apiClient
      .put<ApiResponse<CrewResponse>>(`/v1/crews/${id}`, data)
      .then((r) => r.data),

  remove: (id: string) => apiClient.delete(`/v1/crews/${id}`),

  listByProject: (projectId: string) =>
    apiClient
      .get<ApiResponse<CrewResponse[]>>(`/v1/crews/by-project/${projectId}`)
      .then((r) => r.data),

  listByLead: (leadResourceId: string) =>
    apiClient
      .get<ApiResponse<CrewResponse[]>>(`/v1/crews/by-lead/${leadResourceId}`)
      .then((r) => r.data),

  listMembers: (crewId: string) =>
    apiClient
      .get<ApiResponse<CrewMemberResponse[]>>(`/v1/crews/${crewId}/members`)
      .then((r) => r.data),

  addMember: (crewId: string, data: CrewMemberRequest) =>
    apiClient
      .post<ApiResponse<CrewMemberResponse>>(`/v1/crews/${crewId}/members`, data)
      .then((r) => r.data),

  removeMember: (crewId: string, memberId: string) =>
    apiClient.delete(`/v1/crews/${crewId}/members/${memberId}`),
};
