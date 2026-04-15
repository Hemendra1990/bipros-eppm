import { apiClient } from '@/lib/api/client'
import type { ApiResponse } from '../types'

export type SatelliteGate = 'PASS' | 'HOLD_VARIANCE' | 'RED_VARIANCE' | 'HOLD_SATELLITE_DISPUTE'

export type RaBillStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'PMC_REVIEW_PENDING'
  | 'HOLD_SATELLITE_DISPUTE'
  | 'CERTIFIED'
  | 'APPROVED'
  | 'PAID'
  | 'PAID_PMC_OVERRIDE'
  | 'REJECTED'

export interface RaBill {
  id: string
  projectId: string
  contractId?: string
  wbsPackageCode?: string | null
  billNumber: string
  billPeriodFrom: string
  billPeriodTo: string
  grossAmount: number
  deductions: number
  mobAdvanceRecovery?: number | null
  retention5Pct?: number | null
  tds2Pct?: number | null
  gst18Pct?: number | null
  netAmount: number
  cumulativeAmount: number
  aiSatellitePercent?: number | null
  contractorClaimedPercent?: number | null
  satelliteGate?: SatelliteGate | null
  satelliteGateVariance?: number | null
  pfmsDpaRef?: string | null
  paymentDate?: string | null
  status: RaBillStatus
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
  wbsPackageCode?: string
  billNumber: string
  billPeriodFrom: string
  billPeriodTo: string
  grossAmount: number
  deductions?: number
  mobAdvanceRecovery?: number
  retention5Pct?: number
  tds2Pct?: number
  gst18Pct?: number
  netAmount: number
  contractorClaimedPercent?: number
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
