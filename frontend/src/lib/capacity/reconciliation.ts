// Display helpers for the Capacity Utilization "counted-first" rendering.
//
// PURE — no React, no styling. Each capacity-util surface renders these strings/numbers with its
// own markup so every screen reads identically. See the design preview at
// docs/superpowers/specs/2026-06-22-capacity-util-counted-clarity-before-after.html.
//
// Vocabulary (relabels existing fields — changes NO calculation):
//   counted             = resource-days measured against this trade's productivity norm here.
//                         This is the number Efficiency % and Cost divide by.
//   measured elsewhere  = days on an activity the OTHER side led (SERIES/SUBSTITUTE) — the role's
//                         output is measured in that other section, not counted here.
//   no norm             = days on an activity with no productivity norm for this role.
//   deployed            = total days on site = counted + measured-elsewhere + no-norm.
//
// Backend already returns every number; counted is just deployed minus the two excluded buckets.

export type CapacitySide = "MANPOWER" | "EQUIPMENT";

// ── Temporary visibility flags (hide-don't-remove) ──────────────────────────────────────────
// The client found the dual-norm "measured under the other side" vocabulary confusing on the
// Capacity Utilization screen: on a Manpower row, "+1 measured under Equipment" reads to them as
// an equipment operator being double-counted, when it actually means the day was attributed to
// the Equipment section because Equipment led that activity (SERIES/SUBSTITUTE). They don't
// configure both a manpower AND an equipment norm on the same work activity, so this
// cross-measurement never applies for them. Both surfaces below are HIDDEN for now while the
// producing code is left fully intact — flip a flag to `true` to bring it back if dual-norm
// activities are ever used.
//
//   SHOW_RECONCILIATION_LINE → the per-row "X counted + Y measured under Z = N deployed" line.
//   SHOW_HIDDEN_SIDE_NOTES   → the per-section "<Activity> (SERIES): <side> led this activity…"
//                              note boxes at the bottom of each Manpower/Equipment table.
export const SHOW_RECONCILIATION_LINE = false;
export const SHOW_HIDDEN_SIDE_NOTES = false;

function fmt(n: number | null | undefined, digits = 1): string {
  if (n === null || n === undefined) return "—";
  return n.toLocaleString("en-IN", { maximumFractionDigits: digits });
}

/** Days that drive Efficiency % and Cost: deployed − measured-elsewhere − no-norm. */
export function countedDays(
  deployed: number | null | undefined,
  measuredElsewhere: number | null | undefined,
  noNorm: number | null | undefined,
): number {
  return (deployed ?? 0) - (measuredElsewhere ?? 0) - (noNorm ?? 0);
}

function otherSideName(side: CapacitySide | undefined): string | null {
  if (side === "MANPOWER") return "Equipment";
  if (side === "EQUIPMENT") return "Manpower";
  return null;
}

/**
 * Self-reconciling identity line, e.g.
 *   "118 counted + 73 measured under Equipment + 23 no norm = 191 deployed".
 * Returns null when counted == deployed (nothing measured elsewhere, no no-norm days) — the
 * single headline number is already unambiguous, so no line is drawn.
 */
export function reconciliationText(
  deployed: number | null | undefined,
  measuredElsewhere: number | null | undefined,
  noNorm: number | null | undefined,
  side?: CapacitySide,
): string | null {
  if (!SHOW_RECONCILIATION_LINE) return null;
  const total = deployed ?? 0;
  if (total <= 0) return null;
  const elsewhere = measuredElsewhere ?? 0;
  const noNormDays = noNorm ?? 0;
  if (elsewhere <= 0 && noNormDays <= 0) return null;
  const counted = total - elsewhere - noNormDays;
  const other = otherSideName(side);
  const elsewhereLabel = other ? `measured under ${other}` : "measured elsewhere";
  const parts = [`${fmt(counted)} counted`];
  if (elsewhere > 0) parts.push(`${fmt(elsewhere)} ${elsewhereLabel}`);
  if (noNormDays > 0) parts.push(`${fmt(noNormDays)} no norm`);
  return `${parts.join(" + ")} = ${fmt(total)} deployed`;
}

/**
 * The efficiency division shown next to the % — e.g. "39.63 ÷ 118". Null when not computable.
 * Callers pass the precision of their surrounding Budget / counted cells so the printed formula
 * matches the numbers on the row exactly.
 */
export function efficiencyFormula(
  budgetDays: number | null | undefined,
  counted: number | null | undefined,
  budgetDigits = 1,
  countedDigits = 1,
): string | null {
  if (budgetDays == null || counted == null || counted <= 0) return null;
  return `${fmt(budgetDays, budgetDigits)} ÷ ${fmt(counted, countedDigits)}`;
}

/**
 * Reworded hidden-side banner sentence (the part rendered after the activity name). Replaces the
 * old "… governs this activity. … count toward Actual but are excluded from this section's
 * Efficiency …" wording. `governingSide` is the side that LED (won the SERIES/SUBSTITUTE).
 */
export function hiddenSideSentence(mode: string, governingSide: CapacitySide): string {
  const governing = governingSide === "MANPOWER" ? "Manpower" : "Equipment";
  const thisSide = governingSide === "MANPOWER" ? "Equipment" : "Manpower";
  return ` (${mode}): ${governing} led this activity, so the ${thisSide} deployments here are measured under ${governing} Utilization (not counted in this section's efficiency here).`;
}

export const COUNTED_TOOLTIP =
  "Counted = resource-days measured against this trade's productivity norm here — the number Efficiency % and Cost divide by. The rest were measured under the other side (days on an activity the other side led) or had no norm set. Counted + measured elsewhere + no norm = deployed.";
