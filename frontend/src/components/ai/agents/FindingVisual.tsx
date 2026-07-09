"use client";

import type { AgentFindingDto, EvidenceDto } from "@/lib/types";
import { severityMeta } from "./agentMeta";

interface Metric {
  label: string;
  num: number;
  isPct: boolean;
  raw: string;
}

/** Pull the first signed number out of an evidence value string ("53 days", "-58,000.00", "0.00", "39900%"). */
function parseMetric(ev: EvidenceDto): Metric | null {
  if (ev.type !== "METRIC" || ev.value == null) return null;
  const cleaned = String(ev.value).replace(/,/g, "");
  const m = cleaned.match(/-?\d+(\.\d+)?/);
  if (!m) return null;
  return { label: ev.label, num: parseFloat(m[0]), isPct: /%/.test(ev.value), raw: ev.value };
}

/** Compact human number: 500T, 200Q, 1.2B, 45, -58K. Keeps absurd demo magnitudes bounded. */
export function compactNum(n: number): string {
  const neg = n < 0;
  const abs = Math.abs(n);
  const units: [number, string][] = [
    [1e15, "Q"],
    [1e12, "T"],
    [1e9, "B"],
    [1e6, "M"],
    [1e3, "K"],
  ];
  let out: string;
  const u = units.find(([d]) => abs >= d);
  if (u) {
    const v = abs / u[0];
    out = (v >= 100 ? Math.round(v).toString() : v.toFixed(1).replace(/\.0$/, "")) + u[1];
  } else {
    out = Number.isInteger(abs) ? String(abs) : abs.toFixed(2).replace(/\.00$/, "");
  }
  return (neg ? "-" : "") + out;
}

const PAIRS = [
  { a: /baseline|planned|committed|budget|\bbac\b/i, b: /forecast|p80|estimate|\beac\b|projected|actual|overrun/i },
  { a: /revenue|earned|boq/i, b: /expense|spent|total cost/i },
];

function fmt(m: Metric): string {
  return compactNum(m.num) + (m.isPct ? "%" : "");
}

/** Two-bar comparison (reference vs actual), scaled to the larger magnitude. */
function BarsCompare({ a, b, hue, worse }: { a: Metric; b: Metric; hue: string; worse: boolean }) {
  const max = Math.max(Math.abs(a.num), Math.abs(b.num)) || 1;
  const rows = [
    { m: a, color: "#94a3b8", w: (Math.abs(a.num) / max) * 100 },
    { m: b, color: worse ? hue : "#2E7D5B", w: (Math.abs(b.num) / max) * 100 },
  ];
  return (
    <div className="space-y-2">
      {rows.map((r, i) => (
        <div key={i}>
          <div className="mb-0.5 flex items-baseline justify-between gap-2">
            <span className="truncate text-[11px] text-slate">{r.m.label}</span>
            <span className="font-mono text-xs font-semibold tabular-nums text-charcoal">{fmt(r.m)}</span>
          </div>
          <div className="h-2 w-full overflow-hidden rounded-full bg-ivory">
            <div
              className="h-full rounded-full transition-[width] duration-700 ease-out"
              style={{ width: `${Math.max(3, r.w)}%`, backgroundColor: r.color }}
            />
          </div>
        </div>
      ))}
    </div>
  );
}

/** Big-number stat tiles for up to 4 metrics; negatives read as burgundy, index<1 as the severity hue. */
function StatTiles({ metrics, hue }: { metrics: Metric[]; hue: string }) {
  const tiles = metrics.slice(0, 4);
  return (
    <div className={`grid gap-2 ${tiles.length >= 3 ? "grid-cols-3" : "grid-cols-" + tiles.length}`}>
      {tiles.map((m, i) => {
        const isIndex = /index|\bcpi\b|\bspi\b|margin/i.test(m.label);
        const bad = m.num < 0 || (isIndex && m.num < 1 && !/margin/i.test(m.label) ? m.num < 1 : m.num < 0);
        return (
          <div key={i} className="rounded-lg border border-hairline bg-ivory/60 px-2.5 py-2 text-center">
            <div
              className="font-display text-lg font-semibold tabular-nums leading-tight"
              style={{ color: m.num < 0 ? "#9B2C2C" : isIndex && m.num < 1 ? hue : "#1e293b" }}
            >
              {fmt(m)}
            </div>
            <div className="mt-0.5 truncate text-[10px] uppercase tracking-wide text-slate" title={m.label}>
              {m.label}
            </div>
          </div>
        );
      })}
    </div>
  );
}

/**
 * Turns a finding's numeric evidence into a compact chart — a reference-vs-actual bar pair when a
 * comparison is detectable (baseline vs forecast, budget vs estimate, revenue vs expense), otherwise
 * big-number stat tiles. Renders nothing when there are no numeric metrics.
 */
export function FindingVisual({ finding }: { finding: AgentFindingDto }) {
  const hue = severityMeta(finding.severity).hue;
  const metrics = (finding.evidence ?? []).map(parseMetric).filter((m): m is Metric => m != null);
  if (metrics.length === 0) return null;

  for (const p of PAIRS) {
    const a = metrics.find((m) => p.a.test(m.label));
    const b = metrics.find((m) => p.b.test(m.label) && m !== a);
    if (a && b) {
      const worse = Math.abs(b.num) > Math.abs(a.num) || b.num < 0;
      return (
        <div className="rounded-xl border border-hairline bg-surface/40 p-3">
          <BarsCompare a={a} b={b} hue={hue} worse={worse} />
        </div>
      );
    }
  }

  return (
    <div className="rounded-xl border border-hairline bg-surface/40 p-3">
      <StatTiles metrics={metrics} hue={hue} />
    </div>
  );
}
