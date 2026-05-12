import type { BadgeVariant } from "@/components/ui/badge";
import type { IssueCategory, IssueSeverity, IssueStatus } from "@/lib/types/dpr";

export const SEVERITY_VARIANT: Record<IssueSeverity, BadgeVariant> = {
  LOW: "neutral",
  MEDIUM: "info",
  HIGH: "warning",
  CRITICAL: "danger",
};

export const STATUS_VARIANT: Record<IssueStatus, BadgeVariant> = {
  OPEN: "warning",
  IN_PROGRESS: "info",
  BLOCKED: "danger",
  RESOLVED: "success",
  CLOSED: "neutral",
  CANCELLED: "neutral",
};

export const SEVERITY_OPTIONS: Array<{ value: IssueSeverity; label: string }> = [
  { value: "LOW", label: "Low" },
  { value: "MEDIUM", label: "Medium" },
  { value: "HIGH", label: "High" },
  { value: "CRITICAL", label: "Critical" },
];

export const STATUS_OPTIONS: Array<{ value: IssueStatus; label: string }> = [
  { value: "OPEN", label: "Open" },
  { value: "IN_PROGRESS", label: "In progress" },
  { value: "BLOCKED", label: "Blocked" },
  { value: "RESOLVED", label: "Resolved" },
  { value: "CLOSED", label: "Closed" },
  { value: "CANCELLED", label: "Cancelled" },
];

export const CATEGORY_OPTIONS: Array<{ value: IssueCategory; label: string }> = [
  { value: "SAFETY", label: "Safety" },
  { value: "QUALITY", label: "Quality" },
  { value: "MATERIAL_SHORTAGE", label: "Material shortage" },
  { value: "EQUIPMENT_BREAKDOWN", label: "Equipment breakdown" },
  { value: "MANPOWER_SHORTAGE", label: "Manpower shortage" },
  { value: "WEATHER", label: "Weather" },
  { value: "DESIGN_CHANGE", label: "Design change / RFI" },
  { value: "LAND_ACCESS", label: "Land / access" },
  { value: "UTILITY_CLASH", label: "Utility clash" },
  { value: "PERMIT_DELAY", label: "Permit delay" },
  { value: "SUBCONTRACTOR", label: "Subcontractor" },
  { value: "ENVIRONMENTAL", label: "Environmental" },
  { value: "OTHER", label: "Other" },
];

/** Compact label lookup used in card chips and read-only summaries. */
export function categoryLabel(c: IssueCategory | null | undefined): string {
  if (!c) return "—";
  return CATEGORY_OPTIONS.find((o) => o.value === c)?.label ?? c;
}
