import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface SubContractorMaster {
  id: string;
  code: string;
  name: string;
  location?: string | null;
  primaryContactName?: string | null;
  primaryContactNumber?: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SubContractorMasterRequest {
  code: string;
  name: string;
  location?: string | null;
  primaryContactName?: string | null;
  primaryContactNumber?: string | null;
  active?: boolean;
}

export const subContractorMasterApi = {
  list: () =>
    apiClient
      .get<ApiResponse<SubContractorMaster[]>>("/v1/admin/sub-contractors")
      .then((r) => r.data),

  get: (id: string) =>
    apiClient
      .get<ApiResponse<SubContractorMaster>>(`/v1/admin/sub-contractors/${id}`)
      .then((r) => r.data),

  create: (request: SubContractorMasterRequest) =>
    apiClient
      .post<ApiResponse<SubContractorMaster>>("/v1/admin/sub-contractors", request)
      .then((r) => r.data),

  update: (id: string, request: SubContractorMasterRequest) =>
    apiClient
      .put<ApiResponse<SubContractorMaster>>(`/v1/admin/sub-contractors/${id}`, request)
      .then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/v1/admin/sub-contractors/${id}`),
};
