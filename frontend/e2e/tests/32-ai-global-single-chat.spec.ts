import * as fs from 'fs';
import * as path from 'path';
import { test, expect, loginAs } from '../fixtures/auth.fixture';
import { QUESTIONS } from './data/ai-global-questions';
import { ensureUserOnProject } from './helpers/enroll-on-project';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

/**
 * 6155 Barka-Nakhal — the data-rich project we evaluate the AI against.
 * E2E users are enrolled on ROAD-001 by default, which has near-zero
 * data; we add e2e_pengineer to 6155 at suite startup so the AI has
 * actual DPRs / EVM / activities / equipment rows to talk about.
 */
const PROJECT_6155 = '05829359-4126-48b0-8945-a1c51017859a';
const PROJECT_NAME = '6155 — Dualization of Barka Nakhal Road';

// Outside `test-results/` on purpose — Playwright wipes that directory
// at the start of every run, so a follow-up spec (spec 33: gap report)
// would lose this transcript before it could read it.
const ARTIFACT_DIR = path.resolve('ai-test-artifacts/ai-global-single-chat');

interface ChatTurn {
  qid: string;
  category: string;
  question: string;
  assistantText: string;
  durationMs: number;
  toolNames: string[];
  groundTruthChecks: Array<{ rule: string; passed: boolean; detail?: string }>;
  error?: string;
}

interface GroundTruth {
  project: { data: { code?: string; name?: string } } | Record<string, unknown>;
  evm: { data: Record<string, unknown> } | Record<string, unknown>;
  dpr_count: number;
  dpr_dates: { earliest: string | null; latest: string | null; uniqueDates: number };
  /**
   * Supervisors derived from ResourceAssignment rows where
   * effectiveRoleName ∈ {Supervisor, Foreman, Site Supervisor, Project Manager,
   * Site Engineer}. This is the canonical "who supervises which activity"
   * link in Bipros — DPR.supervisorName is just an audit-trail field that
   * defaults to "Unknown" when nobody backfilled it.
   */
  supervisors_via_assignment: Array<{
    name: string;
    activityCount: number;
    activities: string[];
    plannedCost: number;
    actualCost: number;
    plannedUnits: number;
    actualUnits: number;
  }>;
  resource_assignments_total: number;
  assignment_role_distribution: Record<string, number>;
  weather: Record<string, number>;
  approval_status: Record<string, number>;
  top_materials: Array<{ name: string; count: number }>;
  top_equipment: Array<{ name: string; count: number }>;
  top_trades: Array<{ name: string; count: number }>;
  activity_count: number;
  sample_activities: Array<{ code?: string; name?: string; percentComplete?: number }>;
}

function loadGroundTruth(): GroundTruth {
  const file = path.resolve('e2e/tests/data/6155-ground-truth.json');
  return JSON.parse(fs.readFileSync(file, 'utf-8')) as GroundTruth;
}

/**
 * Compare a single AI answer against the ground truth where the question
 * has a knowable correct answer. Returns a list of named checks; an empty
 * list means "no automated check applies — human review required".
 *
 * The checks are intentionally lenient on phrasing (lowercased substring)
 * but strict on quantitative facts (DPR count, BAC, CPI, SPI). Numbers
 * the AI rounds slightly differ but a tolerance is allowed.
 */
