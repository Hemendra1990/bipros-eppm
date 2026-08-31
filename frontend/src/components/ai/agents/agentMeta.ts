import type { AgentSeverity } from "@/lib/types";
import type { BadgeVariant } from "@/components/ui/badge";

// Distinguishable, mid-saturation hues that read on BOTH the ivory (light) and
// obsidian (dark) canvases. Each agent gets a stable hue derived from its key so
// the same agent always looks the same across the feed, cards, and headers.
const AGENT_HUES = [
  "#B8962E", // gold-deep
  "#2E7D5B", // emerald
  "#475569", // steel
  "#9B2C2C", // burgundy
  "#C7882E", // bronze
  "#E07A1F", // amber-flame
  "#6366F1", // indigo
  "#0EA5E9", // sky
  "#8B5CF6", // violet
  "#14B8A6", // teal
] as const;

export function agentHue(key: string): string {
  let h = 0;
  for (let i = 0; i < key.length; i++) h = (h * 31 + key.charCodeAt(i)) >>> 0;
  return AGENT_HUES[h % AGENT_HUES.length];
}

/** Two-letter glyph for an avatar. Prefers word-initials, falls back to prefix. */
export function agentInitials(name: string): string {
  const words = name.trim().split(/[\s_-]+/).filter(Boolean);
  if (words.length >= 2) return (words[0][0] + words[1][0]).toUpperCase();
  const single = words[0] ?? name;
  return single.slice(0, 2).toUpperCase();
}

export interface SeverityMeta {
  label: string;
  /** Sort weight — higher is more urgent. */
  order: number;
  badge: BadgeVariant;
  /** Solid hue used for dots / left rails. */
  hue: string;
}

export const SEVERITY_META: Record<AgentSeverity, SeverityMeta> = {
  CRITICAL: { label: "Critical", order: 5, badge: "danger", hue: "#9B2C2C" },
  HIGH: { label: "High", order: 4, badge: "danger", hue: "#C2410C" },
  MEDIUM: { label: "Medium", order: 3, badge: "warning", hue: "#C7882E" },
  LOW: { label: "Low", order: 2, badge: "info", hue: "#475569" },
  INFO: { label: "Info", order: 1, badge: "neutral", hue: "#6B7280" },
};

export function severityMeta(sev: string): SeverityMeta {
  return SEVERITY_META[sev as AgentSeverity] ?? SEVERITY_META.INFO;
}

/** Normalise a run status string to a StatusBadge-friendly token + tone. */
export function runStatusTone(status: string): {
  label: string;
  tone: string;
} {
  switch (status) {
    case "RUNNING":
    case "PENDING":
      return { label: status === "RUNNING" ? "Running" : "Queued", tone: "text-gold-deep" };
    case "SUCCEEDED":
      return { label: "Done", tone: "text-emerald" };
    case "FAILED":
      return { label: "Failed", tone: "text-burgundy" };
    case "SKIPPED":
      return { label: "Skipped", tone: "text-slate" };
    case "CANCELLED":
      return { label: "Cancelled", tone: "text-slate" };
    default:
      return { label: status || "Idle", tone: "text-text-muted" };
  }
}

/** Human-friendly label for a screaming-snake finding type. */
export function humanizeType(t: string): string {
  return t
    .toLowerCase()
    .replace(/_/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase());
}
