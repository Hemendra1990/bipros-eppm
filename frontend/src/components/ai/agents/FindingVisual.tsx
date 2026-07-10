"use client";

import type { AgentFindingDto, EvidenceDto } from "@/lib/types";
import { severityMeta } from "./agentMeta";
import { FindingSeriesChart } from "@/components/ai/charts/FindingSeriesChart";

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

/** #rrggbb → rgba() at alpha, so a severity hue can tint over either theme's surface. */
function hexA(hex: string, alpha: number): string {
  const h = hex.replace("#", "");
  const n = parseInt(h.length === 3 ? h.split("").map((x) => x + x).join("") : h, 16);
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`;
}

const PAIRS = [
  { a: /baseline|planned|committed|budget|\bbac\b/i, b: /forecast|p80|estimate|\beac\b|projected|actual|overrun/i },
  { a: /revenue|earned|boq/i, b: /expense|spent|total cost/i },
];

/** True when a metric is a variance/impact figure the reader should notice first. */
const HERO_RE = /overrun|impact|variance|slip|delay|shortfall|gap|exceed|over budget/i;
const INDEX_RE = /index|\bcpi\b|\bspi\b|margin/i;

function fmt(m: Metric): string {
  return compactNum(m.num) + (m.isPct ? "%" : "");
}

/**
 * Bullet comparison: a reference (target/baseline) marker with the actual value filled to it, and any
 * amount past the marker drawn in the severity hue — so "18% over budget" reads as a red overshoot,
 * not two abstract bars. Under-target reads as an emerald bar with no overshoot.
 */
function BulletCompare({ base, act, hue }: { base: Metric; act: Metric; hue: string }) {
  const refV = Math.abs(base.num);
  const actV = Math.abs(act.num);
  const max = Math.max(refV, actV) * 1.08 || 1;
  const over = actV > refV;
  const basePct = (Math.min(actV, refV) / max) * 100;
  const overPct = over ? ((actV - refV) / max) * 100 : 0;
  const markPct = (refV / max) * 100;
  const delta = refV ? Math.round(((act.num - base.num) / refV) * 100) : 0;
  const deltaColor = over ? hue : "var(--emerald)";

  return (
    <div className="rounded-xl border border-hairline bg-surface/40 p-3">
      <div className="mb-1.5 flex items-baseline justify-between gap-2">
        <span className="truncate text-[11px] text-slate">
          {act.label} <span className="text-text-muted">vs {base.label}</span>
        </span>
        <span className="shrink-0 font-mono text-xs font-semibold tabular-nums" style={{ color: deltaColor }}>
          {delta > 0 ? "+" : ""}
          {delta}%
        </span>
      </div>
      <div className="relative h-3.5 w-full overflow-hidden rounded-full bg-ivory">
        <div
          className="absolute inset-y-0 left-0 rounded-l-full transition-[width] duration-700 ease-out"
          style={{ width: `${Math.max(2, basePct)}%`, backgroundColor: over ? "#94a3b8" : "var(--emerald)" }}
        />
        {over && (
          <div
            className="absolute inset-y-0 transition-[width] duration-700 ease-out"
            style={{ left: `${basePct}%`, width: `${Math.max(2, overPct)}%`, backgroundColor: hue }}
          />
        )}
        <span
          className="absolute inset-y-[-2px] w-px bg-charcoal"
          style={{ left: `${markPct}%` }}
          aria-hidden
        />
      </div>
      <div className="mt-1 flex items-baseline justify-between font-mono text-[10px] tabular-nums text-slate">
        <span>
          {fmt(base)} <span className="text-text-muted">{base.label}</span>
        </span>
        <span className="font-semibold text-charcoal">{fmt(act)}</span>
      </div>
    </div>
  );
}

/** Big-number stat tiles for up to 4 metrics; the variance/impact tile reads as a tinted hero. */
function StatTiles({ metrics, hue }: { metrics: Metric[]; hue: string }) {
  const tiles = metrics.slice(0, 4);
  const heroIdx = tiles.findIndex((m) => HERO_RE.test(m.label) || m.num < 0);
  return (
    <div className={`grid gap-2 ${tiles.length >= 3 ? "grid-cols-3" : "grid-cols-" + tiles.length}`}>
      {tiles.map((m, i) => {
        const isIndex = INDEX_RE.test(m.label);
        const bad = m.num < 0 || (isIndex && m.num < 1 && !/margin/i.test(m.label));
        const hero = i === heroIdx;
        const valColor = bad || (isIndex && m.num < 1) ? hue : "var(--charcoal)";
        return (
          <div
            key={i}
            className={
              hero
                ? "rounded-lg border px-2.5 py-2 text-center"
                : "rounded-lg border border-hairline bg-ivory/60 px-2.5 py-2 text-center"
            }
            style={hero ? { borderColor: hexA(hue, 0.35), backgroundColor: hexA(hue, 0.08) } : undefined}
          >
            <div
              className="font-display text-lg font-semibold leading-tight tabular-nums"
              style={{ color: hero ? hue : valColor }}
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
 * Turns a finding's numeric evidence into a compact chart — a reference-vs-actual bullet when a
 * comparison is detectable (baseline vs forecast, budget vs estimate, revenue vs expense), otherwise
 * big-number stat tiles with the variance figure as a hero. Renders nothing when there are no numeric metrics.
 */
export function FindingVisual({ finding }: { finding: AgentFindingDto }) {
  const hue = severityMeta(finding.severity).hue;

  // A charted series (rainfall/day, SPI trend) is the strongest visual — lead with it. The underlying
  // scalar metrics stay available in the expanded Evidence section.
  const seriesEv = (finding.evidence ?? []).find((e) => e.series && (e.series.points?.length ?? 0) >= 2);
  if (seriesEv?.series) {
    return <FindingSeriesChart series={seriesEv.series} hue={hue} />;
  }

  const metrics = (finding.evidence ?? []).map(parseMetric).filter((m): m is Metric => m != null);
  if (metrics.length === 0) return null;

  for (const p of PAIRS) {
    const a = metrics.find((m) => p.a.test(m.label));
    const b = metrics.find((m) => p.b.test(m.label) && m !== a);
    if (a && b) {
      return <BulletCompare base={a} act={b} hue={hue} />;
    }
  }

  return (
    <div className="rounded-xl border border-hairline bg-surface/40 p-3">
      <StatTiles metrics={metrics} hue={hue} />
    </div>
  );
}
