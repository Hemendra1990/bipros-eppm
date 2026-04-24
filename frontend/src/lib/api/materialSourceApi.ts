import { apiClient } from "./client";
import type {
  ApiResponse,
  CreateMaterialSourceRequest,
  MaterialSourceResponse,
  MaterialSourceType,
} from "../types";

export const materialSourceApi = {
  listByProject: (projectId: string, sourceType?: MaterialSourceType) => {
    const qs = sourceType ? `?sourceType=${sourceType}` : "";
    return apiClient
      .get<ApiResponse<MaterialSourceResponse[]>>(`/v1/projects/${projectId}/material-sources${qs}`)
      .then((r) => r.data);
  },

  create: (projectId: string, body: CreateMaterialSourceRequest) =>
    apiClient
      .post<ApiResponse<MaterialSourceResponse>>(
        `/v1/projects/${projectId}/material-sources`,
        body,
      )
      .then((r) => r.data),

  get: (id: string) =>
    apiClient
      .get<ApiResponse<MaterialSourceResponse>>(`/v1/material-sources/${id}`)
      .then((r) => r.data),

  update: (id: string, body: Partial<CreateMaterialSourceRequest>) =>
    apiClient
      .put<ApiResponse<MaterialSourceResponse>>(`/v1/material-sources/${id}`, body)
      .then((r) => r.data),

  delete: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/v1/material-sources/${id}`).then((r) => r.data),
};
