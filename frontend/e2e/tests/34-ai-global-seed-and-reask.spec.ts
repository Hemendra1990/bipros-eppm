import * as fs from 'fs';
import * as path from 'path';
import type { APIRequestContext, Page } from '@playwright/test';
import { test, expect, loginAs, login } from '../fixtures/auth.fixture';
import { QUESTIONS } from './data/ai-global-questions';
import type { Question } from './helpers/ai-asserts';

/**
 * Seed-and-reask: for each NO_DATA category captured in spec 32, seed
 * targeted data via the REST API, open a fresh chat, re-ask only those
 * questions, capture the new answers, and diff against the old ones.
 *
 * Closed gap   = AI now answers substantively → original was a data gap.
 * Still NO_DATA = AI tool isn't reading the seeded data correctly →
 *                 confirmed code/tool gap; that's the queue for the
 *                 backend agent team to fix.
 *
 * Run AFTER spec 32 + spec 33 (need turns.json + gap-report.md as inputs).
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const PROJECT_6155 = '05829359-4126-48b0-8945-a1c51017859a';

const ARTIFACT_DIR = path.resolve('ai-test-artifacts/ai-global-single-chat');
const SEED_OUT_DIR = path.resolve('ai-test-artifacts/ai-global-seed-and-reask');

// Recycled NO_DATA detection — same logic the gap report uses, kept local
// so this spec is self-contained.
const NO_DATA_PHRASES = [
  "i don't have",
  "i don't see",
  "i can't confirm",
  "i can't find",
  'no data',
  'no records',
  'no supervisor',
  'no equipment',
  'no material',
  'no manpower',
  'no daily progress',
  'no dpr',
  'not populated',
  'reporting base is too thin',
  'thin to calculate',
  'not enough data',
  'too thin',
  'unable to find',
  'no entries',
  'nothing reported',
  'is effectively blank',
  "can't compute",
  "can't calculate",
  'is blank',
  'currently empty',
  'no breakdown',
  'i cannot',
];

function isNoData(text: string): boolean {
  const lc = text
    .replace(/[‘’]/g, "'")
    .replace(/[“”]/g, '"')
    .toLowerCase();
  return NO_DATA_PHRASES.some((p) => lc.includes(p));
}

interface PriorTurn {
  qid: string;
  category: string;
  question: string;
  assistantText: string;
  durationMs: number;
  groundTruthChecks: Array<{ rule: string; passed: boolean; detail?: string }>;
  error?: string;
}

interface AssignmentRow {
  id: string;
  activityId: string;
  activityName: string;
  resourceId: string;
  resourceName: string;
  effectiveRoleName: string | null;
}

async function fetchAssignments(api: APIRequestContext, adminToken: string): Promise<AssignmentRow[]> {
  const res = await api.get(`${API_BASE}/v1/projects/${PROJECT_6155}/resource-assignments`, {
    params: { size: 1000 },
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  if (!res.ok()) throw new Error(`assignments fetch: ${res.status()}`);
  const body = (await res.json()) as { data: unknown };
  const rows = Array.isArray(body.data)
    ? (body.data as AssignmentRow[])
    : ((body.data as { content?: AssignmentRow[] }).content ?? []);
  return rows;
}

interface SeedSummary {
  category: string;
  attempted: number;
  succeeded: number;
  failed: number;
  errors: string[];
}

// ResourceRole and ResourceType UUIDs on this DB (queried at /v1/resource-roles
// and /v1/resource-types). Hard-coded for the seed because they're stable
// across the dev DB and looking them up adds two more REST round-trips.
const SUPERVISOR_ROLE_ID = '922401cf-044a-405e-bd3e-b2e424b9b7b3'; // BNK-ROLE-SUPERVISOR
const LABOR_TYPE_ID = '29b9dcb9-2550-4dbc-897e-d3f38aa50354';

const NEW_SUPERVISOR_NAMES = [
  'S. Kumar',
  'M. Patel',
  'R. Krishnan',
  'A. Sharma',
];

interface SeededSupervisor {
  resourceId: string;
  name: string;
  assignmentId: string;
  activityId: string;
  activityName: string;
}

/**
 * Ensure multiple supervisor identities exist on 6155 by:
 *   (a) POSTing N new Resource rows with role=Supervisor / type=Labor;
 *   (b) POSTing one ResourceAssignment per new supervisor onto a
 *       *distinct* activity (so the AI's compare_supervisors tool has
 *       different rollups to compare).
 *
 * Returns the new supervisors with their assignment IDs so the DPR
 * seeder can rotate `supervisorResourceId` across days.
 */
