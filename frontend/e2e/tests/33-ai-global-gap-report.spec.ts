import * as fs from 'fs';
import * as path from 'path';
import { test, expect } from '../fixtures/auth.fixture';
import type { Question } from './helpers/ai-asserts';
import { QUESTIONS } from './data/ai-global-questions';

/**
 * Gap-report generator. Reads the artifact produced by spec 32
 * (test-results/ai-global-single-chat/turns.json) and classifies every
 * turn into one of:
 *
 *   PASSED   — all ground-truth checks passed.
 *   NO_DATA  — AI answered honestly but said "I don't have ___" (or similar).
 *              These are the candidates for seed-and-reask remediation.
 *   WRONG    — AI gave a substantive answer but failed a ground-truth check.
 *   OTHER    — assertion mix; manual review.
 *
 * For NO_DATA turns it emits a per-category remediation plan listing the
 * concrete REST endpoint + payload shape that would seed the missing
 * data, so the next step (seed + re-ask) can build from a checked plan
 * rather than guessing.
 *
 * Run AFTER spec 32 finishes:
 *   npx playwright test 33-ai-global-gap-report.spec.ts
 */

interface Turn {
  qid: string;
  category: string;
  question: string;
  assistantText: string;
  durationMs: number;
  toolNames: string[];
  groundTruthChecks: Array<{ rule: string; passed: boolean; detail?: string }>;
  error?: string;
}

type Verdict = 'PASSED' | 'NO_DATA' | 'WRONG' | 'OTHER';

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
  // The model emits curly Unicode quotes ('U+2019') in contractions like
  // "I don't have"; our phrase list uses straight ASCII apostrophes. Without
  // normalization the classifier matches almost nothing and OTHER absorbs
  // every NO_DATA turn. Normalize both directions of typographic quotes
  // before lowercasing.
  const lc = text
    .replace(/[‘’]/g, "'")
    .replace(/[“”]/g, '"')
    .toLowerCase();
  return NO_DATA_PHRASES.some((p) => lc.includes(p));
}

function classify(t: Turn): Verdict {
  if (t.error) return 'OTHER';
  if (t.assistantText.trim().length === 0) return 'OTHER';
  const passed = t.groundTruthChecks.filter((c) => c.passed).length;
  const total = t.groundTruthChecks.length;
  const allPassed = total > 0 && passed === total;
  const noData = isNoData(t.assistantText);
  if (allPassed && !noData) return 'PASSED';
  if (noData) return 'NO_DATA';
  if (total > 0 && passed < total) return 'WRONG';
  return 'OTHER';
}

interface SeedProposal {
  endpoint: string;
  method: 'POST' | 'PUT';
  payloadSketch: Record<string, unknown>;
  rationale: string;
}

/**
 * Map a NO_DATA turn to a concrete REST seed proposal. The sketch is what
 * a follow-up `seed-and-reask` runner would POST. Returns null if we
 * don't have a clear seed pathway for the category — those need manual
 * review (often code fixes, not data gaps).
 */
