"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Info } from "lucide-react";
import {
  riskApi,
  type RiskResponse,
  type UpdateRiskRequest,
  type RiskProbability,
  type RiskResponseType,
  type ScoringMethod,
  type RiskScoringMatrixCell,
} from "@/lib/api/riskApi";

interface Props {
  risk: RiskResponse;
  projectId: string;
  onUpdate: (data: UpdateRiskRequest) => void;
}

const PROBABILITY_OPTIONS: { value: string; label: string }[] = [
  { value: "1", label: "1 - Very Low" },
  { value: "2", label: "2 - Low" },
  { value: "3", label: "3 - Medium" },
  { value: "4", label: "4 - High" },
  { value: "5", label: "5 - Very High" },
];

const IMPACT_OPTIONS: { value: string; label: string }[] = [
  { value: "1", label: "1 - Very Low" },
  { value: "2", label: "2 - Low" },
  { value: "3", label: "3 - Medium" },
  { value: "4", label: "4 - High" },
  { value: "5", label: "5 - Very High" },
];

// PMBOK / P6 strategies, with which side of the risk axis they apply to.
type ResponseOption = { value: RiskResponseType; label: string; for: "THREAT" | "OPPORTUNITY" | "BOTH" };
const RESPONSE_TYPE_OPTIONS: ResponseOption[] = [
  { value: "AVOID", label: "Avoid", for: "THREAT" },
  { value: "MITIGATE", label: "Reduce (Mitigate)", for: "THREAT" },
  { value: "TRANSFER", label: "Transfer", for: "THREAT" },
  { value: "EXPLOIT", label: "Exploit", for: "OPPORTUNITY" },
  { value: "ENHANCE", label: "Enhance", for: "OPPORTUNITY" },
  { value: "SHARE", label: "Share", for: "OPPORTUNITY" },
  { value: "ACCEPT", label: "Accept", for: "BOTH" },
];

const METHOD_LABELS: Record<ScoringMethod, string> = {
  HIGHEST_IMPACT: "Highest Impact",
  AVERAGE_IMPACT: "Average Impact",
  AVERAGE_INDIVIDUAL: "Average Individual",
};

function ragColorForScore(score: number | null | undefined, isOpportunity: boolean): string {
  if (score == null) return "text-text-muted";
  if (isOpportunity) return "text-success";
  if (score >= 20) return "text-rose-700 dark:text-rose-300";
  if (score >= 12) return "text-rose-600 dark:text-rose-400";
  if (score >= 6) return "text-amber-600 dark:text-amber-400";
  return "text-success";
}

function probabilityValue(p: RiskProbability | null | undefined): number | null {
  if (!p) return null;
  return { VERY_LOW: 1, LOW: 2, MEDIUM: 3, HIGH: 4, VERY_HIGH: 5 }[p] ?? null;
}

/**
 * Score derivation based on the project's scoring method. Mirrors backend
 * RiskScoringMatrixService.computeCompositeScore so users see the same answer
 * before save round-trips.
 */
function deriveScore(
  method: ScoringMethod,
  probability: number | null,
  costImpact: number | null,
  scheduleImpact: number | null,
  cells: RiskScoringMatrixCell[],
): { score: number | null; explain: string } {
  if (probability == null) return { score: null, explain: "needs probability" };
  const ic = costImpact ?? 0;
  const isch = scheduleImpact ?? 0;
  const lookup = (p: number, i: number): number | null => {
    const cell = cells.find((c) => c.probabilityValue === p && c.impactValue === i);
    return cell ? cell.score : p * i; // fallback when matrix is missing
  };
  if (method === "HIGHEST_IMPACT") {
    const i = Math.max(ic, isch);
    if (i === 0) return { score: null, explain: "needs cost or schedule impact" };
    return {
      score: lookup(probability, i),
      explain: `P × max(IC, SI) = ${probability} × max(${ic}, ${isch}) = ${probability} × ${i}`,
    };
  }
  if (method === "AVERAGE_IMPACT") {
    if (ic === 0 && isch === 0) return { score: null, explain: "needs cost or schedule impact" };
    const i = Math.floor((ic + isch) / 2); // backend uses integer truncation
    return {
      score: lookup(probability, i),
      explain: `P × avg(IC, SI) = ${probability} × ⌊(${ic} + ${isch}) / 2⌋ = ${probability} × ${i}`,
    };
  }
  // AVERAGE_INDIVIDUAL: average of two matrix lookups (per dimension).
  const cs = ic > 0 ? lookup(probability, ic) : null;
  const ss = isch > 0 ? lookup(probability, isch) : null;
  if (cs == null && ss == null) return { score: null, explain: "needs cost or schedule impact" };
  if (cs == null) return { score: ss, explain: `M(P, SI) = M(${probability}, ${isch}) = ${ss}` };
  if (ss == null) return { score: cs, explain: `M(P, IC) = M(${probability}, ${ic}) = ${cs}` };
  return {
    score: Math.floor((cs + ss) / 2),
    explain: `avg(M(P, IC), M(P, SI)) = avg(${cs}, ${ss}) = ${Math.floor((cs + ss) / 2)}`,
  };
}