async function ensureMultipleSupervisors(
  api: APIRequestContext,
  adminToken: string,
  assignments: AssignmentRow[],
): Promise<{ created: SeededSupervisor[]; errors: string[] }> {
  const errors: string[] = [];
  // Distinct activities — one per new supervisor. Skip the two activities
  // T. Swamy is already supervising so the new ones land elsewhere.
  const swamyActs = new Set(
    assignments
      .filter((a) => a.resourceName === 'T. Swamy')
      .map((a) => a.activityId),
  );
  const distinctActivities: AssignmentRow[] = [];
  const seen = new Set<string>();
  for (const a of assignments) {
    if (swamyActs.has(a.activityId)) continue;
    if (seen.has(a.activityId)) continue;
    seen.add(a.activityId);
    distinctActivities.push(a);
    if (distinctActivities.length >= NEW_SUPERVISOR_NAMES.length) break;
  }
  if (distinctActivities.length < NEW_SUPERVISOR_NAMES.length) {
    errors.push(
      `Only found ${distinctActivities.length} non-T.Swamy activities to assign new supervisors to (wanted ${NEW_SUPERVISOR_NAMES.length}).`,
    );
  }

  const created: SeededSupervisor[] = [];
  for (let i = 0; i < distinctActivities.length; i += 1) {
    const name = NEW_SUPERVISOR_NAMES[i];
    const activity = distinctActivities[i];

    const resBody: Record<string, unknown> = {
      name,
      roleId: SUPERVISOR_ROLE_ID,
      resourceTypeId: LABOR_TYPE_ID,
      costPerUnit: 100,
      unit: 'PER_DAY',
      status: 'ACTIVE',
    };
    const resPost = await api.post(`${API_BASE}/v1/resources`, {
      headers: { Authorization: `Bearer ${adminToken}`, 'Content-Type': 'application/json' },
      data: resBody,
    });
    if (!resPost.ok()) {
      errors.push(`POST /v1/resources for "${name}" → HTTP ${resPost.status()}: ${(await resPost.text()).slice(0, 200)}`);
      continue;
    }
    const resourceId = ((await resPost.json()) as { data: { id: string } }).data.id;

    // The resource-assignment endpoint enforces RESOURCE_NOT_IN_POOL: a
    // resource must be in the project's resource pool before it can be
    // assigned to an activity. Add it now (idempotent: 409 = already in
    // pool, both 201 and 409 are fine here).
    const poolPost = await api.post(`${API_BASE}/v1/projects/${PROJECT_6155}/resource-pool`, {
      headers: { Authorization: `Bearer ${adminToken}`, 'Content-Type': 'application/json' },
      data: { entries: [{ resourceId }] },
    });
    if (!poolPost.ok() && poolPost.status() !== 409) {
      errors.push(
        `POST resource-pool for "${name}" → HTTP ${poolPost.status()}: ${(await poolPost.text()).slice(0, 200)}`,
      );
      continue;
    }

    // Now create an assignment (resource → activity) for the new supervisor.
    // The controller validates `projectId` in the BODY (not just URL path),
    // so include it explicitly — that was the v1 seed bug.
    const asgBody = {
      projectId: PROJECT_6155,
      activityId: activity.activityId,
      resourceId,
      plannedUnits: 8,
      plannedCost: 800,
      rateType: 'STANDARD',
      plannedStartDate: '2026-04-01',
      plannedFinishDate: '2026-05-31',
      staffed: true,
    };
    const asgPost = await api.post(
      `${API_BASE}/v1/projects/${PROJECT_6155}/resource-assignments`,
      {
        headers: { Authorization: `Bearer ${adminToken}`, 'Content-Type': 'application/json' },
        data: asgBody,
      },
    );
    if (!asgPost.ok()) {
      errors.push(
        `POST resource-assignments for "${name}" on activity ${activity.activityName} → HTTP ${asgPost.status()}: ${(await asgPost.text()).slice(0, 200)}`,
      );
      continue;
    }
    const assignmentId = ((await asgPost.json()) as { data: { id: string } }).data.id;
    created.push({
      resourceId,
      name,
      assignmentId,
      activityId: activity.activityId,
      activityName: activity.activityName,
    });
  }
  return { created, errors };
}

