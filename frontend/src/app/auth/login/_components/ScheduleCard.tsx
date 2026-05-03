"use client";

type Status = "On track" | "At risk" | "Delayed" | "Planning";

const ROWS: Array<{
  label: string;
  trade: string;
  start: number;
  length: number;
  bar: string;
  status: Status;
  pct: number;
}> = [
  { label: "Sutton Coldfield Rail Corridor", trade: "Civil · S2",     start: 4,  length: 62, bar: "var(--charcoal)",  status: "On track", pct: 0.62 },
  { label: "Llanwern Solar Farm — EPC2",     trade: "Energy · 220MW", start: 14, length: 52, bar: "var(--gold-deep)", status: "At risk",  pct: 0.55 },
  { label: "Heathrow T2 Apron Refurb",       trade: "Aviation",       start: 22, length: 40, bar: "var(--steel)",     status: "On track", pct: 0.34 },
  { label: "North Sea Wind — array 4",       trade: "Marine",         start: 6,  length: 28, bar: "var(--burgundy)",  status: "Delayed",  pct: 0.78 },
  { label: "M62 Bridge Replacement",         trade: "Highways",       start: 32, length: 50, bar: "var(--charcoal)",  status: "Planning", pct: 0.10 },
];

const STATUS_TINT: Record<Status, string> = {
  "On track": "text-emerald bg-emerald/10 border-emerald/25",
  "At risk":  "text-bronze-warn bg-bronze-warn/10 border-bronze-warn/25",
  "Delayed":  "text-burgundy bg-burgundy/10 border-burgundy/25",
  "Planning": "text-slate bg-slate/10 border-slate/20",
};

const NOW_PCT = 38;

export function ScheduleCard() {
  return (
    <div className="relative overflow-hidden rounded-2xl border border-hairline bg-paper/95 shadow-[0_18px_55px_rgba(28,28,28,0.10)] backdrop-blur">
      <div
        aria-hidden
        className="absolute inset-x-6 top-0 h-px"
        style={{ background: "linear-gradient(90deg,transparent,#D4AF37,transparent)" }}
      />

      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-hairline bg-ivory/60 px-5 py-3">
        <div className="flex items-center gap-2.5">
          <span
            className="font-display text-[15px] font-semibold tracking-tight text-charcoal"
            style={{ fontVariationSettings: "'opsz' 144" }}
          >
            Q4 Programme Pulse
          </span>
          <span className="font-mono text-[10px] uppercase tracking-[0.14em] text-ash">
            · 12 active · 184 milestones
          </span>
        </div>
        <div className="flex items-center gap-1.5">
          <span aria-hidden className="inline-block h-1.5 w-1.5 animate-pulse rounded-full bg-emerald" />
          <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.16em] text-emerald">
            Live · synced 14s ago
          </span>
        </div>
      </div>

      <div className="px-5 py-4">
        <div className="grid grid-cols-[minmax(150px,200px)_1fr] items-end gap-x-3 pb-2">
          <div className="font-mono text-[9.5px] uppercase tracking-[0.14em] text-ash">
            Programme · trade
          </div>
          <div className="hidden grid-cols-12 font-mono text-[9px] uppercase tracking-[0.14em] text-ash sm:grid">
            {Array.from({ length: 12 }).map((_, i) => (
              <div key={i} className="text-center">
                {`W${i + 1}`}
              </div>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-[minmax(150px,200px)_1fr] gap-x-3">
          {/* Labels column */}
          <div className="flex flex-col gap-y-2.5">
            {ROWS.map((r) => (
              <div key={r.label} className="flex h-9 flex-col justify-center pr-2">
                <div className="truncate text-[12.5px] font-medium text-charcoal">
                  {r.label}
                </div>
                <div className="mt-0.5 flex items-center gap-1.5">
                  <span className="font-mono text-[9.5px] uppercase tracking-[0.14em] text-ash">
                    {r.trade}
                  </span>
                  <span
                    className={`rounded-sm border px-1 py-px font-mono text-[8.5px] uppercase tracking-[0.12em] ${STATUS_TINT[r.status]}`}
                  >
                    {r.status}
                  </span>
                </div>
              </div>
            ))}
          </div>

          {/* Bars column — relative wrapper so the "now" line spans all rows */}
          <div className="relative flex flex-col gap-y-2.5">
            {ROWS.map((r, i) => (
              <div
                key={r.label}
                className="relative h-9 rounded-md border border-hairline bg-ivory"
              >
                <div className="pointer-events-none absolute inset-0 grid grid-cols-12">
                  {Array.from({ length: 11 }).map((_, k) => (
                    <div
                      key={k}
                      className="border-r border-hairline/70 last:border-r-0"
                    />
                  ))}
                </div>

                <div
                  className="absolute top-1.5 bottom-1.5 rounded-sm"
                  style={{
                    left: `${r.start}%`,
                    width: `${r.length}%`,
                    background: r.bar,
                    boxShadow: "inset 0 -1px 0 rgba(255,255,255,0.18)",
                    animation: `barGrow .9s cubic-bezier(.22,1,.36,1) ${0.18 + i * 0.08}s both`,
                    transformOrigin: "left",
                  }}
                />
                <div
                  className="absolute top-1.5 bottom-1.5 rounded-sm"
                  style={{
                    left: `${r.start}%`,
                    width: `${r.length * r.pct}%`,
                    background:
                      "repeating-linear-gradient(45deg,color-mix(in srgb,var(--paper) 38%,transparent) 0 4px,transparent 4px 8px)",
                    pointerEvents: "none",
                    animation: `barGrow 1s cubic-bezier(.22,1,.36,1) ${0.28 + i * 0.08}s both`,
                    transformOrigin: "left",
                  }}
                />
                <span
                  aria-hidden
                  className="absolute top-1/2 h-2.5 w-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-paper bg-gold shadow-[0_0_0_1px_rgba(212,175,55,0.40)]"
                  style={{ left: `${r.start + r.length * r.pct}%` }}
                />
              </div>
            ))}

            {/* Single "now" line spanning the whole bar column */}
            <div
              aria-hidden
              className="pointer-events-none absolute -top-2 -bottom-1 w-px"
              style={{
                left: `${NOW_PCT}%`,
                background:
                  "linear-gradient(180deg,transparent 0%,#D4AF37 10%,#D4AF37 90%,transparent 100%)",
              }}
            >
              <span className="absolute -top-1 left-1/2 -translate-x-1/2 whitespace-nowrap rounded-full border border-gold/40 bg-paper px-1.5 py-px font-mono text-[8.5px] font-semibold uppercase tracking-[0.14em] text-gold-ink shadow-sm">
                Today
              </span>
            </div>
          </div>
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-hairline bg-ivory/40 px-5 py-2.5 font-mono text-[10px] uppercase tracking-[0.14em] text-slate">
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-1.5">
            <span aria-hidden className="inline-block h-2 w-3 rounded-sm bg-charcoal" />
            Plan
          </span>
          <span className="flex items-center gap-1.5">
            <span
              aria-hidden
              className="inline-block h-2 w-3 rounded-sm"
              style={{
                background:
                  "repeating-linear-gradient(45deg,var(--charcoal) 0 2px,color-mix(in srgb,var(--paper) 70%,transparent) 2px 4px)",
              }}
            />
            Actual
          </span>
          <span className="flex items-center gap-1.5">
            <span aria-hidden className="inline-block h-2 w-2 rounded-full bg-gold" />
            Forecast
          </span>
        </div>
        <span>Schedule v4.2 · CPM critical path · 6 floats &lt; 3d</span>
      </div>
    </div>
  );
}
