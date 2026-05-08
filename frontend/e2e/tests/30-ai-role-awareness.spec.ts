import { test, expect, loginAs, login, getE2eProjectId } from '../fixtures/auth.fixture';
import type { APIRequestContext, Page } from '@playwright/test';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

interface SseEvent {
  event: string;
  data: Record<string, unknown>;
}

interface ToolResult {
  name: string;
  success: boolean;
  summary: string;
}

/**
 * POST a chat message to the streaming endpoint and parse the SSE body into
 * a flat list of events. We bypass the UI textarea here on purpose — the
 * goal of this spec is to exercise the role-aware tool-routing layer end to
 * end (auth -> AiAccessGuard -> AiContextResolver -> ToolRegistry filter ->
 * orchestrator), not to re-test the AiChatPanel input field that other e2e
 * specs already cover. Login still goes through the real auth flow via
 * loginAs() so AiContextResolver sees a real authenticated principal.
 */
async function streamChat(
  page: Page,
  body: { conversationId: string | null; projectId: string | null; module: string; message: string },
): Promise<SseEvent[]> {
  const token = await page.evaluate(() => localStorage.getItem('access_token'));
  if (!token) throw new Error('No access_token in localStorage; loginAs failed.');

  const res = await page.request.post(`${API_BASE}/v1/ai/chat/stream`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    data: body,
    timeout: 90_000,
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

function toolResults(events: SseEvent[]): ToolResult[] {
  return events
    .filter((e) => e.event === 'tool_result')
    .map((e) => ({
      name: typeof e.data.name === 'string' ? e.data.name : '',
      success: e.data.success === true,
      summary: typeof e.data.summary === 'string' ? e.data.summary : '',
    }));
}

function chatTerminated(events: SseEvent[]): boolean {
  return events.some(
    (e) => e.event === 'done' || e.event === 'final_answer' || e.event === 'max_rounds_exceeded',
  );
}

async function defaultLlmProviderIsActive(api: APIRequestContext, adminToken: string): Promise<boolean> {
  const res = await api.get(`${API_BASE}/v1/admin/llm-providers`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  if (!res.ok()) return false;
  const body = (await res.json()) as { data: Array<{ isDefault: boolean; isActive: boolean }> };
  return body.data.some((p) => p.isDefault && p.isActive);
}

test.describe('Role-aware AI: per-profile chat access', () => {
  let llmAvailable = false;
  // The project ID is set by Playwright globalSetup, which both picks the
  // project and enrols every e2e user as TEAM_MEMBER on it. Using a
  // different project here would 403 in AiAccessGuard.canChat().
  const projectId: string | null = getE2eProjectId();

  test.beforeAll(async ({ playwright }) => {
    const api = await playwright.request.newContext();
    try {
      const loginRes = await api.post(`${API_BASE}/v1/auth/login`, {
        data: { username: 'admin', password: 'admin123' },
        headers: { 'Content-Type': 'application/json' },
      });
      if (!loginRes.ok()) return;
      const adminToken = ((await loginRes.json()) as { data: { accessToken: string } }).data
        .accessToken;
      llmAvailable = await defaultLlmProviderIsActive(api, adminToken);
    } finally {
      await api.dispose();
    }
  });

  test.beforeEach(async () => {
    test.skip(
      !llmAvailable,
      'No default+active LLM provider configured. Set one via /admin/ai-settings before running this suite.',
    );
    test.skip(
      !projectId,
      'globalSetup did not record a project. Run scripts/seed-icpms-data.sh (or seed-demo-data.sh) before running this suite.',
    );
  });

  // Per-profile chat smoke: each new profile must be able to log in, hit
  // /v1/ai/chat/stream, get past AiAccessGuard.canChat, and receive a final
  // answer. We deliberately don't assert on which tool the LLM picks —
  // model nondeterminism makes that flaky — but we do require:
  //  (a) the request succeeded (200),
  //  (b) at least one terminating event arrived (done / final_answer / max_rounds),
  //  (c) no tool_result reported "not available for your role" for a tool
  //      that the profile *should* see (would mean the registry filter is
  //      under-permissive for this role).
  // These are exactly the failure modes that the role-aware feature was
  // intended to fix; the unit/integration tests in bipros-ai assert the
  // exact tool-name visibility, so we don't duplicate that here.
  for (const profile of [
    'SITE_MANAGER',
    'PROJECT_ENGINEER',
    'QC_MANAGER',
    'BIM_DATA_COORDINATOR',
  ] as const) {
    test(`${profile} can chat and gets a terminating response`, async ({ page }) => {
      await loginAs(page, profile);
      const events = await streamChat(page, {
        conversationId: null,
        projectId,
        module: 'general',
        message:
          'Give me a one-line summary of this project using whatever role-appropriate tool you have. Keep it short.',
      });
      expect(events.length, 'expected SSE events from chat/stream').toBeGreaterThan(0);
      expect(chatTerminated(events), 'expected a done/final_answer/max_rounds event').toBe(true);

      const deniedAuthorized = toolResults(events).filter(
        (r) => !r.success && r.summary.includes('not available for your role'),
      );
      expect(
        deniedAuthorized,
        `${profile} hit a denial — registry filter appears under-permissive: ` +
          deniedAuthorized.map((d) => d.name).join(','),
      ).toEqual([]);
    });
  }

  test('PROJECT_MANAGER can chat (uses seeded pmanager account, profile carries AI.WRITE)', async ({
    page,
  }) => {
    // PROJECT_MANAGER is seeded by DataSeeder (`pmanager`/`manager123`),
    // so it isn't in test-users.json — fall back to the seeded credentials.
    await login(page, 'pmanager', 'manager123');
    const events = await streamChat(page, {
      conversationId: null,
      projectId,
      module: 'general',
      message: 'Give me a one-line cost summary of this project.',
    });
    expect(chatTerminated(events)).toBe(true);
  });

  test('SITE_MANAGER asking for a PM-only tool gets it rejected by the registry', async ({
    page,
  }) => {
    // ToolRegistry.toolsForProfile() removes portfolio_kpi from a SITE_MANAGER
    // tool set, so the tool is NOT advertised to the LLM. If the model
    // hallucinates the call anyway, the orchestrator's isAllowed() check
    // rejects it at execution time and surfaces a tool_result with
    // success=false + "not available for your role." in summary.
    //
    // Either way: there must NOT be a tool_result where name=portfolio_kpi
    // AND success=true. That's the load-bearing invariant.
    await loginAs(page, 'SITE_MANAGER');
    const events = await streamChat(page, {
      conversationId: null,
      projectId,
      module: 'general',
      message:
        'Give me the portfolio KPIs across all projects. If you have a portfolio_kpi tool, use it.',
    });

    const portfolioResults = toolResults(events).filter((r) => r.name === 'portfolio_kpi');
    for (const r of portfolioResults) {
      expect(r.success, `portfolio_kpi must not succeed for SITE_MANAGER: ${r.summary}`).toBe(false);
    }
  });

  test('AI panel UI mounts for a non-PM profile (SITE_MANAGER)', async ({ page }) => {
    // Smoke-check the new auth path: a fresh-out-of-the-box SITE_MANAGER
    // must reach the dashboard and see the floating AI button. AI.READ
    // permission is what gates panel mount; if ProfileSeeder forgot to add
    // AI.READ to SITE_MANAGER, this fails before any chat call.
    await loginAs(page, 'SITE_MANAGER');
    await page.goto('/');
    const aiButton = page.getByRole('button', { name: /ask ai/i });
    await expect(aiButton).toBeVisible({ timeout: 15_000 });
  });
});
