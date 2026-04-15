import { apiClient } from "./client";
import type {
  ApiResponse,
  IcpmsModule,
  ModuleAccessLevel,
  PagedResponse,
  UserResponse,
} from "../types";

export interface UserAccessApiResponse {
  userId: string;
  moduleAccess: Partial<Record<IcpmsModule, ModuleAccessLevel>>;
  corridorScopes: string[];
  allCorridors: boolean;
}

export const userApi = {
  listUsers: (page = 0, size = 50) =>
    apiClient
      .get<ApiResponse<PagedResponse<UserResponse>>>(`/v1/users?page=${page}&size=${size}`)
      .then((r) => r.data),

  getUser: (id: string) =>
    apiClient.get<ApiResponse<UserResponse>>(`/v1/users/${id}`).then((r) => r.data),

  getAccess: (id: string) =>
    apiClient
      .get<ApiResponse<UserAccessApiResponse>>(`/v1/users/${id}/access`)
      .then((r) => r.data),
};
