import { apiClient } from '@/lib/api/client'
import type { ApiResponse } from '../types'

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
    apiClient.post<ApiResponse<RaBill>>(`/v1/projects/${projectId}/ra-bills`, request).then(r => r.data),

  getRaBillsByProject: (projectId: string) =>
    apiClient.get<ApiResponse<RaBill[]>>(`/v1/projects/${projectId}/ra-bills`).then(r => r.data),

  getRaBill: (raBillId: string) =>
    apiClient.get<ApiResponse<RaBill>>(`/v1/ra-bills/${raBillId}`).then(r => r.data),

  updateRaBill: (raBillId: string, request: CreateRaBillRequest) =>
    apiClient.put<ApiResponse<RaBill>>(`/v1/ra-bills/${raBillId}`, request).then(r => r.data),

  // RA Bill Items
  addRaBillItem: (raBillId: string, request: CreateRaBillItemRequest) =>
    apiClient.post<ApiResponse<RaBillItem>>(`/v1/ra-bills/${raBillId}/items`, request).then(r => r.data),

  getRaBillItems: (raBillId: string) =>
    apiClient.get<ApiResponse<RaBillItem[]>>(`/v1/ra-bills/${raBillId}/items`).then(r => r.data),
}
