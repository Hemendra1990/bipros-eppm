import { apiClient } from "./client";
import type {
  ApiResponse,
  CreateOrganisationRequest,
  OrganisationResponse,
  OrganisationType,
} from "../types";

export const organisationApi = {
  listAll: () =>
    apiClient.get<ApiResponse<OrganisationResponse[]>>("/v1/organisations").then((r) => r.data),

  listByType: (type: OrganisationType) =>
    apiClient
      .get<ApiResponse<OrganisationResponse[]>>(`/v1/organisations?type=${type}`)
      .then((r) => r.data),

  get: (id: string) =>
    apiClient.get<ApiResponse<OrganisationResponse>>(`/v1/organisations/${id}`).then((r) => r.data),

  create: (body: CreateOrganisationRequest) =>
    apiClient
      .post<ApiResponse<OrganisationResponse>>("/v1/organisations", body)
      .then((r) => r.data),

  update: (id: string, body: CreateOrganisationRequest) =>
    apiClient
      .put<ApiResponse<OrganisationResponse>>(`/v1/organisations/${id}`, body)
      .then((r) => r.data),

  delete: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/v1/organisations/${id}`).then((r) => r.data),

  assignProjects: (id: string, projectIds: string[]) =>
    apiClient
      .post<ApiResponse<OrganisationResponse>>(`/v1/organisations/${id}/projects`, { projectIds })
      .then((r) => r.data),
};
