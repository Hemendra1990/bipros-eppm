"use client";

import { Suspense } from "react";
import Link from "next/link";
import { Eye, EyeOff, KeyRound, AlertTriangle, HardHat, Wrench, Radio } from "lucide-react";
import { useLoginSubmit } from "../_shared/useLoginSubmit";

/**
 * Concept 3 — Industrial Operations Console.
 * SCADA/HMI inspiration: hard-edged geometry, safety yellow + signal
 * green on concrete, monospace dominance, project command-center mood.
 */
export default function IndustrialPage() {
  return (
    <div className="min-h-screen bg-[#13161A] font-mono text-[#E2E5EA] antialiased">
      <ConsoleStrip />
      <main className="grid min-h-[calc(100vh-44px)] grid-cols-1 lg:grid-cols-[minmax(0,1fr)_420px]">
        <SiteBoard />
        <Suspense fallback={<div className="grid place-items-center text-[12px] text-white/50">Initializing console…</div>}>
          <ShiftAuth />
        </Suspense>
      </main>
      <Keyframes />
    </div>
  );
}

function ConsoleStrip() {
  return (
    <div className="flex h-11 items-center justify-between border-b-2 border-[#FFC600] bg-[#0B0D10] px-5 text-[10.5px] uppercase tracking-[0.20em] text-white/70">
      <div className="flex items-center gap-4">
        <span className="flex items-center gap-2">
          <span aria-hidden className="grid h-6 w-6 place-items-center bg-[#FFC600] text-[12px] font-black text-black">B</span>
          <span className="font-bold tracking-[0.30em] text-white">BIPROS · OPS CONSOLE</span>
        </span>
        <span className="opacity-60">Node OPS-EU-02</span>
        <span className="opacity-60">PLC Bridge: ONLINE</span>
      </div>
      <div className="flex items-center gap-4">
        <span className="flex items-center gap-1.5"><span className="h-2 w-2 animate-pulse bg-[#3DDC84]" />Telemetry · streaming</span>
        <span className="opacity-60">Shift B · 14:32:08 UTC</span>
      </div>
    </div>
  );
}

function SiteBoard() {
  return (
    <section className="relative overflow-hidden border-r border-white/[0.06] bg-[#13161A] px-6 py-9 lg:px-10">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 opacity-[0.035]"
        style={{
          backgroundImage:
            "linear-gradient(#FFC600 1px,transparent 1px),linear-gradient(90deg,#FFC600 1px,transparent 1px)",
          backgroundSize: "32px 32px",
        }}
      />
      {/* caution stripe */}
      <div
        aria-hidden
        className="pointer-events-none absolute left-0 top-0 h-full w-1.5"
        style={{
          backgroundImage:
            "repeating-linear-gradient(45deg,#FFC600 0 8px,#13161A 8px 16px)",
        }}
      />

      <div className="relative">
        <div className="ind-rise mb-4 flex flex-wrap items-center gap-3 text-[10px] tracking-[0.24em] text-[#FFC600]">
          <HardHat size={13} />
          PROJECT COMMAND BOARD
          <span className="rounded-sm border border-[#3DDC84]/40 bg-[#3DDC84]/10 px-1.5 py-0.5 text-[#3DDC84]">
            ALL SYSTEMS · NOMINAL
          </span>
        </div>
        <h1 className="ind-rise ind-d1 max-w-2xl font-sans text-[40px] font-semibold leading-[1.05] tracking-[-0.02em] text-white">
          Programme execution,
          <br />
          <span className="text-[#FFC600]">under control.</span>
        </h1>
        <p className="ind-rise ind-d2 mt-4 max-w-xl text-[13px] leading-[1.6] text-white/65">
          Sign in to the operational layer behind your portfolio. Live status from
          field crews, equipment, and milestones — one console for site, scheduling,
          and supervisory teams.
        </p>

        {/* KPI tiles */}
        <div className="ind-rise ind-d3 mt-9 grid grid-cols-2 gap-2 sm:grid-cols-4">
          {OPS.map((k) => (
            <div key={k.label} className="border border-white/[0.07] bg-black/40 p-3">
              <div className="text-[9.5px] uppercase tracking-[0.18em] text-white/45">{k.label}</div>
              <div className="mt-1 font-sans text-[26px] font-semibold leading-none tabular-nums text-white">{k.value}</div>
              <div className={`mt-1 text-[10px] ${k.tone}`}>{k.note}</div>
            </div>
          ))}
        </div>

        {/* site grid */}
        <div className="ind-rise ind-d4 mt-9">
          <div className="mb-2 flex items-center justify-between text-[10px] uppercase tracking-[0.20em] text-white/50">
            <span className="flex items-center gap-1.5"><Radio size={11} /> Active sites</span>
            <span>26 online · 3 maintenance · 0 down</span>
          </div>
          <div className="grid grid-cols-2 gap-2 md:grid-cols-3">
            {SITES.map((s) => (
              <SiteTile key={s.id} site={s} />
            ))}
          </div>
        </div>

        <div className="ind-rise ind-d5 mt-9 grid grid-cols-1 gap-3 border-t border-white/[0.06] pt-6 sm:grid-cols-3">
          <Reading icon={<Wrench size={11} />} label="Equipment availability" value="96.4%" tone="text-[#3DDC84]" />
          <Reading icon={<AlertTriangle size={11} />} label="Open HSE incidents" value="0" tone="text-[#3DDC84]" />
          <Reading icon={<Radio size={11} />} label="Telemetry latency" value="64 ms" tone="text-white" />
        </div>
      </div>
    </section>
  );
}

