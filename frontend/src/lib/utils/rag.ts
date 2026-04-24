export type Rag = "GREEN" | "AMBER" | "RED" | "CRIMSON" | string;

export function ragCls(rag: Rag | null | undefined): string {
  switch (rag) {
    case "GREEN":
      return "bg-success/15 text-success border-success/40";
    case "AMBER":
      return "bg-warning/15 text-warning border-warning/40";
    case "RED":
    case "CRIMSON":
      return "bg-danger/15 text-danger border-danger/40";
    default:
      return "bg-surface-hover text-text-secondary border-border";
  }
}

export function ragFill(rag: Rag | null | undefined): string {
  switch (rag) {
    case "GREEN":
      return "#10b981";
    case "AMBER":
      return "#f59e0b";
    case "RED":
    case "CRIMSON":
      return "#ef4444";
    default:
      return "#64748b";
  }
}

export function ragFromScore(score: number): "GREEN" | "AMBER" | "RED" {
  if (score >= 80) return "GREEN";
  if (score >= 50) return "AMBER";
  return "RED";
}
