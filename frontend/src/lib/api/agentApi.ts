import { apiClient } from "./client";
import type { SseEvent } from "./aiApi";
import type {
  ApiResponse,
  AgentSummaryDto,
  AgentRunDto,
  AgentFindingDto,
  AgentRunDetailDto,
  AgentSeverity,
  PageDto,
} from "../types";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export interface ListRunsParams {
  agentKey?: string;
  status?: string;
  page?: number;
  size?: number;
}

export interface ListFindingsParams {
  severity?: AgentSeverity;
  agentKey?: string;
  status?: string;
  page?: number;
  size?: number;
}

/**
 * Parse a `text/event-stream` body into an async generator of {@link SseEvent}.
 * Cloned from {@link aiApi.streamChat} — EventSource cannot send a Bearer token,
 * so agent + investigate streams go through fetch + TextDecoderStream instead.
 */
async function* parseSse(
  res: Response,
  signal?: AbortSignal,
): AsyncGenerator<SseEvent> {
  if (!res.ok || !res.body) throw new Error(`stream ${res.status}`);
  const reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
  let buf = "";
  const parseField = (line: string, prefix: string): string | null => {
    if (!line.startsWith(prefix)) return null;
    const rest = line.slice(prefix.length);
    return rest.startsWith(" ") ? rest.slice(1) : rest;
  };
  try {
    while (true) {
      if (signal?.aborted) break;
      const { value, done } = await reader.read();
      if (done) break;
      buf += value;
      const frames = buf.split(/\r?\n\r?\n/);
      buf = frames.pop() ?? "";
      for (const f of frames) {
        const lines = f.split(/\r?\n/);
        let event = "message";
        const dataLines: string[] = [];
        for (const line of lines) {
          if (!line || line.startsWith(":")) continue;
          const ev = parseField(line, "event:");
          if (ev !== null) {
            event = ev.trim();
            continue;
          }
          const d = parseField(line, "data:");
          if (d !== null) dataLines.push(d);
        }
        const data = dataLines.join("\n");
        if (data) {
          try {
            yield { event, data: JSON.parse(data) };
          } catch {
            yield { event, data: { raw: data } };
          }
        }
      }
    }
  } finally {
    try {
      await reader.cancel();
    } catch {
      /* already closed */
    }
  }
}

function authHeaders(extra?: Record<string, string>): Record<string, string> {
  const token = typeof window !== "undefined" ? localStorage.getItem("access_token") : "";
  return { Authorization: `Bearer ${token}`, ...extra };
}

/** One delivery-audit row of the Notification Log. `sentAt` is set only when status is SENT. */
export interface NotificationLogEntry {
  at: string;
  findingId: string;
  findingTitle: string;
  severity: string;
  agentKey: string;
  projectId: string | null;
  recipientUserId: string | null;
  recipientName: string | null;
  channel: string;
  status: "SENT" | "PREVIEW" | "FAILED" | "SKIPPED" | "PENDING";
  detail: string | null;
  sentAt: string | null;
}

/** One per-recipient delivery from the agent mail log (ai.agent_mail_log). */
export interface AgentMailRow {
  id: string;
  category:
    | "DPR_REPORT"
    | "SUPERVISOR_SUMMARY"
    | "MISSING_DPR"
    | "DPR_REJECTION"
    | "ISSUE_ASSIGNMENT"
    | "OUTSTANDING_ISSUES"
    | "MATERIAL_SHORT_SUPPLY";
  channel: "EMAIL" | "IN_APP" | "WHATSAPP";
  recipientUserId?: string | null;
  recipientName?: string | null;
  recipientEmail?: string | null;
  subject?: string | null;
  /** Full mail HTML for the small mails; null for DPR_REPORT rows (use reportId). */
  bodyHtml?: string | null;
  reportId?: string | null;
  status: "SENT" | "FAILED" | "PREVIEW" | "SKIPPED";
  detail?: string | null;
  sentAt: string;
}

export interface AgentDeliverablesResponse {
  reportSchedule: {
    enabled: boolean;
    sendTime: string;
    timezone: string;
    cadence: string;
    lastGeneratedAt?: string | null;
    lastStatus?: string | null;
    lastDeliveryStatus?: string | null;
    lastDeliveredTo?: string | null;
    lastReportId?: string | null;
  };
  missingAlert: {
    enabled: boolean;
    alertTime: string;
    lastCheckedDate?: string | null;
    lastMissingCount?: number | null;
    lastEmailsSent?: number | null;
    lastSkippedNonWorking?: boolean | null;
    lastGeneratedAt?: string | null;
  };
  alertChannel: string;
  mails: AgentMailRow[];
}

