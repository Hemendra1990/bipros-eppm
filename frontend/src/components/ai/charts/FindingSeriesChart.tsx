"use client";

import type { EvidenceSeries } from "@/lib/types";

/**
 * Renders a finding's CHART evidence series inside the card: a COLUMN chart (e.g. rainfall/day, bars
 * that cross a threshold painted in the severity hue) or a LINE trend (e.g. SPI over periods against a
 * target line, with the latest point emphasised). Deterministic data only — the agent fills the series.
 *
 * Theme-aware: neutrals come from CSS tokens (--slate, --emerald, --bronze-warn, --hairline) that flip
 * with the theme; the severity `hue` is passed in. Built as plain SVG/flex so it stays crisp and light.
 */
export function FindingSeriesChart({ series, hue }: { series: EvidenceSeries; hue: string }) {
  const points = series.points ?? [];
  if (points.length < 2) return null;
  return series.kind === "LINE" ? (
    <TrendLine series={series} hue={hue} />
  ) : (
    <ColumnChart series={series} hue={hue} />
  );
}

const NEUTRAL = "#94a3b8"; // steel — reads on both themes

function fmtVal(v: number, unit?: string | null): string {
  if (unit === "mm") return v >= 10 ? String(Math.round(v)) : String(Math.round(v * 10) / 10);
  return Number.isInteger(v) ? String(v) : v.toFixed(2);
}

/** Bars scaled to the tallest value; those at/above the reference line take the severity hue. */
function ColumnChart({ series, hue }: { series: EvidenceSeries; hue: string }) {
  const pts = series.points;
  const ref = series.refValue ?? null;
  const max = Math.max(...pts.map((p) => p.value), ref ?? 0) * 1.12 || 1;
  const H = 96;
  const refBottom = ref != null ? (ref / max) * H : 0;

  return (
    <div className="rounded-xl border border-hairline bg-surface/40 p-3">
      <div className="relative flex items-end gap-1.5 pt-5" style={{ height: H + 20 }}>
        {ref != null && (
          <div
            className="pointer-events-none absolute inset-x-0 border-t border-dashed"
            style={{ bottom: refBottom, borderColor: "var(--bronze-warn)" }}
          >
            {series.refLabel && (
              <span className="absolute right-0 -top-3.5 font-mono text-[9px] text-bronze-warn">
                {series.refLabel}
              </span>
            )}
          </div>
        )}
        {pts.map((p, i) => {
          const over = ref != null && p.value >= ref;
          const h = Math.max(1.5, (p.value / max) * 100);
          return (
            <div key={i} className="relative flex h-full flex-1 flex-col items-center justify-end">
              <span
                className="absolute -top-4 font-mono text-[9px] tabular-nums"
                style={{ color: over ? hue : "var(--slate)" }}
              >
                {fmtVal(p.value, series.unit)}
              </span>
              <div
                className="w-full max-w-[30px] rounded-t-[3px]"
                style={{ height: `${h}%`, backgroundColor: over ? hue : NEUTRAL }}
                title={`${p.label}: ${fmtVal(p.value, series.unit)}${series.unit ? " " + series.unit : ""}`}
              />
            </div>
          );
        })}
      </div>
      <div className="mt-1.5 flex gap-1.5">
        {pts.map((p, i) => (
          <div key={i} className="flex-1 truncate text-center font-mono text-[9px] text-slate">
            {p.label}
          </div>
        ))}
      </div>
    </div>
  );
}

/** A trend line in the severity hue with a soft area fill, a dashed reference line, and an emphasised endpoint. */
function TrendLine({ series, hue }: { series: EvidenceSeries; hue: string }) {
  const pts = series.points;
  const ref = series.refValue ?? null;
  const W = 300;
  const H = 96;
  const padX = 8;
  const padTop = 12;
  const padBottom = 10;
  const vals = pts.map((p) => p.value);
  const lo = Math.min(...vals, ref ?? Infinity);
  const hi = Math.max(...vals, ref ?? -Infinity);
  const span = hi - lo || 1;
  const pad = span * 0.15;
  const yMin = lo - pad;
  const yMax = hi + pad;
  const x = (i: number) => padX + (i / (pts.length - 1)) * (W - padX - 30);
  const y = (v: number) => padTop + (1 - (v - yMin) / (yMax - yMin)) * (H - padTop - padBottom);

  const line = pts.map((p, i) => `${i === 0 ? "M" : "L"}${x(i).toFixed(1)},${y(p.value).toFixed(1)}`).join(" ");
  const area = `${line} L${x(pts.length - 1).toFixed(1)},${(H - padBottom).toFixed(1)} L${x(0).toFixed(1)},${(H - padBottom).toFixed(1)} Z`;
  const last = pts[pts.length - 1];

  return (
    <div className="rounded-xl border border-hairline bg-surface/40 p-3">
      <svg viewBox={`0 0 ${W} ${H}`} className="w-full" style={{ height: "auto" }} role="img"
        aria-label={`Trend from ${fmtVal(pts[0].value, series.unit)} to ${fmtVal(last.value, series.unit)}`}>
        {ref != null && (
          <>
            <line x1={padX} y1={y(ref)} x2={W - 30} y2={y(ref)} stroke="var(--emerald)" strokeWidth="1.25" strokeDasharray="5 5" opacity="0.75" />
            {series.refLabel && (
              <text x={W - 28} y={y(ref) + 3} fontSize="9" fontFamily="ui-monospace, monospace" fill="var(--emerald)">
                {series.refLabel}
              </text>
            )}
          </>
        )}
        <path d={area} fill={hue} opacity="0.12" />
        <path d={line} fill="none" stroke={hue} strokeWidth="2.25" strokeLinejoin="round" strokeLinecap="round" />
        {pts.map((p, i) => (
          <circle key={i} cx={x(i)} cy={y(p.value)} r={i === pts.length - 1 ? 4 : 2.5}
            fill={hue} stroke="var(--surface)" strokeWidth={i === pts.length - 1 ? 2 : 0} />
        ))}
        <text x={x(pts.length - 1)} y={y(last.value) - 8} textAnchor="end" fontSize="12" fontWeight="700"
          fontFamily="ui-monospace, monospace" fill="var(--charcoal)">
          {fmtVal(last.value, series.unit)}
        </text>
      </svg>
      <div className="mt-1 flex gap-1.5">
        {pts.map((p, i) => (
          <div key={i} className="flex-1 truncate text-center font-mono text-[9px] text-slate">
            {p.label}
          </div>
        ))}
      </div>
    </div>
  );
}
