import { stats } from "./landing-data";

export function LandingStats() {
  return (
    <section className="grid gap-8 border-y border-hairline bg-paper px-9 py-14 md:grid-cols-2 lg:grid-cols-4 text-center">
      {stats.map((s) => (
        <div key={s.l}>
          <div
            className="font-display text-[48px] font-semibold leading-none tracking-tight text-gold-deep"
            style={{ fontVariationSettings: "'opsz' 144" }}
          >
            {s.v}
          </div>
          <div className="mt-2 text-[11px] font-semibold uppercase tracking-[0.14em] text-slate">
            {s.l}
          </div>
        </div>
      ))}
    </section>
  );
}
