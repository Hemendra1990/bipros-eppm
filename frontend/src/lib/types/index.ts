// === API Response Envelope ===

export interface ApiResponse<T> {
  data: T | null;
  error: ApiError | null;
  meta: {
    timestamp: string;
    version: string;
  };
}

export interface ApiError {
  code: string;
  message: string;
  details?: FieldError[];
}

export interface FieldError {
  field: string;
  reason: string;
}

export interface PagedResponse<T> {
  content: T[];
  pagination: {
    totalElements: number;
    totalPages: number;
    currentPage: number;
    pageSize: number;
  };
}

// === Auth ===

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface UserResponse {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  enabled: boolean;
  roles: string[];
  // IC-PMS fields (nullable for legacy users)
  organisationId?: string | null;
  designation?: string | null;
  primaryIcpmsRole?: string | null;
  authMethods?: AuthMethod[] | null;
}

// === Project Structure ===

export type ProjectStatus = "PLANNED" | "ACTIVE" | "INACTIVE" | "COMPLETED";

export interface EpsNodeResponse {
  id: string;
  code: string;
  name: string;
  parentId: string | null;
  obsId: string | null;
  sortOrder: number;
  children: EpsNodeResponse[];
}

export interface ObsNodeResponse {
  id: string;
  code: string;
  name: string;
  parentId: string | null;
  description: string;
  sortOrder: number;
  children: ObsNodeResponse[];
}

export interface ProjectResponse {
  id: string;
  code: string;
  name: string;
  description: string;
  epsNodeId: string;
  obsNodeId: string | null;
  plannedStartDate: string;
  plannedFinishDate: string;
  dataDate: string | null;
  status: ProjectStatus;
  mustFinishByDate: string | null;
  priority: number;
  createdAt: string;
  updatedAt: string;
}

export type WbsType = "PROGRAMME" | "NODE" | "PACKAGE" | "WORK_PACKAGE";
export type WbsPhase = "PROGRAMME" | "CONSTRUCTION" | "MOBILISATION" | "TENDER" | "PLANNING";
export type WbsStatus = "ACTIVE" | "IN_PROGRESS" | "NOT_STARTED" | "COMPLETED" | "DELAYED" | "AT_RISK";

export interface WbsNodeResponse {
  id: string;
  code: string;
  name: string;
  parentId: string | null;
  projectId: string;
  obsNodeId: string | null;
  sortOrder: number;
  summaryDuration: number | null;
  summaryPercentComplete: number | null;
  wbsLevel: number | null;
  wbsType: WbsType | null;
  phase: WbsPhase | null;
  wbsStatus: WbsStatus | null;
  responsibleOrganisationId: string | null;
  plannedStart: string | null;
  plannedFinish: string | null;
  budgetCrores: number | null;
  gisPolygonId: string | null;
  children: WbsNodeResponse[];
}

export interface CreateProjectRequest {
  code: string;
  name: string;
  description?: string;
  epsNodeId: string;
  obsNodeId?: string;
  plannedStartDate: string;
  plannedFinishDate?: string;
  priority?: number;
}

export interface UpdateProjectRequest {
  name?: string;
  description?: string;
  obsNodeId?: string;
  plannedStartDate?: string;
  plannedFinishDate?: string;
  mustFinishByDate?: string;
  status?: string;
  priority?: number;
}

export interface CreateEpsNodeRequest {
  code: string;
  name: string;
  parentId?: string;
  obsId?: string;
}

// === Activities ===

export interface ActivityResponse {
  id: string;
  code: string;
  name: string;
  projectId: string;
  wbsNodeId: string;
  status: string;
  plannedStartDate: string | null;
  plannedFinishDate: string | null;
  earlyStartDate?: string | null;
  earlyFinishDate?: string | null;
  lateStartDate?: string | null;
  lateFinishDate?: string | null;
  actualStartDate: string | null;
  actualFinishDate: string | null;
  duration: number;
  percentComplete: number;
  slack: number;
  totalFloat: number;
  freeFloat?: number;
  remainingDuration: number;
  isCritical?: boolean;
  createdAt: string;
  updatedAt: string;
}

// === Resources ===

export interface ResourceResponse {
  id: string;
  code: string;
  name: string;
  type: "LABOR" | "NONLABOR" | "MATERIAL";
  status: string;
  maxUnits: number;
  calendarId: string | null;
  createdAt: string;
  updatedAt: string;
}

// === Risk ===

