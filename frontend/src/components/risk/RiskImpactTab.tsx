"use client";

import { useState } from "react";
import type { RiskResponse, UpdateRiskRequest, RiskProbability, RiskResponseType } from "@/lib/api/riskApi";

interface Props {
  risk: RiskResponse;
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

function ragColorForScore(score: number | null | undefined, isOpportunity: boolean): string {
  if (score == null) return "text-text-muted";
  if (isOpportunity) return "text-success";
  if (score >= 20) return "text-rose-300";
  if (score >= 12) return "text-rose-400";
  if (score >= 6) return "text-amber-400";
  return "text-success";
}

export function RiskImpactTab({ risk, onUpdate }: Props) {
  const [responseDescription, setResponseDescription] = useState(risk.responseDescription || "");
  const isOpportunity = risk.riskType === "OPPORTUNITY";
  const responseOptions = RESPONSE_TYPE_OPTIONS.filter((o) =>
    o.for === "BOTH" || (isOpportunity ? o.for === "OPPORTUNITY" : o.for === "THREAT")
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
      <div className="grid grid-cols-3 gap-6">
        {/* Pre-Response Column */}
        <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
          <h3 className="text-lg font-semibold text-text-primary mb-4 pb-2 border-b border-border">
            Pre-Response
          </h3>
          <div className="space-y-4">
            <SelectField
              label="Probability"
              value={risk.probability ? String(getProbabilityValue(risk.probability)) : ""}
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
            <div className="pt-4 border-t border-border">
              <label className="block text-xs font-medium text-text-secondary uppercase tracking-wide mb-1">
                Score
              </label>
              <div className={`text-2xl font-bold ${ragColorForScore(risk.riskScore, isOpportunity)}`}>
                {risk.riskScore != null ? risk.riskScore : "—"}
              </div>
              <p className="text-[10px] text-text-muted mt-1">Auto-calculated from matrix</p>
            </div>
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
              value={risk.postResponseProbability ? String(getProbabilityValue(risk.postResponseProbability)) : ""}
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
            <div className="pt-4 border-t border-border">
              <label className="block text-xs font-medium text-text-secondary uppercase tracking-wide mb-1">
                Score
              </label>
              <div
                className={`text-2xl font-bold ${ragColorForScore(risk.postResponseRiskScore, isOpportunity)}`}
              >
                {risk.postResponseRiskScore != null ? risk.postResponseRiskScore : "—"}
              </div>
              <p className="text-[10px] text-text-muted mt-1">Auto-calculated from matrix</p>
            </div>
          </div>
        </div>
      </div>
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

function getProbabilityValue(prob: RiskProbability): number {
  const map: Record<RiskProbability, number> = {
    VERY_LOW: 1,
    LOW: 2,
    MEDIUM: 3,
    HIGH: 4,
    VERY_HIGH: 5,
  };
  return map[prob] || 0;
}
