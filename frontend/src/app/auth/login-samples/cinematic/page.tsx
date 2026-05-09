"use client";

import { Suspense } from "react";
import Link from "next/link";
import { ArrowUpRight, Eye, EyeOff, Lock, ShieldCheck } from "lucide-react";
import { useLoginSubmit } from "../_shared/useLoginSubmit";

/**
 * Concept 6 — Site of Record · Cinematic Construction Field.
 * Inline-SVG twilight skyline with silhouetted tower cranes and a half-built
 * tower frame. Editorial column overlays the photograph; auth panel is a
 * refined glass card pinned to the right.
 */
export default function CinematicPage() {
  return (
    <div className="relative min-h-screen overflow-hidden bg-[#0B1224] font-sans text-[#EDEDEA] antialiased">
      <Header />
      <main className="relative grid min-h-[calc(100vh-72px)] grid-cols-1 lg:grid-cols-[1.5fr_1fr]">
        <Backdrop />
        <Suspense
          fallback={
            <div className="grid place-items-center px-6 py-12 text-[12px] text-white/50 lg:px-12">
              Loading…
            </div>
          }
        >
          <Form />
        </Suspense>
      </main>
      <Keyframes />
    </div>
  );
}

/* ---------------------------------------------------------------- Header */

function Header() {
  return (
    <header className="relative z-30 flex h-18 items-center justify-between border-b border-white/[0.06] px-6 py-5 lg:px-12">
      <Link href="/" className="flex items-center gap-3">
        <span
          aria-hidden
          className="grid h-8 w-8 place-items-center rounded-md text-[13px] font-bold"
          style={{
            background: "linear-gradient(180deg,#F4B36A,#C97A3A)",
            color: "#0B1224",
          }}
        >
          B
        </span>
        <div className="leading-tight">
          <div className="text-[15px] font-semibold tracking-[-0.01em]">Bipros</div>
          <div className="text-[10.5px] uppercase tracking-[0.20em] text-white/55">
            Site of Record
          </div>
        </div>
      </Link>
      <div className="hidden items-center gap-3 text-[11px] uppercase tracking-[0.18em] text-white/55 sm:flex">
        <span className="flex items-center gap-1.5">
          <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-400" />
          47 sites live
        </span>
        <span className="hidden font-mono text-white/35 md:inline">·</span>
        <span className="hidden md:inline">18:42 UTC</span>
      </div>
    </header>
  );
}

/* -------------------------------------------------- Cinematic backdrop */

