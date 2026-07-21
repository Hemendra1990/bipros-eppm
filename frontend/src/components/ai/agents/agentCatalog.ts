// Presentation catalog for the always-on "Agent Coverage" cards.
//
// Each agent already computes a canonical `dataSnapshot` in its backend gather()
// (via the same services the tabs use). This file only decides, per agent, WHICH
// snapshot numbers to surface (healthy value), what to say when the register is
// empty (no-data reason + suggestion), and where to link. No calculation happens
// here — the numbers come straight from the snapshot. Source of truth for the
// per-agent mapping: docs/superpowers/specs/2026-07-14-ai-agent-always-on-coverage-cards-design.md §7.

export type CoverageStatus =
  | "ATTENTION" | "HEALTHY" | "NO_DATA" | "NOT_CONFIGURED" | "DORMANT" | "INFRA";

export type AgentArea =
  | "Financial" | "Schedule" | "Resource" | "Site & Quality" | "Risk & Compliance" | "Executive";

export interface MetricTile { label: string; value: string }

type Snap = Record<string, unknown>;
type Money = (n: number) => string;

export interface AgentCatalogEntry {
  kind: "domain" | "meta" | "infra";
  area: AgentArea;
  metrics: (s: Snap, money: Money) => MetricTile[];
  healthy: (s: Snap, money: Money) => string;
  gap: { reason: string; suggestion: string };
  route: (projectId: string) => string;
  // True when the agent's snapshot shows NO source data to analyse (zero documents, zero
  // DPRs, zero risks, …). Every agent still writes a snapshot with zero-valued keys when its
  // register is empty, so "snapshot has keys" is NOT enough to call it healthy — this predicate
  // reads the agent's own source-count field. It MUST stay false for a real-but-unhealthy state
  // (a loss-making P&L, SPI 0.24, zero margin) — that is real analysis, not missing data.
  isEmpty?: (s: Snap) => boolean;
}

// --- snapshot read helpers -------------------------------------------------
// `n` reads a number by key. It tolerates both a flat key ("spi") and a dotted
// path into a nested object ("health.healthScore", "evm.eac") — trying the
// literal key first, then walking the path — so it works regardless of whether
// the backend serialised the snapshot flat or nested.
const asNum = (v: unknown): number | null =>
  typeof v === "number" && Number.isFinite(v) ? v : null;

const n = (s: Snap, key: string): number | null => {
  if (s && key in s) {
    const direct = asNum(s[key]);
    if (direct !== null) return direct;
  }
  let cur: unknown = s;
  for (const part of key.split(".")) {
    if (cur && typeof cur === "object") cur = (cur as Record<string, unknown>)[part];
    else return null;
  }
  return asNum(cur);
};

// Length of a JSON array field (roles, supervisors, risks) — 0 when absent or not an array.
const arrLen = (s: Snap, key: string): number => {
  const v = s?.[key];
  return Array.isArray(v) ? v.length : 0;
};

const pct = (v: number | null, dp = 0): string => (v == null ? "—" : `${v.toFixed(dp)}%`);
const num = (v: number | null): string => (v == null ? "—" : String(v));
const r2 = (v: number | null): string => (v == null ? "—" : v.toFixed(2));

// DBS margin is derived from the two money fields to avoid a ratio-vs-percent
// ambiguity in cumulativeContributionPct.
const dbsMargin = (s: Snap): number | null => {
  const contrib = n(s, "cumulativeContribution");
  const inc = n(s, "cumulativeIncome");
  if (contrib != null && inc != null && inc !== 0) return (contrib / inc) * 100;
  return n(s, "cumulativeContributionPct");
};

