import { steps } from "./landing-data";

export function LandingWorkflow() {
  return (
    <section className="bg-paper px-9 py-20">
      <div className="mx-auto mb-12 max-w-[640px] text-center">
        <div className="mb-2.5 text-[10px] font-semibold uppercase tracking-[0.18em] text-gold-deep">
          How rollouts happen
        </div>
        <h2
          className="font-display text-[34px] font-semibold leading-[1.1] tracking-tight text-charcoal"
          style={{ fontVariationSettings: "'opsz' 144" }}
        >
          From kickoff to live programme in weeks.
        </h2>
      </div>
      <div className="relative mx-auto grid max-w-[1040px] grid-cols-2 gap-3 md:grid-cols-4">
        <div
          aria-hidden
          className="absolute left-[60px] right-[60px] top-6 hidden h-px md:block"
          style={{ background: "repeating-linear-gradient(90deg,#D4AF37 0 4px, transparent 4px 10px)" }}
        />
        {steps.map((s) => (
          <div key={s.roman} className="relative z-10 px-3 text-center">
            <div
              className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-paper font-display font-semibold text-xl text-gold-deep"
              style={{ border: "2px solid #D4AF37" }}
            >
              {s.roman}
            </div>
            <h5 className="font-display text-lg font-semibold tracking-tight text-charcoal">
              {s.title}
            </h5>
            <p className="mt-1 text-xs leading-relaxed text-slate">{s.blurb}</p>
          </div>
        ))}
      </div>
    </section>
  );
}
