import * as fs from 'fs';
import * as path from 'path';
import type { APIRequestContext, Page } from '@playwright/test';
import { expect } from '@playwright/test';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export interface SseEvent {
  event: string;
  data: Record<string, unknown>;
}

export interface ToolResult {
  name: string;
  success: boolean;
  summary: string;
}

export type Role = 'PROJECT_ENGINEER' | 'SITE_MANAGER' | 'PROJECT_MANAGER';

export interface Question {
  id: string;
  category:
    | 'SUPERVISOR_ROSTER'
    | 'SUPERVISOR_PERFORMANCE'
    | 'SUPERVISOR_COMPARISON'
    | 'DPR'
    | 'COST_EVM'
    | 'MANPOWER'
    | 'EQUIPMENT'
    | 'MATERIAL'
    | 'ACTIVITY'
    | 'NEGATIVE';
  question: string;
  allowedRoles: ReadonlyArray<Role>;
  /** Outer = AND, inner = OR. Each inner group: at least one term must appear in the lowercased final answer. */
  mustContainAny?: ReadonlyArray<ReadonlyArray<string>>;
  /** Lowercased substrings the final answer must NOT contain. Combined with the global leak set. */
  mustNotContain?: ReadonlyArray<string>;
  /** At least one tool_result event whose name is in this list. */
  expectsToolAny?: ReadonlyArray<string>;
  /** Match a numeric value in the final answer within tolerance. */
  expectedNumber?: { regex: RegExp; value: number; tolerancePct: number };
  /** True for negative probes that must refuse / clarify in business language. */
  expectRefusal?: boolean;
  /** Marks a question that is also exercised through the actual UI. */
  uiSmoke?: boolean;
}

/**
 * Forbidden tokens that betray "leaked plumbing" per the AiOrchestrator
 * system prompt's DO-NOT list. Any of these in the final answer text fails
 * the case. Lowercased compare.
 *
 * NOTE: the model sometimes correctly says "the table shows..." when it
 * means "the chart" — so we don't flag the bare word "table". We DO flag
 * SQL keywords, schema tokens, and identifier-looking strings.
 */
const FORBIDDEN_LEAK_TOKENS: readonly string[] = [
  'project_id',
  'dim_',
  'fact_',
  'mv_',
  'clickhouse',
  'select ',
  'where ',
  'group by',
  'join ',
  ' uuid',
  'qty_executed',
  'pct_complete',
  'event_ts',
  'reporting_manager_id',
];

const RAW_UUID_RE = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;

/**
 * POST a chat message to the streaming endpoint and parse the SSE body
 * into a flat list of events. Mirrors the helper in
 * 30-ai-role-awareness.spec.ts intentionally — a copy keeps spec 30
 * stable while spec 31 evolves.
 */
export async function streamChat(
  page: Page,
  body: { conversationId: string | null; projectId: string | null; module: string; message: string },
): Promise<SseEvent[]> {
  const token = await page.evaluate(() => localStorage.getItem('access_token'));
  if (!token) throw new Error('No access_token in localStorage; login failed before streamChat.');

  const res = await page.request.post(`${API_BASE}/v1/ai/chat/stream`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    data: body,
    timeout: 180_000,
  });

  if (!res.ok()) {
    throw new Error(`chat/stream HTTP ${res.status()}: ${await res.text()}`);
  }

  const raw = await res.text();
  const out: SseEvent[] = [];
  for (const frame of raw.split(/\r?\n\r?\n/)) {
    const lines = frame.split(/\r?\n/);
    let event = 'message';
    const dataLines: string[] = [];
    for (const line of lines) {
      if (!line || line.startsWith(':')) continue;
      if (line.startsWith('event:')) {
        event = line.slice('event:'.length).trim();
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice('data:'.length).replace(/^ /, ''));
      }
    }
    const data = dataLines.join('\n');
    if (!data) continue;
    try {
      out.push({ event, data: JSON.parse(data) });
    } catch {
      out.push({ event, data: { raw: data } });
    }
  }
  return out;
}

export function toolResults(events: SseEvent[]): ToolResult[] {
  return events
    .filter((e) => e.event === 'tool_result')
    .map((e) => ({
      name: typeof e.data.name === 'string' ? e.data.name : '',
      success: e.data.success === true,
      summary: typeof e.data.summary === 'string' ? e.data.summary : '',
    }));
}

export function chatTerminated(events: SseEvent[]): boolean {
  return events.some(
    (e) => e.event === 'done' || e.event === 'final_answer' || e.event === 'max_rounds_exceeded',
  );
}

/**
 * Reconstruct the final assistant text from streamed events. Prefers a
 * `final_answer` event payload if present (most reliable); falls back to
 * concatenated `token` events for providers that stream incrementally.
 */
export function finalAnswer(events: SseEvent[]): string {
  for (const e of events) {
    if (e.event === 'final_answer') {
      const t = (e.data.text ?? e.data.content ?? e.data.answer) as unknown;
      if (typeof t === 'string' && t.length > 0) return t;
    }
  }
  // Fallback: concat tokens
  const parts: string[] = [];
  for (const e of events) {
    if (e.event === 'token') {
      const t = (e.data.text ?? e.data.delta ?? e.data.content) as unknown;
      if (typeof t === 'string') parts.push(t);
    }
  }
  return parts.join('');
}

