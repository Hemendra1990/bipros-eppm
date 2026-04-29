import type { LabourCategory, LabourGrade } from "@/lib/api/labourMasterApi";

/**
 * Category tone classes — map each labour category to a slot in the app's
 * "White & Gold" palette. Keys mirror KpiTile's tonal pattern so chips and
 * borders read as one identity across the module.
 */
export const CATEGORY_ACCENT: Record<
  LabourCategory,
  {
    /** Outer card background + border for category section header */
    section: string;
    /** Card-level border tint when a designation is in this category */
    cardBorder: string;
    /** Display name colour */
    title: string;
    /** Small skill chip */
    chip: string;
    /** Used as a 3px top stripe on cards / sections */
    stripe: string;
    /** Hex (or rgb) for charts and SVG fills */
    hex: string;
  }
> = {
  SITE_MANAGEMENT: {
    section:    "border-emerald/30 bg-emerald/10",
    cardBorder: "border-emerald/30 hover:border-emerald/55",
    title:      "text-emerald",
    chip:       "border-emerald/30 bg-emerald/10 text-emerald",
    stripe:     "bg-emerald",
    hex:        "var(--emerald)",
  },
  PLANT_EQUIPMENT: {
    section:    "border-amber-flame/35 bg-amber-flame/10",
    cardBorder: "border-amber-flame/30 hover:border-amber-flame/55",
    title:      "text-amber-flame",
    chip:       "border-amber-flame/30 bg-amber-flame/10 text-amber-flame",
    stripe:     "bg-amber-flame",
    hex:        "var(--amber-flame)",
  },
  SKILLED_LABOUR: {
    section:    "border-gold/35 bg-gold-tint/40",
    cardBorder: "border-gold/30 hover:border-gold/55",
    title:      "text-gold-deep",
    chip:       "border-gold/35 bg-gold-tint/55 text-gold-ink",
    stripe:     "bg-gold-deep",
    hex:        "var(--gold)",
  },
  SEMI_SKILLED_LABOUR: {
    section:    "border-bronze-warn/30 bg-bronze-warn/10",
    cardBorder: "border-bronze-warn/30 hover:border-bronze-warn/55",
    title:      "text-bronze-warn",
    chip:       "border-bronze-warn/30 bg-bronze-warn/10 text-bronze-warn",
    stripe:     "bg-bronze-warn",
    hex:        "var(--bronze-warn)",
  },
  GENERAL_UNSKILLED: {
    section:    "border-steel/30 bg-steel/10",
    cardBorder: "border-steel/30 hover:border-steel/55",
    title:      "text-steel",
    chip:       "border-steel/30 bg-steel/10 text-steel",
    stripe:     "bg-steel",
    hex:        "var(--steel)",
  },
};

/** Grade pill classes — drop-in to a span with `inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold border`. */
export const GRADE_BADGE: Record<LabourGrade, string> = {
  A: "border-gold/40 bg-gold-tint/45 text-gold-deep",
  B: "border-emerald/30 bg-emerald/10 text-emerald",
  C: "border-bronze-warn/30 bg-bronze-warn/10 text-bronze-warn",
  D: "border-steel/30 bg-steel/10 text-steel",
  E: "border-hairline bg-ivory text-slate",
};

/** OMR formatter with display-friendly thousand separators. */
export const formatOMR = (value: number | null | undefined): string =>
  value == null
    ? "—"
    : `OMR ${value.toLocaleString("en-OM", { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