/**
 * Seed batch: create 7 DPRs spanning the last 7 days, each with one
 * manpower + one equipment + one material child row referencing existing
 * ResourceAssignments on 6155, AND rotating the `supervisorResourceId`
 * across the new supervisors so the supervisor-comparison tools have
 * multiple identities to roll up.
 *
 * Closes the data side of:
 *   DPR        (last-7-days, this-week, this-month, weather, manpower-on-date)
 *   MANPOWER   (hours-this-month, OT-last-week, productivity, headcount)
 *   EQUIPMENT  (utilization-this-week, idle-hours, fuel-this-month, MA%)
 *   MATERIAL   (wastage, consumption, reconciliation, top-3-wasted)
 *   SUPERVISOR (multiple identities for compare_supervisors / list)
 *
 * Materials don't have ResourceAssignment links in the existing data, so
 * we reuse the manpower assignment id as a placeholder; if the API
 * rejects, summary.errors shows it.
 */
async function seedRecentDprs(
  api: APIRequestContext,
  adminToken: string,
  assignments: AssignmentRow[],
  newSupervisors: SeededSupervisor[],
): Promise<SeedSummary> {
  const summary: SeedSummary = { category: 'DPR (full child arrays, multiple supervisors)', attempted: 0, succeeded: 0, failed: 0, errors: [] };

  // The DPR service enforces "child row's resourceAssignment must belong
  // to the same activityId as the DPR" — that was the v1 seed bug. So
  // we have to bucket assignments by activity and only seed DPRs for
  // activities that have BOTH a manpower-role and an equipment-role
  // assignment available.
  const EQUIPMENT_ROLES = new Set([
    'Earth Moving',
    'Paving Equipment',
    'Cranes Lifting',
    'Transport Vehicles',
    'Concrete Equipment',
  ]);
  const isManpowerRole = (r: string | null) => !!r && r.startsWith('Role ');
  const isEquipmentRole = (r: string | null) => !!r && EQUIPMENT_ROLES.has(r);

  const byActivity = new Map<string, AssignmentRow[]>();
  for (const a of assignments) {
    if (!a.activityId) continue;
    const list = byActivity.get(a.activityId);
    if (list) list.push(a);
    else byActivity.set(a.activityId, [a]);
  }
  const viableActivities: Array<{ activity: AssignmentRow; mp: AssignmentRow; eq: AssignmentRow }> = [];
  for (const [, rows] of byActivity) {
    const mp = rows.find((r) => isManpowerRole(r.effectiveRoleName));
    const eq = rows.find((r) => isEquipmentRole(r.effectiveRoleName));
    if (mp && eq) viableActivities.push({ activity: mp, mp, eq });
    if (viableActivities.length >= 7) break;
  }
  if (viableActivities.length === 0) {
    summary.errors.push('No activities on 6155 have both manpower and equipment assignments to seed against.');
    return summary;
  }

  // Supervisor pool: T. Swamy (already in the data) + the new ones we
  // just created. Rotating across them gives the AI enough variety to
  // produce a real comparison.
  const swamyAssignment = assignments.find(
    (a) => a.resourceName === 'T. Swamy' && a.effectiveRoleName === 'Supervisor',
  );
  const supervisorPool: Array<{ resourceId: string; name: string }> = [
    ...(swamyAssignment
      ? [{ resourceId: swamyAssignment.resourceId, name: swamyAssignment.resourceName }]
      : []),
    ...newSupervisors.map((s) => ({ resourceId: s.resourceId, name: s.name })),
  ];
  if (supervisorPool.length === 0) {
    summary.errors.push('No supervisors available — neither T. Swamy nor newly-seeded supervisors found.');
    return summary;
  }

  const today = new Date();
  for (let dayOffset = 1; dayOffset <= 7 && dayOffset <= viableActivities.length; dayOffset += 1) {
    const d = new Date(today);
    d.setDate(d.getDate() - dayOffset);
    const reportDate = d.toISOString().slice(0, 10);
    summary.attempted += 1;

    // One DPR per viable activity — the assignments for both child rows
    // belong to this activityId, so the DPR service's cross-check passes.
    const { activity, mp, eq } = viableActivities[dayOffset - 1];
    // Rotate through the supervisor pool so each DPR has a different
    // supervisor — mandatory for "compare supervisors" / "rank by CPI"
    // questions to have multiple identities to roll up.
    const supervisor = supervisorPool[dayOffset % supervisorPool.length];

    const body = {
      reportDate,
      supervisorResourceId: supervisor.resourceId,
      supervisorName: supervisor.name,
      chainageFromM: 100 * dayOffset,
      chainageToM: 100 * dayOffset + 80,
      activityId: activity.activityId,
      activityName: activity.activityName,
      unit: 'cum',
      qtyExecuted: 25 + dayOffset * 5,
      weatherCondition: ['CLEAR', 'CLOUDY', 'RAINY'][dayOffset % 3],
      shift: 'DAY',
      approvalStatus: 'SUBMITTED',
      contractorName: 'Lead Contractor',
      manpower: [
        {
          resourceAssignmentId: mp.id,
          resourceId: mp.resourceId,
          trade: mp.resourceName.replace(/^Role\s+/i, '') || 'Mason',
          category: 'SKILLED',
          nos: 4,
          workingHours: 8,
          otHours: dayOffset === 1 ? 2 : 0,
          unitRate: 12,
          unitRateBasis: 'HOUR',
        },
      ],
      equipment: [
        {
          resourceAssignmentId: eq.id,
          resourceId: eq.resourceId,
          equipmentType: eq.resourceName,
          fleetNo: `FLT-${dayOffset}`,
          nos: 1,
          workingHours: 8,
          idleHours: dayOffset === 2 ? 2 : 0.5,
          breakdownHours: dayOffset === 3 ? 1.5 : 0,
          fuelLitres: 40 + dayOffset * 5,
        },
      ],
      // Materials intentionally omitted: the DPR service enforces that
      // each child row's resourceAssignmentId points to an assignment of
      // the matching resource kind. There are no MATERIAL-kind resource
      // assignments in 6155's existing data, so any materials array we
      // send fails with INVALID_DPR_RESOURCE_KIND. That's a real backend
      // constraint — the implementation agent's queue should pick it up
      // ("MATERIAL questions need either a material-pool seed path or a
      // DPR-side relaxation"), and the MAT-* gaps will stay in STILL_GAP
      // until that's resolved.
      materials: [],
    };

    const res = await api.post(`${API_BASE}/v1/projects/${PROJECT_6155}/dpr`, {
      headers: {
        Authorization: `Bearer ${adminToken}`,
        'Content-Type': 'application/json',
      },
      data: body,
    });
    if (res.ok()) {
      summary.succeeded += 1;
    } else {
      summary.failed += 1;
      summary.errors.push(`day=${reportDate} HTTP ${res.status()}: ${(await res.text()).slice(0, 200)}`);
    }
  }
  return summary;
}