export function deriveCoverageStatus(
  entry: AgentCatalogEntry,
  snapshot: Snap | null | undefined,
  activeCount: number,
): CoverageStatus {
  if (entry.kind === "infra") return "INFRA";
  if (activeCount > 0) return "ATTENTION";
  const empty = !snapshot || Object.keys(snapshot).length === 0;
  if (empty) return entry.kind === "meta" ? "DORMANT" : "NO_DATA";
  if ("skipped" in snapshot) return "NOT_CONFIGURED";
  // The snapshot has keys but they're all zero-valued because the agent's source register is
  // empty — treat that as no data (per-agent predicate reads the true source-count field).
  if (entry.isEmpty?.(snapshot)) return entry.kind === "meta" ? "DORMANT" : "NO_DATA";
  if (entry.kind === "meta") {
    const c = n(snapshot, "count") ?? n(snapshot, "activeFindings") ?? 0;
    if (!c) return "DORMANT";
  }
  return "HEALTHY";
}

export const AGENT_CATALOG: Record<string, AgentCatalogEntry> = {
  // ---------- Financial ----------
  dbs_validation: {
    kind: "domain", area: "Financial",
    isEmpty: (s) => n(s, "cumulativeIncome") === null && n(s, "cumulativeExpense") === null,
    metrics: (s, m) => [
      { label: "Contribution", value: n(s, "cumulativeContribution") != null ? m(n(s, "cumulativeContribution")!) : "—" },
      { label: "Margin", value: pct(dbsMargin(s), 1) },
    ],
    healthy: (s, m) =>
      `P&L healthy — contribution ${n(s, "cumulativeContribution") != null ? m(n(s, "cumulativeContribution")!) : "—"}, margin ${pct(dbsMargin(s), 1)}.`,
    gap: {
      reason: "No approved DPR revenue/expense has rolled into the DBS yet.",
      suggestion: "File and approve Daily Progress Reports so the P&L accrues.",
    },
    route: (p) => `/projects/${p}/dbs`,
  },
  labour_cost_intelligence: {
    kind: "domain", area: "Financial",
    isEmpty: (s) => (n(s, "alc") ?? 0) <= 0,
    metrics: (s, m) => [
      { label: "LCPI", value: r2(n(s, "lcpi")) },
      { label: "Planned", value: n(s, "plc") != null ? m(n(s, "plc")!) : "—" },
      { label: "Actual", value: n(s, "alc") != null ? m(n(s, "alc")!) : "—" },
    ],
    healthy: (s) =>
      `Labour on budget — LCPI ${r2(n(s, "lcpi"))}, ${num(n(s, "unitCostOutliers"))} of ${num(n(s, "unitCostActivities"))} activities over unit-cost.`,
    gap: {
      reason: "No manpower actual cost is booked on any approved DPR.",
      suggestion: "Approve DPRs recording manpower cost and add manpower assignments to the Resource Plan.",
    },
    route: (p) => `/projects/${p}/daily-cost-report`,
  },
  subcontractor_performance: {
    kind: "domain", area: "Financial",
    isEmpty: (s) => (n(s, "subContractorCount") ?? 0) === 0,
    metrics: (s, m) => [
      { label: "SC LCPI", value: r2(n(s, "scLcpi")) },
      { label: "Planned", value: n(s, "plannedCost") != null ? m(n(s, "plannedCost")!) : "—" },
      { label: "Actual", value: n(s, "actualCost") != null ? m(n(s, "actualCost")!) : "—" },
    ],
    healthy: (s) => `Sub-contract on budget — LCPI ${r2(n(s, "scLcpi"))}.`,
    gap: {
      reason: "No sub-contractor assignments, or no approved SC workdone yet.",
      suggestion: "Add a sub-contractor assignment on an activity and approve its DPR quantities.",
    },
    route: (p) => `/projects/${p}/dbs`,
  },
  material_intelligence: {
    kind: "domain", area: "Financial",
    isEmpty: (s) => (n(s, "issuedQty") ?? 0) <= 0 && (n(s, "consumedQty") ?? 0) <= 0,
    metrics: (s) => [
      { label: "Utilisation", value: pct(n(s, "materialUtilizationPct") != null ? n(s, "materialUtilizationPct")! * 100 : null) },
      { label: "Wastage", value: pct(n(s, "wastagePct") != null ? n(s, "wastagePct")! * 100 : null) },
    ],
    healthy: (s) =>
      `Material healthy — utilisation ${pct(n(s, "materialUtilizationPct") != null ? n(s, "materialUtilizationPct")! * 100 : null)}, wastage ${pct(n(s, "wastagePct") != null ? n(s, "wastagePct")! * 100 : null)}.`,
    gap: {
      reason: "No material ledger yet — no store issues or consumption logs.",
      suggestion: "Add store issues and daily consumption on the Material Consumptions tab.",
    },
    route: (p) => `/projects/${p}/material-consumption`,
  },

  // ---------- Schedule ----------
  progress_variance: {
    kind: "domain", area: "Schedule",
    isEmpty: (s) => (n(s, "bac") ?? 0) === 0,
    metrics: (s) => [
      { label: "SPI", value: r2(n(s, "spi")) },
      { label: "Earned", value: pct(n(s, "earnedPctOfBudget"), 0) },
      { label: "Planned", value: pct(n(s, "plannedPctOfBudget"), 0) },
    ],
    healthy: (s) =>
      `On schedule — SPI ${r2(n(s, "spi"))} (${pct(n(s, "earnedPctOfBudget"))} earned vs ${pct(n(s, "plannedPctOfBudget"))} planned); ${num(n(s, "activitiesDelayed"))} delayed, ${num(n(s, "milestonesAtRisk"))} milestones at risk.`,
    gap: {
      reason: "No earned-value baseline — no BAC, BOQ execution, or planned window.",
      suggestion: "Set the budget (BAC), record BOQ execution via approved DPRs, and confirm planned dates.",
    },
    route: (p) => `/projects/${p}/evm`,
  },
  planning_intelligence: {
    kind: "domain", area: "Schedule",
    isEmpty: (s) =>
      (n(s, "health.totalActivities") ?? 0) === 0 && (n(s, "baseline.comparable") ?? 0) === 0,
    metrics: (s) => [
      { label: "Health", value: n(s, "health.healthScore") != null ? `${Math.round(n(s, "health.healthScore")!)}/100` : "—" },
      { label: "Deadline slip", value: n(s, "health.deadlineSlipDays") != null ? `${num(n(s, "health.deadlineSlipDays"))}d` : "—" },
    ],
    healthy: (s) =>
      `Schedule healthy — health ${n(s, "health.healthScore") != null ? Math.round(n(s, "health.healthScore")!) + "/100" : "—"}, ${num(n(s, "health.deadlineSlipDays"))}-day slip.`,
    gap: {
      reason: "No schedule-health index computed (CPM not run), or no primary baseline.",
      suggestion: "Run the CPM scheduler on the Schedule tab and capture a primary baseline.",
    },
    route: (p) => `/projects/${p}/activities`,
  },
  baseline_intelligence: {
    kind: "domain", area: "Schedule",
    isEmpty: (s) =>
      s.scheduleRun !== true &&
      (n(s, "openEnded") ?? 0) === 0 &&
      (n(s, "negativeFloat") ?? 0) === 0 &&
      (n(s, "duplicateNames") ?? 0) === 0 &&
      (n(s, "milestones") ?? 0) === 0 &&
      (n(s, "logicViolations") ?? 0) === 0,
    metrics: (s) => [
      { label: "Readiness", value: n(s, "healthScore") != null && n(s, "healthScore")! >= 0 ? `${Math.round(n(s, "healthScore")!)}/100` : "—" },
      { label: "Open-ended", value: num(n(s, "openEnded")) },
      { label: "Neg. float", value: num(n(s, "negativeFloat")) },
    ],
    healthy: (s) =>
      `Baseline ready — ${n(s, "healthScore") != null && n(s, "healthScore")! >= 0 ? Math.round(n(s, "healthScore")!) + "/100" : "—"}, schedule clean (${num(n(s, "openEnded"))} open-ended, ${num(n(s, "negativeFloat"))} neg-float).`,
    gap: {
      reason: "No schedule or baseline captured for this project.",
      suggestion: "Capture a primary baseline and run the CPM scheduler.",
    },
    route: (p) => `/projects/${p}/baselines`,
  },
  forecasting: {
    kind: "domain", area: "Schedule",
    isEmpty: (s) =>
      n(s, "monteCarloSchedule.baselineDuration") === null &&
      n(s, "monteCarloCost.p80Cost") === null &&
      n(s, "evm.bac") === null,
    metrics: (s, m) => [
      { label: "EAC", value: n(s, "evm.eac") != null ? m(n(s, "evm.eac")!) : "—" },
      { label: "BAC", value: n(s, "evm.bac") != null ? m(n(s, "evm.bac")!) : "—" },
      { label: "CPI", value: r2(n(s, "evm.cpi")) },
    ],
    healthy: (s, m) =>
      `Forecast healthy — EAC ${n(s, "evm.eac") != null ? m(n(s, "evm.eac")!) : "—"} vs BAC ${n(s, "evm.bac") != null ? m(n(s, "evm.bac")!) : "—"} (CPI ${r2(n(s, "evm.cpi"))}).`,
    gap: {
      reason: "No completed Monte Carlo run and no cost baseline (BAC).",
      suggestion: "Run a simulation on Risk Analysis and/or set the project budget.",
    },
    route: (p) => `/projects/${p}/risk-analysis`,
  },

  // ---------- Resource ----------
  capacity_utilisation: {
    kind: "domain", area: "Resource",
    isEmpty: (s) => arrLen(s, "roles") === 0,
    metrics: (s) => [
      { label: "Manpower eff.", value: pct(n(s, "manpowerEfficiencyPct")) },
      { label: "Equipment eff.", value: pct(n(s, "equipmentEfficiencyPct")) },
    ],
    healthy: (s) =>
      `Capacity healthy — manpower ${pct(n(s, "manpowerEfficiencyPct"))} of norm, equipment ${pct(n(s, "equipmentEfficiencyPct"))}.`,
    gap: {
      reason: "No approved DPR output matched to a productivity norm yet.",
      suggestion: "Approve DPRs with manpower/equipment nos against activities that have norms, and maintain a resource plan.",
    },
    route: (p) => `/projects/${p}/capacity-utilization`,
  },
  field_utilisation: {
    kind: "domain", area: "Resource",
    isEmpty: (s) => (n(s, "activitiesWithDeployment") ?? 0) === 0,
    metrics: () => [],
    healthy: () => `Field deployment within range over the last 14 days.`,
    gap: {
      reason: "No resource-deployment rows (DPR Section B) recorded.",
      suggestion: "Record daily resource deployment (nos planned/deployed, hours, idle) in DPR Section B.",
    },
    route: (p) => `/projects/${p}/dpr`,
  },
  productivity_analysis: {
    kind: "domain", area: "Resource",
    isEmpty: (s) => (n(s, "activitiesWithNorm") ?? 0) === 0,
    metrics: (s) => [
      { label: "Below norm", value: num(n(s, "belowNorm")) },
      { label: "Compared", value: num(n(s, "activitiesWithNorm")) },
    ],
    healthy: (s) =>
      `Productivity on track — ${num(n(s, "atOrAboveNorm"))} of ${num(n(s, "activitiesWithNorm"))} activities at/above norm (${num(n(s, "belowNorm"))} below).`,
    gap: {
      reason: "No activity could be matched to a productivity norm.",
      suggestion: "Maintain the Productivity Norms master and align DPR activity names/units, then log ≥3 days of DPRs.",
    },
    route: (p) => `/projects/${p}/dpr`,
  },
  supervisor_performance: {
    kind: "domain", area: "Resource",
    isEmpty: (s) => arrLen(s, "supervisors") === 0,
    metrics: (s) => [
      { label: "Supervisors", value: num(n(s, "supervisorCount")) },
      { label: "Median progress", value: pct(n(s, "medianAvgPercentComplete")) },
    ],
    healthy: (s) =>
      `${num(n(s, "supervisorCount"))} supervisors compared — median progress ${pct(n(s, "medianAvgPercentComplete"))}.`,
    gap: {
      reason: "Fewer than 2 supervisors have enough DPR history to compare.",
      suggestion: "Capture DPRs with the supervisor + activity + quantity, and get them approved.",
    },
    route: (p) => `/projects/${p}/dpr`,
  },

  // ---------- Site & Quality ----------
  dpr_intelligence: {
    kind: "domain", area: "Site & Quality",
    isEmpty: (s) => (n(s, "submittedReportCount") ?? 0) === 0,
    metrics: (s) => [
      { label: "Report gap", value: n(s, "reportGapDays") != null ? `${num(n(s, "reportGapDays"))}d` : "—" },
      { label: "Awaiting approval", value: num(n(s, "stuckAwaitingApproval")) },
    ],
    healthy: (s) =>
      `Reporting current — last DPR ${num(n(s, "reportGapDays"))} days ago, ${num(n(s, "stuckAwaitingApproval"))} awaiting approval.`,
    gap: {
      reason: "No submitted/approved Daily Progress Reports yet.",
      suggestion: "File and approve daily reports.",
    },
    route: (p) => `/projects/${p}/dpr`,
  },
  dpr_anomaly: {
    kind: "domain", area: "Site & Quality",
    isEmpty: (s) => (n(s, "dprsScanned") ?? 0) === 0,
    metrics: (s) => [
      { label: "Reports scanned", value: num(n(s, "dprsScanned")) },
      {
        label: "Anomalies",
        value: num(
          (n(s, "noProgressHighLabour") ?? 0) +
            (n(s, "lowOutputHighEquipment") ?? 0) +
            (n(s, "productivityDrops") ?? 0) +
            (n(s, "duplicateGroups") ?? 0) +
            (n(s, "dataInconsistencies") ?? 0),
        ),
      },
    ],
    healthy: (s) => `DPR data clean — ${num(n(s, "dprsScanned"))} reports scanned, 0 anomalies.`,
    gap: {
      reason: "No approved DPR history to analyse.",
      suggestion: "Submit and approve Daily Progress Reports.",
    },
    route: (p) => `/projects/${p}/dpr`,
  },
  root_cause: {
    kind: "domain", area: "Site & Quality",
    isEmpty: (s) => (n(s, "delayReasonsCategorised") ?? 0) === 0,
    metrics: (s) => [{ label: "Delay reasons", value: num(n(s, "delayReasonsCategorised")) }],
    healthy: () => `No recurring delay cause detected.`,
    gap: {
      reason: "DPRs have no Delay Reason logged (or none matches a known cause).",
      suggestion: "Fill the Delay Reason field on DPRs with cause wording (material / weather / breakdown / manpower / design / approval / access).",
    },
    route: (p) => `/projects/${p}/dpr`,
  },
  issue_intelligence: {
    kind: "domain", area: "Site & Quality",
    isEmpty: (s) =>
      (n(s, "dprOpen") ?? 0) === 0 &&
      (n(s, "ncrOpen") ?? 0) === 0 &&
      (n(s, "snagOpen") ?? 0) === 0 &&
      (n(s, "safetyOpen") ?? 0) === 0,
    metrics: (s) => [
      { label: "Open HSE", value: num(n(s, "hseOpen")) },
      { label: "Aged NCR", value: num(n(s, "agedNcr")) },
      { label: "Aged snag", value: num(n(s, "agedSnag")) },
    ],
    healthy: (s) =>
      `Issue backlog healthy — ${num(n(s, "hseOpen"))} open HSE, ${num(n(s, "agedNcr"))} NCRs past 7d, ${num(n(s, "agedSnag"))} snags past 14d.`,
    gap: {
      reason: "Issue, NCR, snag and safety logs are empty.",
      suggestion: "Log field issues on the DPR, NCRs & snags on Quality, and safety events on HSE.",
    },
    route: (p) => `/projects/${p}/issues`,
  },
  weather_risk: {
    kind: "domain", area: "Site & Quality",
    metrics: () => [],
    healthy: () => `No adverse weather in the 7-day forecast.`,
    gap: {
      reason: "Site location is not set, or weather monitoring is off.",
      suggestion: "Set the project's site location and enable weather monitoring on the Overview page.",
    },
    route: (p) => `/projects/${p}`,
  },
  gis_intelligence: {
    kind: "domain", area: "Site & Quality",
    metrics: () => [],
    healthy: () => `Field progress verified against contractor claims.`,
    gap: {
      reason: "No WBS polygons drawn or no satellite progress snapshots ingested.",
      suggestion: "Draw a WBS polygon on the GIS Viewer and ingest a progress snapshot (satellite % + claimed %).",
    },
    route: (p) => `/projects/${p}/gis-viewer`,
  },

  // ---------- Risk & Compliance ----------
  risk_intelligence: {
    kind: "domain", area: "Risk & Compliance",
    isEmpty: (s) => (n(s, "openRiskCount") ?? 0) === 0,
    metrics: (s, m) => [
      { label: "Open risks", value: num(n(s, "openCount")) },
      { label: "EMV", value: n(s, "emv") != null ? m(n(s, "emv")!) : "—" },
    ],
    healthy: (s, m) =>
      `Risk register healthy — ${num(n(s, "openCount"))} open risks, EMV ${n(s, "emv") != null ? m(n(s, "emv")!) : "—"}, none worsening.`,
    gap: {
      reason: "No risks in the register.",
      suggestion: "Add risks with probability + impact; assign activities so EMV can be derived.",
    },
    route: (p) => `/projects/${p}/risks`,
  },
  document_intelligence: {
    kind: "domain", area: "Risk & Compliance",
    isEmpty: (s) => (n(s, "totalDocuments") ?? 0) === 0 && (n(s, "expiringPermits") ?? 0) === 0,
    metrics: (s) => [
      { label: "Documents", value: num(n(s, "totalDocuments")) },
      { label: "Permits expiring", value: num(n(s, "expiringPermits")) },
    ],
    healthy: (s) =>
      `Docs healthy — ${num(n(s, "totalDocuments"))} documents, no permits expiring in 7 days.`,
    gap: {
      reason: "Document and permit registers are empty.",
      suggestion: "Upload documents (set the type) and create permit-to-work records.",
    },
    route: (p) => `/projects/${p}/documents`,
  },

  // ---------- Executive (meta) ----------
  executive_insights: {
    kind: "meta", area: "Executive",
    metrics: (s) => [{ label: "Active concerns", value: num(n(s, "count")) }],
    healthy: (s) => `${num(n(s, "count"))} active concerns across all agents.`,
    gap: {
      reason: "No MEDIUM+ findings to summarise — all agents currently clear.",
      suggestion: "Concerns appear here automatically when other agents raise findings.",
    },
    route: (p) => `/projects/${p}/ai`,
  },
  role_briefings: {
    kind: "meta", area: "Executive",
    metrics: (s) => [{ label: "Findings digested", value: num(n(s, "activeFindings")) }],
    healthy: (s) => `${num(n(s, "activeFindings"))} findings digested into role briefs.`,
    gap: {
      reason: "No active findings to brief yet.",
      suggestion: "Briefs appear once the upstream agents produce findings.",
    },
    route: (p) => `/projects/${p}/ai`,
  },
  historical_learning: {
    kind: "meta", area: "Executive",
    metrics: () => [],
    healthy: () => `Cross-project precedents available.`,
    gap: {
      reason: "No cross-project precedents yet.",
      suggestion: "A precedent appears once a matching issue is resolved on another project.",
    },
    route: (p) => `/projects/${p}/ai`,
  },

  // ---------- Infrastructure ----------
  notification: {
    kind: "infra", area: "Executive",
    metrics: () => [],
    healthy: () => `Routes findings to in-app / email / SMS.`,
    gap: { reason: "Delivery router — it never produces its own card.", suggestion: "" },
    route: (p) => `/projects/${p}/ai`,
  },
};

const FALLBACK: AgentCatalogEntry = {
  kind: "domain",
  area: "Executive",
  metrics: () => [],
  healthy: () => "No issues reported.",
  gap: {
    reason: "This agent has no data to analyse yet.",
    suggestion: "Maintain the data this agent reads, then run a sweep.",
  },
  route: (p) => `/projects/${p}/ai`,
};

export const catalogFor = (key: string): AgentCatalogEntry => AGENT_CATALOG[key] ?? FALLBACK;
