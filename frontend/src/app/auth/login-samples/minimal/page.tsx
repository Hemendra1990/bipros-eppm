"use client";

import { Suspense } from "react";
import Link from "next/link";
import { ArrowRight, Eye, EyeOff } from "lucide-react";
import { useLoginSubmit } from "../_shared/useLoginSubmit";

/**
 * Concept 2 — Modern Minimal Enterprise.
 * Linear / Stripe rhythm: centered narrow column, near-monochrome,
 * a single decisive accent, surgical typography.
 */
export default function MinimalPage() {
  return (
    <div className="min-h-screen bg-[#FAFAFA] font-sans text-[#0A0A0A] antialiased">
      <Topbar />
      <Suspense fallback={<div className="grid min-h-[80vh] place-items-center text-[12px] text-zinc-500">Loading…</div>}>
        <Center />
      </Suspense>
      <Footnote />
      <Keyframes />
    </div>
  );
}

function Topbar() {
  return (
    <header className="border-b border-zinc-200/80 bg-white">
      <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-6">
        <Link href="/" className="flex items-center gap-2">
          <span className="grid h-6 w-6 place-items-center rounded-md bg-[#0A0A0A] text-[11px] font-bold text-white">B</span>
          <span className="text-[14px] font-semibold tracking-[-0.01em]">Bipros</span>
          <span className="rounded border border-zinc-200 bg-zinc-50 px-1.5 py-0.5 font-mono text-[9.5px] uppercase tracking-[0.14em] text-zinc-500">
            EPPM
          </span>
        </Link>
        <div className="flex items-center gap-6 text-[13px] text-zinc-600">
          <Link href="#" className="hover:text-black">Documentation</Link>
          <Link href="#" className="hover:text-black">Status</Link>
          <Link href="#" className="hidden sm:inline-block hover:text-black">Contact sales</Link>
        </div>
      </div>
    </header>
  );
}

