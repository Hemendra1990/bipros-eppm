"use client";

import { Suspense } from "react";
import Link from "next/link";
import { ArrowUpRight, Eye, EyeOff, Lock, ShieldCheck } from "lucide-react";
import { useLoginSubmit } from "../_shared/useLoginSubmit";

/**
 * Concept — "Atrium" · Editorial Daylight.
 * A bright photographic-feeling atrium scene composed entirely from inline SVG:
 * sunlight beams, glass-facade silhouette, a small walking figure, a leaf shadow.
 * Cream / linen palette, deep ink text, refined Fraunces serif.
 */
export default function EditorialPage() {
  return (
    <div className="relative min-h-screen overflow-hidden bg-[#F5F1EA] font-sans text-[#1A1F2C] antialiased">
      <Atrium />
      <Header />
      <main className="relative mx-auto flex w-full max-w-5xl flex-col items-center px-6 pb-16 pt-10 sm:pt-14 lg:pt-20">
        <Hero />
        <Suspense
          fallback={
            <div className="mt-12 grid h-[480px] w-full max-w-[460px] place-items-center text-[12px] text-[#1A1F2C]/50">
              Loading…
            </div>
          }
        >
          <Form />
        </Suspense>
        <PullQuote />
      </main>
      <Footer />
      <Keyframes />
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  Atrium scene                                                              */
/* -------------------------------------------------------------------------- */

function Atrium() {
  return (
    <div aria-hidden className="pointer-events-none absolute inset-0 -z-10 overflow-hidden">
      {/* Warm linen base wash */}
      <div
        className="absolute inset-0"
        style={{
          background:
            "radial-gradient(ellipse 90% 70% at 30% 15%, #FBF6EC 0%, #F5F1EA 45%, #ECE5D6 100%)",
        }}
      />
      {/* Cool stone wash from lower-right (steel + concrete) */}
      <div
        className="absolute inset-0 opacity-70"
        style={{
          background:
            "radial-gradient(ellipse 70% 60% at 90% 95%, rgba(216,220,223,0.65), transparent 60%)",
        }}
      />

      {/* Sunlight beams — full-bleed inline SVG, skewed diagonal stripes */}
      <svg
        viewBox="0 0 1600 1000"
        preserveAspectRatio="xMidYMid slice"
        className="absolute inset-0 h-full w-full"
        style={{ mixBlendMode: "screen" }}
      >
        <defs>
          <linearGradient id="ed-beam-a" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="#FFF9EC" stopOpacity="0" />
            <stop offset="50%" stopColor="#FFF1CF" stopOpacity="0.55" />
            <stop offset="100%" stopColor="#FFF9EC" stopOpacity="0" />
          </linearGradient>
          <linearGradient id="ed-beam-b" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="#FFFFFF" stopOpacity="0" />
            <stop offset="50%" stopColor="#FFE9B8" stopOpacity="0.42" />
            <stop offset="100%" stopColor="#FFFFFF" stopOpacity="0" />
          </linearGradient>
          <linearGradient id="ed-beam-c" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="#FFFFFF" stopOpacity="0" />
            <stop offset="50%" stopColor="#FFF6DC" stopOpacity="0.30" />
            <stop offset="100%" stopColor="#FFFFFF" stopOpacity="0" />
          </linearGradient>
        </defs>
        <g transform="translate(0,0) skewX(-22)">
          <rect x="-120" y="-200" width="260" height="1500" fill="url(#ed-beam-a)" />
          <rect x="220" y="-200" width="180" height="1500" fill="url(#ed-beam-b)" />
          <rect x="520" y="-200" width="320" height="1500" fill="url(#ed-beam-c)" />
          <rect x="940" y="-200" width="140" height="1500" fill="url(#ed-beam-b)" />
          <rect x="1180" y="-200" width="240" height="1500" fill="url(#ed-beam-a)" />
        </g>
      </svg>

      {/* Atrium glass facade — vertical mullions, anchored at bottom */}
      <svg
        viewBox="0 0 1600 1000"
        preserveAspectRatio="xMidYMax slice"
        className="absolute inset-x-0 bottom-0 h-full w-full"
      >
        <g opacity="0.16" stroke="#1A1F2C" strokeWidth="1.1">
          {Array.from({ length: 28 }).map((_, i) => {
            const x = 40 + i * 56;
            return <line key={i} x1={x} y1="380" x2={x} y2="900" />;
          })}
          {/* horizontal floor-line */}
          <line x1="0" y1="900" x2="1600" y2="900" strokeWidth="1.6" />
          {/* mid mullion crossbar */}
          <line x1="0" y1="640" x2="1600" y2="640" strokeWidth="0.6" opacity="0.65" />
        </g>

        {/* Walking figure silhouette — small, low opacity, mid-foreground */}
        <g opacity="0.28" fill="#1A1F2C" transform="translate(880,820)">
          <circle cx="0" cy="-46" r="6.5" />
          <path d="M -4,-39 L -10,-6 L -7,18 L -10,40 L -4,40 L 0,18 L 6,40 L 12,40 L 9,18 L 12,-6 L 6,-39 Z" />
          {/* faint shadow */}
          <ellipse cx="2" cy="44" rx="14" ry="2" opacity="0.5" />
        </g>

        {/* Distant second figure for depth */}
        <g opacity="0.18" fill="#1A1F2C" transform="translate(560,830) scale(0.78)">
          <circle cx="0" cy="-46" r="6.5" />
          <path d="M -4,-39 L -9,-8 L -7,18 L -10,40 L -4,40 L 0,16 L 5,40 L 11,40 L 9,18 L 11,-8 L 6,-39 Z" />
        </g>
      </svg>

      {/* Potted-plant / leaf silhouette — bottom-left corner, editorial touch */}
      <svg
        viewBox="0 0 600 600"
        className="absolute -bottom-6 -left-6 h-[300px] w-[300px] sm:h-[380px] sm:w-[380px]"
        aria-hidden
      >
        <g opacity="0.18" fill="#1A1F2C">
          <path d="M300,560 C295,500 270,450 230,420 C195,395 175,360 178,320 C212,338 240,374 252,418 C258,378 280,340 318,318 C322,358 308,395 280,420 C312,408 348,412 380,440 C354,470 318,488 286,478 C300,500 304,532 300,560 Z" />
          {/* pot */}
          <path d="M252,560 L348,560 L338,598 L262,598 Z" opacity="0.9" />
        </g>
      </svg>

      {/* Warm grain — keeps the scene photographic */}
      <div
        className="absolute inset-0 opacity-[0.045] mix-blend-multiply"
        style={{
          backgroundImage:
            "url(\"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='220' height='220'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2' stitchTiles='stitch'/><feColorMatrix values='0 0 0 0 0.10 0 0 0 0 0.12 0 0 0 0 0.17 0 0 0 0.5 0'/></filter><rect width='100%' height='100%' filter='url(%23n)'/></svg>\")",
        }}
      />

      {/* Soft vignette to settle edges */}
      <div
        className="absolute inset-0"
        style={{
          background:
            "radial-gradient(ellipse 100% 80% at 50% 40%, transparent 55%, rgba(26,31,44,0.07) 100%)",
        }}
      />
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  Header                                                                    */
/* -------------------------------------------------------------------------- */

function Header() {
  return (
    <header className="relative mx-auto flex h-16 w-full max-w-6xl items-center justify-between px-6">
      <Link href="/" className="flex items-center gap-2.5">
        <span
          aria-hidden
          className="grid h-7 w-7 place-items-center rounded-[7px] text-[12px] font-semibold text-[#F5F1EA]"
          style={{ background: "linear-gradient(160deg,#3A3F4E,#1A1F2C)" }}
        >
          B
        </span>
        <span className="font-display text-[18px] font-medium tracking-[-0.01em] text-[#1A1F2C]">
          Bipros
        </span>
        <span className="ml-1 hidden rounded-sm border border-[#1A1F2C]/15 bg-[#FBF7EE]/60 px-1.5 py-0.5 font-mono text-[9.5px] uppercase tracking-[0.18em] text-[#1A1F2C]/65 sm:inline-block">
          EPPM
        </span>
      </Link>
      <nav className="hidden items-center gap-7 text-[13px] text-[#1A1F2C]/65 sm:flex">
        <Link href="#" className="transition hover:text-[#1A1F2C]">Capabilities</Link>
        <Link href="#" className="transition hover:text-[#1A1F2C]">Customers</Link>
        <Link href="#" className="transition hover:text-[#1A1F2C]">Contact</Link>
      </nav>
    </header>
  );
}

/* -------------------------------------------------------------------------- */
/*  Hero                                                                       */
/* -------------------------------------------------------------------------- */

function Hero() {
  return (
    <section className="ed-rise relative mt-6 flex w-full max-w-3xl flex-col items-center text-center sm:mt-10">
      <div className="ed-rise ed-d1 mb-6 inline-flex items-center gap-3 font-mono text-[10.5px] uppercase tracking-[0.26em] text-[#1A1F2C]/60">
        <span aria-hidden className="inline-block h-px w-7 bg-[#1A1F2C]/40" />
        For programme leaders · 2026
        <span aria-hidden className="inline-block h-px w-7 bg-[#1A1F2C]/40" />
      </div>
      <h1
        className="ed-rise ed-d2 font-display text-[44px] font-medium leading-[1.04] tracking-[-0.022em] text-[#1A1F2C] sm:text-[58px] lg:text-[68px]"
        style={{ fontVariationSettings: "'opsz' 144" }}
      >
        Run <em className="font-normal italic text-[#7C5A1F]">the</em> book of work,
        <span className="block">in the light.</span>
      </h1>
      <p className="ed-rise ed-d3 mt-7 max-w-xl text-[15.5px] leading-[1.7] text-[#1A1F2C]/72">
        Bipros is the operational layer for capital programmes — a single,
        accountable surface for schedule, cost, contract and risk. Decisions
        land on numbers nobody disputes.
      </p>
    </section>
  );
}

/* -------------------------------------------------------------------------- */
/*  Auth card                                                                  */
/* -------------------------------------------------------------------------- */

function Form() {
  const f = useLoginSubmit();
  return (
    <aside className="ed-rise ed-d4 relative mt-12 w-full max-w-[460px]">
      {/* Long warm shadow underlay */}
      <div
        aria-hidden
        className="pointer-events-none absolute -inset-x-2 -bottom-6 h-16 rounded-full"
        style={{
          background:
            "radial-gradient(ellipse 80% 100% at 50% 0%, rgba(124,90,31,0.15), transparent 70%)",
          filter: "blur(8px)",
        }}
      />
      <form
        onSubmit={f.handleSubmit}
        noValidate
        className="relative rounded-2xl border border-[#1A1F2C]/[0.08] bg-[#FDFBF6]/95 p-7 shadow-[0_30px_70px_-20px_rgba(26,31,44,0.22),0_8px_24px_-12px_rgba(124,90,31,0.18)] backdrop-blur-sm"
      >
        {/* Cream-on-cream nested header strip */}
        <div className="-mx-7 -mt-7 mb-6 rounded-t-2xl border-b border-[#1A1F2C]/[0.06] bg-[#F5EFE0]/55 px-7 py-5">
          <div className="flex items-start justify-between">
            <div>
              <div className="flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.24em] text-[#7C5A1F]">
                <span aria-hidden className="inline-block h-px w-5 bg-[#7C5A1F]/60" />
                Sign in
              </div>
              <h2
                className="mt-1.5 font-display text-[24px] font-medium leading-[1.1] tracking-[-0.014em] text-[#1A1F2C]"
                style={{ fontVariationSettings: "'opsz' 144" }}
              >
                Welcome back.
              </h2>
            </div>
            <span className="flex items-center gap-1 rounded border border-[#1A1F2C]/[0.10] bg-white/70 px-1.5 py-1 font-mono text-[9px] uppercase tracking-[0.18em] text-[#1A1F2C]/65">
              <Lock size={10} /> TLS
            </span>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-2">
          <SsoBtn label="Google" />
          <SsoBtn label="Microsoft" />
          <SsoBtn label="SAML" />
        </div>

        <div className="my-5 flex items-center gap-3 font-mono text-[9.5px] uppercase tracking-[0.20em] text-[#1A1F2C]/45">
          <div className="h-px flex-1 bg-[#1A1F2C]/10" />
          or with email
          <div className="h-px flex-1 bg-[#1A1F2C]/10" />
        </div>

        <div className="space-y-4">
          <div>
            <label
              htmlFor="ed-user"
              className="mb-1.5 block font-mono text-[10px] uppercase tracking-[0.18em] text-[#1A1F2C]/60"
            >
              Username or email
            </label>
            <input
              id="ed-user"
              type="text"
              autoComplete="username"
              autoFocus
              required
              value={f.username}
              onChange={(e) => f.setUsername(e.target.value)}
              disabled={f.submitting}
              placeholder="you@company.com"
              className="block h-11 w-full rounded-lg border border-[#1A1F2C]/15 bg-white px-3.5 text-[14px] text-[#1A1F2C] placeholder:text-[#1A1F2C]/30 transition focus:border-[#7C5A1F]/60 focus:outline-none focus:ring-2 focus:ring-[#7C5A1F]/20"
            />
          </div>
          <div>
            <div className="mb-1.5 flex items-baseline justify-between">
              <label
                htmlFor="ed-pwd"
                className="font-mono text-[10px] uppercase tracking-[0.18em] text-[#1A1F2C]/60"
              >
                Password
              </label>
              <button
                type="button"
                onClick={() => {}}
                className="text-[11.5px] font-medium text-[#7C5A1F] underline-offset-2 hover:underline"
              >
                Forgot?
              </button>
            </div>
            <div className="relative">
              <input
                id="ed-pwd"
                type={f.showPassword ? "text" : "password"}
                autoComplete="current-password"
                required
                value={f.password}
                onChange={(e) => f.setPassword(e.target.value)}
                disabled={f.submitting}
                placeholder="••••••••"
                className="block h-11 w-full rounded-lg border border-[#1A1F2C]/15 bg-white px-3.5 pr-10 text-[14px] text-[#1A1F2C] placeholder:text-[#1A1F2C]/30 transition focus:border-[#7C5A1F]/60 focus:outline-none focus:ring-2 focus:ring-[#7C5A1F]/20"
              />
              <button
                type="button"
                onClick={() => f.setShowPassword((v) => !v)}
                className="absolute inset-y-0 right-0 flex items-center px-3 text-[#1A1F2C]/55 transition hover:text-[#7C5A1F]"
                aria-label={f.showPassword ? "Hide password" : "Show password"}
                tabIndex={-1}
              >
                {f.showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>
          </div>
        </div>

        <label className="mt-3.5 flex cursor-pointer items-center gap-2 text-[12.5px] text-[#1A1F2C]/75">
          <input
            type="checkbox"
            checked={f.remember}
            onChange={(e) => f.setRemember(e.target.checked)}
            className="h-3.5 w-3.5 rounded border-[#1A1F2C]/30 accent-[#7C5A1F]"
          />
          Keep me signed in for 7 days
        </label>

        {f.fieldError && (
          <div
            role="alert"
            className="mt-4 rounded-lg border border-red-300/70 bg-red-50/90 px-3.5 py-2.5 text-[13px] text-red-800"
          >
            {f.fieldError}
          </div>
        )}

        <button
          type="submit"
          disabled={f.submitting}
          className="group relative mt-5 inline-flex h-12 w-full items-center justify-center gap-2 overflow-hidden rounded-lg text-[14.5px] font-semibold text-[#F8F2E4] transition disabled:opacity-60"
          style={{
            background: "linear-gradient(180deg,#3B3325,#27201A)",
            boxShadow: "0 12px 28px -8px rgba(39,32,26,0.45)",
          }}
        >
          {f.submitting ? (
            <>
              <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white/30 border-t-white" />
              Signing in…
            </>
          ) : (
            <>
              Sign in
              <ArrowUpRight
                size={15}
                className="transition group-hover:-translate-y-0.5 group-hover:translate-x-0.5"
              />
            </>
          )}
        </button>

        <div className="mt-6 flex items-center justify-between border-t border-[#1A1F2C]/10 pt-4">
          <span className="flex items-center gap-1.5 text-[11.5px] text-[#1A1F2C]/65">
            <ShieldCheck size={12} className="text-[#7C5A1F]" />
            JWT-bound · end-to-end
          </span>
          <div className="flex flex-wrap justify-end gap-1">
            {["SOC 2", "ISO 27001", "GDPR"].map((b) => (
              <span
                key={b}
                className="rounded border border-[#1A1F2C]/12 bg-white/60 px-1.5 py-0.5 font-mono text-[8.5px] uppercase tracking-[0.14em] text-[#1A1F2C]/65"
              >
                {b}
              </span>
            ))}
          </div>
        </div>
        <p className="mt-4 text-center text-[12.5px] text-[#1A1F2C]/65">
          New here?{" "}
          <Link
            href="/welcome"
            className="font-medium text-[#7C5A1F] underline-offset-4 hover:underline"
          >
            Take the tour →
          </Link>
        </p>
      </form>
    </aside>
  );
}

function SsoBtn({ label }: { label: string }) {
  return (
    <button
      type="button"
      onClick={() => {}}
      className="inline-flex h-10 items-center justify-center gap-1.5 rounded-[10px] border border-[#1A1F2C]/14 bg-white/70 text-[12px] font-medium text-[#1A1F2C]/85 transition hover:-translate-y-px hover:border-[#7C5A1F]/40 hover:bg-white"
    >
      {label}
    </button>
  );
}

/* -------------------------------------------------------------------------- */
/*  Pull-quote                                                                 */
/* -------------------------------------------------------------------------- */

function PullQuote() {
  return (
    <figure className="ed-rise ed-d5 mt-14 max-w-2xl text-center">
      <span aria-hidden className="mx-auto block h-px w-10 bg-[#1A1F2C]/30" />
      <blockquote
        className="mt-6 font-display text-[20px] italic leading-[1.5] tracking-[-0.005em] text-[#1A1F2C]/85 sm:text-[22px]"
        style={{ fontVariationSettings: "'opsz' 24" }}
      >
        “Daylight on the schedule. Risk reviews now end with a decision —
        not another action.”
      </blockquote>
      <figcaption className="mt-4 font-mono text-[10px] uppercase tracking-[0.22em] text-[#1A1F2C]/55">
        CPMO · Tier-1 European EPC · €4.2B portfolio
      </figcaption>
    </figure>
  );
}

/* -------------------------------------------------------------------------- */
/*  Footer                                                                     */
/* -------------------------------------------------------------------------- */

function Footer() {
  return (
    <footer className="relative border-t border-[#1A1F2C]/10 bg-[#F5F1EA]/70 px-6 py-5 backdrop-blur-sm">
      <div className="mx-auto flex max-w-6xl flex-col items-start justify-between gap-2 text-[11px] text-[#1A1F2C]/60 sm:flex-row sm:items-center">
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-1.5">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
            <span className="font-mono uppercase tracking-[0.18em]">All systems operational</span>
          </span>
          <span className="font-mono uppercase tracking-[0.16em] text-[#1A1F2C]/40">v0.1.0</span>
        </div>
        <div className="flex flex-wrap items-center gap-x-5 gap-y-1">
          {["SOC 2 Type II", "ISO 27001", "GDPR"].map((b) => (
            <span
              key={b}
              className="font-mono text-[10px] uppercase tracking-[0.18em] text-[#1A1F2C]/60"
            >
              {b}
            </span>
          ))}
          <Link href="#" className="hover:text-[#1A1F2C]">Privacy</Link>
          <Link href="#" className="hover:text-[#1A1F2C]">Terms</Link>
        </div>
      </div>
    </footer>
  );
}

/* -------------------------------------------------------------------------- */
/*  Keyframes                                                                  */
/* -------------------------------------------------------------------------- */

function Keyframes() {
  return (
    <style>{`
      @keyframes edRise { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: none; } }
      .ed-rise { animation: edRise .7s cubic-bezier(.22,1,.36,1) both; }
      .ed-d1 { animation-delay: .05s; }
      .ed-d2 { animation-delay: .14s; }
      .ed-d3 { animation-delay: .26s; }
      .ed-d4 { animation-delay: .38s; }
      .ed-d5 { animation-delay: .52s; }
      @media (prefers-reduced-motion: reduce) {
        .ed-rise { animation: none !important; }
      }
    `}</style>
  );
}