function ShiftAuth() {
  const f = useLoginSubmit();
  return (
    <aside className="relative flex flex-col bg-[#0B0D10] px-7 py-9 lg:px-9">
      <div
        aria-hidden
        className="absolute inset-x-0 top-0 h-1.5"
        style={{
          backgroundImage:
            "repeating-linear-gradient(135deg,#FFC600 0 12px,#0B0D10 12px 24px)",
        }}
      />
      <div className="ind-rise mt-2 flex items-center justify-between text-[9.5px] uppercase tracking-[0.22em] text-white/50">
        <span className="flex items-center gap-1.5"><KeyRound size={11} /> Operator authentication</span>
        <span className="flex items-center gap-1.5 text-[#3DDC84]">SECURE CHANNEL</span>
      </div>
      <h2 className="ind-rise ind-d1 mt-4 font-sans text-[28px] font-semibold leading-[1.05] tracking-[-0.015em] text-white">
        Console access
      </h2>
      <p className="ind-rise ind-d2 mt-1 text-[12.5px] text-white/55">
        Each session is logged to the audit trail. Identify yourself before continuing.
      </p>

      <form onSubmit={f.handleSubmit} noValidate className="ind-rise ind-d3 mt-7 space-y-4">
        <div>
          <label htmlFor="ind-user" className="mb-1.5 block text-[10px] uppercase tracking-[0.18em] text-white/55">
            Operator ID / email
          </label>
          <input
            id="ind-user"
            type="text"
            autoComplete="username"
            autoFocus
            required
            value={f.username}
            onChange={(e) => f.setUsername(e.target.value)}
            disabled={f.submitting}
            placeholder="OP-2041 / name@firm.com"
            className="block h-11 w-full border border-white/[0.10] bg-black/50 px-3 text-[13px] tracking-[0.02em] text-white placeholder:text-white/30 transition focus:border-[#FFC600] focus:outline-none focus:ring-2 focus:ring-[#FFC600]/30"
          />
        </div>
        <div>
          <div className="mb-1.5 flex items-baseline justify-between">
            <label htmlFor="ind-pwd" className="text-[10px] uppercase tracking-[0.18em] text-white/55">
              Passphrase
            </label>
            <button type="button" onClick={() => {}} className="text-[10px] uppercase tracking-[0.16em] text-[#FFC600] hover:text-white">
              Reset via supervisor
            </button>
          </div>
          <div className="relative">
            <input
              id="ind-pwd"
              type={f.showPassword ? "text" : "password"}
              autoComplete="current-password"
              required
              value={f.password}
              onChange={(e) => f.setPassword(e.target.value)}
              disabled={f.submitting}
              placeholder="••••••••••"
              className="block h-11 w-full border border-white/[0.10] bg-black/50 px-3 pr-10 text-[13px] tracking-[0.02em] text-white placeholder:text-white/30 transition focus:border-[#FFC600] focus:outline-none focus:ring-2 focus:ring-[#FFC600]/30"
            />
            <button
              type="button"
              onClick={() => f.setShowPassword((v) => !v)}
              className="absolute inset-y-0 right-0 flex items-center px-3 text-white/50 hover:text-[#FFC600]"
              aria-label={f.showPassword ? "Hide" : "Show"}
              tabIndex={-1}
            >
              {f.showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
            </button>
          </div>
        </div>

        <div className="flex items-center justify-between border border-dashed border-white/[0.12] bg-black/30 px-3 py-2.5">
          <label className="flex cursor-pointer items-center gap-2 text-[11.5px] text-white/65">
            <input
              type="checkbox"
              checked={f.remember}
              onChange={(e) => f.setRemember(e.target.checked)}
              className="h-3.5 w-3.5 accent-[#FFC600]"
            />
            Lock to this terminal · 8h shift
          </label>
          <span className="text-[9.5px] uppercase tracking-[0.18em] text-white/40">SHIFT B</span>
        </div>

        {f.fieldError && (
          <div role="alert" className="border border-red-500/40 bg-red-500/10 px-3 py-2 text-[12.5px] text-red-300">
            <span className="mr-1.5 font-bold tracking-[0.18em]">FAULT</span> {f.fieldError}
          </div>
        )}

        <button
          type="submit"
          disabled={f.submitting}
          className="group inline-flex h-12 w-full items-center justify-center gap-2 bg-[#FFC600] text-[13px] font-bold uppercase tracking-[0.18em] text-black transition hover:bg-[#FFD740] disabled:opacity-60"
        >
          {f.submitting ? (
            <>
              <span className="h-3.5 w-3.5 animate-spin border-2 border-black/30 border-t-black" />
              Authenticating
            </>
          ) : (
            <>Engage console</>
          )}
        </button>
      </form>

      <div className="mt-auto pt-8">
        <div className="grid grid-cols-2 gap-2 text-[10px] uppercase tracking-[0.18em] text-white/45">
          <div className="border border-white/[0.07] bg-black/40 px-2.5 py-2">
            <div className="opacity-60">Audit log</div>
            <div className="mt-0.5 text-[#3DDC84]">Append-only</div>
          </div>
          <div className="border border-white/[0.07] bg-black/40 px-2.5 py-2">
            <div className="opacity-60">Compliance</div>
            <div className="mt-0.5 text-white">ISO 27001 · SOC 2 · GDPR</div>
          </div>
        </div>
        <p className="mt-3 text-[10.5px] tracking-[0.14em] text-white/45">
          NEW OPERATOR?{" "}
          <Link href="/welcome" className="text-[#FFC600] hover:text-white">REQUEST CREDENTIALS →</Link>
        </p>
      </div>
    </aside>
  );
}

function SiteTile({ site }: { site: Site }) {
  const tones = {
    GREEN: "border-[#3DDC84]/40 bg-[#3DDC84]/[0.06]",
    AMBER: "border-amber-400/40 bg-amber-400/[0.06]",
    RED: "border-red-500/40 bg-red-500/[0.06]",
  } as const;
  const dot = {
    GREEN: "bg-[#3DDC84]",
    AMBER: "bg-amber-400",
    RED: "bg-red-500",
  } as const;
  return (
    <div className={`group border ${tones[site.state]} p-3 transition hover:bg-white/[0.04]`}>
      <div className="flex items-start justify-between">
        <div>
          <div className="text-[10px] uppercase tracking-[0.18em] text-white/40">{site.region}</div>
          <div className="font-sans text-[13.5px] font-semibold tracking-[-0.005em] text-white">{site.name}</div>
        </div>
        <span className={`mt-1.5 h-2 w-2 ${dot[site.state]} ${site.state === "GREEN" ? "animate-pulse" : ""}`} />
      </div>
      <div className="mt-2 flex items-center justify-between text-[10px] uppercase tracking-[0.14em] text-white/55">
        <span>{site.crew} crew</span>
        <span className="tabular-nums text-white/85">{site.pct}%</span>
      </div>
      <div className="mt-1 h-1 w-full bg-white/[0.06]">
        <div
          className={`h-full ${site.state === "RED" ? "bg-red-500" : site.state === "AMBER" ? "bg-amber-400" : "bg-[#3DDC84]"}`}
          style={{ width: `${site.pct}%` }}
        />
      </div>
    </div>
  );
}

function Reading({ icon, label, value, tone }: { icon: React.ReactNode; label: string; value: string; tone: string }) {
  return (
    <div className="flex items-center justify-between border border-white/[0.06] bg-black/40 px-3 py-2.5">
      <span className="flex items-center gap-1.5 text-[10px] uppercase tracking-[0.18em] text-white/50">{icon}{label}</span>
      <span className={`font-sans text-[15px] font-semibold tabular-nums ${tone}`}>{value}</span>
    </div>
  );
}

type Site = { id: string; name: string; region: string; pct: number; crew: number; state: "GREEN" | "AMBER" | "RED" };
const SITES: Site[] = [
  { id: "S1", name: "Sutton Coldfield", region: "UK·MIDLANDS", pct: 62, crew: 184, state: "GREEN" },
  { id: "S2", name: "North Sea Cluster III", region: "OFFSHORE·NL", pct: 28, crew: 92, state: "AMBER" },
  { id: "S3", name: "Frankfurt DC P2", region: "DE·HESSEN", pct: 91, crew: 41, state: "GREEN" },
  { id: "S4", name: "Lyon–Marseille HSR", region: "FR·RHÔNE", pct: 14, crew: 230, state: "AMBER" },
  { id: "S5", name: "Substation Mod·UK", region: "UK·NATIONAL", pct: 47, crew: 76, state: "RED" },
  { id: "S6", name: "Antwerp Petrochem", region: "BE·FLANDERS", pct: 78, crew: 112, state: "GREEN" },
];

const OPS: Array<{ label: string; value: string; note: string; tone: string }> = [
  { label: "ACTIVE SITES", value: "26", note: "+1 today", tone: "text-[#3DDC84]" },
  { label: "ACTIVE CREW", value: "3.4K", note: "across 12 trades", tone: "text-white/55" },
  { label: "MILESTONES (W)", value: "184", note: "98% on track", tone: "text-[#3DDC84]" },
  { label: "INCIDENTS", value: "0", note: "27d clean", tone: "text-[#3DDC84]" },
];

function Keyframes() {
  return (
    <style>{`
      @keyframes indRise { from { opacity:0; transform: translateY(8px); } to { opacity:1; transform:none; } }
      .ind-rise { animation: indRise .55s cubic-bezier(.22,1,.36,1) both; }
      .ind-d1 { animation-delay: .06s; }
      .ind-d2 { animation-delay: .12s; }
      .ind-d3 { animation-delay: .20s; }
      .ind-d4 { animation-delay: .30s; }
      .ind-d5 { animation-delay: .42s; }
      @media (prefers-reduced-motion: reduce) { .ind-rise { animation: none !important; } }
    `}</style>
  );
}