function proposeSeed(t: Turn, q: Question): SeedProposal | null {
  const projectIdPlaceholder = '<6155-uuid>';

  switch (q.category) {
    case 'SUPERVISOR_ROSTER':
    case 'SUPERVISOR_PERFORMANCE':
    case 'SUPERVISOR_COMPARISON':
      return {
        endpoint: `/v1/projects/${projectIdPlaceholder}/resource-assignments`,
        method: 'POST',
        payloadSketch: {
          activityId: '<activity-uuid>',
          resourceId: '<supervisor-resource-uuid>',
          roleName: 'Supervisor',
          plannedUnits: 8,
          plannedCost: 800,
          rateType: 'STANDARD',
          plannedStartDate: '2026-04-01',
          plannedFinishDate: '2026-04-30',
        },
        rationale:
          'Supervisor coverage is thin (only T. Swamy on 2 activities). Add more ResourceAssignments with effectiveRoleName="Supervisor" so compare_supervisors / supervisor tools have content to roll up.',
      };

    case 'DPR':
      return {
        endpoint: `/v1/projects/${projectIdPlaceholder}/dpr`,
        method: 'POST',
        payloadSketch: {
          reportDate: '<YYYY-MM-DD>',
          supervisorResourceId: '<supervisor-uuid>',
          activityId: '<activity-uuid>',
          chainageFromM: 0,
          chainageToM: 100,
          unit: 'cum',
          qtyExecuted: 50.0,
          weatherCondition: 'CLEAR',
          shift: 'DAY',
          approvalStatus: 'SUBMITTED',
          contractorName: 'Lead Contractor',
          manpower: [{ resourceId: '<r>', trade: 'Mason', category: 'SKILLED', nos: 4, workingHours: 8 }],
          equipment: [{ equipmentType: 'Excavator', nos: 1, workingHours: 8, fuelLitres: 40 }],
          materials: [{ materialName: 'Cement', quantity: 10, unit: 'bags' }],
        },
        rationale:
          'DPR coverage on 6155 is 65 rows but most resource child arrays are empty. Add DPRs with populated manpower/equipment/material so query_dpr_resources has content.',
      };

    case 'MANPOWER':
      return {
        endpoint: `/v1/projects/${projectIdPlaceholder}/dpr`,
        method: 'POST',
        payloadSketch: {
          reportDate: '<YYYY-MM-DD>',
          activityId: '<activity-uuid>',
          unit: 'cum',
          qtyExecuted: 25,
          manpower: [
            { resourceId: '<r>', trade: 'Mason', category: 'SKILLED', nos: 6, workingHours: 8, otHours: 2, unitRate: 12 },
            { resourceId: '<r>', trade: 'Helper', category: 'UNSKILLED', nos: 4, workingHours: 8, otHours: 0, unitRate: 8 },
          ],
        },
        rationale:
          'No manpower lines in current DPRs (top trade is "T. Swamy" — a name, not a trade). Seed DPRs with realistic manpower rows so query_dpr_resources(resource_kind=manpower) returns rollups.',
      };

    case 'EQUIPMENT':
      return {
        endpoint: `/v1/projects/${projectIdPlaceholder}/dpr`,
        method: 'POST',
        payloadSketch: {
          reportDate: '<YYYY-MM-DD>',
          activityId: '<activity-uuid>',
          unit: 'cum',
          qtyExecuted: 100,
          equipment: [
            { equipmentType: 'Excavator', fleetNo: 'EX-01', nos: 1, workingHours: 8, idleHours: 0.5, breakdownHours: 0, fuelLitres: 50 },
            { equipmentType: 'Dump Truck', fleetNo: 'DT-01', nos: 2, workingHours: 8, idleHours: 1, breakdownHours: 0, fuelLitres: 80 },
          ],
        },
        rationale:
          'Only 2 equipment types in DPRs (Bull Dozer, Asphalt Cutter). Add Excavator/Crane/Dump Truck DPR rows with utilization/idle/breakdown hours for the equipment KPI questions.',
      };

    case 'MATERIAL':
      return {
        endpoint: `/v1/projects/${projectIdPlaceholder}/dpr`,
        method: 'POST',
        payloadSketch: {
          reportDate: '<YYYY-MM-DD>',
          activityId: '<activity-uuid>',
          unit: 'cum',
          qtyExecuted: 20,
          materials: [
            { materialName: 'Bitumen', quantity: 5.5, unit: 'MT', source: 'Vendor A', unitRate: 45000 },
            { materialName: 'Cement', quantity: 200, unit: 'bags', source: 'Vendor B', unitRate: 350 },
          ],
        },
        rationale:
          'Zero materials in DPR child arrays. Wastage / consumption / reconciliation questions have nothing to roll up. Seed materials with a few days of consumption so analyze_material_burn_rate has signal.',
      };

    case 'ACTIVITY':
      return {
        endpoint: `/v1/activities`,
        method: 'POST',
        payloadSketch: {
          projectId: projectIdPlaceholder,
          code: 'ACT-NEW',
          name: 'Sample Activity for AI Testing',
          status: 'IN_PROGRESS',
          percentComplete: 35,
          plannedStartDate: '2026-01-01',
          plannedFinishDate: '2026-06-30',
        },
        rationale:
          'Several activity questions failed because list_activities returns sparse status/progress data. Either seed more activities with varied status/progress, or fix the activities endpoint to return percentComplete + status filters.',
      };

    case 'COST_EVM':
      // EVM is computed, not directly seedable. Most often the underlying
      // data exists but the calculator hasn't run, or the AI tool reads
      // a stale rollup.
      return null;

    case 'NEGATIVE':
      // Negative probes shouldn't have any seed remediation — refusal IS
      // the desired behavior.
      return null;
  }
  return null;
}

