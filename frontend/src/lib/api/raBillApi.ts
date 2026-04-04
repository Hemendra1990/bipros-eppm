import { apiClient } from '@/lib/api/client'

export interface RaBill {
  id: string
  projectId: string
  contractId?: string
  billNumber: string
  billPeriodFrom: string
  billPeriodTo: string
  grossAmount: number
  deductions: number
  netAmount: number
  cumulativeAmount: number
  status: 'DRAFT' | 'SUBMITTED' | 'CERTIFIED' | 'APPROVED' | 'PAID' | 'REJECTED'
  submittedDate?: string
  certifiedDate?: string
  approvedDate?: string
  paidDate?: string
  certifiedBy?: string
  approvedBy?: string
  remarks?: string
}

export interface RaBillItem {
  id: string
  raBillId: string
  itemCode: string
  description: string
  unit?: string
  rate?: number
  previousQuantity?: number
  currentQuantity?: number
  cumulativeQuantity?: number
  amount: number
}

export interface CreateRaBillRequest {
  projectId: string
  contractId?: string
  billNumber: string
  billPeriodFrom: string
  billPeriodTo: string
  grossAmount: number
  deductions?: number
  netAmount: number
  remarks?: string
}

export interface CreateRaBillItemRequest {
  raBillId: string
  itemCode: string
  description: string
  unit?: string
  rate?: number
  previousQuantity?: number
  currentQuantity?: number
  cumulativeQuantity?: number
  amount: number
}

export const raBillApi = {
  // RA Bills
  createRaBill: (projectId: string, request: CreateRaBillRequest) =>
    apiClient.post<RaBill>(`/v1/projects/${projectId}/ra-bills`, request),

  getRaBillsByProject: (projectId: string) =>
    apiClient.get<RaBill[]>(`/v1/projects/${projectId}/ra-bills`),

  getRaBill: (raBillId: string) =>
    apiClient.get<RaBill>(`/v1/ra-bills/${raBillId}`),

  updateRaBill: (raBillId: string, request: CreateRaBillRequest) =>
    apiClient.put<RaBill>(`/v1/ra-bills/${raBillId}`, request),

  // RA Bill Items
  addRaBillItem: (raBillId: string, request: CreateRaBillItemRequest) =>
    apiClient.post<RaBillItem>(`/v1/ra-bills/${raBillId}/items`, request),

  getRaBillItems: (raBillId: string) =>
    apiClient.get<RaBillItem[]>(`/v1/ra-bills/${raBillId}/items`),
}
