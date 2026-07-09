// === Multi-agent AI platform (Track D) ===
//
// Types mirror the frozen Phase-0 backend contract. Every list/detail response
// is still wrapped in `ApiResponse<T>` (see index.ts) — these are the inner
// payload shapes.

export type AgentSeverity = "INFO" | "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export type AgentFindingStatus =
  | "ACTIVE"
  | "SUPERSEDED"
  | "RESOLVED_BY_USER"
  | "EXPIRED";

export type AgentRunStatus =
  | "PENDING"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED"
  | "SKIPPED"
  | "CANCELLED";

export type EvidenceType = "METRIC" | "ENTITY" | "TOOL_RESULT" | "CHART";

export interface EvidenceDto {
  type: EvidenceType | string;
  label: string;
  value?: string | null;
  entityType?: string | null;
  entityId?: string | null;
  /** Deep-link into the app for the referenced entity, if resolvable. */
  linkUrl?: string | null;
}

export interface AgentFindingDto {
  id: string;
  agentKey: string;
  projectId: string;
  findingType: string;
  subjectRef?: string | null;
  severity: AgentSeverity;
  /** 0..1 model/heuristic confidence. */
  confidence: number;
  confidenceBasis?: string | null;
  title: string;
  whatHappened?: string | null;
  whyItHappened?: string | null;
  businessImpact?: string | null;
  recommendedAction?: string | null;
  evidence: EvidenceDto[];
  status: AgentFindingStatus | string;
  notifiable: boolean;
  validUntil?: string | null;
  lastSeenAt?: string | null;
  acknowledgedBy?: string | null;
  acknowledgedAt?: string | null;
  resolvedBy?: string | null;
  resolvedAt?: string | null;
  createdAt: string;
}

export interface AgentRunDto {
  id: string;
  agentKey: string;
  projectId: string;
  pipelineRunId?: string | null;
  status: AgentRunStatus | string;
  triggerType?: string | null;
  triggerRef?: string | null;
  tokensInput?: number | null;
  tokensOutput?: number | null;
  /** When the run short-circuited without an LLM call (e.g. "no new data"). */
  llmSkipReason?: string | null;
  findingsCount: number;
  startedAt?: string | null;
  finishedAt?: string | null;
  durationMs?: number | null;
  errorMessage?: string | null;
}

export interface AgentSummaryDto {
  key: string;
  displayName: string;
  supportsPortfolio: boolean;
  lastRun: AgentRunDto | null;
}

/** Spring-style page envelope used by the agent list endpoints. */
export interface PageDto<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AgentRunDetailDto {
  run: AgentRunDto;
  findings: AgentFindingDto[];
}