function Backdrop() {
  return (
    <section className="relative isolate min-h-[60vh] overflow-hidden lg:min-h-full">
      {/* Sky gradient base */}
      <div aria-hidden className="absolute inset-0" style={{ background: "linear-gradient(180deg,#0B1224 0%,#16243F 42%,#3A2A1F 78%,#C97A3A 100%)" }} />
      {/* Soft sun glow on the horizon */}
      <div aria-hidden className="absolute inset-0" style={{ background: "radial-gradient(ellipse 50% 28% at 70% 78%, rgba(244,179,106,0.55), transparent 65%)" }} />
      {/* Dust haze across the lower half */}
      <div aria-hidden className="absolute inset-0" style={{ background: "radial-gradient(ellipse 90% 40% at 50% 95%, rgba(201,122,58,0.30), transparent 70%)" }} />

      {/* The construction scene */}
      <Skyline />

      {/* 1px grid overlay (architectural) */}
      <div
        aria-hidden
        className="absolute inset-0 opacity-[0.06]"
        style={{
          backgroundImage: "linear-gradient(#fff 1px,transparent 1px),linear-gradient(90deg,#fff 1px,transparent 1px)",
          backgroundSize: "64px 64px",
          maskImage: "radial-gradient(ellipse 80% 60% at 40% 30%,#000 20%,transparent 80%)",
          WebkitMaskImage: "radial-gradient(ellipse 80% 60% at 40% 30%,#000 20%,transparent 80%)",
        }}
      />

      {/* Film grain */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 opacity-[0.05] mix-blend-overlay"
        style={{
          backgroundImage:
            "url(\"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='220' height='220'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2' stitchTiles='stitch'/><feColorMatrix values='0 0 0 0 1 0 0 0 0 1 0 0 0 0 1 0 0 0 0.6 0'/></filter><rect width='100%' height='100%' filter='url(%23n)'/></svg>\")",
        }}
      />

      {/* Vignette right edge so the auth card lands on a darker scrim on lg */}
      <div aria-hidden className="pointer-events-none absolute inset-0 hidden lg:block" style={{ background: "linear-gradient(90deg,transparent 60%,rgba(11,18,36,0.55) 100%)" }} />

      <Editorial />
    </section>
  );
}

function Skyline() {
  return (
    <svg aria-hidden viewBox="0 0 1600 1000" preserveAspectRatio="xMidYMax slice" className="absolute inset-0 h-full w-full">
      <defs>
        <linearGradient id="cn-tower" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0" stopColor="#0B1224" stopOpacity="0.1" />
          <stop offset="1" stopColor="#050810" stopOpacity="0.95" />
        </linearGradient>
        <linearGradient id="cn-city" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0" stopColor="#1B2238" stopOpacity="0.85" />
          <stop offset="1" stopColor="#070B17" stopOpacity="1" />
        </linearGradient>
        <radialGradient id="cn-lamp" cx="0.5" cy="0.5" r="0.5">
          <stop offset="0" stopColor="#FFE3B0" stopOpacity="1" />
          <stop offset="0.4" stopColor="#FFB35E" stopOpacity="0.55" />
          <stop offset="1" stopColor="#FFB35E" stopOpacity="0" />
        </radialGradient>
      </defs>

      {/* Distant city skyline strip */}
      <path
        fill="url(#cn-city)"
        d="M0,720 L0,820 L60,820 L60,760 L120,760 L120,790 L180,790 L180,740 L240,740 L240,800 L300,800 L300,760 L360,760 L360,810 L420,810 L420,770 L500,770 L500,800 L560,800 L560,750 L620,750 L620,790 L700,790 L700,760 L780,760 L780,810 L840,810 L840,775 L920,775 L920,800 L1000,800 L1000,755 L1080,755 L1080,790 L1160,790 L1160,770 L1240,770 L1240,810 L1320,810 L1320,775 L1400,775 L1400,800 L1480,800 L1480,765 L1600,765 L1600,820 Z"
      />

      {/* Crane #1 — far left, smallest */}
      <g stroke="#070B17" strokeWidth="2" fill="#070B17" opacity="0.85">
        <rect x="138" y="380" width="6" height="360" />
        <rect x="60" y="372" width="240" height="4" />
        <rect x="40" y="378" width="60" height="6" />
        <rect x="124" y="368" width="22" height="14" />
        <line x1="220" y1="376" x2="220" y2="500" strokeWidth="1.5" />
        <rect x="216" y="500" width="8" height="6" />
      </g>

      {/* Half-built tower — ribbed steel skeleton */}
      <rect x="420" y="300" width="220" height="440" fill="#050810" opacity="0.92" />
      <g stroke="#2A3550" strokeWidth="1.2" opacity="0.85">
        {[340, 380, 420, 460, 500, 540, 580, 620, 660, 700].map((y) => (
          <line key={`fp-${y}`} x1="420" y1={y} x2="640" y2={y} />
        ))}
        {[450, 490, 530, 570, 610].map((x) => (
          <line key={`col-${x}`} x1={x} y1="300" x2={x} y2="740" />
        ))}
      </g>
      {/* warm interior lights */}
      <g fill="#FFB35E" opacity="0.75">
        <rect x="494" y="424" width="6" height="6" />
        <rect x="574" y="464" width="6" height="6" />
        <rect x="454" y="544" width="6" height="6" />
        <rect x="614" y="624" width="6" height="6" />
        <rect x="534" y="684" width="6" height="6" />
      </g>
      <rect x="420" y="296" width="220" height="6" fill="#0B1224" />
      <rect x="420" y="290" width="120" height="8" fill="#0B1224" />

      {/* Crane #2 — tallest, centre, atop the tower */}
      <g stroke="#040711" strokeWidth="2.5" fill="#040711">
        <rect x="524" y="80" width="8" height="220" />
        <rect x="380" y="74" width="320" height="5" />
        <rect x="360" y="80" width="80" height="8" />
        <rect x="510" y="68" width="26" height="16" />
        <line x1="528" y1="60" x2="700" y2="79" strokeWidth="1.5" stroke="#1B2238" />
        <line x1="528" y1="60" x2="360" y2="80" strokeWidth="1.5" stroke="#1B2238" />
        <rect x="525" y="56" width="6" height="10" />
        <line x1="640" y1="79" x2="640" y2="240" strokeWidth="1.5" />
        <rect x="634" y="240" width="12" height="8" />
      </g>
      {/* Working crane lamp — single point of light */}
      <circle cx="640" cy="244" r="42" fill="url(#cn-lamp)" />
      <circle cx="640" cy="244" r="3" fill="#FFE3B0" />

      {/* Crane #3 — mid-right, medium height */}
      <g stroke="#050810" strokeWidth="2" fill="#050810" opacity="0.92">
        <rect x="980" y="240" width="7" height="500" />
        <rect x="850" y="232" width="280" height="4" />
        <rect x="824" y="238" width="60" height="7" />
        <rect x="966" y="226" width="24" height="14" />
        <line x1="1060" y1="236" x2="1060" y2="380" strokeWidth="1.5" />
        <rect x="1054" y="380" width="12" height="7" />
      </g>

      {/* Crane #4 — far right, second tallest */}
      <g stroke="#040711" strokeWidth="2.5" fill="#040711">
        <rect x="1320" y="160" width="8" height="580" />
        <rect x="1180" y="152" width="320" height="5" />
        <rect x="1158" y="158" width="68" height="8" />
        <rect x="1308" y="146" width="26" height="16" />
        <line x1="1324" y1="138" x2="1500" y2="157" strokeWidth="1.5" stroke="#1B2238" />
        <line x1="1324" y1="138" x2="1158" y2="158" strokeWidth="1.5" stroke="#1B2238" />
        <rect x="1321" y="134" width="6" height="10" />
        <line x1="1400" y1="157" x2="1400" y2="320" strokeWidth="1.5" />
        <rect x="1394" y="320" width="12" height="8" />
      </g>

      {/* Crane #5 — short utility crane */}
      <g stroke="#050810" strokeWidth="2" fill="#050810" opacity="0.85">
        <rect x="1148" y="450" width="5" height="290" />
        <rect x="1060" y="444" width="180" height="3" />
        <rect x="1042" y="448" width="46" height="5" />
        <rect x="1138" y="438" width="20" height="12" />
        <line x1="1200" y1="447" x2="1200" y2="540" strokeWidth="1.2" />
        <rect x="1196" y="540" width="8" height="5" />
      </g>

      {/* Foreground ground */}
      <rect x="0" y="740" width="1600" height="260" fill="url(#cn-tower)" />
      <rect x="0" y="740" width="1600" height="2" fill="#040711" />

      {/* Distant specs */}
      <circle cx="1080" cy="120" r="1.4" fill="#FFE3B0" opacity="0.7" />
      <circle cx="280" cy="180" r="1" fill="#FFE3B0" opacity="0.5" />
    </svg>
  );
}

/* ----------------------------------------------------- Editorial column */

function Editorial() {
  return (
    <div className="relative z-10 flex h-full flex-col justify-end px-6 pb-10 pt-16 lg:px-14 lg:pb-16 lg:pt-24">
      <div className="cn-rise cn-d1 mb-5 inline-flex items-center gap-2 text-[10.5px] uppercase tracking-[0.24em] text-[#F4B36A]">
        <span aria-hidden className="inline-block h-px w-7 bg-[#F4B36A]" />
        Site of record · twilight, on schedule
      </div>
      <h1
        className="cn-rise cn-d2 max-w-2xl text-[44px] font-medium leading-[1.04] tracking-[-0.025em] text-white sm:text-[56px] lg:text-[64px]"
        style={{ fontVariationSettings: "'opsz' 144" }}
      >
        Behind every skyline,
        <span className="block italic font-normal text-[#F4B36A]">a single source of truth.</span>
      </h1>
      <p className="cn-rise cn-d3 mt-6 max-w-xl text-[14.5px] leading-[1.7] text-white/70">
        Bipros holds schedule, cost, contract and risk for the world&apos;s most
        demanding capital programmes — so the people closing the day shift,
        and the people closing the books, see the same numbers.
      </p>

      <div className="cn-rise cn-d4 mt-8 flex max-w-xl flex-wrap items-end gap-x-10 gap-y-4 border-t border-white/[0.10] pt-6">
        {STATS.map((s) => (
          <div key={s.label}>
            <div
              className="text-[26px] font-medium leading-none text-white tabular-nums sm:text-[30px]"
              style={{ fontVariationSettings: "'opsz' 144" }}
            >
              {s.value}
            </div>
            <div className="mt-1.5 text-[10.5px] uppercase tracking-[0.18em] text-white/55">
              {s.label}
            </div>
          </div>
        ))}
      </div>

      <figure className="cn-rise cn-d5 mt-8 hidden max-w-lg border-l-2 border-[#F4B36A]/50 pl-5 lg:block">
        <blockquote className="text-[15.5px] leading-[1.55] text-white/85">
          &ldquo;The crane comes down at 19:00. The numbers settle by 19:02.
          Bipros is the only system that keeps up with the site.&rdquo;
        </blockquote>
        <figcaption className="mt-3 flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.18em] text-white/55">
          <span aria-hidden className="inline-block h-px w-5 bg-[#F4B36A]" />
          Programme Director · Tower One · Riyadh
        </figcaption>
      </figure>
    </div>
  );
}

const STATS: Array<{ value: string; label: string }> = [
  { value: "€18B+", label: "Capital under control" },
  { value: "184K", label: "Activities tracked" },
  { value: "47", label: "Active sites" },
];

/* ---------------------------------------------------------- Auth panel */

function Form() {
  const f = useLoginSubmit();
  return (
    <aside className="relative flex items-center justify-center px-6 py-10 lg:px-10 lg:py-16">
      {/* Scrim behind the card so it stays readable on top of the photo on mobile */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 lg:hidden"
        style={{
          background:
            "linear-gradient(180deg,rgba(11,18,36,0.92),rgba(11,18,36,0.97))",
        }}
      />
      <div className="cn-rise cn-d2 relative w-full max-w-[420px]">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-x-7 top-0 h-px"
          style={{
            background:
              "linear-gradient(90deg,transparent,#F4B36A,transparent)",
          }}
        />
        <form
          onSubmit={f.handleSubmit}
          noValidate
          className="relative rounded-2xl border border-white/[0.10] bg-[#0B1224]/75 p-7 shadow-[0_40px_90px_rgba(0,0,0,0.55)] backdrop-blur-xl"
        >
          <div className="flex items-start justify-between">
            <div>
              <div className="flex items-center gap-2 text-[10.5px] uppercase tracking-[0.22em] text-[#F4B36A]">
                <span aria-hidden className="inline-block h-px w-5 bg-[#F4B36A]" />
                Sign in
              </div>
              <h2 className="mt-2 text-[28px] font-medium leading-[1.05] tracking-[-0.015em] text-white" style={{ fontVariationSettings: "'opsz' 144" }}>
                Welcome back.
              </h2>
              <p className="mt-1 text-[13px] text-white/60">Sign in to your operational layer.</p>
            </div>
            <span className="flex items-center gap-1 rounded border border-white/[0.10] bg-white/[0.04] px-1.5 py-1 font-mono text-[9px] uppercase tracking-[0.18em] text-white/65">
              <Lock size={10} /> TLS
            </span>
          </div>

          <div className="mt-6 grid grid-cols-3 gap-2">
            <SsoBtn label="Google" />
            <SsoBtn label="Microsoft" />
            <SsoBtn label="SAML" />
          </div>

          <div className="my-5 flex items-center gap-3 text-[10px] uppercase tracking-[0.18em] text-white/45">
            <div className="h-px flex-1 bg-white/[0.10]" />
            or with email
            <div className="h-px flex-1 bg-white/[0.10]" />
          </div>

          <div className="space-y-4">
            <div>
              <label htmlFor="cn-user" className="mb-1.5 block text-[10.5px] uppercase tracking-[0.16em] text-white/60">
                Username or email
              </label>
              <input
                id="cn-user"
                type="text"
                autoComplete="username"
                autoFocus
                required
                value={f.username}
                onChange={(e) => f.setUsername(e.target.value)}
                disabled={f.submitting}
                placeholder="you@company.com"
                className="block h-11 w-full rounded-xl border border-white/[0.10] bg-black/30 px-3.5 text-[14px] text-white placeholder:text-white/30 transition focus:border-[#F4B36A]/60 focus:bg-black/50 focus:outline-none focus:ring-2 focus:ring-[#F4B36A]/25"
              />
            </div>
            <div>
              <div className="mb-1.5 flex items-baseline justify-between">
                <label htmlFor="cn-pwd" className="text-[10.5px] uppercase tracking-[0.16em] text-white/60">Password</label>
                <button type="button" onClick={() => {}} className="text-[11.5px] font-medium text-[#F4B36A] hover:text-white">
                  Forgot?
                </button>
              </div>
              <div className="relative">
                <input
                  id="cn-pwd"
                  type={f.showPassword ? "text" : "password"}
                  autoComplete="current-password"
                  required
                  value={f.password}
                  onChange={(e) => f.setPassword(e.target.value)}
                  disabled={f.submitting}
                  placeholder="••••••••"
                  className="block h-11 w-full rounded-xl border border-white/[0.10] bg-black/30 px-3.5 pr-10 text-[14px] text-white placeholder:text-white/30 transition focus:border-[#F4B36A]/60 focus:bg-black/50 focus:outline-none focus:ring-2 focus:ring-[#F4B36A]/25"
                />
                <button
                  type="button"
                  onClick={() => f.setShowPassword((v) => !v)}
                  className="absolute inset-y-0 right-0 flex items-center px-3 text-white/50 hover:text-[#F4B36A]"
                  aria-label={f.showPassword ? "Hide password" : "Show password"}
                  tabIndex={-1}
                >
                  {f.showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              </div>
            </div>
          </div>

          <label className="mt-3.5 flex cursor-pointer items-center gap-2 text-[12.5px] text-white/70">
            <input
              type="checkbox"
              checked={f.remember}
              onChange={(e) => f.setRemember(e.target.checked)}
              className="h-3.5 w-3.5 rounded border-white/30 accent-[#F4B36A]"
            />
            Keep me signed in for 7 days
          </label>

          {f.fieldError && (
            <div role="alert" className="mt-4 rounded-xl border border-red-500/30 bg-red-500/10 px-3.5 py-2.5 text-[13px] text-red-200">
              {f.fieldError}
            </div>
          )}

          <button
            type="submit"
            disabled={f.submitting}
            className="group relative mt-5 inline-flex h-12 w-full items-center justify-center gap-2 overflow-hidden rounded-xl text-[14.5px] font-semibold text-[#0B1224] transition disabled:opacity-60"
            style={{ background: "linear-gradient(180deg,#FFC988,#F4B36A 55%,#C97A3A)", boxShadow: "0 18px 40px rgba(244,179,106,0.28)" }}
          >
            {f.submitting ? (
              <>
                <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-black/30 border-t-black" />
                Signing in…
              </>
            ) : (
              <>
                Sign in
                <ArrowUpRight size={15} className="transition group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
              </>
            )}
          </button>

          <div className="mt-6 flex items-center justify-between border-t border-white/[0.08] pt-4">
            <span className="flex items-center gap-1.5 text-[11.5px] text-white/60">
              <ShieldCheck size={12} className="text-[#F4B36A]" />
              JWT-bound · end-to-end
            </span>
            <div className="flex flex-wrap justify-end gap-1">
              {["SOC 2", "ISO 27001", "GDPR"].map((b) => (
                <span key={b} className="rounded border border-white/[0.10] bg-white/[0.04] px-1.5 py-0.5 font-mono text-[8.5px] uppercase tracking-[0.14em] text-white/60">
                  {b}
                </span>
              ))}
            </div>
          </div>
          <p className="mt-4 text-center text-[12.5px] text-white/60">
            New here?{" "}
            <Link href="/welcome" className="font-medium text-[#F4B36A] hover:text-white">Take the tour →</Link>
          </p>
        </form>
      </div>
    </aside>
  );
}

function SsoBtn({ label }: { label: string }) {
  return (
    <button
      type="button"
      onClick={() => {}}
      className="inline-flex h-10 items-center justify-center gap-1.5 rounded-[10px] border border-white/[0.10] bg-white/[0.04] text-[11.5px] font-medium text-white/85 transition hover:-translate-y-px hover:border-[#F4B36A]/40 hover:bg-white/[0.07]"
    >
      {label}
    </button>
  );
}

/* --------------------------------------------------------- Animations */

function Keyframes() {
  return (
    <style>{`
      @keyframes cnRise { from { opacity:0; transform: translateY(12px); } to { opacity:1; transform:none; } }
      .cn-rise { animation: cnRise .7s cubic-bezier(.22,1,.36,1) both; }
      .cn-d1 { animation-delay: .05s; }
      .cn-d2 { animation-delay: .14s; }
      .cn-d3 { animation-delay: .24s; }
      .cn-d4 { animation-delay: .34s; }
      .cn-d5 { animation-delay: .46s; }
      @media (prefers-reduced-motion: reduce) { .cn-rise { animation: none !important; } }
    `}</style>
  );
}
