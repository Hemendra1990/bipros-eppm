"use client";

import { Suspense } from "react";
import Link from "next/link";
import { Eye, EyeOff, Lock, ShieldCheck, ArrowRight, Compass, Radio, MapPin } from "lucide-react";
import { useLoginSubmit } from "../_shared/useLoginSubmit";

/**
 * Concept — Aerial Programme View.
 * Cartographic intelligence: topographic contours, project pins,
 * great-circle arcs, navigation graticule. Calm command from above.
 */
export default function AerialPage() {
  return (
    <div className="relative min-h-screen overflow-hidden bg-[#0F1A18] font-sans text-[#E8ECEA] antialiased">
      <AerialBackdrop />
      <StatusStrip />
      <main className="relative grid min-h-[calc(100vh-32px)] grid-cols-1 lg:grid-cols-12">
        <Editorial />
        <Suspense fallback={<div className="grid place-items-center px-6 py-10 text-[12px] text-white/40">Loading sign-in…</div>}>
          <AuthPanel />
        </Suspense>
      </main>
      <Keyframes />
    </div>
  );
}

function StatusStrip() {
  return (
    <div className="relative z-30 flex h-8 items-center justify-between border-b border-white/[0.06] bg-[#0A1311]/80 px-4 font-mono text-[10px] uppercase tracking-[0.20em] text-white/55 backdrop-blur-sm">
      <div className="flex items-center gap-4">
        <span className="flex items-center gap-1.5 text-[#C8B273]">
          <span className="grid h-4 w-4 place-items-center rounded-[3px] bg-[#C8B273] text-[10px] font-bold text-[#0A1311]">B</span>
          Bipros · Aerial
        </span>
        <span className="hidden sm:inline opacity-60">Station · OPS-EU-02</span>
        <span className="hidden md:inline opacity-60">51.50N · 0.13W</span>
      </div>
      <div className="flex items-center gap-4">
        <span className="hidden md:flex items-center gap-1.5"><Radio size={11} className="text-emerald-400" />Telemetry · OK</span>
        <span className="opacity-60">UTC 14:32:08</span>
      </div>
    </div>
  );
}

