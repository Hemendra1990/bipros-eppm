"use client";

import { cn } from "@/lib/utils/cn";
import { agentHue, agentInitials } from "./agentMeta";
import { AgentWorkingIndicator } from "./AgentWorkingIndicator";

/** Convert a #rrggbb hex to an rgba() string at the given alpha. */
function hexA(hex: string, alpha: number): string {
  const m = hex.replace("#", "");
  const n = parseInt(
    m.length === 3 ? m.split("").map((x) => x + x).join("") : m,
    16,
  );
  const r = (n >> 16) & 255;
  const g = (n >> 8) & 255;
  const b = n & 255;
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

const SIZES = {
  sm: { box: 28, text: "text-[10px]" },
  md: { box: 40, text: "text-xs" },
  lg: { box: 52, text: "text-sm" },
} as const;

/**
 * Deterministic per-agent avatar: a hue-tinted square with the agent's
 * initials. The tint is an alpha layer over the card surface (badge idiom), so
 * it reads on both light and dark themes without a hardcoded white surface.
 * When `working`, an orbiting-dots + shimmer + pulse-ring overlay is composed.
 */
export function AgentAvatar({
  agentKey,
  displayName,
  size = "md",
  working = false,
  className,
}: {
  agentKey: string;
  displayName: string;
  size?: keyof typeof SIZES;
  working?: boolean;
  className?: string;
}) {
  const hue = agentHue(agentKey);
  const { box, text } = SIZES[size];

  return (
    <span
      className={cn(
        "relative inline-flex shrink-0 items-center justify-center rounded-lg font-display font-semibold tracking-tight",
        text,
        className,
      )}
      style={{
        width: box,
        height: box,
        backgroundColor: hexA(hue, 0.14),
        color: hue,
        boxShadow: `inset 0 0 0 1px ${hexA(hue, 0.34)}`,
      }}
      title={displayName}
    >
      {agentInitials(displayName)}
      {working && <AgentWorkingIndicator size={box} hue={hue} />}
    </span>
  );
}
