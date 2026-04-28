import { apiClient } from "./client";
import type {
  ApiResponse,
  LlmProviderRequest,
  LlmProviderResponse,
  TestConnectionResponse,
} from "@/lib/types";

export const llmProvidersApi = {
  list: () =>
    apiClient
      .get<ApiResponse<LlmProviderResponse[]>>("/v1/llm-providers")
      .then((r) => r.data),

  create: (body: LlmProviderRequest) =>
    apiClient
      .post<ApiResponse<LlmProviderResponse>>("/v1/llm-providers", body)
      .then((r) => r.data),

  setDefault: (id: string) =>
    apiClient
      .post<ApiResponse<LlmProviderResponse>>(`/v1/llm-providers/${id}/default`)
      .then((r) => r.data),

  test: (id: string) =>
    apiClient
      .post<ApiResponse<TestConnectionResponse>>(`/v1/llm-providers/${id}/test`)
      .then((r) => r.data),

  remove: (id: string) =>
    apiClient.delete<void>(`/v1/llm-providers/${id}`).then((r) => r.data),
};