test.describe('AI Global — gap report', () => {
  test('Classify spec 32 turns and emit gap-report.md', async () => {
    const turnsPath = path.resolve('ai-test-artifacts/ai-global-single-chat/turns.json');
    expect(
      fs.existsSync(turnsPath),
      `Run spec 32 first. Expected ${turnsPath}.`,
    ).toBe(true);

    const turns = JSON.parse(fs.readFileSync(turnsPath, 'utf-8')) as Turn[];
    const byQid = new Map<string, Question>();
    for (const q of QUESTIONS) byQid.set(q.id, q);

    const counts: Record<Verdict, number> = { PASSED: 0, NO_DATA: 0, WRONG: 0, OTHER: 0 };
    const buckets: Record<Verdict, Array<{ turn: Turn; q: Question }>> = {
      PASSED: [],
      NO_DATA: [],
      WRONG: [],
      OTHER: [],
    };
    for (const t of turns) {
      const q = byQid.get(t.qid);
      if (!q) continue;
      const v = classify(t);
      counts[v] += 1;
      buckets[v].push({ turn: t, q });
    }

    // Group NO_DATA by question category so the seed plan reads as a
    // category-level intent rather than 50 individual line items.
    const seedsByCategory = new Map<string, { proposal: SeedProposal | null; turns: Array<{ turn: Turn; q: Question }> }>();
    for (const item of buckets.NO_DATA) {
      const cat = item.q.category;
      const existing = seedsByCategory.get(cat);
      if (existing) {
        existing.turns.push(item);
      } else {
        seedsByCategory.set(cat, { proposal: proposeSeed(item.turn, item.q), turns: [item] });
      }
    }

    const lines: string[] = [];
    lines.push('# AI Global — gap report');
    lines.push('');
    lines.push(`- **Source**: ${turnsPath}`);
    lines.push(`- **Total turns**: ${turns.length}`);
    lines.push(`- **PASSED**: ${counts.PASSED}`);
    lines.push(`- **NO_DATA** (candidates for seed-and-reask): ${counts.NO_DATA}`);
    lines.push(`- **WRONG** (AI answered substantively but failed a check): ${counts.WRONG}`);
    lines.push(`- **OTHER** (errored / empty / mixed): ${counts.OTHER}`);
    lines.push('');

    // ── NO_DATA section ────────────────────────────────────────────────
    lines.push('## NO_DATA — proposed seed actions (per category)');
    lines.push('');
    if (seedsByCategory.size === 0) {
      lines.push('_No "I don\'t have" answers detected. Nothing to seed._');
      lines.push('');
    } else {
      for (const [cat, info] of seedsByCategory) {
        lines.push(`### ${cat} — ${info.turns.length} affected turn(s)`);
        lines.push('');
        if (info.proposal) {
          lines.push(`**Proposed remediation**: \`${info.proposal.method} ${info.proposal.endpoint}\``);
          lines.push('');
          lines.push('**Rationale**: ' + info.proposal.rationale);
          lines.push('');
          lines.push('**Payload sketch**:');
          lines.push('```json');
          lines.push(JSON.stringify(info.proposal.payloadSketch, null, 2));
          lines.push('```');
        } else {
          lines.push('_No automated seed proposal — likely a code/tool gap (see "WRONG" or investigate manually)._');
        }
        lines.push('');
        lines.push('Affected questions:');
        for (const it of info.turns) {
          const oneLine = it.turn.assistantText.replace(/\s+/g, ' ').slice(0, 160);
          lines.push(`- **${it.q.id}** — ${it.q.question}`);
          lines.push(`  > ${oneLine}…`);
        }
        lines.push('');
        lines.push('---');
        lines.push('');
      }
    }

    // ── WRONG section ─────────────────────────────────────────────────
    lines.push('## WRONG — AI gave content but failed a ground-truth check');
    lines.push('');
    if (buckets.WRONG.length === 0) {
      lines.push('_No turns in this bucket._');
    } else {
      for (const it of buckets.WRONG) {
        const failed = it.turn.groundTruthChecks.filter((c) => !c.passed);
        const oneLine = it.turn.assistantText.replace(/\s+/g, ' ').slice(0, 200);
        lines.push(`### ${it.q.id} — ${it.q.category}`);
        lines.push(`**Q:** ${it.q.question}`);
        lines.push(`**Got:** ${oneLine}…`);
        lines.push('**Failed checks:**');
        for (const c of failed) lines.push(`- ${c.rule}${c.detail ? ` — ${c.detail}` : ''}`);
        lines.push('');
      }
    }
    lines.push('');

    // ── OTHER section ─────────────────────────────────────────────────
    if (buckets.OTHER.length > 0) {
      lines.push('## OTHER — errored / empty / ambiguous (manual review)');
      lines.push('');
      for (const it of buckets.OTHER) {
        lines.push(`- **${it.q.id}** — ${it.q.question}`);
        if (it.turn.error) lines.push(`  ERROR: ${it.turn.error}`);
        else lines.push(`  text: "${it.turn.assistantText.slice(0, 120)}"`);
      }
      lines.push('');
    }

    const outPath = path.resolve('ai-test-artifacts/ai-global-single-chat/gap-report.md');
    fs.writeFileSync(outPath, lines.join('\n'));
    // eslint-disable-next-line no-console
    console.log(`\nGap report written to ${outPath}\n`);
    // eslint-disable-next-line no-console
    console.log(
      `PASSED=${counts.PASSED}, NO_DATA=${counts.NO_DATA}, WRONG=${counts.WRONG}, OTHER=${counts.OTHER}`,
    );

    expect(turns.length, 'expected at least one turn from spec 32').toBeGreaterThan(0);
  });
});