async function streamChat(
  page: Page,
  body: { conversationId: string | null; projectId: string | null; module: string; message: string },
): Promise<{ events: Array<{ event: string; data: Record<string, unknown> }>; finalText: string }> {
  const token = await page.evaluate(() => localStorage.getItem('access_token'));
  if (!token) throw new Error('No access_token in localStorage');
  const res = await page.request.post(`${API_BASE}/v1/ai/chat/stream`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    data: body,
    timeout: 240_000,
  });
  if (!res.ok()) throw new Error(`chat/stream HTTP ${res.status()}: ${await res.text()}`);
  const raw = await res.text();
  const events: Array<{ event: string; data: Record<string, unknown> }> = [];
  for (const frame of raw.split(/\r?\n\r?\n/)) {
    const lines = frame.split(/\r?\n/);
    let event = 'message';
    const dataLines: string[] = [];
    for (const line of lines) {
      if (!line || line.startsWith(':')) continue;
      if (line.startsWith('event:')) event = line.slice('event:'.length).trim();
      else if (line.startsWith('data:')) dataLines.push(line.slice('data:'.length).replace(/^ /, ''));
    }
    const data = dataLines.join('\n');
    if (!data) continue;
    try {
      events.push({ event, data: JSON.parse(data) });
    } catch {
      events.push({ event, data: { raw: data } });
    }
  }
  let finalText = '';
  for (const e of events) {
    if (e.event === 'final_answer' && typeof e.data.text === 'string') finalText = e.data.text;
  }
  if (!finalText) {
    const tokens: string[] = [];
    for (const e of events) {
      if (e.event === 'token' && typeof e.data.delta === 'string') tokens.push(e.data.delta);
    }
    finalText = tokens.join('');
  }
  return { events, finalText };
}

