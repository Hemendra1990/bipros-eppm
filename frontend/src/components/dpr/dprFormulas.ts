import type {
  DprEquipmentRow,
  DprManpowerRow,
  DprMaterialRow,
  RateBasis,
} from "@/lib/types/dpr";

/**
 * Mirror of {@code com.bipros.project.application.util.DprCostFormulas}. Used for the live
 * cost preview in each grid's Cost column so the supervisor sees what the row will save as.
 * Server-side recomputes from the same formulas at save time, so any drift here is visual only.
 */
export const OT_MULTIPLIER = 1.5;

export function manpowerLineCost(row: DprManpowerRow, basis: RateBasis | null | undefined): number | null {
  if (row.unitRate == null || row.nos == null || row.nos <= 0) return null;
  if (basis === "HOUR") {
    const hrs = (row.workingHours ?? 0) + (row.otHours ?? 0) * OT_MULTIPLIER;
    return round2(row.unitRate * row.nos * hrs);
  }
  return round2(row.unitRate * row.nos);
}

export function equipmentLineCost(row: DprEquipmentRow, basis: RateBasis | null | undefined): number | null {
  if (row.unitRate == null || row.nos == null || row.nos <= 0) return null;
  if (basis === "HOUR" || basis == null) {
    const hrs = row.workingHours ?? 0;
    return round2(row.unitRate * row.nos * hrs);
  }
  return round2(row.unitRate * row.nos);
}

export function materialLineCost(row: DprMaterialRow): number | null {
  if (row.unitRate == null || row.quantity == null) return null;
  return round2(row.unitRate * row.quantity);
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

export function fmtMoney(n: number | null | undefined): string {
  if (n == null || !isFinite(n)) return "—";
  return n.toLocaleString(undefined, { maximumFractionDigits: 2, minimumFractionDigits: 2 });
}

export function fmtRate(n: number | null | undefined): string {
  if (n == null || !isFinite(n)) return "—";
  return n.toLocaleString(undefined, { maximumFractionDigits: 2, minimumFractionDigits: 2 });
}

/** Suffix for the rate cell — only HOUR and DAY get a visible badge; EACH stays bare. */
export function rateBasisSuffix(basis: RateBasis | null | undefined): string {
  if (basis === "HOUR") return "/hr";
  if (basis === "DAY") return "/day";
  return "";
}
