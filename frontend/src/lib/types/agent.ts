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

export type SeriesKind = "COLUMN" | "LINE";

export interface EvidenceSeriesPoint {
  label: string;
  value: number;
}

/** A compact chartable series carried by a CHART evidence ref (rainfall/day, SPI over periods, …). */
export interface EvidenceSeries {
  kind: SeriesKind | string;
  unit?: string | null;
  points: EvidenceSeriesPoint[];
  /** Reference-line value (threshold/target), e.g. 20 (mm) or 1.0 (SPI target). */
  refValue?: number | null;
  refLabel?: string | null;
}

export interface EvidenceDto {
  type: EvidenceType | string;
  label: string;
  value?: string | null;
  entityType?: string | null;
  entityId?: string | null;
  /** Deep-link into the app for the referenced entity, if resolvable. */
  linkUrl?: string | null;
  /** Present only on CHART refs — a small series to plot on the card. */
  series?: EvidenceSeries | null;
  /** Raw number for the frontend to format (money etc.); null for text values. */
  numericValue?: number | null;
  /** Formatting hint: "MONEY" | "%" | "d" | null. MONEY is rendered via useProjectCurrency().moneyCompact. */
  unit?: string | null;
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
  acknowledgedByName?: string | null;
  acknowledgedAt?: string | null;
  resolvedBy?: string | null;
  resolvedByName?: string | null;
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
  /** Raw gather() snapshot from the last run — drives the always-on coverage card. */
  snapshot?: Record<string, unknown> | null;
}

export interface AgentSummaryDto {
  key: string;
  displayName: string;
  supportsPortfolio: boolean;
  lastRun: AgentRunDto | null;
  /** Currently-ACTIVE findings for this agent (persistent basis the KPI cards sum). Correct across
   *  a SKIPPED_NO_CHANGE run, unlike lastRun.findingsCount which is 0 on a skip. */
  activeFindingsCount?: number;
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
