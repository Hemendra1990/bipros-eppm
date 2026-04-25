import type { BillingCycle, ContractType, ContractAttachmentType } from "../types";

export const CONTRACT_TYPE_OPTIONS: { value: ContractType; label: string }[] = [
  { value: "EPC_LUMP_SUM_FIDIC_YELLOW", label: "EPC Lump-Sum (FIDIC Yellow)" },
  { value: "EPC_LUMP_SUM_FIDIC_RED", label: "EPC Lump-Sum (FIDIC Red)" },
  { value: "EPC_LUMP_SUM_FIDIC_SILVER", label: "EPC Lump-Sum (FIDIC Silver)" },
  { value: "ITEM_RATE_FIDIC_RED", label: "Item Rate (FIDIC Red)" },
  { value: "PERCENTAGE_BASED_PMC", label: "Percentage-Based PMC" },
  { value: "LUMP_SUM_UNIT_RATE", label: "Lump-Sum / Unit-Rate" },
  { value: "EPC", label: "EPC" },
  { value: "BOT", label: "BOT (Build–Operate–Transfer)" },
  { value: "HAM", label: "HAM (Hybrid Annuity Model)" },
  { value: "ITEM_RATE", label: "Item Rate" },
  { value: "LUMP_SUM", label: "Lump Sum" },
  { value: "ANNUITY", label: "Annuity" },
];

export const BILLING_CYCLE_OPTIONS: { value: BillingCycle; label: string }[] = [
  { value: "MONTHLY", label: "Monthly" },
  { value: "QUARTERLY", label: "Quarterly" },
  { value: "MILESTONE_BASED", label: "Milestone-based" },
  { value: "ON_COMPLETION", label: "On Completion" },
];

export const ATTACHMENT_TYPE_OPTIONS: { value: ContractAttachmentType; label: string }[] = [
  { value: "LOA", label: "Letter of Award (LOA)" },
  { value: "AGREEMENT", label: "Agreement" },
  { value: "BOQ", label: "Bill of Quantities (BOQ)" },
  { value: "DRAWING", label: "Drawing" },
  { value: "BG_SCAN", label: "Bank Guarantee scan" },
  { value: "MOM", label: "Minutes of Meeting" },
  { value: "MEASUREMENT_BOOK", label: "Measurement Book" },
  { value: "TEST_REPORT", label: "Test Report" },
  { value: "CERTIFICATE", label: "Certificate" },
  { value: "PHOTO", label: "Photograph" },
  { value: "OTHER", label: "Other" },
];

export const ATTACHMENT_TYPE_LABELS: Record<ContractAttachmentType, string> =
  Object.fromEntries(ATTACHMENT_TYPE_OPTIONS.map((o) => [o.value, o.label])) as Record<
    ContractAttachmentType,
    string
  >;
