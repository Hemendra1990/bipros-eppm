import { apiClient } from './client'

export interface IntegrationConfig {
  id: string
  systemCode: string
  systemName: string
  baseUrl: string
  apiKey?: string
  isEnabled: boolean
  authType: 'NONE' | 'API_KEY' | 'OAUTH2' | 'JWT'
  lastSyncAt?: string
  status: 'ACTIVE' | 'INACTIVE' | 'ERROR'
  configJson?: string
  createdAt: string
  updatedAt: string
}

export interface IntegrationLog {
  id: string
  integrationConfigId: string
  direction: 'INBOUND' | 'OUTBOUND'
  endpoint: string
  requestPayload?: string
  responsePayload?: string
  httpStatus?: number
  status: 'SUCCESS' | 'FAILED' | 'TIMEOUT' | 'PENDING'
  errorMessage?: string
  durationMs?: number
  createdAt: string
}

export interface PfmsFundTransfer {
  id: string
  projectId: string
  pfmsReferenceNumber?: string
  sanctionOrderNumber: string
  amount: number
  purpose?: string
  beneficiary: string
  transferDate: string
  status: 'INITIATED' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'REVERSED'
  pfmsStatus?: string
  createdAt: string
  updatedAt: string
}

export const integrationApi = {
  // Integration Configs
  listIntegrations: () => apiClient.get<IntegrationConfig[]>('/v1/integrations'),

  getIntegrationById: (id: string) => apiClient.get<IntegrationConfig>(`/v1/integrations/${id}`),

  getIntegrationBySystemCode: (systemCode: string) =>
    apiClient.get<IntegrationConfig>(`/v1/integrations/system/${systemCode}`),

  updateIntegration: (id: string, data: Partial<IntegrationConfig>) =>
    apiClient.put<IntegrationConfig>(`/v1/integrations/${id}`, data),

  // PFMS Operations
  checkPfmsFundStatus: (projectId: string, sanctionOrderNumber: string) =>
    apiClient.post<PfmsFundTransfer>(
      `/v1/projects/${projectId}/pfms/check-fund`,
      {},
      { params: { sanctionOrderNumber } }
    ),

  initiatePfmsPayment: (
    projectId: string,
    sanctionOrderNumber: string,
    beneficiary: string,
    amount: number,
    purpose?: string
  ) =>
    apiClient.post<PfmsFundTransfer>(
      `/v1/projects/${projectId}/pfms/initiate-payment`,
      {},
      { params: { sanctionOrderNumber, beneficiary, amount, purpose } }
    ),

  getPfmsFundTransfers: (projectId: string, page: number = 0, size: number = 20) =>
    apiClient.get<{ content: PfmsFundTransfer[]; totalElements: number; totalPages: number }>(
      `/v1/projects/${projectId}/pfms/transfers`,
      { params: { page, size } }
    ),
}