export const agentApi = {
  listAgents: (projectId: string) =>
    apiClient
      .get<ApiResponse<AgentSummaryDto[]>>(`/v1/projects/${projectId}/agents`)
      .then((r) => r.data),

  /** Fire a single agent. Backend returns 202 + {runId}. */
  runAgent: (projectId: string, agentKey: string) =>
    apiClient
      .post<ApiResponse<{ runId: string }>>(
        `/v1/projects/${projectId}/agents/${agentKey}/run`,
      )
      .then((r) => r.data),

  /** Fire a whole pipeline (e.g. DAILY_PROJECT_SWEEP). Backend returns 202 + {pipelineRunId}. */
  runPipeline: (projectId: string, pipelineKey: string) =>
    apiClient
      .post<ApiResponse<{ pipelineRunId: string }>>(
        `/v1/projects/${projectId}/agents/pipelines/${pipelineKey}/run`,
      )
      .then((r) => r.data),

  /**
   * Notification Log — who was sent what, when, over which channel, with the honest status
   * (SENT / PREVIEW / FAILED / SKIPPED). Project route: PM of the project or admin (others 403 —
   * callers hide the section). Admin route: every project.
   */
  notificationLog: (projectId: string, limit = 100) =>
    apiClient
      .get<ApiResponse<NotificationLogEntry[]>>(
        `/v1/projects/${projectId}/notifications/log`,
        { params: { limit } },
      )
      .then((r) => r.data),

  adminNotificationLog: (projectId?: string, limit = 200) =>
    apiClient
      .get<ApiResponse<NotificationLogEntry[]>>(`/v1/admin/notifications/log`, {
        params: { limit, ...(projectId ? { projectId } : {}) },
      })
      .then((r) => r.data),

  /** Agent deliverables — schedule status + per-recipient delivery log for the AI tab panel. */
  getDeliverables: (projectId: string) =>
    apiClient
      .get<ApiResponse<AgentDeliverablesResponse>>(
        `/v1/projects/${projectId}/agent-deliverables`,
      )
      .then((r) => r.data),

  /** Recent agent runs across every project the caller can see — the portfolio activity feed. */
  portfolioActivity: (limit = 30) =>
    apiClient
      .get<ApiResponse<AgentRunDto[]>>(`/v1/portfolio/agent-activity`, { params: { limit } })
      .then((r) => r.data),

  listRuns: (projectId: string, params: ListRunsParams = {}) =>
    apiClient
      .get<ApiResponse<PageDto<AgentRunDto>>>(
        `/v1/projects/${projectId}/agent-runs`,
        { params },
      )
      .then((r) => r.data),

  getRun: (runId: string) =>
    apiClient
      .get<ApiResponse<AgentRunDetailDto>>(`/v1/agent-runs/${runId}`)
      .then((r) => r.data),

  listFindings: (projectId: string, params: ListFindingsParams = {}) =>
    apiClient
      .get<ApiResponse<PageDto<AgentFindingDto>>>(
        `/v1/projects/${projectId}/agent-findings`,
        { params },
      )
      .then((r) => r.data),

  acknowledgeFinding: (id: string) =>
    apiClient
      .post<ApiResponse<AgentFindingDto>>(`/v1/agent-findings/${id}/acknowledge`)
      .then((r) => r.data),

  resolveFinding: (id: string) =>
    apiClient
      .post<ApiResponse<AgentFindingDto>>(`/v1/agent-findings/${id}/resolve`)
      .then((r) => r.data),

  /**
   * Live agent activity stream. The endpoint may not exist yet on the backend —
   * callers must treat a thrown fetch / non-2xx as "not connected" and fall back
   * to polling {@link agentApi.listRuns} (see `useAgentStream`).
   */
  streamAgents: (projectId: string, signal: AbortSignal): AsyncGenerator<SseEvent> => {
    async function* run() {
      const res = await fetch(
        `${API_BASE}/v1/projects/${projectId}/agents/stream`,
        {
          method: "GET",
          signal,
          headers: authHeaders({ Accept: "text/event-stream" }),
        },
      );
      yield* parseSse(res, signal);
    }
    return run();
  },

  /**
   * Ask the supervisor agent a free-form question and stream the answer.
   * Endpoint may not exist yet — the client is built regardless so it lights up
   * automatically once the backend lands it.
   */
  investigate: (
    projectId: string,
    question: string,
    signal: AbortSignal,
  ): AsyncGenerator<SseEvent> => {
    async function* run() {
      const res = await fetch(
        `${API_BASE}/v1/projects/${projectId}/agents/investigate`,
        {
          method: "POST",
          signal,
          headers: authHeaders({
            "Content-Type": "application/json",
            Accept: "text/event-stream",
          }),
          body: JSON.stringify({ question }),
        },
      );
      yield* parseSse(res, signal);
    }
    return run();
  },
};
