import { apiClient } from "./client";
import type {
  ApiResponse,
  AssetClass,
  CorridorCodeResponse,
  CreateCorridorCodeRequest,
  CreateWbsTemplateRequest,
  WbsTemplateResponse,
} from "../types";

export const wbsTemplateApi = {
  // WBS Templates
  listTemplates: () =>
    apiClient
      .get<ApiResponse<WbsTemplateResponse[]>>("/v1/wbs-templates")
      .then((r) => r.data),

  listTemplatesByAssetClass: (assetClass: AssetClass) =>
    apiClient
      .get<ApiResponse<WbsTemplateResponse[]>>(`/v1/wbs-templates/asset-class/${assetClass}`)
      .then((r) => r.data),

  getTemplate: (id: string) =>
    apiClient
      .get<ApiResponse<WbsTemplateResponse>>(`/v1/wbs-templates/${id}`)
      .then((r) => r.data),

  createTemplate: (data: CreateWbsTemplateRequest) =>
    apiClient
      .post<ApiResponse<WbsTemplateResponse>>("/v1/wbs-templates", data)
      .then((r) => r.data),

  applyTemplate: (templateId: string, projectId: string) =>
    apiClient
      .post(`/v1/wbs-templates/${templateId}/apply`, null, { params: { projectId } })
      .then(() => undefined),

  // Corridor Codes
  getCorridorCode: (projectId: string) =>
    apiClient
      .get<ApiResponse<CorridorCodeResponse>>(
        `/v1/projects/${projectId}/corridor-code`
      )
      .then((r) => r.data),

  generateCorridorCode: (projectId: string, data: CreateCorridorCodeRequest) =>
    apiClient
      .post<ApiResponse<CorridorCodeResponse>>(
        `/v1/projects/${projectId}/corridor-code`,
        data
      )
      .then((r) => r.data),
};
