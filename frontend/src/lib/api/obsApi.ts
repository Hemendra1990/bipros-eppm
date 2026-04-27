import { apiClient } from "./client";
import type {
  ApiResponse,
  CreateObsNodeRequest,
  NodeSearchResult,
  ObsNodeResponse,
  PagedResponse,
} from "../types";

export const obsApi = {
  // OBS Tree
  getObsTree: () =>
    apiClient.get<ApiResponse<ObsNodeResponse[]>>("/v1/obs").then((r) => r.data),

  searchObs: (q: string, page = 0, size = 25) =>
    apiClient
      .get<ApiResponse<PagedResponse<NodeSearchResult>>>("/v1/obs/search", {
        params: { q, page, size },
      })
      .then((r) => r.data),

  createObsNode: (data: CreateObsNodeRequest) =>
    apiClient
      .post<ApiResponse<ObsNodeResponse>>("/v1/obs", data)
      .then((r) => r.data),

  updateObsNode: (id: string, data: Partial<CreateObsNodeRequest>) =>
    apiClient
      .put<ApiResponse<ObsNodeResponse>>(`/v1/obs/${id}`, data)
      .then((r) => r.data),

  deleteObsNode: (id: string) =>
    apiClient.delete(`/v1/obs/${id}`),
};
