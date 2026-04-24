import { pillars } from "./landing-data";

export function LandingPillars() {
  return (
    <section className="bg-ivory px-9 py-20">
      <div className="mx-auto mb-10 max-w-[640px] text-center">
        <div className="mb-2.5 text-[10px] font-semibold uppercase tracking-[0.18em] text-gold-deep">
          The platform
        </div>
        <h2
          className="font-display text-[38px] font-semibold leading-[1.1] tracking-tight text-charcoal"
          style={{ fontVariationSettings: "'opsz' 144" }}
        >
          One spine for planning, execution, and control
        </h2>
        <p className="mt-2.5 text-[15px] leading-relaxed text-slate">
          Three disciplines, nine modules, one data model. Replace stitched-together tools with a
          system designed for how programmes actually run.
        </p>
      </div>
      <div className="mx-auto grid max-w-[1180px] gap-5 lg:grid-cols-3">
        {pillars.map((p) => (
          <div
            key={p.title}
            className="rounded-xl border border-hairline border-l-[3px] border-l-gold bg-paper p-7 transition-all duration-200 hover:shadow-[0_4px_20px_rgba(28,28,28,0.05)] hover:-translate-y-0.5"
          >
            <div className="font-mono text-[10px] tracking-[0.14em] text-gold-deep">{p.roman}</div>
            <div
              className="mt-4 flex h-10 w-10 items-center justify-center rounded-[10px] text-gold-deep"
              style={{ background: "linear-gradient(135deg,#FDF6DD,#F5E7B5)" }}
            >
              <p.Icon size={20} strokeWidth={1.5} />
            </div>
            <h4 className="mt-4 font-display text-xl font-semibold tracking-tight text-charcoal">
              {p.title}
            </h4>
            <p className="mt-2 text-[13px] leading-relaxed text-slate">{p.blurb}</p>
            <a className="mt-3.5 inline-block text-xs font-semibold text-gold-deep hover:text-gold-ink cursor-pointer">
              {p.link}
            </a>
          </div>
        ))}
      </div>
    </section>
  );
}
