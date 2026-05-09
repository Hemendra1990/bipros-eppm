"use client";

import { Suspense } from "react";
import Link from "next/link";
import { ArrowRight, Eye, EyeOff, Sparkles, Brain, Zap, Lock } from "lucide-react";
import { useLoginSubmit } from "../_shared/useLoginSubmit";

/**
 * Concept 4 — AI-Powered Programme Intelligence.
 * Modern AI-native enterprise UX: a live insights feed previews the
 * value the user gets after sign-in. Restrained gradient mesh,
 * indigo + cyan accents, soft monochrome surface.
 */
export default function AiPage() {
  return (
    <div className="relative min-h-screen overflow-hidden bg-[#0B0E1A] font-sans text-white antialiased">
      <Mesh />
      <Header />
      <main className="relative grid min-h-[calc(100vh-64px)] grid-cols-1 gap-8 px-6 py-10 lg:grid-cols-[1fr_440px] lg:gap-14 lg:px-12 lg:py-14">
        <Insights />
        <Suspense fallback={<div className="grid place-items-center text-[12px] text-white/50">Loading…</div>}>
          <AuthCard />
        </Suspense>
      </main>
      <Keyframes />
    </div>
  );
}

function Mesh() {
  return (
    <>
      <div
        aria-hidden
        className="pointer-events-none absolute -top-32 -left-24 -z-10 h-[640px] w-[640px] rounded-full opacity-60 blur-3xl"
        style={{
          background:
            "radial-gradient(circle, rgba(99,102,241,0.45) 0%, rgba(99,102,241,0) 60%)",
        }}
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -bottom-40 -right-24 -z-10 h-[680px] w-[680px] rounded-full opacity-50 blur-3xl"
        style={{
          background:
            "radial-gradient(circle, rgba(34,211,238,0.40) 0%, rgba(34,211,238,0) 65%)",
        }}
      />
      <div
        aria-hidden
        className="absolute inset-0 -z-10 opacity-[0.05]"
        style={{
          backgroundImage:
            "linear-gradient(#fff 1px,transparent 1px),linear-gradient(90deg,#fff 1px,transparent 1px)",
          backgroundSize: "48px 48px",
          maskImage: "radial-gradient(ellipse 100% 70% at 50% 30%,#000 30%,transparent 80%)",
          WebkitMaskImage: "radial-gradient(ellipse 100% 70% at 50% 30%,#000 30%,transparent 80%)",
        }}
      />
    </>
  );
}

function Header() {
  return (
    <header className="relative flex h-16 items-center justify-between border-b border-white/[0.06] px-6 backdrop-blur lg:px-12">
      <Link href="/" className="flex items-center gap-2.5">
        <span
          aria-hidden
          className="grid h-7 w-7 place-items-center rounded-lg text-[12px] font-bold text-white"
          style={{ background: "linear-gradient(135deg,#6366F1,#22D3EE)" }}
        >
          B
        </span>
        <span className="text-[14.5px] font-semibold tracking-[-0.01em]">Bipros</span>
        <span className="rounded-full border border-white/15 bg-white/[0.04] px-2 py-0.5 text-[10px] uppercase tracking-[0.16em] text-white/70">
          EPPM · AI
        </span>
      </Link>
      <div className="hidden items-center gap-2 rounded-full border border-white/[0.10] bg-white/[0.04] px-3 py-1.5 text-[12px] text-white/70 sm:flex">
        <span aria-hidden className="h-1.5 w-1.5 animate-pulse rounded-full bg-cyan-300" />
        Programme intelligence engine · v0.1
      </div>
    </header>
  );
}

