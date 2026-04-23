/**
 * Format a chainage value in metres as the conventional road-survey label `km+metres`.
 * 145000 → "145+000"; 165200 → "165+200"; null → "—".
 */
export function chainageLabel(metres: number | null | undefined): string {
  if (metres == null || Number.isNaN(metres)) return "—";
  const abs = Math.abs(metres);
  const km = Math.floor(abs / 1000);
  const m = abs % 1000;
  const sign = metres < 0 ? "-" : "";
  return `${sign}${km}+${m.toString().padStart(3, "0")}`;
}

/**
 * Parse `145+000` / `145+200.5` / `145000` back into metres. Returns null on invalid input.
 */
export function parseChainage(input: string | null | undefined): number | null {
  if (!input) return null;
  const trimmed = input.trim();
  if (!trimmed) return null;
  const plusIdx = trimmed.indexOf("+");
  if (plusIdx === -1) {
    const n = Number(trimmed);
    return Number.isFinite(n) ? Math.round(n) : null;
  }
  const km = Number(trimmed.slice(0, plusIdx));
  const m = Number(trimmed.slice(plusIdx + 1));
  if (!Number.isFinite(km) || !Number.isFinite(m)) return null;
  return Math.round(km * 1000 + m);
}
