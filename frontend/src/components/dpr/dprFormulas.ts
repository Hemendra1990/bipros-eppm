import type {
  DprEquipmentRow,
  DprManpowerRow,
  DprMaterialRow,
  RateBasis,
} from "@/lib/types/dpr";

/**
 * Mirror of {@code com.bipros.project.application.util.DprCostFormulas}. Used for the live
 * cost preview in each grid's Cost column so the supervisor sees what the row will save as.
 * Line cost = unitRate × nos (unitRate × quantity for material). working_hours / ot_hours are
 * captured as logging fields only — never multiplied into cost, matching the server.
 */
export function manpowerLineCost(row: DprManpowerRow): number | null {
  if (row.unitRate == null || row.nos == null || row.nos <= 0) return null;
  return round2(row.unitRate * row.nos);
}

export function equipmentLineCost(row: DprEquipmentRow): number | null {
  if (row.unitRate == null || row.nos == null || row.nos <= 0) return null;
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