function evaluateAgainstGroundTruth(
  qid: string,
  answer: string,
  gt: GroundTruth,
): Array<{ rule: string; passed: boolean; detail?: string }> {
  const checks: Array<{ rule: string; passed: boolean; detail?: string }> = [];
  const a = answer.toLowerCase();

  const evm = (gt.evm as { data?: Record<string, unknown> }).data ?? (gt.evm as Record<string, unknown>);
  const cpi = Number(evm.costPerformanceIndex);
  const spi = Number(evm.schedulePerformanceIndex);
  const bac = Number(evm.budgetAtCompletion);

  // Project name should be referenced when the question implies project scope
  const projData = (gt.project as { data?: { name?: string } }).data;
  const projName = projData?.name?.toLowerCase() ?? '';

  if (qid === 'EVM-001') {
    // BAC question. AI should mention budget at completion. Ground truth
    // BAC=10.0 is suspicious (looks like a unit/currency artefact), so
    // we only check that the answer mentions a budget figure, not the
    // exact value.
    checks.push({
      rule: 'mentions BAC / budget',
      passed: /budget|bac|completion/i.test(a),
    });
    if (!Number.isNaN(bac)) {
      const bacRe = new RegExp(`\\b${bac.toFixed(0)}\\b|${bac.toFixed(1)}|${bac.toFixed(2)}`);
      checks.push({
        rule: `BAC numeric ≈ ${bac}`,
        passed: bacRe.test(answer),
        detail: `looking for ${bac} in answer`,
      });
    }
  }

  if (qid === 'EVM-002') {
    // CPI. Tolerance ±0.05 to allow rounding (the model often says "1.5"
    // instead of "1.517").
    if (!Number.isNaN(cpi)) {
      const m = answer.match(/(\d+\.\d+|\d+)/g);
      const numbers = (m ?? []).map(Number).filter((n) => n > 0.5 && n < 5);
      const ok = numbers.some((n) => Math.abs(n - cpi) <= 0.05);
      checks.push({
        rule: `CPI numeric ≈ ${cpi}`,
        passed: ok,
        detail: `extracted: ${numbers.join(', ')}`,
      });
    }
    checks.push({
      rule: 'mentions cost performance',
      passed: /cost performance|cpi/i.test(a),
    });
  }

  if (qid === 'EVM-003') {
    if (!Number.isNaN(spi)) {
      const m = answer.match(/0\.\d{1,3}|\d+\.\d{1,3}/g) ?? [];
      const ok = m.some((s) => Math.abs(parseFloat(s) - spi) <= 0.05);
      checks.push({
        rule: `SPI numeric ≈ ${spi}`,
        passed: ok,
        detail: `extracted: ${m.join(', ')}`,
      });
    }
    checks.push({
      rule: 'mentions schedule performance',
      passed: /schedule performance|spi|schedule/i.test(a),
    });
  }

  if (qid === 'DPR-001') {
    // Total DPR count check. Ground truth is exactly gt.dpr_count.
    const expected = gt.dpr_count;
    const re = new RegExp(`\\b${expected}\\b`);
    checks.push({
      rule: `DPR count = ${expected}`,
      passed: re.test(answer),
      detail: `expected the exact number ${expected}`,
    });
  }

  if (qid.startsWith('SUP-R-') || qid.startsWith('SUP-P-') || qid.startsWith('SUP-C-')) {
    // Supervisors come via ResourceAssignment.effectiveRoleName='Supervisor',
    // not via DPR.supervisorName (which is mostly "Unknown"). Real ground
    // truth: T. Swamy is the only supervisor on 6155, assigned to 2
    // activities ('Mobilisation Complete', 'Soil Investigation and report').
    // The AI should either name T. Swamy / those activities, or honestly
    // flag the thinness of the data.
    const known = gt.supervisors_via_assignment;
    const mentionsAny = known.some((s) => a.includes(s.name.toLowerCase()));
    const flagsGap = /no supervisor|don't have|missing|sparse|only one|just one|single supervisor/i.test(a);
    checks.push({
      rule: 'names a real supervisor (via assignment) OR flags the data gap',
      passed: mentionsAny || flagsGap,
      detail: `known via assignments: ${known.map((s) => s.name).join(', ') || '(none)'}`,
    });

    // Hallucination check: AI must not invent supervisor names that aren't
    // in the assignment data. If the answer contains a Title-Case name
    // (e.g. "Ahmed Al-Rashidi", "Sandeep Kumar") that we don't have, that's
    // a fabrication.
    const knownLower = known.map((s) => s.name.toLowerCase());
    const fabricatedNames = (
      answer.match(/\b[A-Z][a-z]+(?:\s+[A-Z]\.\s+[A-Z][a-z]+|\s+[A-Z][a-z]+){0,2}\b/g) ?? []
    ).filter((n) => {
      const lc = n.toLowerCase();
      // Filter out non-supervisor-noise words and known names
      if (knownLower.some((k) => lc.includes(k.split(' ').pop() ?? ''))) return false;
      const NOISE = [
        'project',
        'manager',
        'engineer',
        'site',
        'mobilisation',
        'soil',
        'investigation',
        'water',
        'pipe',
        'concrete',
        'culvert',
        'bull',
        'dozer',
        'asphalt',
        'cutter',
        'barka',
        'nakhal',
        'rajasthan',
        'india',
        'oman',
        'cost',
        'schedule',
        'cleared',
        'clear',
        'cloudy',
        'rainy',
        'heatwave',
      ];
      return !NOISE.some((w) => lc.includes(w));
    });
    if (fabricatedNames.length > 0 && qid.startsWith('SUP-R-')) {
      checks.push({
        rule: 'does not fabricate supervisor names',
        passed: false,
        detail: `unexpected names in answer: ${[...new Set(fabricatedNames)].slice(0, 5).join(', ')}`,
      });
    }
  }

  if (qid === 'DPR-005') {
    // Weather question. Ground truth shows 4 weather conditions distributed
    // 16/16/16/16 — answer should mention at least one of them.
    const mentioned = Object.keys(gt.weather)
      .map((w) => w.toLowerCase())
      .filter((w) => a.includes(w));
    checks.push({
      rule: 'names at least one weather condition',
      passed: mentioned.length >= 1 || /weather|don't have/i.test(a),
      detail: `weathers in DB: ${Object.keys(gt.weather).join(', ')}, answer mentioned: ${mentioned.join(', ') || 'none'}`,
    });
  }

  if (qid.startsWith('EQP-')) {
    // Ground truth on 6155: Bull Dozer + Asphalt Cutter only in DPR child
    // arrays. If the AI claims excavator/JCB/crane, that's hallucination.
    const known = gt.top_equipment.map((e) => e.name.toLowerCase());
    const mentionedKnown = known.filter((n) => a.includes(n));
    if (gt.top_equipment.length <= 3 && qid === 'EQP-001') {
      const inventsExcavator = /\bexcavator\b/i.test(a) && !/no excavator|don't have|no data/i.test(a);
      checks.push({
        rule: 'does not hallucinate excavator',
        passed: !inventsExcavator,
        detail: `equipment in DPRs: ${gt.top_equipment.map((e) => e.name).join(', ') || 'none'}`,
      });
    }
    checks.push({
      rule: 'mentions known equipment OR flags missing data',
      passed: mentionedKnown.length > 0 || /don't have|no data|no equipment/i.test(a),
      detail: `known: ${known.join(', ')}, mentioned: ${mentionedKnown.join(', ') || 'none'}`,
    });
  }

  if (qid.startsWith('MAT-')) {
    // Ground truth: zero materials in DPR child arrays. If the AI claims
    // bitumen wastage values, that's hallucinated.
    if (gt.top_materials.length === 0) {
      const hallucinatesMaterial = /\d+\s*%|\d+\s*kg|\d+\s*ton/i.test(a);
      const flagsMissing = /don't have|no material|no data|missing/i.test(a);
      checks.push({
        rule: 'does not invent material values when DB has none',
        passed: !hallucinatesMaterial || flagsMissing,
        detail: 'no materials are recorded against DPRs in the live DB',
      });
    }
  }

  if (projName && /this project|the project|nh-48|project name/i.test(answer)) {
    // If the AI is naming a project, it should match what's in scope.
    // The 6155 project is named "Dualization of Barka Nakhal Road".
    if (projName.length > 0) {
      const namesCorrectProject = a.includes(projName.split(' ')[0]) || a.includes('barka') || a.includes('6155');
      const namesWrongProject = /nh-?48|rajasthan/i.test(a) && !/no nh-?48|don't|not loaded/i.test(a);
      if (namesWrongProject) {
        checks.push({
          rule: `does not invent NH-48 (current project is ${projName})`,
          passed: false,
          detail: 'AI mentioned NH-48 / Rajasthan but that project is not in this DB',
        });
      } else if (namesCorrectProject) {
        checks.push({
          rule: 'project reference matches scope',
          passed: true,
        });
      }
    }
  }

  return checks;
}

