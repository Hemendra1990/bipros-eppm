"use client";

import { useId } from "react";

/**
 * Circular confidence gauge (0..1) rendered as an SVG arc, with the numeric
 * percent in the centre and the `confidenceBasis` surfaced as a hover tooltip.
 * The arc animates in via stroke-dashoffset (Sparkline idiom), gated on
 * `prefers-reduced-motion: no-preference`.
 */
export function ConfidenceBadge({
  confidence,
  basis,
  size = 40,
}: {
  confidence: number;
  basis?: string | null;
  size?: number;
}) {
  const id = useId().replace(/:/g, "");
  const pct = Math.max(0, Math.min(1, confidence ?? 0));
  const stroke = 3.5;
  const r = size / 2 - stroke;
  const c = 2 * Math.PI * r;
  const hue =
    pct >= 0.75 ? "var(--emerald)" : pct >= 0.5 ? "var(--gold)" : "var(--bronze-warn)";

  return (
    <span className="group relative inline-flex flex-col items-center">
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden>
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke="var(--hairline)"
          strokeWidth={stroke}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke={hue}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={c}
          strokeDashoffset={c * (1 - pct)}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
          style={{ ["--cb-off" as string]: `${c * (1 - pct)}`, ["--cb-c" as string]: `${c}` }}
          className={`cb-arc-${id}`}
        />
        <text
          x="50%"
          y="50%"
          dominantBaseline="central"
          textAnchor="middle"
          className="fill-charcoal font-mono font-semibold"
          style={{ fontSize: size * 0.28 }}
        >
          {Math.round(pct * 100)}
        </text>
      </svg>
      <span className="mt-0.5 font-display text-[9px] font-medium uppercase tracking-[0.18em] text-slate">
        conf
      </span>

      {basis && (
        <span
          role="tooltip"
          className="pointer-events-none absolute bottom-full left-1/2 z-20 mb-2 w-max max-w-[220px] -translate-x-1/2 rounded-lg border border-hairline bg-surface px-3 py-2 text-left text-[11px] leading-snug text-text-secondary opacity-0 shadow-[0_4px_20px_rgba(28,28,28,0.10)] transition-opacity duration-150 group-hover:opacity-100"
        >
          <span className="mb-0.5 block font-semibold text-text-primary">
            Confidence basis
          </span>
          {basis}
        </span>
      )}

      <style>{`
        @media (prefers-reduced-motion: no-preference) {
          .cb-arc-${id} {
            animation: cb-draw-${id} 800ms cubic-bezier(.2,.7,.2,1) forwards;
          }
          @keyframes cb-draw-${id} {
            from { stroke-dashoffset: var(--cb-c); }
            to { stroke-dashoffset: var(--cb-off); }
          }
        }
      `}</style>
    </span>
  );
}