function Insights() {
  return (
    <section className="relative">
      <div className="ai-rise mb-3 inline-flex items-center gap-2 rounded-full border border-white/[0.10] bg-white/[0.04] px-3 py-1 text-[11px] uppercase tracking-[0.16em] text-white/70">
        <Sparkles size={11} className="text-cyan-300" /> Programme Intelligence
      </div>
      <h1 className="ai-rise ai-d1 max-w-2xl text-[44px] font-semibold leading-[1.05] tracking-[-0.025em] text-white">
        See what your portfolio is{" "}
        <span
          className="bg-clip-text text-transparent"
          style={{ backgroundImage: "linear-gradient(90deg,#A5B4FC,#67E8F9)" }}
        >
          already telling you.
        </span>
      </h1>
      <p className="ai-rise ai-d2 mt-4 max-w-xl text-[14.5px] leading-[1.65] text-white/65">
        Bipros analyses schedule drift, cost variance and resource conflicts every
        few minutes — and surfaces the few decisions that move the needle.
        Sign in to your tenant&apos;s intelligence stream.
      </p>

      {/* Insight feed */}
      <div className="ai-rise ai-d3 mt-9">
        <div className="mb-3 flex items-center justify-between text-[11px] uppercase tracking-[0.18em] text-white/50">
          <span className="flex items-center gap-1.5"><Brain size={12} className="text-indigo-300" /> Live insights · last 24h</span>
          <span className="flex items-center gap-1.5"><span className="h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-400" />Updated 38s ago</span>
        </div>
        <div className="space-y-2.5">
          {INSIGHTS.map((it, i) => (
            <Insight key={it.id} item={it} index={i} />
          ))}
        </div>
      </div>

      <div className="ai-rise ai-d4 mt-8 grid grid-cols-3 gap-2 text-[11px] uppercase tracking-[0.16em] text-white/55">
        {[
          { k: "Predictions/day", v: "2,140" },
          { k: "Time saved (wk)", v: "32 h" },
          { k: "Models in stack", v: "9" },
        ].map((m) => (
          <div key={m.k} className="rounded-lg border border-white/[0.07] bg-white/[0.025] p-3">
            <div>{m.k}</div>
            <div className="mt-0.5 font-display text-[20px] font-semibold tabular-nums text-white" style={{ fontVariationSettings: "'opsz' 144" }}>
              {m.v}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function Insight({ item, index }: { item: InsightItem; index: number }) {
  const tone =
    item.kind === "RISK"
      ? "border-amber-400/30 bg-amber-400/[0.05]"
      : item.kind === "OPPORTUNITY"
      ? "border-emerald-400/30 bg-emerald-400/[0.05]"
      : "border-indigo-300/30 bg-indigo-300/[0.05]";
  const tag =
    item.kind === "RISK"
      ? "text-amber-300 border-amber-300/40 bg-amber-300/10"
      : item.kind === "OPPORTUNITY"
      ? "text-emerald-300 border-emerald-300/40 bg-emerald-300/10"
      : "text-indigo-200 border-indigo-200/40 bg-indigo-200/10";
  return (
    <div
      className={`ai-rise group relative rounded-xl border ${tone} p-4 transition hover:bg-white/[0.045]`}
      style={{ animationDelay: `${0.36 + index * 0.07}s` }}
    >
      <div className="flex items-start gap-3">
        <span
          aria-hidden
          className="mt-0.5 grid h-7 w-7 shrink-0 place-items-center rounded-md text-[12px]"
          style={{ background: "linear-gradient(135deg,rgba(99,102,241,0.40),rgba(34,211,238,0.40))" }}
        >
          <Zap size={13} className="text-white" />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className={`rounded-full border px-2 py-0.5 font-mono text-[9.5px] uppercase tracking-[0.16em] ${tag}`}>
              {item.kind}
            </span>
            <span className="font-mono text-[10px] uppercase tracking-[0.14em] text-white/45">{item.programme}</span>
            <span className="font-mono text-[10px] text-white/40">{item.confidence}% conf</span>
          </div>
          <p className="mt-1.5 text-[13.5px] leading-[1.55] text-white/85">{item.text}</p>
          <div className="mt-2 flex items-center gap-2 text-[11.5px] text-white/50">
            <span>Suggested: {item.action}</span>
            <ArrowRight size={12} className="text-white/40 transition group-hover:translate-x-0.5 group-hover:text-cyan-300" />
          </div>
        </div>
      </div>
    </div>
  );
}

function AuthCard() {
  const f = useLoginSubmit();
  return (
    <aside className="ai-rise ai-d2 relative">
      <div
        aria-hidden
        className="pointer-events-none absolute -inset-px rounded-[20px] opacity-70 blur-[1px]"
        style={{
          background:
            "linear-gradient(135deg,rgba(99,102,241,0.55),rgba(34,211,238,0.45) 60%,rgba(99,102,241,0.20))",
        }}
      />
      <div className="relative rounded-[20px] border border-white/[0.10] bg-[#0E1224]/80 p-6 backdrop-blur-xl sm:p-7">
        <div className="flex items-start justify-between">
          <div>
            <div className="flex items-center gap-1.5 text-[10.5px] uppercase tracking-[0.20em] text-cyan-300">
              <Sparkles size={11} /> Sign in
            </div>
            <h2 className="mt-2 text-[26px] font-semibold leading-[1.1] tracking-[-0.02em] text-white">
              Tap into the engine.
            </h2>
            <p className="mt-1 text-[13px] text-white/60">
              Your tenant&apos;s insights are waiting.
            </p>
          </div>
          <span className="flex items-center gap-1 rounded-md border border-white/15 bg-white/[0.04] px-1.5 py-1 font-mono text-[9px] uppercase tracking-[0.16em] text-white/70">
            <Lock size={10} /> SSE · TLS
          </span>
        </div>

        <form onSubmit={f.handleSubmit} noValidate className="mt-6 space-y-3.5">
          <SsoCard label="Continue with Microsoft" hint="Entra ID · most teams" />
          <SsoCard label="Continue with Google Workspace" />
          <SsoCard label="Continue with SAML SSO" mono />

          <div className="my-3 flex items-center gap-3 text-[10px] uppercase tracking-[0.18em] text-white/40">
            <div className="h-px flex-1 bg-white/[0.10]" />
            or with email
            <div className="h-px flex-1 bg-white/[0.10]" />
          </div>

          <div>
            <label htmlFor="ai-user" className="mb-1.5 block text-[11px] uppercase tracking-[0.16em] text-white/55">
              Work email
            </label>
            <input
              id="ai-user"
              type="text"
              autoComplete="username"
              autoFocus
              required
              value={f.username}
              onChange={(e) => f.setUsername(e.target.value)}
              disabled={f.submitting}
              placeholder="you@company.com"
              className="block h-11 w-full rounded-lg border border-white/[0.10] bg-white/[0.03] px-3.5 text-[13.5px] text-white placeholder:text-white/30 transition focus:border-cyan-300/60 focus:bg-white/[0.05] focus:outline-none focus:ring-2 focus:ring-cyan-300/25"
            />
          </div>
          <div>
            <div className="mb-1.5 flex items-baseline justify-between">
              <label htmlFor="ai-pwd" className="text-[11px] uppercase tracking-[0.16em] text-white/55">Password</label>
              <button type="button" onClick={() => {}} className="text-[11px] text-cyan-300 hover:text-white">Forgot?</button>
            </div>
            <div className="relative">
              <input
                id="ai-pwd"
                type={f.showPassword ? "text" : "password"}
                autoComplete="current-password"
                required
                value={f.password}
                onChange={(e) => f.setPassword(e.target.value)}
                disabled={f.submitting}
                placeholder="••••••••"
                className="block h-11 w-full rounded-lg border border-white/[0.10] bg-white/[0.03] px-3.5 pr-10 text-[13.5px] text-white placeholder:text-white/30 transition focus:border-cyan-300/60 focus:bg-white/[0.05] focus:outline-none focus:ring-2 focus:ring-cyan-300/25"
              />
              <button
                type="button"
                onClick={() => f.setShowPassword((v) => !v)}
                className="absolute inset-y-0 right-0 flex items-center px-3 text-white/50 hover:text-cyan-300"
                aria-label={f.showPassword ? "Hide password" : "Show password"}
                tabIndex={-1}
              >
                {f.showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>
          </div>

          <label className="flex cursor-pointer items-center gap-2 text-[12px] text-white/65">
            <input
              type="checkbox"
              checked={f.remember}
              onChange={(e) => f.setRemember(e.target.checked)}
              className="h-3.5 w-3.5 rounded border-white/30 accent-cyan-400"
            />
            Keep this session active for 7 days
          </label>

          {f.fieldError && (
            <div role="alert" className="rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-2.5 text-[12.5px] text-red-200">
              {f.fieldError}
            </div>
          )}

          <button
            type="submit"
            disabled={f.submitting}
            className="group relative inline-flex h-11 w-full items-center justify-center gap-2 overflow-hidden rounded-lg text-[13.5px] font-semibold text-white transition disabled:opacity-60"
            style={{
              background: "linear-gradient(120deg,#6366F1,#22D3EE)",
              boxShadow: "0 12px 32px rgba(99,102,241,0.30)",
            }}
          >
            {f.submitting ? (
              <>
                <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white/40 border-t-white" />
                Signing in
              </>
            ) : (
              <>
                Continue
                <ArrowRight size={14} className="transition group-hover:translate-x-0.5" />
              </>
            )}
          </button>
        </form>

        <div className="mt-6 flex items-center justify-between border-t border-white/[0.08] pt-4 text-[10.5px] uppercase tracking-[0.16em] text-white/45">
          <span>Models · audited</span>
          <div className="flex gap-1.5">
            {["SOC 2", "ISO 27001", "GDPR", "AI ACT"].map((b) => (
              <span key={b} className="rounded border border-white/15 bg-white/[0.04] px-1.5 py-0.5">{b}</span>
            ))}
          </div>
        </div>
        <p className="mt-3 text-center text-[12px] text-white/55">
          New here? <Link href="/welcome" className="font-medium text-cyan-300 hover:text-white">Book a guided tour →</Link>
        </p>
      </div>
    </aside>
  );
}

function SsoCard({ label, hint, mono }: { label: string; hint?: string; mono?: boolean }) {
  return (
    <button
      type="button"
      className="group flex h-11 w-full items-center justify-between rounded-lg border border-white/[0.10] bg-white/[0.03] px-3.5 text-[13px] font-medium text-white/85 transition hover:border-cyan-300/40 hover:bg-white/[0.06]"
    >
      <span className={mono ? "font-mono text-[12px] tracking-[0.04em]" : ""}>{label}</span>
      <span className="flex items-center gap-2">
        {hint && <span className="hidden sm:inline rounded-full border border-white/15 bg-white/[0.04] px-1.5 py-0.5 font-mono text-[9px] uppercase tracking-[0.16em] text-white/55">{hint}</span>}
        <ArrowRight size={13} className="text-white/40 transition group-hover:translate-x-0.5 group-hover:text-cyan-300" />
      </span>
    </button>
  );
}

type InsightItem = { id: string; kind: "RISK" | "OPPORTUNITY" | "DRIFT"; programme: string; text: string; action: string; confidence: number };
const INSIGHTS: InsightItem[] = [
  {
    id: "1",
    kind: "RISK",
    programme: "RAIL-SCR · Sutton Coldfield",
    text: "Critical-path slack on milestone M4.2 collapsed to 0.4 days after subcontractor reschedule. Likely 3-day overrun unless rebaselined this week.",
    action: "Open critical-path explorer",
    confidence: 87,
  },
  {
    id: "2",
    kind: "OPPORTUNITY",
    programme: "DC-FRA02 · Frankfurt DC P2",
    text: "Cooling fit-out is 11 days ahead. Pulling commissioning forward unlocks ~€840K of early energization revenue.",
    action: "Simulate accelerated cut-over",
    confidence: 92,
  },
  {
    id: "3",
    kind: "DRIFT",
    programme: "Portfolio · Cost",
    text: "CPI on three programmes drifted from 1.02 → 0.97 over 4 weeks. Common cause: steel price index swing on shared MRO contracts.",
    action: "View root-cause trace",
    confidence: 78,
  },
];

function Keyframes() {
  return (
    <style>{`
      @keyframes aiRise { from { opacity:0; transform: translateY(10px); } to { opacity:1; transform:none; } }
      .ai-rise { animation: aiRise .6s cubic-bezier(.22,1,.36,1) both; }
      .ai-d1 { animation-delay: .07s; }
      .ai-d2 { animation-delay: .14s; }
      .ai-d3 { animation-delay: .22s; }
      .ai-d4 { animation-delay: .60s; }
      @media (prefers-reduced-motion: reduce) { .ai-rise { animation: none !important; } }
    `}</style>
  );
}