function Editorial() {
  return (
    <section className="relative z-20 col-span-1 px-6 py-10 sm:px-10 lg:col-span-7 lg:px-14 lg:py-16">
      <div className="aerial-rise relative max-w-md">
        <div
          aria-hidden
          className="pointer-events-none absolute -inset-x-6 -inset-y-6 rounded-[14px] bg-[#0A1311]/45 backdrop-blur-[6px]"
          style={{
            maskImage: "radial-gradient(ellipse 90% 80% at 30% 40%, #000 50%, transparent 100%)",
            WebkitMaskImage: "radial-gradient(ellipse 90% 80% at 30% 40%, #000 50%, transparent 100%)",
          }}
        />
        <div className="relative">
          <div className="mb-5 flex items-center gap-3 font-mono text-[10px] uppercase tracking-[0.26em] text-[#C8B273]">
            <span className="inline-block h-px w-7 bg-[#C8B273]" />
            Programme · Aerial View
          </div>
          <h1 className="aerial-rise aerial-d1 font-display text-[44px] font-medium leading-[1.02] tracking-[-0.025em] text-white sm:text-[52px]">
            Every site,<br /><span className="text-[#C8B273]">on one chart.</span>
          </h1>
          <p className="aerial-rise aerial-d2 mt-5 max-w-sm text-[14px] leading-[1.65] text-white/65">
            A tracking station for your portfolio. Programmes, sites and critical paths plotted on a single cartographic surface — schedule, cost and risk visible from above.
          </p>
          <div className="aerial-rise aerial-d3 mt-8 flex flex-col gap-1 font-mono text-[10.5px] uppercase tracking-[0.18em] text-white/45">
            <span>Coordinates · 51.5074°N / 0.1278°W</span>
            <span>Datum · WGS-84 · Projection · Web Mercator</span>
            <span>Refresh · Live · 14s</span>
          </div>
          <div className="aerial-rise aerial-d4 mt-10 flex flex-wrap gap-2">
            {LEGEND.map((l) => (
              <span key={l.label} className="inline-flex items-center gap-1.5 rounded-[3px] border border-white/[0.10] bg-white/[0.03] px-2 py-1 font-mono text-[9.5px] uppercase tracking-[0.16em] text-white/65">
                <span className={`h-1.5 w-1.5 rounded-full ${l.dot}`} aria-hidden />
                {l.label}
              </span>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

function AuthPanel() {
  const f = useLoginSubmit();
  return (
    <aside className="relative z-20 col-span-1 flex items-end justify-end px-6 pb-10 sm:px-10 lg:col-span-5 lg:px-14 lg:pb-14">
      <div className="aerial-rise aerial-d2 relative w-full max-w-[440px] rounded-[10px] border border-white/[0.10] bg-[#0A1311]/85 p-7 shadow-[0_24px_60px_-12px_rgba(0,0,0,0.7)] backdrop-blur-xl">
        <div aria-hidden className="absolute inset-x-0 top-0 h-px" style={{ background: "linear-gradient(90deg,transparent,#C8B273,transparent)" }} />
        <CornerTick className="left-2 top-2" />
        <CornerTick className="right-2 top-2 rotate-90" />
        <CornerTick className="left-2 bottom-2 -rotate-90" />
        <CornerTick className="right-2 bottom-2 rotate-180" />

        <div className="flex items-center justify-between font-mono text-[10px] uppercase tracking-[0.22em] text-white/45">
          <span>Tracking station · Sign in</span>
          <span className="flex items-center gap-1.5 text-emerald-400"><Lock size={11} /> TLS 1.3</span>
        </div>
        <h2 className="mt-3 font-display text-[26px] font-medium leading-[1.1] tracking-[-0.015em] text-white" style={{ fontVariationSettings: "'opsz' 144" }}>
          Lock onto your portfolio.
        </h2>
        <p className="mt-1.5 text-[13px] text-white/55">Authenticate to bring every site into view.</p>

        <form onSubmit={f.handleSubmit} noValidate className="mt-6 space-y-3.5">
          <div className="grid grid-cols-1 gap-2">
            <SsoRow label="Continue with Microsoft" />
            <SsoRow label="Continue with Google Workspace" />
            <SsoRow label="Continue with SAML SSO" />
          </div>
          <div className="my-4 flex items-center gap-3 font-mono text-[9.5px] uppercase tracking-[0.20em] text-white/40">
            <div className="h-px flex-1 bg-white/[0.08]" />Local credentials<div className="h-px flex-1 bg-white/[0.08]" />
          </div>
          <Field label="Operator · Email" htmlFor="aerial-user">
            <input
              id="aerial-user" type="text" autoComplete="username" autoFocus required
              value={f.username} onChange={(e) => f.setUsername(e.target.value)}
              disabled={f.submitting} placeholder="operator@acme.com"
              className="block h-11 w-full rounded-[5px] border border-white/[0.10] bg-white/[0.03] px-3.5 text-[13.5px] text-white placeholder:text-white/30 transition focus:border-[#C8B273] focus:bg-white/[0.05] focus:outline-none focus:ring-2 focus:ring-[#C8B273]/25"
            />
          </Field>
          <Field
            label="Passphrase" htmlFor="aerial-pwd"
            right={<button type="button" onClick={() => {}} className="font-mono text-[10px] uppercase tracking-[0.16em] text-[#C8B273] hover:text-white">Reset</button>}
          >
            <div className="relative">
              <input
                id="aerial-pwd" type={f.showPassword ? "text" : "password"} autoComplete="current-password" required
                value={f.password} onChange={(e) => f.setPassword(e.target.value)}
                disabled={f.submitting} placeholder="••••••••••••"
                className="block h-11 w-full rounded-[5px] border border-white/[0.10] bg-white/[0.03] px-3.5 pr-10 text-[13.5px] text-white placeholder:text-white/30 transition focus:border-[#C8B273] focus:bg-white/[0.05] focus:outline-none focus:ring-2 focus:ring-[#C8B273]/25"
              />
              <button
                type="button" onClick={() => f.setShowPassword((v) => !v)}
                aria-label={f.showPassword ? "Hide password" : "Show password"}
                className="absolute inset-y-0 right-0 flex items-center px-3 text-white/50 hover:text-[#C8B273]" tabIndex={-1}
              >
                {f.showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>
          </Field>
          <label className="flex cursor-pointer items-center gap-2 pt-1 text-[12px] text-white/55">
            <input type="checkbox" checked={f.remember} onChange={(e) => f.setRemember(e.target.checked)} className="h-3.5 w-3.5 rounded-sm border-white/30 bg-transparent accent-[#C8B273]" />
            Lock to this station for 8h
          </label>
          {f.fieldError && (
            <div role="alert" className="rounded-[5px] border border-red-500/30 bg-red-500/10 px-3 py-2.5 text-[12.5px] text-red-300">{f.fieldError}</div>
          )}
          <button
            type="submit" disabled={f.submitting}
            className="group relative mt-2 inline-flex h-11 w-full items-center justify-center gap-2 rounded-[5px] bg-[#C8B273] text-[13.5px] font-semibold tracking-[0.01em] text-[#0A1311] shadow-[0_8px_24px_rgba(200,178,115,0.22)] transition hover:bg-[#D6C083] disabled:opacity-60"
          >
            {f.submitting ? (
              <><span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-[#0A1311]/30 border-t-[#0A1311]" />Acquiring signal</>
            ) : (
              <>Authenticate<ArrowRight size={15} className="transition group-hover:translate-x-0.5" /></>
            )}
          </button>
        </form>

        <div className="mt-6 border-t border-white/[0.08] pt-4">
          <div className="flex items-center justify-between font-mono text-[9.5px] uppercase tracking-[0.18em] text-white/40">
            <span className="flex items-center gap-1.5"><ShieldCheck size={11} className="text-[#C8B273]" />JWT · RS256</span>
            <div className="flex gap-1.5">
              {["SOC 2", "ISO 27001", "GDPR"].map((b) => (
                <span key={b} className="rounded-[3px] border border-white/[0.10] px-1.5 py-0.5">{b}</span>
              ))}
            </div>
          </div>
          <p className="mt-3 text-[11px] text-white/40">
            Need access? <Link href="/welcome" className="text-[#C8B273] hover:text-white">Contact your tenant administrator</Link>
          </p>
        </div>
      </div>
    </aside>
  );
}

/* ---------- Aerial backdrop ---------- */

const CONTOURS_A = [
  "M 60,600 C 180,520 280,560 400,510 C 520,460 560,540 640,500 C 720,460 740,580 660,640 C 580,700 420,700 300,680 C 180,660 80,680 60,600 Z",
  "M 110,600 C 220,540 300,570 400,530 C 500,490 540,560 610,520 C 680,480 700,580 630,620 C 560,660 420,660 320,650 C 220,640 130,650 110,600 Z",
  "M 160,600 C 250,560 320,580 400,550 C 480,520 520,570 580,540 C 640,510 660,580 600,610 C 540,640 420,640 340,630 C 260,620 175,625 160,600 Z",
  "M 210,600 C 280,580 340,590 400,570 C 460,550 500,580 540,560 C 580,540 600,580 560,600 C 520,620 420,620 360,615 C 300,610 220,610 210,600 Z",
  "M 260,600 C 310,590 350,595 400,585 C 450,575 480,590 510,580 C 540,570 555,590 525,600 C 495,610 420,610 370,608 C 320,606 265,605 260,600 Z",
];
const CONTOURS_B = [
  "M 800,200 C 950,160 1100,210 1240,180 C 1380,150 1480,220 1500,300 C 1520,380 1450,440 1320,440 C 1190,440 1040,400 920,360 C 800,320 720,260 800,200 Z",
  "M 850,220 C 980,190 1110,230 1230,210 C 1350,190 1430,240 1450,300 C 1470,360 1410,410 1300,410 C 1190,410 1060,380 960,350 C 860,320 800,270 850,220 Z",
  "M 900,240 C 1010,220 1120,250 1220,235 C 1320,220 1380,260 1395,300 C 1410,340 1370,375 1280,378 C 1190,381 1080,360 1000,340 C 920,320 880,290 900,240 Z",
  "M 950,260 C 1040,250 1130,265 1210,255 C 1290,245 1335,275 1340,300 C 1345,325 1325,345 1255,348 C 1185,351 1100,340 1040,330 C 980,320 940,300 950,260 Z",
  "M 1000,280 C 1070,275 1140,285 1200,278 C 1260,272 1290,290 1290,300 C 1290,310 1280,320 1230,322 C 1180,324 1120,318 1080,313 C 1040,308 1000,300 1000,280 Z",
];
const CONTOURS_C = [
  "M 700,820 C 850,760 1020,790 1180,770 C 1340,750 1480,800 1560,860 C 1480,920 1300,950 1120,940 C 940,930 800,900 700,820 Z",
  "M 760,820 C 880,780 1030,800 1170,790 C 1310,780 1430,810 1500,855 C 1430,895 1290,920 1130,910 C 970,900 850,880 760,820 Z",
  "M 820,820 C 920,800 1040,815 1160,810 C 1280,805 1380,820 1440,850 C 1380,880 1280,895 1140,888 C 1000,881 900,870 820,820 Z",
  "M 880,825 C 960,815 1050,825 1150,822 C 1250,819 1330,830 1380,848 C 1330,866 1260,876 1150,872 C 1040,868 950,862 880,825 Z",
];
const CONTOURS_D = [
  "M 40,400 C 220,330 380,420 540,360 C 700,300 760,420 700,510 C 640,600 440,610 280,580 C 120,550 0,490 40,400 Z",
  "M 1100,560 C 1220,530 1380,560 1500,540 C 1620,520 1700,560 1690,620 C 1680,680 1530,700 1380,690 C 1230,680 1090,640 1100,560 Z",
  "M 240,150 C 380,110 520,140 640,120 C 760,100 820,150 800,200 C 780,250 640,260 500,250 C 360,240 220,210 240,150 Z",
];

function AerialBackdrop() {
  return (
    <div aria-hidden className="pointer-events-none absolute inset-0 z-0">
      <div
        className="absolute inset-0"
        style={{
          background:
            "radial-gradient(ellipse 70% 60% at 18% 30%,#1B2A26 0%,transparent 60%),radial-gradient(ellipse 60% 50% at 95% 95%,#2C2A1E 0%,transparent 65%),radial-gradient(ellipse 50% 50% at 80% 10%,#1F2421 0%,transparent 60%),linear-gradient(180deg,#0F1A18 0%,#0B1412 100%)",
        }}
      />
      <div
        className="absolute inset-0 opacity-[0.06]"
        style={{
          backgroundImage: "linear-gradient(#C8B273 1px,transparent 1px),linear-gradient(90deg,#C8B273 1px,transparent 1px)",
          backgroundSize: "64px 64px",
          maskImage: "radial-gradient(ellipse 90% 80% at 60% 50%,#000 20%,transparent 90%)",
          WebkitMaskImage: "radial-gradient(ellipse 90% 80% at 60% 50%,#000 20%,transparent 90%)",
        }}
      />
      <svg className="absolute inset-0 h-full w-full" viewBox="0 0 1600 1000" preserveAspectRatio="xMidYMid slice">
        <defs>
          <filter id="aerial-noise" x="0" y="0" width="100%" height="100%">
            <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" stitchTiles="stitch" />
            <feColorMatrix values="0 0 0 0 0.85 0 0 0 0 0.78 0 0 0 0 0.55 0 0 0 0.04 0" />
          </filter>
          <radialGradient id="aerial-vignette" cx="50%" cy="50%" r="65%">
            <stop offset="60%" stopColor="#000" stopOpacity="0" />
            <stop offset="100%" stopColor="#000" stopOpacity="0.55" />
          </radialGradient>
        </defs>

        <g fill="none" stroke="#C8B273" strokeOpacity="0.16" className="aerial-drift">
          {CONTOURS_A.map((d, i) => <path key={`a${i}`} d={d} strokeWidth={i % 2 ? 0.5 : 1} />)}
        </g>
        <g fill="none" stroke="#C8B273" strokeOpacity="0.14" className="aerial-drift-2">
          {CONTOURS_B.map((d, i) => <path key={`b${i}`} d={d} strokeWidth={i % 2 ? 0.5 : 1} />)}
        </g>
        <g fill="none" stroke="#C8B273" strokeOpacity="0.13" className="aerial-drift">
          {CONTOURS_C.map((d, i) => <path key={`c${i}`} d={d} strokeWidth={i % 2 ? 0.5 : 1} />)}
        </g>
        <g fill="none" stroke="#C8B273" strokeOpacity="0.10" strokeWidth="0.5" className="aerial-drift-2">
          {CONTOURS_D.map((d, i) => <path key={`d${i}`} d={d} />)}
        </g>

        <g fill="none" stroke="#C8B273" strokeOpacity="0.22" strokeWidth="0.75" strokeDasharray="3 5" className="aerial-arc">
          <path d="M 300,640 Q 700,300 1100,300" />
          <path d="M 1100,300 Q 1300,560 1280,820" />
          <path d="M 300,640 Q 600,820 1280,820" strokeOpacity="0.14" />
        </g>

        {PINS.map((p) => <Pin key={p.code} {...p} />)}

        <rect width="100%" height="100%" filter="url(#aerial-noise)" opacity="0.6" />
        <rect width="100%" height="100%" fill="url(#aerial-vignette)" />
      </svg>

      <div className="absolute bottom-6 right-6 flex flex-col items-end gap-3">
        <div className="flex items-center gap-2 font-mono text-[9.5px] uppercase tracking-[0.20em] text-white/50">
          <span className="inline-block h-px w-12 bg-white/40" /><span>500 km</span>
        </div>
        <div className="relative grid h-14 w-14 place-items-center rounded-full border border-white/15 bg-white/[0.02] backdrop-blur-sm">
          <Compass size={22} className="aerial-compass text-[#C8B273]" strokeWidth={1.25} />
          <span className="absolute -top-3 font-mono text-[9px] uppercase tracking-[0.20em] text-white/55">N</span>
        </div>
      </div>
    </div>
  );
}

type PinDef = { code: string; label: string; state: "ON_TRACK" | "AT_RISK" | "OVERRUN"; cx: number; cy: number; align?: "left" | "right" };

function Pin({ code, label, state, cx, cy, align = "right" }: PinDef) {
  const color = state === "ON_TRACK" ? "#34D399" : state === "AT_RISK" ? "#FBBF24" : "#F87171";
  const tagX = align === "right" ? cx + 14 : cx - 14;
  const anchor = align === "right" ? "start" : "end";
  const tagStyle = { fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace", letterSpacing: "0.14em", textTransform: "uppercase" as const };
  return (
    <g className="aerial-pin">
      <circle cx={cx} cy={cy} r="14" fill="none" stroke={color} strokeOpacity="0.25" strokeWidth="1" className="aerial-pulse" style={{ transformOrigin: `${cx}px ${cy}px` }} />
      <circle cx={cx} cy={cy} r="7" fill="none" stroke={color} strokeOpacity="0.85" strokeWidth="1.5" />
      <circle cx={cx} cy={cy} r="2.4" fill={color} />
      <text x={tagX} y={cy - 6} textAnchor={anchor} className="fill-white/85" style={{ ...tagStyle, fontSize: 10 }}>{code}</text>
      <text x={tagX} y={cy + 7} textAnchor={anchor} className="fill-white/45" style={{ ...tagStyle, fontSize: 9 }}>{label}</text>
    </g>
  );
}

function SsoRow({ label }: { label: string }) {
  return (
    <button type="button" onClick={() => {}} className="group flex h-10 w-full items-center justify-between rounded-[5px] border border-white/[0.10] bg-white/[0.025] px-3.5 text-[12.5px] font-medium text-white/85 transition hover:border-[#C8B273]/55 hover:bg-white/[0.05]">
      <span className="flex items-center gap-2">
        <MapPin size={13} className="text-white/40 group-hover:text-[#C8B273]" />
        {label}
      </span>
      <ArrowRight size={13} className="text-white/40 transition group-hover:translate-x-0.5 group-hover:text-[#C8B273]" />
    </button>
  );
}

function Field({ label, htmlFor, right, children }: { label: string; htmlFor: string; right?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div>
      <div className="mb-1.5 flex items-baseline justify-between">
        <label htmlFor={htmlFor} className="font-mono text-[10px] uppercase tracking-[0.18em] text-white/55">{label}</label>
        {right}
      </div>
      {children}
    </div>
  );
}

function CornerTick({ className = "" }: { className?: string }) {
  return <span aria-hidden className={`pointer-events-none absolute h-2.5 w-2.5 border-l border-t border-[#C8B273]/55 ${className}`} />;
}

const PINS: PinDef[] = [
  { code: "SCR-RAIL", label: "ON_TRACK", state: "ON_TRACK", cx: 320, cy: 600, align: "right" },
  { code: "WND-NS3", label: "AT_RISK", state: "AT_RISK", cx: 1100, cy: 300, align: "left" },
  { code: "DC-FRA02", label: "ON_TRACK", state: "ON_TRACK", cx: 1280, cy: 820, align: "left" },
  { code: "HSR-LYM", label: "AT_RISK", state: "AT_RISK", cx: 720, cy: 470, align: "right" },
  { code: "PWR-SUB", label: "OVERRUN", state: "OVERRUN", cx: 540, cy: 250, align: "right" },
  { code: "MED-OSL", label: "ON_TRACK", state: "ON_TRACK", cx: 980, cy: 720, align: "right" },
];

const LEGEND: Array<{ label: string; dot: string }> = [
  { label: "On track", dot: "bg-emerald-400" },
  { label: "At risk", dot: "bg-amber-400" },
  { label: "Overrun", dot: "bg-red-400" },
];

function Keyframes() {
  return (
    <style>{`
      @keyframes aerialRise { from { opacity:0; transform: translateY(10px); } to { opacity:1; transform:none; } }
      .aerial-rise { animation: aerialRise .65s cubic-bezier(.22,1,.36,1) both; }
      .aerial-d1 { animation-delay: .08s; } .aerial-d2 { animation-delay: .18s; }
      .aerial-d3 { animation-delay: .28s; } .aerial-d4 { animation-delay: .38s; }
      @keyframes aerialPulse { 0% { transform: scale(1); opacity: .5; } 70% { transform: scale(1.6); opacity: 0; } 100% { transform: scale(1.6); opacity: 0; } }
      .aerial-pulse { animation: aerialPulse 3.4s ease-out infinite; transform-box: fill-box; }
      @keyframes aerialDrift { 0%,100% { transform: translate3d(0,0,0); } 50% { transform: translate3d(6px,-3px,0); } }
      .aerial-drift { animation: aerialDrift 22s ease-in-out infinite; }
      .aerial-drift-2 { animation: aerialDrift 28s ease-in-out infinite reverse; }
      @keyframes aerialArc { 0% { stroke-dashoffset: 0; } 100% { stroke-dashoffset: -80; } }
      .aerial-arc path { animation: aerialArc 18s linear infinite; }
      @keyframes aerialCompass { 0%,100% { transform: rotate(-2deg); } 50% { transform: rotate(3deg); } }
      .aerial-compass { animation: aerialCompass 9s ease-in-out infinite; transform-origin: 50% 50%; }
      @media (prefers-reduced-motion: reduce) {
        .aerial-rise, .aerial-pulse, .aerial-drift, .aerial-drift-2, .aerial-arc path, .aerial-compass { animation: none !important; }
      }
    `}</style>
  );
}
