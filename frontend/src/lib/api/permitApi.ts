import { apiClient } from "./client";
import type { ApiResponse } from "../types";

// ── Types ──────────────────────────────────────────────────────────────────

export type PermitStatus =
  | "DRAFT"
  | "PENDING_SITE_ENGINEER"
  | "PENDING_HSE"
  | "AWAITING_GAS_TEST"
  | "PENDING_PM"
  | "APPROVED"
  | "ISSUED"
  | "IN_PROGRESS"
  | "SUSPENDED"
  | "CLOSED"
  | "REJECTED"
  | "EXPIRED"
  | "REVOKED";

export type RiskLevel = "LOW" | "MEDIUM" | "HIGH";

export type WorkShift = "DAY" | "NIGHT" | "SPLIT";

export type WorkerRole = "PRINCIPAL" | "HELPER" | "FIRE_WATCH" | "STANDBY";

export type ApprovalStatus = "PENDING" | "APPROVED" | "REJECTED" | "SKIPPED";

export type GasTestResult = "PASS" | "FAIL";

export type IsolationType =
  | "ELECTRICAL"
  | "MECHANICAL"
  | "HYDRAULIC"
  | "PNEUMATIC"
  | "CHEMICAL"
  | "THERMAL";

export type NightWorkPolicy = "ALLOWED" | "LIMITED" | "RESTRICTED";

export type LifecycleEventType =
  | "SUBMITTED"
  | "APPROVED"
  | "REJECTED"
  | "ESCALATED"
  | "SMS_DISPATCHED"
  | "QR_GENERATED"
  | "GAS_TEST_RECORDED"
  | "ISOLATION_APPLIED"
  | "ISOLATION_REMOVED"
  | "AMENDED"
  | "SUSPENDED"
  | "RESUMED"
  | "REVOKED"
  | "EXPIRED"
  | "CLOSED";

export interface PermitWorker {
  id?: string;
  fullName: string;
  civilId?: string;
  nationality?: string;
  trade?: string;
  roleOnPermit: WorkerRole;
  trainingCertsJson?: string;
}

export interface PpeCheck {
  id: string;
  ppeItemTemplateId: string;
  ppeItemCode?: string | null;
  ppeItemName?: string | null;
  confirmed: boolean;
  confirmedBy?: string | null;
  confirmedAt?: string | null;
}

export interface ApprovalDto {
  id: string;
  stepNo: number;
  label: string;
  role: string;
  status: ApprovalStatus;
  reviewerId?: string | null;
  reviewedAt?: string | null;
  remarks?: string | null;
}

export interface GasTestDto {
  id: string;
  lelPct?: number | null;
  o2Pct?: number | null;
  h2sPpm?: number | null;
  coPpm?: number | null;
  result: GasTestResult;
  testedBy?: string | null;
  testedAt: string;
  instrumentSerial?: string | null;
}

export interface IsolationPointDto {
  id: string;
  isolationType: IsolationType;
  pointLabel: string;
  lockNumber?: string | null;
  appliedAt?: string | null;
  appliedBy?: string | null;
  removedAt?: string | null;
  removedBy?: string | null;
}

export interface LifecycleEventDto {
  id: string;
  eventType: LifecycleEventType;
  payloadJson?: string | null;
  occurredAt: string;
  actorUserId?: string | null;
}

export interface PermitSummary {
  id: string;
  permitCode: string;
  projectId: string;
  permitTypeTemplateId: string;
  permitTypeCode?: string | null;
  permitTypeName?: string | null;
  permitTypeColorHex?: string | null;
  permitTypeIconKey?: string | null;
  status: PermitStatus;
  riskLevel: RiskLevel;
  shift: WorkShift;
  workDescription?: string | null;
  principalWorkerName?: string | null;
  principalWorkerNationality?: string | null;
  startAt: string;
  endAt: string;
}

