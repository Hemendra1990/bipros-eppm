import { apiClient } from "./client";
import type { ApiResponse, AuthResponse, LoginRequest, RegisterRequest, UserResponse } from "../types";

export const authApi = {
  login: (data: LoginRequest) =>
    apiClient.post<ApiResponse<AuthResponse>>("/v1/auth/login", data).then((r) => r.data),

  register: (data: RegisterRequest) =>
    apiClient.post<ApiResponse<AuthResponse>>("/v1/auth/register", data).then((r) => r.data),

  refresh: (refreshToken: string) =>
    apiClient.post<ApiResponse<AuthResponse>>("/v1/auth/refresh", { refreshToken }).then((r) => r.data),

  me: () =>
    apiClient.get<ApiResponse<UserResponse>>("/v1/users/me").then((r) => r.data),
};