export function RiskImpactTab({ risk, projectId, onUpdate }: Props) {
  const [responseDescription, setResponseDescription] = useState(risk.responseDescription || "");
  const isOpportunity = risk.riskType === "OPPORTUNITY";
  const responseOptions = RESPONSE_TYPE_OPTIONS.filter((o) =>
    o.for === "BOTH" || (isOpportunity ? o.for === "OPPORTUNITY" : o.for === "THREAT")
  );

  const { data: configData } = useQuery({
    queryKey: ["risk-scoring-config", projectId],
    queryFn: () => riskApi.getScoringConfig(projectId),
  });
  const { data: matrixData } = useQuery({
    queryKey: ["risk-scoring-matrix", projectId],
    queryFn: () => riskApi.getScoringMatrix(projectId),
  });
  const method: ScoringMethod = configData?.data?.scoringMethod ?? "HIGHEST_IMPACT";
  const cells: RiskScoringMatrixCell[] = matrixData?.data ?? [];

  const preProb = probabilityValue(risk.probability);
  const postProb = probabilityValue(risk.postResponseProbability);
  const preDerivation = deriveScore(method, preProb, risk.impactCost, risk.impactSchedule, cells);
  const postDerivation = deriveScore(
    method,
    postProb,
    risk.postResponseImpactCost ?? null,
    risk.postResponseImpactSchedule ?? null,
    cells,
  );

  const handlePreResponseChange = (field: string, value: string) => {
    onUpdate({ [field]: parseInt(value, 10) } as UpdateRiskRequest);
  };

  const handlePostResponseChange = (field: string, value: string) => {
    onUpdate({ [field]: parseInt(value, 10) } as UpdateRiskRequest);
  };

  const handleResponseTypeChange = (value: string) => {
    onUpdate({ responseType: value as UpdateRiskRequest["responseType"] });
  };

  const handleResponseDescriptionSave = () => {
    onUpdate({ responseDescription });
  };

  return (
    <div className="space-y-6">
      <ScoreFormulaPanel method={method} cells={cells} />

      <div className="grid grid-cols-3 gap-6">
        {/* Pre-Response Column */}
        <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
          <h3 className="text-lg font-semibold text-text-primary mb-4 pb-2 border-b border-border">
            Pre-Response
          </h3>
          <div className="space-y-4">
            <SelectField
              label="Probability"
              value={preProb != null ? String(preProb) : ""}
              options={PROBABILITY_OPTIONS}
              onChange={(v) => handlePreResponseChange("probability", v)}
            />
            <SelectField
              label="Schedule Impact"
              value={risk.impactSchedule != null ? String(risk.impactSchedule) : ""}
              options={IMPACT_OPTIONS}
              onChange={(v) => handlePreResponseChange("impactSchedule", v)}
            />
            <SelectField
              label="Cost Impact"
              value={risk.impactCost != null ? String(risk.impactCost) : ""}
              options={IMPACT_OPTIONS}
              onChange={(v) => handlePreResponseChange("impactCost", v)}
            />
            <ScoreDisplay
              score={risk.riskScore}
              isOpportunity={isOpportunity}
              derivation={preDerivation.explain}
            />
          </div>
        </div>

        {/* Response Column */}
        <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
          <h3 className="text-lg font-semibold text-text-primary mb-4 pb-2 border-b border-border">
            Response
          </h3>
          <div className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-text-secondary uppercase tracking-wide mb-1">
                Response Type
              </label>
              <select
                className="w-full px-3 py-2 bg-surface-hover border border-border rounded text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                value={risk.responseType || ""}
                onChange={(e) => handleResponseTypeChange(e.target.value)}
              >
                <option value="">-- Select --</option>
                {responseOptions.map((opt) => (
                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                ))}
              </select>
              <p className="text-[10px] text-text-muted mt-1">
                {isOpportunity ? "Opportunity strategies (PMBOK)" : "Threat strategies (PMBOK)"}
              </p>
            </div>
            <div>
              <label className="block text-xs font-medium text-text-secondary uppercase tracking-wide mb-1">
                Response Description
              </label>
              <textarea
                className="w-full px-3 py-2 bg-surface-hover border border-border rounded text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent min-h-[120px]"
                value={responseDescription}
                onChange={(e) => setResponseDescription(e.target.value)}
                onBlur={handleResponseDescriptionSave}
                placeholder="Describe the mitigation strategy..."
              />
            </div>
          </div>
        </div>

        {/* Post-Response Column */}
        <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
          <h3 className="text-lg font-semibold text-text-primary mb-4 pb-2 border-b border-border">
            Post-Response
          </h3>
          <div className="space-y-4">
            <SelectField
              label="Probability"
              value={postProb != null ? String(postProb) : ""}
              options={PROBABILITY_OPTIONS}
              onChange={(v) => handlePostResponseChange("postResponseProbability", v)}
            />
            <SelectField
              label="Schedule Impact"
              value={risk.postResponseImpactSchedule != null ? String(risk.postResponseImpactSchedule) : ""}
              options={IMPACT_OPTIONS}
              onChange={(v) => handlePostResponseChange("postResponseImpactSchedule", v)}
            />
            <SelectField
              label="Cost Impact"
              value={risk.postResponseImpactCost != null ? String(risk.postResponseImpactCost) : ""}
              options={IMPACT_OPTIONS}
              onChange={(v) => handlePostResponseChange("postResponseImpactCost", v)}
            />
            <ScoreDisplay
              score={risk.postResponseRiskScore}
              isOpportunity={isOpportunity}
              derivation={postDerivation.explain}
            />
          </div>
        </div>
      </div>
    </div>
  );
}

