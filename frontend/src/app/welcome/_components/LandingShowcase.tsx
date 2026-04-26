import { ganttRows } from "./landing-data";

const fills: Record<(typeof ganttRows)[number]["tone"], string> = {
  default: "linear-gradient(90deg,#D4AF37,#B8962E)",
  success: "linear-gradient(90deg,#2E7D5B,#256B4C)",
  warning: "linear-gradient(90deg,#C7882E,#A6701F)",
  danger: "linear-gradient(90deg,#9B2C2C,#7F2424)",
};

export function LandingShowcase() {
  return (
    <section className="grid items-center gap-12 bg-ivory px-9 py-20 lg:grid-cols-[1fr_1.2fr]">
      <div>
        <div className="mb-3 text-[10px] font-semibold uppercase tracking-[0.18em] text-gold-deep">
          Showcase · Master schedule
        </div>
        <h2
          className="font-display text-[38px] font-semibold leading-[1.1] tracking-tight text-charcoal"
          style={{ fontVariationSettings: "'opsz' 144" }}
        >
          Every programme on{" "}
          <em className="not-italic font-medium italic text-gold-deep">one</em> Gantt.
        </h2>
        <p className="mt-3 text-[15px] leading-relaxed text-charcoal">
          Roll 2,000+ activities across 50 projects into a single master schedule. Drive
          critical-path across the portfolio, not project-by-project.
        </p>
        <ul className="mt-5 space-y-2.5">
          {[
            "Portfolio-level CPM with resource levelling",
            "Baselines per programme, per sponsor, per audit",
            "Import from P6, MS Project, Primavera in minutes",
            "Variance surfaced against baseline, not version-to-version",
          ].map((item) => (
            <li key={item} className="flex items-start gap-2.5 text-sm text-charcoal">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#D4AF37" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className="mt-1 flex-shrink-0">
                <polyline points="20 6 9 17 4 12" />
              </svg>
              {item}
            </li>
          ))}
        </ul>
        <button
          type="button"
          className="mt-6 inline-flex h-10 items-center rounded-[10px] border border-gold bg-paper px-4 text-sm font-semibold text-gold-deep hover:bg-paper hover:text-gold-ink"
        >
          Explore scheduling →
        </button>
      </div>

      {/* Gantt */}
      <div className="rounded-2xl border border-hairline bg-paper p-4 shadow-[0_4px_20px_rgba(28,28,28,0.05)]">
        <div className="flex justify-between border-b border-hairline px-2 pb-2.5 font-mono text-[10px] tracking-[0.08em] text-slate">
          <span>NORTHWEST RAIL EXT · WBS 1.4.2</span>
          <span>JAN · FEB · MAR · APR · MAY · JUN</span>
        </div>
        {ganttRows.map((row) => (
          <div key={row.name} className="grid grid-cols-[140px_1fr] items-center px-2 py-2.5 text-[11px]">
            <span className="font-medium text-charcoal">{row.name}</span>
            <div className="relative h-3.5 rounded bg-ivory">
              <div
                className="absolute inset-y-0 rounded"
                style={{ left: `${row.leftPct}%`, width: `${row.widthPct}%`, background: fills[row.tone] }}
              />
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
