"use client";

/**
 * Pure CSS/SVG "the agent is thinking" overlay. No framer-motion.
 *
 * Layers (all wrapped in `prefers-reduced-motion: no-preference`, and the app's
 * global reduce rule kills them a second time as a safety net):
 *   1. an SVG stroke-dashoffset pulse ring drawn around the host,
 *   2. two orbiting dots on a rotating track,
 *   3. a gold shimmer sweep across the surface.
 *
 * Everything is driven off theme CSS variables (`--gold`, `--surface`, …) so it
 * reads correctly on both the light ivory hero and the dark obsidian canvas —
 * no white-alpha surfaces that vanish on cream.
 */
export function AgentWorkingIndicator({
  size = 40,
  hue = "var(--gold)",
}: {
  size?: number;
  hue?: string;
}) {
  const r = size / 2 - 1.5;
  const c = 2 * Math.PI * r;

  return (
    <span
      className="agent-working pointer-events-none absolute inset-0"
      style={{ ["--awi-hue" as string]: hue }}
      aria-hidden
    >
      {/* Pulse ring */}
      <svg
        className="agent-working__ring absolute inset-0"
        width={size}
        height={size}
        viewBox={`0 0 ${size} ${size}`}
        fill="none"
      >
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          stroke="var(--awi-hue)"
          strokeWidth={1.5}
          strokeLinecap="round"
          strokeDasharray={`${c * 0.28} ${c * 0.72}`}
        />
      </svg>

      {/* Orbiting dots */}
      <span className="agent-working__orbit absolute inset-0">
        <span className="agent-working__dot" />
        <span className="agent-working__dot agent-working__dot--2" />
      </span>

      {/* Shimmer sweep */}
      <span className="agent-working__shimmer absolute inset-0 overflow-hidden rounded-[inherit]" />

      <style>{`
        @media (prefers-reduced-motion: no-preference) {
          .agent-working__ring {
            animation: awi-spin 2.6s linear infinite, awi-pulse 1.6s ease-in-out infinite;
            transform-origin: center;
          }
          .agent-working__orbit {
            animation: awi-spin 2.2s linear infinite reverse;
            transform-origin: center;
          }
          .agent-working__dot {
            position: absolute;
            top: -1px;
            left: 50%;
            height: 3px;
            width: 3px;
            margin-left: -1.5px;
            border-radius: 9999px;
            background: var(--awi-hue);
            box-shadow: 0 0 5px var(--awi-hue);
          }
          .agent-working__dot--2 {
            top: auto;
            bottom: -1px;
          }
          .agent-working__shimmer {
            background: linear-gradient(
              115deg,
              transparent 30%,
              color-mix(in srgb, var(--awi-hue) 45%, transparent) 50%,
              transparent 70%
            );
            background-size: 250% 100%;
            animation: awi-sweep 2.1s ease-in-out infinite;
            mix-blend-mode: plus-lighter;
          }
        }
        @keyframes awi-spin { to { transform: rotate(360deg); } }
        @keyframes awi-pulse {
          0%, 100% { opacity: 0.35; }
          50% { opacity: 0.9; }
        }
        @keyframes awi-sweep {
          0% { background-position: 150% 0; }
          100% { background-position: -150% 0; }
        }
      `}</style>
    </span>
  );
}