export async function defaultLlmProviderIsActive(
  api: APIRequestContext,
  adminToken: string,
): Promise<boolean> {
  const res = await api.get(`${API_BASE}/v1/admin/llm-providers`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  if (!res.ok()) return false;
  const body = (await res.json()) as { data: Array<{ isDefault: boolean; isActive: boolean }> };
  return body.data.some((p) => p.isDefault && p.isActive);
}

export interface AssertionFailure {
  reason: string;
  detail?: string;
}

export function evaluateAnswer(events: SseEvent[], q: Question): AssertionFailure[] {
  const failures: AssertionFailure[] = [];
  const text = finalAnswer(events);
  const lower = text.toLowerCase();

  if (!chatTerminated(events)) {
    failures.push({ reason: 'no terminating event (done / final_answer / max_rounds_exceeded)' });
  }
  if (text.trim().length === 0) {
    failures.push({ reason: 'empty final answer' });
  }

  // Global leak check
  for (const token of FORBIDDEN_LEAK_TOKENS) {
    if (lower.includes(token)) {
      failures.push({ reason: 'leaked plumbing token', detail: `"${token}"` });
    }
  }
  const uuidMatch = text.match(RAW_UUID_RE);
  if (uuidMatch) {
    failures.push({ reason: 'leaked UUID', detail: uuidMatch[0] });
  }

  // Per-question forbidden
  for (const token of q.mustNotContain ?? []) {
    if (lower.includes(token.toLowerCase())) {
      failures.push({ reason: 'forbidden phrase present', detail: `"${token}"` });
    }
  }

  // mustContainAny: AND-of-OR
  for (const group of q.mustContainAny ?? []) {
    const hit = group.some((t) => lower.includes(t.toLowerCase()));
    if (!hit) {
      failures.push({
        reason: 'missing required phrase',
        detail: `at least one of [${group.join(', ')}] must appear`,
      });
    }
  }

  // expectsToolAny
  if (q.expectsToolAny && q.expectsToolAny.length > 0) {
    const tools = toolResults(events);
    const successfulNames = tools.filter((t) => t.success).map((t) => t.name);
    const allNames = tools.map((t) => t.name);
    const matched = q.expectsToolAny.some((name) => successfulNames.includes(name));
    if (!matched) {
      failures.push({
        reason: 'expected tool was not invoked successfully',
        detail: `expected one of [${q.expectsToolAny.join(', ')}], saw [${allNames.join(', ') || '(none)'}]`,
      });
    }
  }

  // expectedNumber
  if (q.expectedNumber) {
    const m = text.match(q.expectedNumber.regex);
    if (!m) {
      failures.push({
        reason: 'expected numeric pattern not found',
        detail: q.expectedNumber.regex.toString(),
      });
    } else {
      const captured = parseFloat(m[1] ?? m[0]);
      const want = q.expectedNumber.value;
      const tol = (Math.abs(want) * q.expectedNumber.tolerancePct) / 100;
      if (Number.isNaN(captured) || Math.abs(captured - want) > tol) {
        failures.push({
          reason: 'numeric value out of tolerance',
          detail: `got ${captured}, expected ${want} ±${q.expectedNumber.tolerancePct}%`,
        });
      }
    }
  }

  // expectRefusal: must NOT show raw SQL / UUID etc., AND must say something like "I can't" / business framing
  if (q.expectRefusal) {
    const refusalCues = [
      "i can't",
      'i cannot',
      "i don't expose",
      "i don't share",
      'cost performance',
      'schedule',
      'daily progress',
      'not something i share',
      'business categories',
      'i can answer',
    ];
    const looksLikeRefusal = refusalCues.some((cue) => lower.includes(cue));
    if (!looksLikeRefusal) {
      failures.push({
        reason: 'expected business-language refusal but answer looked like compliance',
      });
    }
  }

  return failures;
}

const ARTIFACT_DIR = path.resolve('test-results/ai-global');

export function ensureArtifactDir(): void {
  if (!fs.existsSync(ARTIFACT_DIR)) {
    fs.mkdirSync(ARTIFACT_DIR, { recursive: true });
  }
}

export function writeFailureArtifact(
  q: Question,
  role: Role,
  events: SseEvent[],
  failures: AssertionFailure[],
): string {
  ensureArtifactDir();
  const file = path.join(ARTIFACT_DIR, `${q.id}__${role}.json`);
  const body = {
    id: q.id,
    role,
    category: q.category,
    question: q.question,
    failures,
    finalAnswer: finalAnswer(events),
    toolResults: toolResults(events),
    eventCount: events.length,
    events,
  };
  fs.writeFileSync(file, JSON.stringify(body, null, 2));
  return file;
}

export function appendFailureSummary(
  q: Question,
  role: Role,
  events: SseEvent[],
  failures: AssertionFailure[],
): void {
  ensureArtifactDir();
  const md = path.join(ARTIFACT_DIR, 'failures.md');
  const tools = toolResults(events)
    .map((t) => `${t.name}${t.success ? '' : '(✗)'}`)
    .join(', ');
  const text = finalAnswer(events).slice(0, 500).replace(/\n/g, ' ');
  const reasons = failures.map((f) => `- ${f.reason}${f.detail ? `: ${f.detail}` : ''}`).join('\n');
  const block = [
    `### [${q.id}] [${role}] ${q.category}`,
    `**Q:** ${q.question}`,
    '',
    `**Tools:** ${tools || '(none)'}`,
    `**Got (truncated):** ${text}`,
    '',
    '**Failures:**',
    reasons,
    '',
    '---',
    '',
  ].join('\n');
  fs.appendFileSync(md, block);
}

export function assertAnswer(events: SseEvent[], q: Question, role: Role): void {
  const failures = evaluateAnswer(events, q);
  if (failures.length > 0) {
    writeFailureArtifact(q, role, events, failures);
    appendFailureSummary(q, role, events, failures);
    expect(failures, `Question ${q.id} (${role}) failed:\n${JSON.stringify(failures, null, 2)}`).toEqual(
      [],
    );
  }
}