function ensureDir(): void {
  if (!fs.existsSync(ARTIFACT_DIR)) fs.mkdirSync(ARTIFACT_DIR, { recursive: true });
}

function writeTranscript(turns: ChatTurn[], conversationId: string | null): void {
  ensureDir();
  const passes = turns.flatMap((t) => t.groundTruthChecks).filter((c) => c.passed).length;
  const checks = turns.flatMap((t) => t.groundTruthChecks).length;
  const errors = turns.filter((t) => t.error).length;
  const lines: string[] = [];
  lines.push('# AI Global — single-chat run transcript');
  lines.push('');
  lines.push(`- **Project**: ${PROJECT_NAME}`);
  lines.push(`- **Conversation ID**: ${conversationId ?? '(unknown — open the AI History panel and look for the most recent one)'}`);
  lines.push(`- **Questions sent**: ${turns.length} of ${QUESTIONS.length}`);
  lines.push(`- **Errored turns**: ${errors}`);
  lines.push(`- **Ground-truth checks**: ${passes} / ${checks} passed`);
  lines.push('');
  lines.push('To review in the UI: open the app, click the **Ask AI** floating button, click the **History** icon, and select the most recent conversation. All 115 turns are persisted there.');
  lines.push('');
  lines.push('---');
  lines.push('');
  for (const t of turns) {
    lines.push(`## [${t.qid}] ${t.category}`);
    lines.push('');
    lines.push(`**Q:** ${t.question}`);
    lines.push('');
    if (t.error) {
      lines.push(`**ERROR:** ${t.error}`);
      lines.push('');
    } else {
      lines.push(`**A** _(${t.durationMs}ms, tools: ${t.toolNames.join(', ') || 'none'}):_`);
      lines.push('');
      lines.push('> ' + t.assistantText.replace(/\n/g, '\n> '));
      lines.push('');
    }
    if (t.groundTruthChecks.length > 0) {
      lines.push('**Ground-truth checks:**');
      for (const c of t.groundTruthChecks) {
        const icon = c.passed ? '✓' : '✗';
        lines.push(`- ${icon} ${c.rule}${c.detail ? ` — ${c.detail}` : ''}`);
      }
      lines.push('');
    }
    lines.push('---');
    lines.push('');
  }
  fs.writeFileSync(path.join(ARTIFACT_DIR, 'transcript.md'), lines.join('\n'));
  fs.writeFileSync(path.join(ARTIFACT_DIR, 'turns.json'), JSON.stringify(turns, null, 2));
}

