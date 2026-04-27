import { apiClient } from "./client";
import type {
  ApiResponse,
  CreateEpsNodeRequest,
  CreateProjectRequest,
  UpdateProjectRequest,
  EpsNodeResponse,
  NodeSearchResult,
  PagedResponse,
  ProjectResponse,
  WbsNodeResponse,
} from "../types";

export const projectApi = {
  // EPS
  getEpsTree: () =>
    apiClient.get<ApiResponse<EpsNodeResponse[]>>("/v1/eps").then((r) => r.data),

  searchEps: (q: string, page = 0, size = 25) =>
    apiClient
      .get<ApiResponse<PagedResponse<NodeSearchResult>>>("/v1/eps/search", {
        params: { q, page, size },
      })
      .then((r) => r.data),

  createEpsNode: (data: CreateEpsNodeRequest) =>
    apiClient.post<ApiResponse<EpsNodeResponse>>("/v1/eps", data).then((r) => r.data),

  deleteEpsNode: (id: string) =>
    apiClient.delete(`/v1/eps/${id}`),

  moveEpsNode: (id: string, parentId: string | null) =>
    apiClient.patch<ApiResponse<EpsNodeResponse>>(`/v1/eps/${id}/move`, { parentId }).then((r) => r.data),

  // Projects
  listProjects: (page = 0, size = 20) =>
    apiClient
      .get<ApiResponse<PagedResponse<ProjectResponse>>>("/v1/projects", { params: { page, size } })
      .then((r) => r.data),

  listArchivedProjects: (page = 0, size = 20) =>
    apiClient
      .get<ApiResponse<PagedResponse<ProjectResponse>>>("/v1/projects/archived", { params: { page, size } })
      .then((r) => r.data),

  getProject: (id: string) =>
    apiClient.get<ApiResponse<ProjectResponse>>(`/v1/projects/${id}`).then((r) => r.data),

  createProject: (data: CreateProjectRequest) =>
    apiClient.post<ApiResponse<ProjectResponse>>("/v1/projects", data).then((r) => r.data),

  updateProject: (id: string, data: UpdateProjectRequest) =>
    apiClient.put<ApiResponse<ProjectResponse>>(`/v1/projects/${id}`, data).then((r) => r.data),

  /** Soft archive — backend stamps {@code archived_at}. Restorable via {@link restoreProject}. */
  archiveProject: (id: string) =>
    apiClient.delete(`/v1/projects/${id}`),

  restoreProject: (id: string) =>
    apiClient.post<ApiResponse<ProjectResponse>>(`/v1/projects/${id}/restore`).then((r) => r.data),

  // WBS
  getWbsTree: (projectId: string) =>
    apiClient
      .get<ApiResponse<WbsNodeResponse[]>>(`/v1/projects/${projectId}/wbs`)
      .then((r) => r.data),
};