interface DiffEntry {
  qid: string;
  category: string;
  question: string;
  before: string;
  after: string;
  beforeNoData: boolean;
  afterNoData: boolean;
  verdict: 'CLOSED' | 'STILL_GAP' | 'CHANGED' | 'UNCHANGED';
}

function ensureDir(p: string): void {
  if (!fs.existsSync(p)) fs.mkdirSync(p, { recursive: true });
}

test.describe('AI Global — seed and reask', () => {
  test.describe.configure({ mode: 'serial', timeout: 3_600_000 });

  test('Seed missing data, then re-ask NO_DATA questions in a fresh chat', async ({ page, playwright }) => {
    test.setTimeout(3_600_000);

    // 1) Load prior turns and pick NO_DATA ones.
    const turnsPath = path.join(ARTIFACT_DIR, 'turns.json');
    expect(fs.existsSync(turnsPath), `Run spec 32 first. Missing ${turnsPath}.`).toBe(true);
    const prior = JSON.parse(fs.readFileSync(turnsPath, 'utf-8')) as PriorTurn[];
    const byQid = new Map<string, Question>();
    for (const q of QUESTIONS) byQid.set(q.id, q);

    const noDataTurns = prior.filter((t) => !t.error && t.assistantText.trim() && isNoData(t.assistantText));
    // eslint-disable-next-line no-console
    console.log(`NO_DATA candidates from spec 32: ${noDataTurns.length}`);

    // 2) Admin login + assignment fetch (used both for seeding and to
    //    enroll the e2e_pengineer on 6155 if not already there).
    const adminApi = await playwright.request.newContext();
    let adminToken: string;
    let assignments: AssignmentRow[];
    try {
      const loginRes = await adminApi.post(`${API_BASE}/v1/auth/login`, {
        data: { username: 'admin', password: 'admin123' },
        headers: { 'Content-Type': 'application/json' },
      });
      expect(loginRes.ok(), 'admin login should succeed').toBe(true);
      adminToken = ((await loginRes.json()) as { data: { accessToken: string } }).data.accessToken;
      assignments = await fetchAssignments(adminApi, adminToken);
      // eslint-disable-next-line no-console
      console.log(`Existing ResourceAssignments on 6155: ${assignments.length}`);
    } finally {
      // Keep adminApi open for seeding; we'll dispose at end.
    }

    // 3) Seed: ensure multiple supervisors exist (T. Swamy + 4 new ones
    //    on distinct activities), then create 7 DPRs covering the last 7
    //    days with manpower + equipment + material child rows, rotating
    //    the `supervisorResourceId` so the comparison-tools have data.
    const seedSummaries: SeedSummary[] = [];
    const supSeed = await ensureMultipleSupervisors(adminApi, adminToken, assignments);
    seedSummaries.push({
      category: 'Supervisor identities (Resource + Assignment)',
      attempted: NEW_SUPERVISOR_NAMES.length,
      succeeded: supSeed.created.length,
      failed: NEW_SUPERVISOR_NAMES.length - supSeed.created.length,
      errors: supSeed.errors,
    });
    // Re-fetch assignments so the new supervisor assignments are available
    // to the DPR seeder (their assignmentIds aren't strictly needed for
    // DPR.supervisorResourceId, but a fresh list keeps the picture honest
    // for any future logic).
    const refreshedAssignments = await fetchAssignments(adminApi, adminToken);
    seedSummaries.push(await seedRecentDprs(adminApi, adminToken, refreshedAssignments, supSeed.created));
    await adminApi.dispose();

    // 4) Login as PROJECT_ENGINEER, ensure-on-project, set 6155 in store.
    await loginAs(page, 'PROJECT_ENGINEER');
    const playApi = await playwright.request.newContext();
    try {
      // Belt-and-braces: re-add to 6155 (idempotent 409).
      const adminLogin = await playApi.post(`${API_BASE}/v1/auth/login`, {
        data: { username: 'admin', password: 'admin123' },
        headers: { 'Content-Type': 'application/json' },
      });
      const t = ((await adminLogin.json()) as { data: { accessToken: string } }).data.accessToken;
      const usersRes = await playApi.get(`${API_BASE}/v1/users?page=0&size=200`, {
        headers: { Authorization: `Bearer ${t}` },
      });
      const usersBody = (await usersRes.json()) as {
        data: { content: Array<{ id: string; username: string }> };
      };
      const peUser = usersBody.data.content.find((u) => u.username === 'e2e_pengineer');
      if (peUser) {
        await playApi.post(`${API_BASE}/v1/projects/${PROJECT_6155}/members`, {
          headers: { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' },
          data: { userId: peUser.id, role: 'TEAM_MEMBER' },
        });
      }
    } finally {
      await playApi.dispose();
    }

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

    // 5) Re-ask each NO_DATA question via streaming API in a SHARED fresh
    //    conversation (same conversationId across all questions, but a
    //    NEW conversation distinct from spec 32's). Using the API rather
    //    than the UI: we already validated the UI flow in spec 32, and
    //    the API is ~3x faster — important when re-asking ~50 questions.
    let conversationId: string | null = null;
    const diffs: DiffEntry[] = [];
    let i = 0;
    for (const t of noDataTurns) {
      i += 1;
      const q = byQid.get(t.qid);
      if (!q) continue;
      try {
        const { events, finalText } = await streamChat(page, {
          conversationId,
          projectId: PROJECT_6155,
          module: 'general',
          message: q.question,
        });
        if (!conversationId) {
          for (const e of events) {
            if (e.event === 'conversation_started' && typeof e.data.conversationId === 'string') {
              conversationId = e.data.conversationId;
              break;
            }
          }
        }
        const afterNoData = isNoData(finalText);
        const diff: DiffEntry = {
          qid: t.qid,
          category: t.category,
          question: q.question,
          before: t.assistantText,
          after: finalText,
          beforeNoData: true,
          afterNoData,
          verdict:
            !afterNoData && finalText.trim().length > 0
              ? 'CLOSED'
              : afterNoData && finalText.trim().length > 0
                ? 'STILL_GAP'
                : finalText.trim() === t.assistantText.trim()
                  ? 'UNCHANGED'
                  : 'CHANGED',
        };
        diffs.push(diff);
        // eslint-disable-next-line no-console
        console.log(
          `[${i}/${noDataTurns.length}] ${t.qid} ${diff.verdict} — ${finalText.slice(0, 100).replace(/\n/g, ' ')}`,
        );
      } catch (err) {
        diffs.push({
          qid: t.qid,
          category: t.category,
          question: q.question,
          before: t.assistantText,
          after: `ERROR: ${(err as Error).message}`,
          beforeNoData: true,
          afterNoData: true,
          verdict: 'STILL_GAP',
        });
      }

      // Incremental save so a crash doesn't lose progress.
      ensureDir(SEED_OUT_DIR);
      fs.writeFileSync(path.join(SEED_OUT_DIR, 'diffs.json'), JSON.stringify(diffs, null, 2));
    }

    // 6) Final report: diff-report.md
    const closed = diffs.filter((d) => d.verdict === 'CLOSED');
    const stillGap = diffs.filter((d) => d.verdict === 'STILL_GAP');
    const changed = diffs.filter((d) => d.verdict === 'CHANGED');
    const unchanged = diffs.filter((d) => d.verdict === 'UNCHANGED');

    const lines: string[] = [];
    lines.push('# AI Global — seed and reask diff report');
    lines.push('');
    lines.push(`- **Project**: 6155 — Dualization of Barka Nakhal Road`);
    lines.push(`- **Re-ask conversation ID**: ${conversationId ?? '(unknown)'}`);
    lines.push(`- **NO_DATA questions re-asked**: ${diffs.length}`);
    lines.push(`- **CLOSED** (data fix worked, AI now answers): ${closed.length}`);
    lines.push(`- **STILL_GAP** (AI still says "I don't have" — code/tool fix needed): ${stillGap.length}`);
    lines.push(`- **CHANGED** (different answer, but ambiguous): ${changed.length}`);
    lines.push(`- **UNCHANGED**: ${unchanged.length}`);
    lines.push('');
    lines.push('## Seed summary');
    for (const s of seedSummaries) {
      lines.push(`- **${s.category}**: ${s.succeeded}/${s.attempted} created${s.errors.length ? ` (errors: ${s.errors.length})` : ''}`);
      for (const e of s.errors) lines.push(`  - ${e}`);
    }
    lines.push('');

    const renderBucket = (title: string, items: DiffEntry[]) => {
      lines.push(`## ${title} (${items.length})`);
      lines.push('');
      for (const d of items) {
        lines.push(`### [${d.qid}] ${d.category}`);
        lines.push(`**Q:** ${d.question}`);
        lines.push(`**Before:** ${d.before.slice(0, 200).replace(/\n/g, ' ')}…`);
        lines.push(`**After:** ${d.after.slice(0, 400).replace(/\n/g, ' ')}…`);
        lines.push('');
      }
    };

    renderBucket('CLOSED — data was the gap (now answers)', closed);
    renderBucket('STILL_GAP — code/tool fix needed', stillGap);
    renderBucket('CHANGED — different answer, manual review', changed);
    if (unchanged.length > 0) renderBucket('UNCHANGED', unchanged);

    fs.writeFileSync(path.join(SEED_OUT_DIR, 'diff-report.md'), lines.join('\n'));
    fs.writeFileSync(path.join(SEED_OUT_DIR, 'diffs.json'), JSON.stringify(diffs, null, 2));
    fs.writeFileSync(path.join(SEED_OUT_DIR, 'seed-summary.json'), JSON.stringify(seedSummaries, null, 2));

    // eslint-disable-next-line no-console
    console.log(
      `\n=== Seed-and-reask summary ===\n` +
        `Re-asked: ${diffs.length}\n` +
        `CLOSED: ${closed.length}, STILL_GAP: ${stillGap.length}, CHANGED: ${changed.length}, UNCHANGED: ${unchanged.length}\n` +
        `Conversation ID: ${conversationId}\n` +
        `Report: ${path.join(SEED_OUT_DIR, 'diff-report.md')}\n`,
    );

    expect(diffs.length, 'expected at least one re-asked turn').toBeGreaterThan(0);
  });
});
