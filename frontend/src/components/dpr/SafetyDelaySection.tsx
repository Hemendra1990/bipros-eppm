"use client";

import { useState } from "react";
import { ChevronDown, ChevronRight, ShieldAlert } from "lucide-react";
import type { SafetyIncidentType } from "@/lib/types/dpr";

interface Props {
  delayReason: string | null | undefined;
  safetyObservation: string | null | undefined;
  safetyIncidentType: SafetyIncidentType | null | undefined;
  onChange: (
    patch: Partial<{
      delayReason: string | null;
      safetyObservation: string | null;
      safetyIncidentType: SafetyIncidentType | null;
    }>
  ) => void;
}

const INCIDENT_OPTIONS: Array<{ value: SafetyIncidentType; label: string }> = [
  { value: "NONE", label: "None" },
  { value: "NEAR_MISS", label: "Near miss" },
  { value: "INCIDENT", label: "Incident / Accident" },
];

export function SafetyDelaySection({
  delayReason,
  safetyObservation,
  safetyIncidentType,
  onChange,
}: Props) {
  const [open, setOpen] = useState(
    Boolean(delayReason || safetyObservation || (safetyIncidentType && safetyIncidentType !== "NONE"))
  );

  return (
    <div className="rounded-lg border border-hairline bg-paper">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center justify-between px-4 py-2.5 text-left"
      >
        <div className="flex items-center gap-2 text-sm font-semibold text-charcoal">
          <ShieldAlert className="h-4 w-4 text-bronze-warn" />
          Safety & Delay
          {(safetyIncidentType && safetyIncidentType !== "NONE") || delayReason ? (
            <span className="rounded-md bg-burgundy/10 px-2 py-0.5 text-[10px] font-bold uppercase text-burgundy">
              flagged
            </span>
          ) : null}
        </div>
        {open ? (
          <ChevronDown className="h-4 w-4 text-slate" />
        ) : (
          <ChevronRight className="h-4 w-4 text-slate" />
        )}
      </button>
      {open && (
        <div className="grid gap-4 border-t border-hairline px-4 py-4 md:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate">
              Delay reason
            </label>
            <textarea
              value={delayReason ?? ""}
              onChange={(e) => onChange({ delayReason: e.target.value || null })}
              rows={3}
              placeholder="Rain stoppage, machine breakdown, permit delay…"
              className="w-full rounded-md border border-hairline bg-paper px-3 py-2 text-sm text-charcoal focus:border-gold focus:outline-none focus:ring-1 focus:ring-gold/40"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate">
              Incident type
            </label>
            <select
              value={safetyIncidentType ?? "NONE"}
              onChange={(e) =>
                onChange({ safetyIncidentType: e.target.value as SafetyIncidentType })
              }
              className="w-full rounded-md border border-hairline bg-paper px-3 py-2 text-sm text-charcoal focus:border-gold focus:outline-none focus:ring-1 focus:ring-gold/40"
            >
              {INCIDENT_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>
          <div className="md:col-span-2">
            <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate">
              Safety observation
            </label>
            <textarea
              value={safetyObservation ?? ""}
              onChange={(e) => onChange({ safetyObservation: e.target.value || null })}
              rows={2}
              placeholder="Toolbox talk topic, PPE check, hazards observed…"
              className="w-full rounded-md border border-hairline bg-paper px-3 py-2 text-sm text-charcoal focus:border-gold focus:outline-none focus:ring-1 focus:ring-gold/40"
            />
          </div>
        </div>
      )}
    </div>
  );
}