function Center() {
  const f = useLoginSubmit();
  return (
    <main className="mx-auto flex w-full max-w-md flex-col items-stretch px-6 py-16 sm:py-24">
      <div className="mn-rise">
        <div className="mb-2 flex items-center gap-2 text-[12px] text-zinc-500">
          <span className="font-mono text-[10px] uppercase tracking-[0.20em]">Step 1 / 1</span>
          <span className="h-px flex-1 bg-zinc-200" />
        </div>
        <h1 className="text-[28px] font-semibold leading-[1.1] tracking-[-0.025em] text-black">
          Sign in to Bipros
        </h1>
        <p className="mt-1.5 text-[14px] leading-[1.55] text-zinc-600">
          Use your work email. We&apos;ll route you to your tenant&apos;s identity provider if it&apos;s configured.
        </p>
      </div>

      <form onSubmit={f.handleSubmit} noValidate className="mn-rise mn-d1 mt-9 space-y-4">
        <div>
          <label htmlFor="mn-user" className="block text-[12.5px] font-medium text-zinc-700">
            Work email
          </label>
          <input
            id="mn-user"
            type="text"
            autoComplete="username"
            autoFocus
            required
            value={f.username}
            onChange={(e) => f.setUsername(e.target.value)}
            disabled={f.submitting}
            placeholder="you@company.com"
            className="mt-1.5 block h-10 w-full rounded-md border border-zinc-300 bg-white px-3 text-[14px] text-black placeholder:text-zinc-400 transition focus:border-black focus:outline-none focus:ring-2 focus:ring-black/10"
          />
        </div>
        <div>
          <div className="flex items-baseline justify-between">
            <label htmlFor="mn-pwd" className="block text-[12.5px] font-medium text-zinc-700">
              Password
            </label>
            <button
              type="button"
              onClick={() => {}}
              className="text-[12px] text-zinc-500 underline-offset-2 hover:text-black hover:underline"
            >
              Forgot password?
            </button>
          </div>
          <div className="relative mt-1.5">
            <input
              id="mn-pwd"
              type={f.showPassword ? "text" : "password"}
              autoComplete="current-password"
              required
              value={f.password}
              onChange={(e) => f.setPassword(e.target.value)}
              disabled={f.submitting}
              placeholder="•••••••••"
              className="block h-10 w-full rounded-md border border-zinc-300 bg-white px-3 pr-10 text-[14px] text-black placeholder:text-zinc-400 transition focus:border-black focus:outline-none focus:ring-2 focus:ring-black/10"
            />
            <button
              type="button"
              onClick={() => f.setShowPassword((v) => !v)}
              className="absolute inset-y-0 right-0 flex items-center px-3 text-zinc-500 hover:text-black"
              aria-label={f.showPassword ? "Hide password" : "Show password"}
              tabIndex={-1}
            >
              {f.showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
            </button>
          </div>
        </div>

        <label className="flex cursor-pointer items-center gap-2 text-[12.5px] text-zinc-700">
          <input
            type="checkbox"
            checked={f.remember}
            onChange={(e) => f.setRemember(e.target.checked)}
            className="h-3.5 w-3.5 rounded border-zinc-300 accent-black"
          />
          Stay signed in on this device
        </label>

        {f.fieldError && (
          <div role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-[13px] text-red-700">
            {f.fieldError}
          </div>
        )}

        <button
          type="submit"
          disabled={f.submitting}
          className="group relative inline-flex h-10 w-full items-center justify-center gap-2 rounded-md bg-black text-[14px] font-medium text-white transition hover:bg-zinc-800 disabled:opacity-60"
        >
          {f.submitting ? (
            <>
              <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white/30 border-t-white" />
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

      <div className="mn-rise mn-d2 mt-7">
        <div className="flex items-center gap-3 text-[11px] uppercase tracking-[0.16em] text-zinc-400">
          <div className="h-px flex-1 bg-zinc-200" />
          or
          <div className="h-px flex-1 bg-zinc-200" />
        </div>
        <div className="mt-4 grid gap-2">
          <SsoMin label="Continue with Google" />
          <SsoMin label="Continue with Microsoft" />
          <SsoMin label="Continue with SAML SSO" mono />
        </div>
      </div>

      <p className="mn-rise mn-d3 mt-10 text-center text-[13px] text-zinc-500">
        New to Bipros?{" "}
        <Link href="/welcome" className="font-medium text-black underline-offset-4 hover:underline">
          Talk to sales
        </Link>
      </p>
    </main>
  );
}

function SsoMin({ label, mono }: { label: string; mono?: boolean }) {
  return (
    <button
      type="button"
      className={`group inline-flex h-10 w-full items-center justify-center gap-2 rounded-md border border-zinc-300 bg-white text-[13.5px] font-medium text-zinc-800 transition hover:bg-zinc-50 ${mono ? "font-mono text-[12px] tracking-[0.04em]" : ""}`}
    >
      {label}
    </button>
  );
}

function Footnote() {
  return (
    <footer className="border-t border-zinc-200/80 bg-white">
      <div className="mx-auto flex max-w-6xl flex-col items-start justify-between gap-3 px-6 py-6 text-[12px] text-zinc-500 sm:flex-row sm:items-center">
        <div className="flex items-center gap-2">
          <span className="h-2 w-2 rounded-full bg-emerald-500" />
          All systems operational
          <span className="ml-3 font-mono text-[10px] uppercase tracking-[0.14em] text-zinc-400">v0.1.0</span>
        </div>
        <div className="flex flex-wrap gap-x-5 gap-y-1">
          {["SOC 2 Type II", "ISO 27001", "GDPR"].map((b) => (
            <span key={b} className="font-mono text-[10.5px] uppercase tracking-[0.14em] text-zinc-500">{b}</span>
          ))}
          <Link href="#" className="hover:text-black">Privacy</Link>
          <Link href="#" className="hover:text-black">Terms</Link>
        </div>
      </div>
    </footer>
  );
}

function Keyframes() {
  return (
    <style>{`
      @keyframes mnRise { from { opacity:0; transform: translateY(8px); } to { opacity:1; transform:none; } }
      .mn-rise { animation: mnRise .55s cubic-bezier(.22,1,.36,1) both; }
      .mn-d1 { animation-delay: .06s; }
      .mn-d2 { animation-delay: .14s; }
      .mn-d3 { animation-delay: .20s; }
      @media (prefers-reduced-motion: reduce) { .mn-rise { animation: none !important; } }
    `}</style>
  );
}