export interface RiskResponse {
  id: string;
  code: string;
  title: string;
  description: string;
  category: string;
  probability: number;
  impact: number;
  score: number;
  status: "OPEN" | "MITIGATED" | "CLOSED";
  owner: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRiskRequest {
  code: string;
  title: string;
  description: string;
  category: string;
  probability: number;
  impact: number;
}

// === Calendar ===

export interface CalendarResponse {
  id: string;
  code: string;
  name: string;
  type: "GLOBAL" | "PROJECT" | "RESOURCE";
  baseCalendarId: string | null;
  workHoursPerDay: number;
  workDaysPerWeek: number;
  createdAt: string;
  updatedAt: string;
}

// === Documents ===

export interface DocumentFolder {
  id: string;
  projectId: string;
  name: string;
  code: string;
  category: string;
  parentId: string | null;
  wbsNodeId: string | null;
  sortOrder: number;
}

export interface DocumentResponse {
  id: string;
  folderId: string;
  projectId: string;
  documentNumber: string;
  title: string;
  description: string;
  fileName: string;
  fileSize: number;
  mimeType: string;
  filePath: string;
  currentVersion: number;
  status: "DRAFT" | "UNDER_REVIEW" | "APPROVED" | "SUPERSEDED" | "ARCHIVED";
  tags: string;
  createdAt: string;
  updatedAt: string;
}

export interface DrawingRegisterResponse {
  id: string;
  projectId: string;
  documentId: string | null;
  drawingNumber: string;
  title: string;
  discipline: string;
  revision: string;
  revisionDate: string;
  status: "PRELIMINARY" | "IFA" | "IFC" | "AS_BUILT" | "SUPERSEDED";
  packageCode: string;
  scale: string;
  createdAt: string;
  updatedAt: string;
}

export interface TransmittalResponse {
  id: string;
  projectId: string;
  transmittalNumber: string;
  subject: string;
  fromParty: string;
  toParty: string;
  sentDate: string;
  dueDate: string;
  status: "DRAFT" | "SENT" | "RECEIVED" | "ACKNOWLEDGED" | "OVERDUE";
  remarks: string;
  createdAt: string;
  updatedAt: string;
}

export interface RfiRegisterResponse {
  id: string;
  projectId: string;
  rfiNumber: string;
  subject: string;
  description: string;
  raisedBy: string;
  assignedTo: string;
  raisedDate: string;
  dueDate: string;
  closedDate: string | null;
  status: "OPEN" | "RESPONDED" | "CLOSED" | "OVERDUE";
  priority: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  response: string;
  createdAt: string;
  updatedAt: string;
}

// === Resources ===

export interface ResourceAssignmentResponse {
  id: string;
  activityId: string;
  resourceId: string;
  projectId: string;
  plannedUnits: number;
  actualUnits: number;
  remainingUnits: number;
  rateType: string;
  plannedCost: number;
  actualCost: number;
  remainingCost: number;
  plannedStartDate: string | null;
  plannedFinishDate: string | null;
}

export interface CreateResourceAssignmentRequest {
  activityId: string;
  resourceId: string;
  plannedUnits: number;
  rateType: string;
}

// === Costs ===

export interface ExpenseResponse {
  id: string;
  projectId: string;
  activityId: string | null;
  description: string;
  amount: number;
  currency: string;
  expenseDate: string;
  category: string;
  createdAt: string;
  updatedAt: string;
}

export interface CostSummaryResponse {
  projectId: string;
  totalBudget: number;
  totalActual: number;
  totalRemaining: number;
  atCompletion: number;
  currency: string;
}

// === EVM ===

export interface EvmCalculationResponse {
  projectId: string;
  periodDate: string;
  plannedValue: number;
  earnedValue: number;
  actualCost: number;
  scheduleVariance: number;
  costVariance: number;
  spi: number;
  cpi: number;
  eac: number;
  etc: number;
}

export interface EvmMetricsResponse {
  projectId: string;
  periodDate: string;
  pv: number;
  ev: number;
  ac: number;
  sv: number;
  cv: number;
  spi: number;
  cpi: number;
  eac: number;
  etc: number;
  vac: number;
  tcpi: number;
}

export interface EvmHistoryEntryResponse {
  periodDate: string;
  pv: number;
  ev: number;
  ac: number;
}

// === Baseline ===

export interface BaselineResponse {
  id: string;
  projectId: string;
  name: string;
  description: string | null;
  baselineType: "PROJECT" | "PRIMARY" | "SECONDARY" | "TERTIARY";
  baselineDate: string;
  isActive: boolean;
  totalActivities: number;
  totalCost: number;
  projectDuration: number;
  projectStartDate: string | null;
  projectFinishDate: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface BaselineVarianceRow {
  activityId: string;
  activityName: string;
  startVarianceDays: number;
  finishVarianceDays: number;
  durationVariance: number;
  costVariance: number;
}

// === Portfolio ===

export interface PortfolioResponse {
  id: string;
  code: string;
  name: string;
  description: string;
  projectCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePortfolioRequest {
  code: string;
  name: string;
  description?: string;
}

export interface PortfolioProjectResponse {
  projectId: string;
  projectCode: string;
  projectName: string;
}

export interface PortfolioScoringCriterion {
  id: string;
  name: string;
  weight: number;
  description?: string;
}

export interface PortfolioScenarioResponse {
  id: string;
  name: string;
  description: string;
  createdAt: string;
}

// === OBS (Organizational Breakdown Structure) ===

export interface CreateObsNodeRequest {
  code: string;
  name: string;
  description?: string;
  parentId?: string;
}

// === Contracts & Procurement ===

export type ProcurementMethod = "OPEN_TENDER" | "LIMITED_TENDER" | "GEM_PORTAL" | "SINGLE_SOURCE" | "EOI" | "DESIGN_BUILD";
export type ProcurementPlanStatus = "DRAFT" | "APPROVED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
export type TenderStatus = "DRAFT" | "PUBLISHED" | "BID_OPEN" | "EVALUATION" | "AWARDED" | "CANCELLED";
export type BidSubmissionStatus = "SUBMITTED" | "TECHNICALLY_QUALIFIED" | "NOT_QUALIFIED" | "L1" | "AWARDED" | "REJECTED";
export type ContractStatus =
  | "DRAFT"
  | "MOBILISATION"
  | "ACTIVE"
  | "ACTIVE_AT_RISK"
  | "ACTIVE_DELAYED"
  | "DELAYED"
  | "SUSPENDED"
  | "COMPLETED"
  | "TERMINATED"
  | "DLP";
export type ContractType =
  | "EPC_LUMP_SUM_FIDIC_YELLOW"
  | "EPC_LUMP_SUM_FIDIC_RED"
  | "EPC_LUMP_SUM_FIDIC_SILVER"
  | "ITEM_RATE_FIDIC_RED"
  | "PERCENTAGE_BASED_PMC"
  | "LUMP_SUM_UNIT_RATE";
export type MilestoneStatus = "PENDING" | "ACHIEVED" | "DELAYED" | "WAIVED";
export type VariationOrderStatus = "INITIATED" | "RECOMMENDED" | "APPROVED" | "REJECTED";
export type BondType = "PERFORMANCE_GUARANTEE" | "EMD" | "ADVANCE_GUARANTEE" | "RETENTION";
export type BondStatus = "ACTIVE" | "EXPIRED" | "RELEASED" | "INVOKED";

export interface ProcurementPlanResponse {
  id: string;
  projectId: string;
  wbsNodeId: string | null;
  planCode: string;
  description: string;
  procurementMethod: ProcurementMethod;
  estimatedValue: number;
  currency: string;
  targetNitDate: string;
  targetAwardDate: string;
  status: ProcurementPlanStatus;
  approvalLevel: string | null;
  approvedBy: string | null;
  approvedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProcurementPlanRequest {
  projectId: string;
  wbsNodeId?: string;
  planCode: string;
  description: string;
  procurementMethod: ProcurementMethod;
  estimatedValue: number;
  currency: string;
  targetNitDate: string;
  targetAwardDate: string;
  approvalLevel?: string;
  approvedBy?: string;
}

export interface TenderResponse {
  id: string;
  procurementPlanId: string;
  projectId: string;
  tenderNumber: string;
  nitDate: string;
  scope: string;
  estimatedValue: number;
  emdAmount: number;
  completionPeriodDays: number;
  bidDueDate: string;
  bidOpenDate: string;
  status: TenderStatus;
  awardedContractId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTenderRequest {
  procurementPlanId: string;
  projectId: string;
  tenderNumber: string;
  nitDate: string;
  scope: string;
  estimatedValue: number;
  emdAmount: number;
  completionPeriodDays: number;
  bidDueDate: string;
  bidOpenDate: string;
}

export interface BidSubmissionResponse {
  id: string;
  tenderId: string;
  bidderName: string;
  bidderCode: string;
  technicalScore: number;
  financialBid: number;
  status: BidSubmissionStatus;
  evaluationRemarks: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateBidSubmissionRequest {
  tenderId: string;
  bidderName: string;
  bidderCode: string;
  technicalScore: number;
  financialBid: number;
  evaluationRemarks?: string;
}

export interface ContractResponse {
  id: string;
  projectId: string;
  tenderId: string | null;
  contractNumber: string;
  loaNumber: string | null;
  contractorName: string;
  contractorCode: string | null;
  contractValue: number;
  loaDate: string;
  startDate: string;
  completionDate: string;
  dlpMonths: number;
  ldRate: number;
  status: ContractStatus;
  contractType: ContractType;
  // IC-PMS denormalised KPI fields
  wbsPackageCode: string | null;
  packageDescription: string | null;
  actualCompletionDate: string | null;
  spi: number | null;
  cpi: number | null;
  physicalProgressAi: number | null;
  cumulativeRaBillsCrores: number | null;
  voNumbersIssued: number | null;
  voValueCrores: number | null;
  performanceScore: number | null;
  bgExpiry: string | null;
  kpiRefreshedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateContractRequest {
  projectId: string;
  tenderId?: string;
  contractNumber: string;
  loaNumber?: string;
  contractorName: string;
  contractorCode?: string;
  contractValue: number;
  loaDate: string;
  startDate: string;
  completionDate: string;
  dlpMonths?: number;
  ldRate: number;
  contractType: ContractType;
}

export interface ContractMilestoneResponse {
  id: string;
  contractId: string;
  milestoneCode: string;
  milestoneName: string;
  targetDate: string;
  actualDate: string | null;
  paymentPercentage: number;
  amount: number;
  status: MilestoneStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateContractMilestoneRequest {
  contractId: string;
  milestoneCode: string;
  milestoneName: string;
  targetDate: string;
  actualDate?: string;
  paymentPercentage: number;
  amount: number;
}

export interface VariationOrderResponse {
  id: string;
  contractId: string;
  voNumber: string;
  description: string;
  voValue: number;
  justification: string;
  status: VariationOrderStatus;
  impactOnBudget: number;
  impactOnScheduleDays: number;
  approvedBy: string | null;
  approvedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateVariationOrderRequest {
  contractId: string;
  voNumber: string;
  description: string;
  voValue: number;
  justification: string;
  impactOnBudget: number;
  impactOnScheduleDays: number;
  approvedBy?: string;
}

export interface PerformanceBondResponse {
  id: string;
  contractId: string;
  bondType: BondType;
  bondValue: number;
  bankName: string;
  issueDate: string;
  expiryDate: string;
  status: BondStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePerformanceBondRequest {
  contractId: string;
  bondType: BondType;
  bondValue: number;
  bankName: string;
  issueDate: string;
  expiryDate: string;
}

export interface ContractorScorecardResponse {
  id: string;
  contractId: string;
  period: string;
  qualityScore: number;
  safetyScore: number;
  progressScore: number;
  paymentComplianceScore: number;
  overallScore: number;
  remarks: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateContractorScorecardRequest {
  contractId: string;
  period: string;
  qualityScore: number;
  safetyScore: number;
  progressScore: number;
  paymentComplianceScore: number;
  overallScore: number;
  remarks?: string;
}

// === WBS Templates and Asset Classes ===

export type AssetClass =
  | "ROAD"
  | "RAIL"
  | "POWER"
  | "WATER"
  | "ICT"
  | "BUILDING"
  | "GREEN_INFRASTRUCTURE";

export interface WbsTemplateResponse {
  id: string;
  code: string;
  name: string;
  assetClass: AssetClass;
  description: string;
  defaultStructure: string; // JSON string
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateWbsTemplateRequest {
  code: string;
  name: string;
  assetClass: AssetClass;
  description?: string;
  defaultStructure: string;
  isActive?: boolean;
}

export interface CorridorCodeResponse {
  id: string;
  projectId: string;
  corridorPrefix: string;
  zoneCode: string;
  nodeCode: string;
  generatedCode: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCorridorCodeRequest {
  projectId: string;
  corridorPrefix: string;
  zoneCode: string;
  nodeCode: string;
}

// === IC-PMS Master Data ===

export type OrganisationType = "EMPLOYER" | "SPV" | "PMC" | "EPC_CONTRACTOR" | "GOVERNMENT_AUDITOR";

export interface OrganisationResponse {
  id: string;
  code: string;
  name: string;
  shortName: string | null;
  organisationType: OrganisationType;
  parentOrganisationId: string | null;
  active: boolean;
}

export type AuthMethod = "AADHAAR_OTP" | "NIC_SSO" | "DSC_CLASS_3" | "USERNAME_PASSWORD";

export type IcpmsModule =
  | "M1_WBS_GIS"
  | "M2_SCHEDULE_EVM"
  | "M3_SATELLITE_MONITORING"
  | "M4_COST_RA_BILLS"
  | "M5_CONTRACTS"
  | "M6_DOCUMENTS"
  | "M7_RISKS"
  | "M8_RESOURCES"
  | "M9_REPORTS";

export type ModuleAccessLevel = "NONE" | "VIEW" | "EDIT" | "CERTIFY" | "APPROVE" | "FULL";

export interface UserModuleAccessResponse {
  id: string;
  userId: string;
  module: IcpmsModule;
  accessLevel: ModuleAccessLevel;
}

export interface UserCorridorScopeResponse {
  id: string;
  userId: string;
  wbsNodeId: string | null; // NULL = All Corridors
}
