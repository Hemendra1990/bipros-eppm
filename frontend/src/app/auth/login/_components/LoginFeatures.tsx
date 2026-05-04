"use client";

import {
  CalendarRange,
  Users2,
  CircleDollarSign,
  PieChart,
} from "lucide-react";
import type { ReactNode } from "react";

type Feature = {
  icon: ReactNode;
  eyebrow: string;
  title: string;
  body: string;
  art: ReactNode;
};

const FEATURES: Feature[] = [
  {
    icon: <CalendarRange size={16} strokeWidth={1.6} />,
    eyebrow: "Schedule",
    title: "CPM-driven Gantt",
    body: "Critical-path scheduling with float, drift, and forecast lines on every milestone.",
    art: <SchedArt />,
  },
  {
    icon: <Users2 size={16} strokeWidth={1.6} />,
    eyebrow: "Resources",
    title: "Capacity & deployment",
    body: "Trade-by-trade load balancing across programmes — no team overcommitted, no skill underused.",
    art: <ResourceArt />,
  },
  {
    icon: <CircleDollarSign size={16} strokeWidth={1.6} />,
    eyebrow: "Cost",
    title: "Earned-value control",
    body: "Plan, actual, and forecast curves with EV / SV variance surfaced before the period closes.",
    art: <CostArt />,
  },
  {
    icon: <PieChart size={16} strokeWidth={1.6} />,
    eyebrow: "Analytics",
    title: "Programme intelligence",
    body: "Live dashboards & MDX-narrated insights — 184 metrics, one composed source of truth.",
    art: <AnalyticsArt />,
  },
];