function ScoreFormulaPanel({ method, cells }: { method: ScoringMethod; cells: RiskScoringMatrixCell[] }) {
  const formula =
    method === "HIGHEST_IMPACT"
      ? "Score = Matrix(Probability, max(Cost Impact, Schedule Impact))"
      : method === "AVERAGE_IMPACT"
        ? "Score = Matrix(Probability, ⌊(Cost Impact + Schedule Impact) / 2⌋)"
        : "Score = ⌊(Matrix(P, Cost Impact) + Matrix(P, Schedule Impact)) / 2⌋";
  const description =
    method === "HIGHEST_IMPACT"
      ? "Picks the worse of cost vs. schedule impact, then looks up the matrix cell. Default P6 method, conservative."
      : method === "AVERAGE_IMPACT"
        ? "Averages cost and schedule impact first, then looks up one matrix cell. Smooths over single-axis spikes."
        : "Looks up the matrix per dimension and averages the two scores. Preserves contribution of each axis to the final score.";

  return (
    <div className="rounded-xl border border-accent/30 bg-accent/5 p-4">
      <div className="flex items-start gap-3">
        <Info className="h-5 w-5 flex-shrink-0 text-accent mt-0.5" aria-hidden />
        <div className="space-y-2 text-sm">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <span className="font-semibold text-text-primary">How is the score calculated?</span>
            <span className="rounded bg-accent/20 px-1.5 py-0.5 font-mono text-[11px] text-accent">
              {METHOD_LABELS[method]}
            </span>
          </div>
          <p className="text-text-secondary">{description}</p>
          <p className="font-mono text-[12px] text-text-primary">{formula}</p>
          <ul className="ml-2 list-disc space-y-0.5 pl-4 text-[12px] text-text-secondary">
            <li>
              <span className="font-mono text-text-primary">Matrix(P, I)</span> is the cell score from this
              project&apos;s configurable Probability × Impact matrix
              {cells.length > 0 ? ` (${cells.length} cells configured)` : " (no cells configured yet — falls back to P × I)"}
              .
            </li>
            <li>
              RAG band:{" "}
              <span className="text-success">&lt; 6 Green</span> ·{" "}
              <span className="text-amber-500">6–11 Amber</span> ·{" "}
              <span className="text-rose-500">12–19 Red</span> ·{" "}
              <span className="text-fuchsia-600 dark:text-fuchsia-300">≥ 20 Crimson</span>. Opportunities are always tagged{" "}
              <span className="text-blue-600 dark:text-blue-300">Opportunity</span>.
            </li>
            <li>
              The scoring method and the matrix cells are configured per-project in{" "}
              <span className="font-mono">Admin → Risk Scoring Matrix</span>.
            </li>
          </ul>
        </div>
      </div>
    </div>
  );
}

function ScoreDisplay({
  score,
  isOpportunity,
  derivation,
}: {
  score: number | null | undefined;
  isOpportunity: boolean;
  derivation: string;
}) {
  return (
    <div className="pt-4 border-t border-border">
      <label className="block text-xs font-medium text-text-secondary uppercase tracking-wide mb-1">
        Score
      </label>
      <div className={`text-2xl font-bold ${ragColorForScore(score, isOpportunity)}`}>
        {score != null ? score : "—"}
      </div>
      <p className="mt-1 font-mono text-[10px] text-text-muted" title="How this score was derived">
        {score != null ? derivation : "Set probability + at least one impact"}
      </p>
    </div>
  );
}

function SelectField({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: { value: string; label: string }[];
  onChange: (value: string) => void;
}) {
  return (
    <div>
      <label className="block text-xs font-medium text-text-secondary uppercase tracking-wide mb-1">
        {label}
      </label>
      <select
        className="w-full px-3 py-2 bg-surface-hover border border-border rounded text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
        value={value}
        onChange={(e) => onChange(e.target.value)}
      >
        <option value="">-- Select --</option>
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>{opt.label}</option>
        ))}
      </select>
    </div>
  );
}
