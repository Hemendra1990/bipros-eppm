import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export type UdfDataType = "TEXT" | "NUMBER" | "COST" | "DATE" | "INDICATOR" | "CODE";
export type UdfSubject = "ACTIVITY" | "RESOURCE_ASSIGNMENT" | "WBS" | "PROJECT";
export type UdfScope = "GLOBAL" | "PROJECT";
export type IndicatorColor = "NONE" | "RED" | "YELLOW" | "GREEN" | "BLUE";

export interface UserDefinedFieldDto {
  id: string;
  name: string;
  description: string | null;
  dataType: UdfDataType;
  subject: UdfSubject;
  scope: UdfScope;
  projectId: string | null;
  isFormula: boolean;
  formulaExpression: string | null;
  defaultValue: string | null;
  sortOrder: number;
}

export interface CreateUserDefinedFieldRequest {
  name: string;
  description?: string;
  dataType: UdfDataType;
  subject: UdfSubject;
  scope?: UdfScope;
  projectId?: string;
  isFormula?: boolean;
  formulaExpression?: string;
  defaultValue?: string;
  sortOrder?: number;
}

export interface SetUdfValueRequest {
  textValue?: string;
  numberValue?: number;
  costValue?: number;
  dateValue?: string;
  indicatorValue?: IndicatorColor;
  codeValue?: string;
}

export interface UdfValueResponse {
  id: string;
  userDefinedFieldId: string;
  entityId: string;
  textValue: string | null;
  numberValue: number | null;
  costValue: number | null;
  dateValue: string | null;
  indicatorValue: IndicatorColor | null;
  codeValue: string | null;
}

export const udfApi = {
  createField: (data: CreateUserDefinedFieldRequest) =>
    apiClient
      .post<ApiResponse<UserDefinedFieldDto>>("/v1/udf/fields", data)
      .then((r) => r.data),

  listFields: (subject: UdfSubject, scope?: UdfScope, projectId?: string) =>
    apiClient
      .get<ApiResponse<UserDefinedFieldDto[]>>("/v1/udf/fields", {
        params: {
          subject,
          ...(scope ? { scope } : {}),
          ...(projectId ? { projectId } : {}),
        },
      })
      .then((r) => r.data),

  updateField: (fieldId: string, data: CreateUserDefinedFieldRequest) =>
    apiClient
      .put<ApiResponse<UserDefinedFieldDto>>(`/v1/udf/fields/${fieldId}`, data)
      .then((r) => r.data),

  deleteField: (fieldId: string) =>
    apiClient.delete(`/v1/udf/fields/${fieldId}`),

  setValue: (fieldId: string, entityId: string, data: SetUdfValueRequest) =>
    apiClient
      .put<ApiResponse<UdfValueResponse>>(
        `/v1/udf/values/${fieldId}/${entityId}`,
        data
      )
      .then((r) => r.data),

  getValue: (fieldId: string, entityId: string) =>
    apiClient
      .get<ApiResponse<UdfValueResponse>>(
        `/v1/udf/values/${fieldId}/${entityId}`
      )
      .then((r) => r.data),

  getEntityValues: (entityId: string) =>
    apiClient
      .get<ApiResponse<UdfValueResponse[]>>(`/v1/udf/values/${entityId}`)
      .then((r) => r.data),

  evaluateFormula: (fieldId: string, entityId: string) =>
    apiClient
      .get<ApiResponse<string>>(`/v1/udf/fields/${fieldId}/evaluate/${entityId}`)
      .then((r) => r.data),
};