export function LoginFeatures() {
  return (
    <section
      id="features"
      className="relative border-t border-hairline bg-ivory/40 px-4 py-16 sm:px-8 sm:py-20"
    >
      <div className="mx-auto max-w-7xl">
        <div className="flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-end">
          <div>
            <div className="mb-3 flex items-center gap-2 text-[10.5px] font-semibold uppercase tracking-[0.24em] text-gold-deep">
              <span aria-hidden className="inline-block h-px w-7 bg-gold" />
              What Bipros runs
            </div>
            <h2
              className="font-display text-[34px] font-semibold leading-[1.05] tracking-[-0.02em] text-charcoal sm:text-[40px]"
              style={{ fontVariationSettings: "'opsz' 144" }}
            >
              Four disciplines.
              <br />
              <em className="font-medium italic text-gold-deep">One spine.</em>
            </h2>
          </div>
          <p className="max-w-md text-[15px] leading-[1.6] text-slate">
            Modules built for programme delivery, not for general-purpose work
            management. Each one connects to the same schedule, cost code, and
            resource book — so every screen tells the same story.
          </p>
        </div>

        <div className="mt-10 grid grid-cols-1 gap-px overflow-hidden rounded-2xl border border-hairline bg-hairline sm:grid-cols-2 lg:grid-cols-4">
          {FEATURES.map((f) => (
            <article
              key={f.eyebrow}
              className="group relative flex flex-col gap-4 bg-paper/95 p-6 transition-colors duration-200 hover:bg-paper"
            >
              <div
                aria-hidden
                className="absolute inset-x-6 top-0 h-px scale-x-0 transition-transform duration-300 group-hover:scale-x-100"
                style={{
                  background:
                    "linear-gradient(90deg,transparent,#D4AF37,transparent)",
                  transformOrigin: "left",
                }}
              />

              <div className="flex items-center justify-between">
                <span className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-hairline bg-ivory text-gold-deep shadow-[0_2px_6px_rgba(28,28,28,0.04)] transition-colors group-hover:border-gold/60 group-hover:bg-gold-tint/40">
                  {f.icon}
                </span>
                <span className="font-mono text-[9.5px] font-semibold uppercase tracking-[0.18em] text-ash">
                  {f.eyebrow}
                </span>
              </div>

              <div className="-mx-2 -mb-1 flex h-[68px] items-end overflow-hidden rounded-md bg-ivory/60 px-2">
                {f.art}
              </div>

              <div>
                <h3
                  className="font-display text-[18px] font-semibold leading-[1.15] tracking-[-0.01em] text-charcoal"
                  style={{ fontVariationSettings: "'opsz' 144" }}
                >
                  {f.title}
                </h3>
                <p className="mt-1.5 text-[13px] leading-[1.55] text-slate">
                  {f.body}
                </p>
              </div>

              <span className="mt-auto inline-flex items-center gap-1 text-[11.5px] font-semibold text-gold-deep opacity-0 transition-opacity duration-200 group-hover:opacity-100">
                Learn more →
              </span>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

function SchedArt() {
  const bars = [
    { x: 4, w: 38, c: "var(--charcoal)" },
    { x: 22, w: 50, c: "var(--gold-deep)" },
    { x: 8, w: 70, c: "var(--steel)" },
  ];
  return (
    <svg
      viewBox="0 0 200 60"
      className="h-full w-full"
      preserveAspectRatio="none"
      aria-hidden
    >
      {bars.map((b, i) => (
        <rect
          key={i}
          x={`${b.x}%`}
          y={6 + i * 18}
          width={`${b.w}%`}
          height="9"
          rx="2"
          fill={b.c}
          opacity={0.85}
        />
      ))}
      <line
        x1="38%"
        x2="38%"
        y1="2"
        y2="58"
        stroke="#D4AF37"
        strokeWidth="1"
        strokeDasharray="2 2"
      />
    </svg>
  );
}

function ResourceArt() {
  const stacks = [
    [22, 8],
    [30, 6],
    [18, 14],
    [26, 10],
    [34, 4],
    [20, 12],
    [28, 8],
  ];
  return (
    <svg
      viewBox="0 0 200 60"
      className="h-full w-full"
      preserveAspectRatio="none"
      aria-hidden
    >
      {stacks.map(([a, b], i) => {
        const x = 8 + i * 26;
        return (
          <g key={i}>
            <rect x={x} y={50 - a} width="16" height={a} rx="1.5" fill="var(--charcoal)" />
            <rect
              x={x}
              y={50 - a - b}
              width="16"
              height={b}
              rx="1.5"
              fill="#D4AF37"
            />
          </g>
        );
      })}
      <line x1="0" x2="200" y1="50" y2="50" stroke="var(--hairline)" strokeWidth="1" />
    </svg>
  );
}

function CostArt() {
  return (
    <svg
      viewBox="0 0 200 60"
      className="h-full w-full"
      preserveAspectRatio="none"
      aria-hidden
    >
      <path
        d="M0 52 C 30 48, 55 36, 80 28 S 130 14, 200 6"
        stroke="var(--charcoal)"
        strokeWidth="1.6"
        fill="none"
      />
      <path
        d="M0 54 C 30 52, 55 46, 80 42 S 130 30, 200 22"
        stroke="var(--gold-deep)"
        strokeWidth="1.6"
        strokeDasharray="3 3"
        fill="none"
      />
      <path
        d="M0 54 C 30 52, 55 46, 80 42 S 130 30, 200 22 L 200 60 L 0 60 Z"
        fill="rgba(212,175,55,0.14)"
      />
      <circle cx="118" cy="30" r="2.6" fill="#D4AF37" />
      <line
        x1="118"
        x2="118"
        y1="0"
        y2="60"
        stroke="#D4AF37"
        strokeWidth="0.8"
        strokeDasharray="2 2"
        opacity="0.6"
      />
    </svg>
  );
}

function AnalyticsArt() {
  return (
    <svg
      viewBox="0 0 200 60"
      className="h-full w-full"
      preserveAspectRatio="xMidYMid meet"
      aria-hidden
    >
      <g transform="translate(28 30)">
        <circle r="22" fill="none" stroke="var(--hairline)" strokeWidth="6" />
        <circle
          r="22"
          fill="none"
          stroke="#D4AF37"
          strokeWidth="6"
          strokeDasharray="138.2"
          strokeDashoffset="55"
          transform="rotate(-90)"
          strokeLinecap="round"
        />
        <text
          x="0"
          y="3"
          textAnchor="middle"
          fontSize="11"
          fontWeight="600"
          fill="var(--charcoal)"
          fontFamily="Fraunces, serif"
        >
          60%
        </text>
      </g>
      <g transform="translate(74 8)">
        {[
          { y: 6, w: 100, label: "Schedule" },
          { y: 20, w: 80, label: "Cost" },
          { y: 34, w: 120, label: "Risk" },
        ].map((b, i) => (
          <g key={i}>
            <rect x="0" y={b.y} width="120" height="6" rx="2" fill="var(--hairline)" />
            <rect
              x="0"
              y={b.y}
              width={b.w * 0.7}
              height="6"
              rx="2"
              fill={i === 1 ? "var(--gold-deep)" : "var(--charcoal)"}
            />
          </g>
        ))}
      </g>
    </svg>
  );
}
