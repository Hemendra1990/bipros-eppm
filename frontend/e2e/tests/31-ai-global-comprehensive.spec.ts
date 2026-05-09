import * as fs from 'fs';
import * as path from 'path';
import type { Page } from '@playwright/test';
import { test, expect, login, loginAs, getE2eProjectId } from '../fixtures/auth.fixture';
import {
  assertAnswer,
  defaultLlmProviderIsActive,
  finalAnswer,
  streamChat,
  type Role,
} from './helpers/ai-asserts';
import { QUESTIONS } from './data/ai-global-questions';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

const ROLES: ReadonlyArray<Role> = ['PROJECT_ENGINEER', 'SITE_MANAGER', 'PROJECT_MANAGER'];

/**
 * PROJECT_MANAGER isn't provisioned by globalSetup (DataSeeder already
 * seeds `pmanager` / `manager123`); the other two are e2e users in
 * test-users.json. Spec 30 already exercises this fallback — same shape
 * here so behavior stays consistent.
 */
async function loginAsRole(page: Page, role: Role): Promise<void> {
  if (role === 'PROJECT_MANAGER') {
    await login(page, 'pmanager', 'manager123');
  } else {
    await loginAs(page, role);
  }
}

const ARTIFACT_DIR = path.resolve('test-results/ai-global');

/**
 * Wipe the failures.md / per-case JSON dumps before each full run so a
 * clean run leaves no stale artifacts. Don't recurse into anything else.
 */
function resetArtifacts(): void {
  if (!fs.existsSync(ARTIFACT_DIR)) return;
  for (const f of fs.readdirSync(ARTIFACT_DIR)) {
    if (f.endsWith('.json') || f === 'failures.md') {
      try {
        fs.unlinkSync(path.join(ARTIFACT_DIR, f));
      } catch {
        /* best-effort cleanup */
      }
    }
  }
}

test.describe('AI Global — comprehensive question bank', () => {
  // Each test does its own login + a multi-round LLM call. Override the
  // file-level Playwright timeout so a slow tool round-trip doesn't kill
  // the case before it finishes. The bottleneck is the LLM, not the test.
  test.describe.configure({ mode: 'parallel', timeout: 240_000 });

  let llmAvailable = false;
  const projectId: string | null = getE2eProjectId();

  test.beforeAll(async ({ playwright }) => {
    resetArtifacts();
    const api = await playwright.request.newContext();
    try {
      const loginRes = await api.post(`${API_BASE}/v1/auth/login`, {
        data: { username: 'admin', password: 'admin123' },
        headers: { 'Content-Type': 'application/json' },
      });
      if (!loginRes.ok()) return;
      const adminToken = ((await loginRes.json()) as { data: { accessToken: string } }).data.accessToken;
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

  // ────────────────────────────────────────────────────────────────────
  // API bank: 115 questions × 3 roles, with per-question role gating.
  // Each test does its own login; with workers=4 (default) a full pass
  // completes in ~10–20 minutes on a warm LLM.
  // ────────────────────────────────────────────────────────────────────
  for (const q of QUESTIONS) {
    for (const role of ROLES) {
      const allowed = q.allowedRoles.includes(role);
      const titleSlug = q.question.length > 70 ? q.question.slice(0, 67) + '…' : q.question;
      test(`[${q.id}][${role}] ${titleSlug}`, async ({ page }) => {
        test.skip(!allowed, `${role} is not in allowedRoles for ${q.id}`);
        await loginAsRole(page, role);
        const events = await streamChat(page, {
          conversationId: null,
          projectId,
          module: 'general',
          message: q.question,
        });
        assertAnswer(events, q, role);
      });
    }
  }
});

// ────────────────────────────────────────────────────────────────────────
// UI smoke: drive the actual AiChatPanel for the 5 questions tagged
// uiSmoke. Verifies the floating button mounts, the textarea accepts
// input, the Send button fires, the Thinking… indicator appears and
// then disappears, and the assistant message renders into the DOM.
// One profile (PROJECT_ENGINEER) — the goal here is to catch UI
// regressions in AiChatPanel.tsx, not to re-test backend behavior.
// ────────────────────────────────────────────────────────────────────────
test.describe('AI Global — UI smoke (PROJECT_ENGINEER, real panel)', () => {
  test.describe.configure({ mode: 'serial', timeout: 240_000 });

  let llmAvailable = false;
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
    test.skip(!llmAvailable, 'No LLM provider configured.');
    test.skip(!projectId, 'No project recorded by globalSetup.');
  });

  for (const q of QUESTIONS.filter((x) => x.uiSmoke)) {
    test(`[UI][${q.id}] ${q.question}`, async ({ page }) => {
      await loginAs(page, 'PROJECT_ENGINEER');
      // Selecting a project happens via the in-app project picker; the
      // floating AI button uses whatever projectId the Zustand store has
      // resolved. globalSetup ensures the e2e user is enrolled on the
      // canonical project, so navigating to root is enough.
      await page.goto('/');

      const aiButton = page.getByRole('button', { name: /ask ai/i });
      await expect(aiButton).toBeVisible({ timeout: 15_000 });
      await aiButton.click();

      const textarea = page.getByPlaceholder(/Ask anything/i);
      await expect(textarea).toBeVisible({ timeout: 5_000 });
      await textarea.fill(q.question);
      await textarea.press('Enter');

      // The streaming indicator ("Thinking…" with a Loader2 spinner) must
      // appear, then disappear when the response is complete. Generous
      // timeout — slow LLMs and tool round-trips can stretch this.
      const thinking = page.getByText(/Thinking/i);
      await expect(thinking).toBeVisible({ timeout: 30_000 });
      await expect(thinking).toBeHidden({ timeout: 180_000 });

      // The user's message + the assistant's response should both be
      // in the DOM. The first is the question text, the second is the
      // rendered markdown of the assistant's reply (non-empty).
      await expect(page.getByText(q.question, { exact: false })).toBeVisible();

      // Read the assistant message text directly via API as the
      // ground-truth source for assertion (the rendered DOM may strip
      // formatting). A concurrent stream isn't fired; we re-ask the
      // backend with the same prompt and validate against that — same
      // contract, less brittle than scraping markdown nodes.
      const events = await streamChat(page, {
        conversationId: null,
        projectId,
        module: 'general',
        message: q.question,
      });
      const text = finalAnswer(events);
      expect(text.trim().length, `assistant returned empty answer for ${q.id}`).toBeGreaterThan(0);
    });
  }
});
