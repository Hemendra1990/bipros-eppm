import { apiClient } from "./client";
import type {
  ApiResponse,
  CreateProfileRequest,
  PermissionDescriptor,
  ProfileResponse,
  UpdateProfileRequest,
} from "../types";

export const profileApi = {
  listProfiles: () =>
    apiClient.get<ApiResponse<ProfileResponse[]>>("/v1/profiles").then((r) => r.data),

  getProfile: (id: string) =>
    apiClient.get<ApiResponse<ProfileResponse>>(`/v1/profiles/${id}`).then((r) => r.data),

  createProfile: (body: CreateProfileRequest) =>
    apiClient.post<ApiResponse<ProfileResponse>>("/v1/profiles", body).then((r) => r.data),

  updateProfile: (id: string, body: UpdateProfileRequest) =>
    apiClient.put<ApiResponse<ProfileResponse>>(`/v1/profiles/${id}`, body).then((r) => r.data),

  deleteProfile: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/v1/profiles/${id}`).then((r) => r.data),

  listPermissions: () =>
    apiClient
      .get<ApiResponse<PermissionDescriptor[]>>("/v1/profiles/permissions")
      .then((r) => r.data),
};