test.describe('AI Global — single-chat UI run (115 questions in one conversation)', () => {
  test.describe.configure({ mode: 'serial', timeout: 7_200_000 });

  test('All 115 questions sent through AiChatPanel as PROJECT_ENGINEER on 6155', async ({ page, playwright }) => {
    test.setTimeout(7_200_000); // 2 hours — 115 LLM round-trips with multi-tool calling

    const gt = loadGroundTruth();
    ensureDir();

    // 1) Login through the real auth flow as the PROJECT_ENGINEER e2e user.
    await loginAs(page, 'PROJECT_ENGINEER');

    // 2) Add e2e_pengineer to 6155 as TEAM_MEMBER. Idempotent.
    const api = await playwright.request.newContext();
    try {
      await ensureUserOnProject(api, 'e2e_pengineer', PROJECT_6155, 'TEAM_MEMBER');
    } finally {
      await api.dispose();
    }

    // 3) Pin currentProjectId to 6155 BEFORE the page renders so AiChatPanel
    //    starts in the right project from the very first message. Also
    //    ensure the AI panel is open so we can drive it without a click race.
    //
    // We do this two ways for belt-and-braces: (a) addInitScript so the
    // value is in localStorage before any page script runs, and (b) after
    // the page loads, post-evaluate to write the localStorage entry again
    // and reload — guards against any case where loginAs's prior goto
    // already mounted Zustand with currentProjectId=null and React doesn't
    // re-hydrate from a localStorage update. After the reload, projectId
    // is reliably 6155 in the chat request payload.
    await page.addInitScript((pid) => {
      try {
        localStorage.setItem(
          'bipros-app',
          JSON.stringify({ state: { currentProjectId: pid, sidebarCollapsed: false }, version: 0 }),
        );
        localStorage.setItem(
          'bipros-ai',
          JSON.stringify({ state: { open: true, currentConversationId: null, draft: '' }, version: 0 }),
        );
      } catch {
        /* ignore */
      }
    }, PROJECT_6155);

    await page.goto('/');
    await page.evaluate((pid) => {
      try {
        localStorage.setItem(
          'bipros-app',
          JSON.stringify({ state: { currentProjectId: pid, sidebarCollapsed: false }, version: 0 }),
        );
      } catch {
        /* ignore */
      }
    }, PROJECT_6155);
    await page.reload();

    // Sanity: the persisted store should now report 6155 as the current
    // project. If something's off we'd rather find out before sending 115
    // questions than after.
    const persistedProject = await page.evaluate(() => {
      try {
        const raw = localStorage.getItem('bipros-app');
        return raw ? (JSON.parse(raw) as { state?: { currentProjectId?: string } }).state?.currentProjectId : null;
      } catch {
        return null;
      }
    });
    expect(persistedProject, 'bipros-app store should pin currentProjectId to 6155').toBe(PROJECT_6155);

    // 4) AI panel should be open. Find the textarea.
    const textarea = page.getByPlaceholder(/Ask anything/i);
    await expect(textarea).toBeVisible({ timeout: 30_000 });

    // 5) Loop through all 115 questions. Each one is independent — a
    //    failure on one shouldn't kill the rest.
    const turns: ChatTurn[] = [];
    let i = 0;
    for (const q of QUESTIONS) {
      i += 1;
      const t0 = Date.now();
      const turn: ChatTurn = {
        qid: q.id,
        category: q.category,
        question: q.question,
        assistantText: '',
        durationMs: 0,
        toolNames: [],
        groundTruthChecks: [],
      };
      try {
        // Count assistant message bubbles BEFORE sending. The assistant
        // bubble class is "bg-surface-hover text-text-primary border
        // border-border" (line 744 of AiChatPanel.tsx) — combine the first
        // two classes for a unique-to-assistant selector that avoids the
        // module chip / textarea / hover states.
        const ASSISTANT_BUBBLE = 'div.bg-surface-hover.text-text-primary';
        const before = await page.locator(ASSISTANT_BUBBLE).count();

        await textarea.fill(q.question);
        await textarea.press('Enter');

        // The textarea is `disabled={isStreaming}` (line 886). It re-enables
        // exactly when the AI is done — including across multi-step tool
        // calls — so this is a more reliable "stream complete" signal than
        // matching the spinner status text (which the panel rotates between
        // "Thinking", "Working", "Looking up projects", etc.).
        await expect(textarea).toBeDisabled({ timeout: 60_000 });
        await expect(textarea).toBeEnabled({ timeout: 240_000 });

        // Brief settle so ReactMarkdown finishes swapping in.
        await page.waitForTimeout(800);

        const newCount = await page.locator(ASSISTANT_BUBBLE).count();
        if (newCount > before) {
          const last = page.locator(ASSISTANT_BUBBLE).nth(newCount - 1);
          turn.assistantText = (await last.innerText()).trim();
        } else {
          turn.assistantText = '(could not locate assistant block in DOM)';
        }

        // Capture tool names from any tool_call bubbles rendered for this
        // turn. The tool_call bubble class is "bg-info/10 text-info border
        // border-info/20" (line 741). Tailwind compiles bg-info/10 to
        // something like bg-info/10 in the class string, so target by the
        // border-info color which is unique to tool_call bubbles.
        const tools = await page
          .locator('div.border-info\\/20, div[class*="border-info"]')
          .allInnerTexts();
        turn.toolNames = tools.map((s) => s.replace(/\s+/g, ' ').trim()).slice(-12);
      } catch (err) {
        turn.error = (err as Error).message;
      }
      turn.durationMs = Date.now() - t0;
      turn.groundTruthChecks = evaluateAgainstGroundTruth(q.id, turn.assistantText, gt);
      turns.push(turn);

      // Incremental save in case the run crashes — losing 60 minutes of
      // LLM time to a single timeout would be painful.
      writeTranscript(turns, null);

      // eslint-disable-next-line no-console
      console.log(
        `[${i}/${QUESTIONS.length}] ${q.id} (${turn.durationMs}ms) — ${
          turn.error ? 'ERROR: ' + turn.error : turn.assistantText.slice(0, 100)
        }`,
      );

      // Tiny pause so the next message doesn't race the previous render.
      await page.waitForTimeout(400);
    }

    // 6) Capture the conversation id so the user can find the chat in the
    //    History panel afterwards. The store persists it on first message.
    const conversationId = await page.evaluate(() => {
      try {
        const raw = localStorage.getItem('bipros-ai');
        if (!raw) return null;
        const parsed = JSON.parse(raw) as { state?: { currentConversationId?: string } };
        return parsed.state?.currentConversationId ?? null;
      } catch {
        return null;
      }
    });

    writeTranscript(turns, conversationId);

    // 7) Take a final screenshot of the panel for the artifact bundle.
    try {
      await page.screenshot({ path: path.join(ARTIFACT_DIR, 'final-panel.png'), fullPage: true });
    } catch {
      /* best-effort */
    }

    const passed = turns.flatMap((t) => t.groundTruthChecks).filter((c) => c.passed).length;
    const total = turns.flatMap((t) => t.groundTruthChecks).length;
    const errors = turns.filter((t) => t.error).length;

    // eslint-disable-next-line no-console
    console.log(
      `\n=== AI Global single-chat run summary ===\n` +
        `Project: ${PROJECT_NAME}\n` +
        `Questions sent: ${turns.length}/${QUESTIONS.length}\n` +
        `Errored turns: ${errors}\n` +
        `Ground-truth checks passed: ${passed}/${total}\n` +
        `Conversation ID: ${conversationId ?? '(unknown)'}\n` +
        `Transcript: ${path.join(ARTIFACT_DIR, 'transcript.md')}\n`,
    );

    // The run itself doesn't fail the test on assertion mismatches — that's
    // captured in the transcript for human review. We DO fail if too many
    // turns errored (suggesting a systemic UI/auth problem).
    expect(errors, `${errors} of ${QUESTIONS.length} turns errored`).toBeLessThan(QUESTIONS.length / 4);
  });
});
