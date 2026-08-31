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

  /**
   * Slim list of every project the current user can see — used by the AI chat
   * panel's portfolio-mode banner ("All accessible projects (N)") and by any
   * other surface that needs an unpaged roster of code/name pairs.
   *
   * Backend exposes the full {@link ProjectResponse} via the paginated
   * {@link listProjects} endpoint; the dedicated `/v1/projects/accessible`
   * endpoint is not yet wired (planned in a sibling phase). Until then we
   * fall back to a large page from `/v1/projects` and project to the slim
   * shape so callers can swap implementations without touching the call site.
   */
  listAccessible: async (): Promise<ApiResponse<Array<{ id: string; code: string; name: string }>>> => {
    const res = await apiClient.get<ApiResponse<PagedResponse<ProjectResponse>>>("/v1/projects", {
      params: { page: 0, size: 500 },
    });
    const envelope = res.data;
    const slim = (envelope.data?.content ?? []).map((p) => ({ id: p.id, code: p.code, name: p.name }));
    return { ...envelope, data: slim };
  },

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
