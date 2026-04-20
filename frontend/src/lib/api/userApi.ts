import { apiClient } from "./client";
import type { ApiResponse, PagedResponse, UserResponse } from "../types";

export interface UpdateUserRolesRequest {
  roles: string[];
}

export const userApi = {
  listUsers: (page = 0, size = 50) =>
    apiClient
      .get<ApiResponse<PagedResponse<UserResponse>>>("/v1/users", {
        params: { page, size },
      })
      .then((r) => r.data),

  updateUserRoles: (userId: string, data: UpdateUserRolesRequest) =>
    apiClient
      .put<ApiResponse<UserResponse>>(`/v1/users/${userId}/roles`, data)
      .then((r) => r.data),

  toggleUserEnabled: (userId: string, enabled: boolean) =>
    apiClient
      .put<ApiResponse<UserResponse>>(`/v1/users/${userId}/status`, { enabled })
      .then((r) => r.data),
};