export interface PermitDetail {
  id: string;
  permitCode: string;
  projectId: string;
  parentPermitId?: string | null;
  permitTypeTemplateId: string;
  permitTypeCode?: string | null;
  permitTypeName?: string | null;
  permitTypeColorHex?: string | null;
  permitTypeIconKey?: string | null;
  status: PermitStatus;
  riskLevel: RiskLevel;
  contractorOrgId?: string | null;
  supervisorName?: string | null;
  locationZone?: string | null;
  chainageMarker?: string | null;
  startAt: string;
  endAt: string;
  validFrom?: string | null;
  validTo?: string | null;
  shift: WorkShift;
  taskDescription?: string | null;
  declarationAcceptedAt?: string | null;
  declarationAcceptedBy?: string | null;
  qrAvailable: boolean;
  smsDispatchedAt?: string | null;
  currentApprovalStep: number;
  approvalsCompleted: number;
  totalApprovalsRequired: number;
  closedAt?: string | null;
  closedBy?: string | null;
  closeRemarks?: string | null;
  revokedAt?: string | null;
  revokedBy?: string | null;
  revokeReason?: string | null;
  expiredAt?: string | null;
  suspendedAt?: string | null;
  suspendReason?: string | null;
  customFieldsJson?: string | null;
  createdAt: string;
  updatedAt: string;
  workers: PermitWorker[];
  approvals: ApprovalDto[];
  ppeChecks: PpeCheck[];
  gasTests: GasTestDto[];
  isolationPoints: IsolationPointDto[];
  lifecycleEvents: LifecycleEventDto[];
}

export interface PermitPack {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  active: boolean;
  sortOrder: number;
}

export interface PermitTypeTemplate {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  defaultRiskLevel: RiskLevel;
  jsaRequired: boolean;
  gasTestRequired: boolean;
  isolationRequired: boolean;
  blastingRequired: boolean;
  divingRequired: boolean;
  nightWorkPolicy: NightWorkPolicy;
  maxDurationHours: number;
  minApprovalRole?: string | null;
  colorHex?: string | null;
  iconKey?: string | null;
  sortOrder: number;
}

export interface PpeItemTemplate {
  id: string;
  code: string;
  name: string;
  iconKey?: string | null;
  mandatory: boolean;
  sortOrder: number;
}

export interface ApprovalStepTemplate {
  id: string;
  permitTypeTemplateId: string;
  stepNo: number;
  label: string;
  role: string;
  requiredForRiskLevels?: string | null;
  optional: boolean;
}

export interface DashboardSummary {
  activePermits: number;
  pendingReview: number;
  expiringToday: number;
  closedThisMonth: number;
  statusBreakdown: Partial<Record<PermitStatus, number>>;
  activeByType: Array<{ code: string; name: string; colorHex?: string | null; count: number }>;
  recentActivity: PermitSummary[];
}

export interface CreatePermitRequest {
  permitTypeTemplateId: string;
  riskLevel: RiskLevel;
  contractorOrgId?: string | null;
  supervisorName?: string | null;
  locationZone?: string | null;
  chainageMarker?: string | null;
  startAt: string;
  endAt: string;
  shift: WorkShift;
  taskDescription?: string | null;
  workers: PermitWorker[];
  confirmedPpeItemIds?: string[];
  declarationAccepted: boolean;
  customFieldsJson?: string | null;
}

export type UpdatePermitRequest = Partial<CreatePermitRequest>;

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ── Client ────────────────────────────────────────────────────────────────

const unwrap = <T,>(r: { data: ApiResponse<T> }): T => {
  if (r.data.error) throw new Error(r.data.error.message);
  return r.data.data as T;
};

