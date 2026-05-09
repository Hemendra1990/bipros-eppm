"use client";

import { Suspense } from "react";
import Link from "next/link";
import { ArrowUpRight, Eye, EyeOff, Lock, ShieldCheck } from "lucide-react";
import { useLoginSubmit } from "../_shared/useLoginSubmit";

/**
 * Concept 5 — Premium Dark Enterprise.
 * Refined dark theme. Deep, layered blacks. One champagne accent.
 * Generous spacing. Editorial confidence without clutter.
 */
export default function DarkPage() {
  return (
    <div className="relative min-h-screen overflow-hidden bg-[#0A0A0B] font-sans text-[#EDEDEA] antialiased">
      <Atmosphere />
      <Header />
      <main className="relative grid min-h-[calc(100vh-72px)] grid-cols-1 gap-12 px-6 py-12 lg:grid-cols-[1.1fr_440px] lg:gap-20 lg:px-16 lg:py-20">
        <Editorial />
        <Suspense fallback={<div className="grid place-items-center text-[12px] text-white/50">Loading…</div>}>
          <Form />
        </Suspense>
      </main>
      <Footer />
      <Keyframes />
    </div>
  );
}

function Atmosphere() {
  return (
    <>
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 -z-10"
        style={{
          background:
            "radial-gradient(ellipse 70% 50% at 30% 20%, rgba(232,198,123,0.10), transparent 60%), radial-gradient(ellipse 60% 50% at 80% 90%, rgba(232,198,123,0.06), transparent 60%), #0A0A0B",
        }}
      />
      <div
        aria-hidden
        className="absolute inset-0 -z-10 opacity-[0.04]"
        style={{
          backgroundImage:
            "linear-gradient(#fff 1px,transparent 1px),linear-gradient(90deg,#fff 1px,transparent 1px)",
          backgroundSize: "72px 72px",
          maskImage: "radial-gradient(ellipse 90% 60% at 50% 30%,#000 30%,transparent 80%)",
          WebkitMaskImage: "radial-gradient(ellipse 90% 60% at 50% 30%,#000 30%,transparent 80%)",
        }}
      />
      {/* film grain */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 -z-10 opacity-[0.04] mix-blend-overlay"
        style={{
          backgroundImage:
            "url(\"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='200' height='200'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='2' stitchTiles='stitch'/><feColorMatrix values='0 0 0 0 1 0 0 0 0 1 0 0 0 0 1 0 0 0 0.6 0'/></filter><rect width='100%' height='100%' filter='url(%23n)'/></svg>\")",
        }}
      />
    </>
  );
}

function Header() {
  return (
    <header className="relative flex h-18 items-center justify-between border-b border-white/[0.06] px-6 py-5 lg:px-16">
      <Link href="/" className="flex items-center gap-3">
        <span
          aria-hidden
          className="grid h-8 w-8 place-items-center rounded-md text-[13px] font-bold"
          style={{ background: "linear-gradient(180deg,#E8C67B,#B8962E)", color: "#0A0A0B" }}
        >
          B
        </span>
        <div className="leading-tight">
          <div className="text-[15px] font-semibold tracking-[-0.01em]">Bipros</div>
          <div className="text-[10.5px] uppercase tracking-[0.20em] text-white/50">Enterprise EPPM</div>
        </div>
      </Link>
      <div className="hidden items-center gap-6 text-[13px] text-white/55 sm:flex">
        <Link href="#" className="hover:text-white">Capabilities</Link>
        <Link href="#" className="hover:text-white">Customers</Link>
        <Link href="#" className="hover:text-white">Trust</Link>
        <Link href="#" className="hover:text-white">Contact</Link>
      </div>
    </header>
  );
}

function Editorial() {
  return (
    <section className="relative max-w-2xl">
      <div className="dk-rise mb-5 inline-flex items-center gap-2 text-[10.5px] uppercase tracking-[0.24em] text-[#E8C67B]">
        <span aria-hidden className="inline-block h-px w-7 bg-[#E8C67B]" />
        For programme leaders
      </div>
      <h1
        className="dk-rise dk-d1 font-display text-[58px] font-medium leading-[1.02] tracking-[-0.025em] text-white sm:text-[68px]"
        style={{ fontVariationSettings: "'opsz' 144" }}
      >
        The quiet
        <span className="block italic font-normal text-[#E8C67B]">infrastructure</span>
        of delivery.
      </h1>
      <p className="dk-rise dk-d2 mt-7 max-w-lg text-[15px] leading-[1.7] text-white/60">
        Bipros is the operational layer that holds schedule, cost, contract and
        risk in a single accountable surface — so leadership decisions land
        on numbers nobody disputes.
      </p>

      <div className="dk-rise dk-d3 mt-12 grid grid-cols-3 gap-px overflow-hidden rounded-2xl border border-white/[0.08] bg-white/[0.03]">
        {STATS.map((s) => (
          <div key={s.label} className="bg-[#0E0E10] p-5">
            <div className="font-display text-[28px] font-medium leading-none text-white tabular-nums" style={{ fontVariationSettings: "'opsz' 144" }}>
              {s.value}
            </div>
            <div className="mt-2 text-[11.5px] uppercase tracking-[0.16em] text-white/50">{s.label}</div>
          </div>
        ))}
      </div>

      <figure className="dk-rise dk-d4 mt-12 max-w-xl border-l-2 border-[#E8C67B]/40 pl-6">
        <blockquote
          className="font-display text-[22px] leading-[1.4] tracking-[-0.005em] text-white/85"
          style={{ fontVariationSettings: "'opsz' 24" }}
        >
          “We replaced four governance dashboards with one. Risk reviews now
          take half a meeting and end with a decision, not another action.”
        </blockquote>
        <figcaption className="mt-4 flex items-center gap-2 font-mono text-[10.5px] uppercase tracking-[0.18em] text-white/45">
          <span aria-hidden className="inline-block h-px w-5 bg-[#E8C67B]" />
          CPMO · Tier-1 European EPC · €4.2B portfolio
        </figcaption>
      </figure>

      <div className="dk-rise dk-d5 mt-10 flex flex-wrap items-center gap-x-9 gap-y-3 text-[13.5px] font-medium tracking-[0.01em] text-white/45">
        <span className="font-mono text-[10.5px] uppercase tracking-[0.20em] text-white/35">Trusted by</span>
        {["Network Rail", "Ørsted", "Bechtel", "AECOM", "Skanska"].map((n) => (
          <span key={n} className="transition-colors hover:text-white">{n}</span>
        ))}
      </div>
    </section>
  );
}

function Form() {
  const f = useLoginSubmit();
  return (
    <aside className="dk-rise dk-d1 relative">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-7 top-0 h-px"
        style={{ background: "linear-gradient(90deg,transparent,#E8C67B,transparent)" }}
      />
      <form
        onSubmit={f.handleSubmit}
        noValidate
        className="relative rounded-2xl border border-white/[0.08] bg-[#0E0E10]/90 p-7 shadow-[0_30px_80px_rgba(0,0,0,0.55)] backdrop-blur"
      >
        <div className="flex items-start justify-between">
          <div>
            <div className="flex items-center gap-2 text-[10.5px] uppercase tracking-[0.22em] text-[#E8C67B]">
              <span aria-hidden className="inline-block h-px w-5 bg-[#E8C67B]" />
              Sign in
            </div>
            <h2
              className="mt-2 font-display text-[28px] font-medium leading-[1.05] tracking-[-0.015em] text-white"
              style={{ fontVariationSettings: "'opsz' 144" }}
            >
              Welcome back.
            </h2>
            <p className="mt-1 text-[13px] text-white/55">Use your work account to continue.</p>
          </div>
          <span className="flex items-center gap-1 rounded border border-white/[0.10] bg-white/[0.03] px-1.5 py-1 font-mono text-[9px] uppercase tracking-[0.18em] text-white/60">
            <Lock size={10} /> TLS
          </span>
        </div>

        <div className="mt-6 grid grid-cols-3 gap-2">
          <SsoBtn label="Google" />
          <SsoBtn label="Microsoft" />
          <SsoBtn label="SAML" />
        </div>

        <div className="my-5 flex items-center gap-3 text-[10px] uppercase tracking-[0.18em] text-white/40">
          <div className="h-px flex-1 bg-white/[0.10]" />
          or with email
          <div className="h-px flex-1 bg-white/[0.10]" />
        </div>

        <div className="space-y-4">
          <div>
            <label htmlFor="dk-user" className="mb-1.5 block text-[10.5px] uppercase tracking-[0.16em] text-white/55">
              Username or email
            </label>
            <input
              id="dk-user"
              type="text"
              autoComplete="username"
              autoFocus
              required
              value={f.username}
              onChange={(e) => f.setUsername(e.target.value)}
              disabled={f.submitting}
              placeholder="you@company.com"
              className="block h-11 w-full rounded-xl border border-white/[0.10] bg-black/30 px-3.5 text-[14px] text-white placeholder:text-white/30 transition focus:border-[#E8C67B]/60 focus:bg-black/50 focus:outline-none focus:ring-2 focus:ring-[#E8C67B]/25"
            />
          </div>
          <div>
            <div className="mb-1.5 flex items-baseline justify-between">
              <label htmlFor="dk-pwd" className="text-[10.5px] uppercase tracking-[0.16em] text-white/55">Password</label>
              <button type="button" onClick={() => {}} className="text-[11.5px] font-medium text-[#E8C67B] hover:text-white">Forgot?</button>
            </div>
            <div className="relative">
              <input
                id="dk-pwd"
                type={f.showPassword ? "text" : "password"}
                autoComplete="current-password"
                required
                value={f.password}
                onChange={(e) => f.setPassword(e.target.value)}
                disabled={f.submitting}
                placeholder="••••••••"
                className="block h-11 w-full rounded-xl border border-white/[0.10] bg-black/30 px-3.5 pr-10 text-[14px] text-white placeholder:text-white/30 transition focus:border-[#E8C67B]/60 focus:bg-black/50 focus:outline-none focus:ring-2 focus:ring-[#E8C67B]/25"
              />
              <button
                type="button"
                onClick={() => f.setShowPassword((v) => !v)}
                className="absolute inset-y-0 right-0 flex items-center px-3 text-white/50 hover:text-[#E8C67B]"
                aria-label={f.showPassword ? "Hide password" : "Show password"}
                tabIndex={-1}
              >
                {f.showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>
          </div>
        </div>

        <label className="mt-3.5 flex cursor-pointer items-center gap-2 text-[12.5px] text-white/65">
          <input
            type="checkbox"
            checked={f.remember}
            onChange={(e) => f.setRemember(e.target.checked)}
            className="h-3.5 w-3.5 rounded border-white/30 accent-[#E8C67B]"
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
          className="group relative mt-5 inline-flex h-12 w-full items-center justify-center gap-2 overflow-hidden rounded-xl text-[14.5px] font-semibold text-[#0A0A0B] transition disabled:opacity-60"
          style={{
            background: "linear-gradient(180deg,#F0CF7E,#E8C67B 60%,#B8962E)",
            boxShadow: "0 14px 36px rgba(232,198,123,0.25)",
          }}
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
          <span className="flex items-center gap-1.5 text-[11.5px] text-white/55">
            <ShieldCheck size={12} className="text-[#E8C67B]" />
            JWT-bound · end-to-end
          </span>
          <div className="flex flex-wrap justify-end gap-1">
            {["SOC 2", "ISO 27001", "GDPR"].map((b) => (
              <span key={b} className="rounded border border-white/[0.10] bg-white/[0.03] px-1.5 py-0.5 font-mono text-[8.5px] uppercase tracking-[0.14em] text-white/55">
                {b}
              </span>
            ))}
          </div>
        </div>
        <p className="mt-4 text-center text-[12.5px] text-white/55">
          New to Bipros EPPM?{" "}
          <Link href="/welcome" className="font-medium text-[#E8C67B] hover:text-white">Take the tour →</Link>
        </p>
      </form>
    </aside>
  );
}

function SsoBtn({ label }: { label: string }) {
  return (
    <button
      type="button"
      className="inline-flex h-10 items-center justify-center gap-1.5 rounded-[10px] border border-white/[0.10] bg-white/[0.03] text-[11.5px] font-medium text-white/85 transition hover:-translate-y-px hover:border-[#E8C67B]/40 hover:bg-white/[0.06]"
    >
      {label}
    </button>
  );
}

function Footer() {
  return (
    <footer className="relative border-t border-white/[0.06] px-6 py-5 lg:px-16">
      <div className="flex flex-col items-start justify-between gap-2 text-[11.5px] text-white/45 sm:flex-row sm:items-center">
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-1.5"><span className="h-1.5 w-1.5 rounded-full bg-emerald-400" /> All systems operational</span>
          <span className="font-mono uppercase tracking-[0.16em] text-white/35">v0.1.0</span>
        </div>
        <div className="flex flex-wrap gap-x-6 gap-y-1">
          <Link href="#" className="hover:text-white">Privacy</Link>
          <Link href="#" className="hover:text-white">Terms</Link>
          <Link href="#" className="hover:text-white">Status</Link>
          <Link href="#" className="hover:text-white">Contact</Link>
        </div>
      </div>
    </footer>
  );
}

const STATS: Array<{ value: string; label: string }> = [
  { value: "€18B+", label: "Capital under control" },
  { value: "184K", label: "Activities tracked" },
  { value: "94%", label: "Schedule adherence" },
];

function Keyframes() {
  return (
    <style>{`
      @keyframes dkRise { from { opacity:0; transform: translateY(10px); } to { opacity:1; transform:none; } }
      .dk-rise { animation: dkRise .65s cubic-bezier(.22,1,.36,1) both; }
      .dk-d1 { animation-delay: .07s; }
      .dk-d2 { animation-delay: .15s; }
      .dk-d3 { animation-delay: .25s; }
      .dk-d4 { animation-delay: .36s; }
      .dk-d5 { animation-delay: .48s; }
      @media (prefers-reduced-motion: reduce) { .dk-rise { animation: none !important; } }
    `}</style>
  );
}
