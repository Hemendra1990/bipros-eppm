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
  actualStartDate: string | null;
  actualFinishDate: string | null;
  duration: number;
  percentComplete: number;
  slack: number;
  totalFloat: number;
  remainingDuration: number;
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

// === Baseline ===

export interface BaselineResponse {
  id: string;
  code: string;
  name: string;
  projectId: string;
  description: string;
  snapshotDate: string;
  createdAt: string;
  updatedAt: string;
}