export const permitApi = {
  // Lookups
  listPacks: () =>
    apiClient.get<ApiResponse<PermitPack[]>>("/v1/permit-packs").then(unwrap),
  listTypesForPack: (packCode: string) =>
    apiClient
      .get<ApiResponse<PermitTypeTemplate[]>>(`/v1/permit-packs/${packCode}/types`)
      .then(unwrap),
  getType: (id: string) =>
    apiClient.get<ApiResponse<PermitTypeTemplate>>(`/v1/permit-types/${id}`).then(unwrap),
  ppeItemsForType: (id: string) =>
    apiClient
      .get<ApiResponse<PpeItemTemplate[]>>(`/v1/permit-types/${id}/ppe-items`)
      .then(unwrap),
  approvalStepsForType: (id: string) =>
    apiClient
      .get<ApiResponse<ApprovalStepTemplate[]>>(`/v1/permit-types/${id}/approval-steps`)
      .then(unwrap),

  // Listing & detail
  list: (params: {
    projectId?: string;
    status?: PermitStatus;
    typeId?: string;
    riskLevel?: RiskLevel;
    page?: number;
    size?: number;
  }) =>
    apiClient
      .get<ApiResponse<PageResponse<PermitSummary>>>("/v1/permits", { params })
      .then(unwrap),

  get: (id: string) =>
    apiClient.get<ApiResponse<PermitDetail>>(`/v1/permits/${id}`).then(unwrap),

  qrPngUrl: (id: string, size = 240) =>
    `${apiClient.defaults.baseURL}/v1/permits/${id}/qr.png?size=${size}`,

  dashboardSummary: (projectId?: string) =>
    apiClient
      .get<ApiResponse<DashboardSummary>>("/v1/permits/dashboard-summary", {
        params: projectId ? { projectId } : {},
      })
      .then(unwrap),

  // Project-scoped mutations
  create: (projectId: string, body: CreatePermitRequest) =>
    apiClient
      .post<ApiResponse<PermitDetail>>(`/v1/projects/${projectId}/permits`, body)
      .then(unwrap),

  update: (projectId: string, permitId: string, body: UpdatePermitRequest) =>
    apiClient
      .put<ApiResponse<PermitDetail>>(`/v1/projects/${projectId}/permits/${permitId}`, body)
      .then(unwrap),

  submit: (projectId: string, permitId: string) =>
    apiClient
      .post<ApiResponse<PermitDetail>>(`/v1/projects/${projectId}/permits/${permitId}/submit`)
      .then(unwrap),

  delete: (projectId: string, permitId: string) =>
    apiClient.delete(`/v1/projects/${projectId}/permits/${permitId}`).then(() => undefined),

  // Approvals
  approve: (permitId: string, stepNo: number, remarks?: string) =>
    apiClient
      .post<ApiResponse<PermitDetail>>(
        `/v1/permits/${permitId}/approvals/${stepNo}/approve`,
        { remarks }
      )
      .then(unwrap),

  reject: (permitId: string, stepNo: number, reason: string) =>
    apiClient
      .post<ApiResponse<PermitDetail>>(
        `/v1/permits/${permitId}/approvals/${stepNo}/reject`,
        { reason }
      )
      .then(unwrap),

  recordGasTest: (
    permitId: string,
    body: {
      lelPct?: number | null;
      o2Pct?: number | null;
      h2sPpm?: number | null;
      coPpm?: number | null;
      result: GasTestResult;
      instrumentSerial?: string | null;
    }
  ) =>
    apiClient
      .post<ApiResponse<PermitDetail>>(`/v1/permits/${permitId}/gas-tests`, body)
      .then(unwrap),

  applyIsolation: (
    permitId: string,
    body: { isolationType: IsolationType; pointLabel: string; lockNumber?: string | null }
  ) =>
    apiClient
      .post<ApiResponse<IsolationPointDto>>(`/v1/permits/${permitId}/isolation-points`, body)
      .then(unwrap),

  removeIsolation: (permitId: string, pointId: string) =>
    apiClient
      .put<ApiResponse<IsolationPointDto>>(
        `/v1/permits/${permitId}/isolation-points/${pointId}/remove`
      )
      .then(unwrap),

  issue: (permitId: string) =>
    apiClient.post<ApiResponse<PermitDetail>>(`/v1/permits/${permitId}/issue`).then(unwrap),

  start: (permitId: string) =>
    apiClient.post<ApiResponse<PermitDetail>>(`/v1/permits/${permitId}/start`).then(unwrap),

  close: (permitId: string, remarks?: string) =>
    apiClient
      .post<ApiResponse<PermitDetail>>(`/v1/permits/${permitId}/close`, { remarks })
      .then(unwrap),

  revoke: (permitId: string, reason: string) =>
    apiClient
      .post<ApiResponse<PermitDetail>>(`/v1/permits/${permitId}/revoke`, { reason })
      .then(unwrap),

  suspend: (permitId: string, reason: string) =>
    apiClient
      .post<ApiResponse<PermitDetail>>(`/v1/permits/${permitId}/suspend`, { reason })
      .then(unwrap),

  resume: (permitId: string) =>
    apiClient.post<ApiResponse<PermitDetail>>(`/v1/permits/${permitId}/resume`).then(unwrap),
};
