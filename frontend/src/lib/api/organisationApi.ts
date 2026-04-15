import { apiClient } from "./client";
import type { ApiResponse, OrganisationResponse, OrganisationType } from "../types";

export const organisationApi = {
  listAll: () =>
    apiClient.get<ApiResponse<OrganisationResponse[]>>("/v1/organisations").then((r) => r.data),

  listByType: (type: OrganisationType) =>
    apiClient
      .get<ApiResponse<OrganisationResponse[]>>(`/v1/organisations?type=${type}`)
      .then((r) => r.data),

  get: (id: string) =>
    apiClient.get<ApiResponse<OrganisationResponse>>(`/v1/organisations/${id}`).then((r) => r.data),
};
