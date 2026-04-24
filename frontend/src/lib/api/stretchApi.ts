import { apiClient } from "./client";
import type {
  ApiResponse,
  CreateStretchRequest,
  StretchResponse,
} from "../types";

export interface StretchProgressResponse {
  stretchId: string;
  stretchCode: string;
  stretchName: string | null;
  linkedBoqItemCount: number;
  totalBoqAmount: number;
  totalExecutedAmount: number;
  percentComplete: number;
}

export const stretchApi = {
  listByProject: (projectId: string) =>
    apiClient
      .get<ApiResponse<StretchResponse[]>>(`/v1/projects/${projectId}/stretches`)
      .then((r) => r.data),

  create: (projectId: string, body: CreateStretchRequest) =>
    apiClient
      .post<ApiResponse<StretchResponse>>(`/v1/projects/${projectId}/stretches`, body)
      .then((r) => r.data),

  get: (id: string) =>
    apiClient.get<ApiResponse<StretchResponse>>(`/v1/stretches/${id}`).then((r) => r.data),

  update: (id: string, body: Partial<CreateStretchRequest>) =>
    apiClient
      .put<ApiResponse<StretchResponse>>(`/v1/stretches/${id}`, body)
      .then((r) => r.data),

  delete: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/v1/stretches/${id}`).then((r) => r.data),

  assignActivities: (id: string, boqItemIds: string[]) =>
    apiClient
      .post<ApiResponse<StretchResponse>>(`/v1/stretches/${id}/activities`, { boqItemIds })
      .then((r) => r.data),

  progress: (id: string) =>
    apiClient
      .get<ApiResponse<StretchProgressResponse>>(`/v1/stretches/${id}/progress`)
      .then((r) => r.data),
};
