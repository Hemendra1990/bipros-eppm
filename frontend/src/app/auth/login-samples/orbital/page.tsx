"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { ArrowRight, Eye, EyeOff, Globe, Lock, MapPin } from "lucide-react";
import { useLoginSubmit } from "../_shared/useLoginSubmit";

/**
 * Concept — Operations Bridge · Global Network.
 * A stylised dot-map of the world with glowing project pins and faint
 * great-circle arcs, paired with a glass auth card. Quiet command of a
 * worldwide capital portfolio.
 */
export default function OrbitalPage() {
  return (
    <div className="relative min-h-screen overflow-hidden bg-[#0B1020] font-sans text-white antialiased">
      <Backdrop />
      <Grain />
      <StatusStrip />
      <main className="relative grid min-h-[calc(100vh-44px)] grid-cols-1 gap-10 px-6 py-10 lg:grid-cols-[1fr_440px] lg:gap-14 lg:px-12 lg:py-14">
        <Editorial />
        <Suspense fallback={<div className="grid place-items-center text-[12px] text-white/50">Loading…</div>}>
          <AuthCard />
        </Suspense>
      </main>
      <Keyframes />
    </div>
  );
}

/* ---------- BACKDROP ------------------------------------------------- */

function Backdrop() {
  return (
    <div aria-hidden className="pointer-events-none absolute inset-0 -z-10 overflow-hidden">
      <div
        className="absolute inset-0"
        style={{ background: "radial-gradient(ellipse 80% 60% at 50% 35%, #11192C 0%, #0B1020 60%, #07090F 100%)" }}
      />
      <WorldMap />
      <div
        className="absolute inset-0"
        style={{ background: "radial-gradient(ellipse 95% 75% at 50% 50%, transparent 50%, rgba(7,9,15,0.85) 100%)" }}
      />
      <div
        className="absolute -top-40 left-1/3 h-[640px] w-[640px] rounded-full opacity-[0.18] blur-3xl"
        style={{ background: "radial-gradient(circle, rgba(94,191,212,0.55) 0%, rgba(94,191,212,0) 65%)" }}
      />
    </div>
  );
}

function WorldMap() {
  return (
    <svg viewBox="0 0 720 360" preserveAspectRatio="xMidYMid slice" className="absolute inset-0 h-full w-full">
      <defs>
        <radialGradient id="orb-pin" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor="#7DE2F2" stopOpacity="1" />
          <stop offset="40%" stopColor="#5EBFD4" stopOpacity="0.55" />
          <stop offset="100%" stopColor="#5EBFD4" stopOpacity="0" />
        </radialGradient>
        <radialGradient id="orb-pin-warn" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor="#FFD9A8" stopOpacity="1" />
          <stop offset="40%" stopColor="#F5A66B" stopOpacity="0.55" />
          <stop offset="100%" stopColor="#F5A66B" stopOpacity="0" />
        </radialGradient>
        <linearGradient id="orb-arc" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="#5EBFD4" stopOpacity="0" />
          <stop offset="50%" stopColor="#5EBFD4" stopOpacity="0.55" />
          <stop offset="100%" stopColor="#5EBFD4" stopOpacity="0" />
        </linearGradient>
        <filter id="orb-soft" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="0.8" />
        </filter>
      </defs>

      {/* graticule */}
      <g stroke="#5EBFD4" strokeOpacity="0.06" strokeWidth="0.4">
        {[60, 120, 180, 240, 300, 360, 420, 480, 540, 600, 660].map((x) => (
          <line key={`m-${x}`} x1={x} y1={0} x2={x} y2={360} />
        ))}
        {[60, 120, 180, 240, 300].map((y) => (
          <line key={`p-${y}`} x1={0} y1={y} x2={720} y2={y} />
        ))}
      </g>
      <line x1={0} y1={180} x2={720} y2={180} stroke="#5EBFD4" strokeOpacity="0.12" strokeDasharray="2 4" strokeWidth="0.5" />

      {/* land dots */}
      <g fill="#5EBFD4" fillOpacity="0.55">
        {LAND_DOTS.map(([x, y], i) => <circle key={`d-${i}`} cx={x} cy={y} r={0.9} />)}
      </g>
      <g fill="#1A2434" fillOpacity="0.85" filter="url(#orb-soft)">
        {LAND_DOTS.map(([x, y], i) => i % 3 === 0 ? <circle key={`f-${i}`} cx={x} cy={y} r={2.2} /> : null)}
      </g>

      {/* arcs */}
      <g fill="none" stroke="url(#orb-arc)" strokeWidth="0.6" strokeDasharray="2 3">
        {ARCS.map((arc, i) => {
          const a = PINS.find((p) => p.id === arc[0])!;
          const b = PINS.find((p) => p.id === arc[1])!;
          return <path key={`arc-${i}`} d={greatArc(a.x, a.y, b.x, b.y)} className="orb-arc" style={{ animationDelay: `${0.3 + i * 0.15}s` }} />;
        })}
      </g>

      {/* pins */}
      <g>
        {PINS.map((p, i) => (
          <g key={p.id} className="orb-pin" style={{ animationDelay: `${0.5 + i * 0.06}s` }}>
            <circle cx={p.x} cy={p.y} r={8} fill={p.warn ? "url(#orb-pin-warn)" : "url(#orb-pin)"} />
            <circle cx={p.x} cy={p.y} r={1.4} fill={p.warn ? "#FFD9A8" : "#7DE2F2"} />
            <text x={p.x + 4.5} y={p.y - 3} fontFamily="ui-monospace, SFMono-Regular, Menlo, monospace" fontSize="3.4" fill={p.warn ? "#FFD9A8" : "#9FE7F2"} fillOpacity="0.85">
              {p.label}
            </text>
          </g>
        ))}
      </g>
    </svg>
  );
}

function Grain() {
  return (
    <div
      aria-hidden
      className="pointer-events-none absolute inset-0 -z-10 opacity-[0.04] mix-blend-overlay"
      style={{
        backgroundImage:
          "url(\"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='160' height='160'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2'/></filter><rect width='100%' height='100%' filter='url(%23n)' opacity='0.7'/></svg>\")",
      }}
    />
  );
}

/* ---------- TOP STATUS STRIP ----------------------------------------- */

function StatusStrip() {
  const [now, setNow] = useState("14:32:08");
  useEffect(() => {
    const tick = () => {
      const d = new Date();
      const hh = String(d.getUTCHours()).padStart(2, "0");
      const mm = String(d.getUTCMinutes()).padStart(2, "0");
      const ss = String(d.getUTCSeconds()).padStart(2, "0");
      setNow(`${hh}:${mm}:${ss}`);
    };
    tick();
    const id = window.setInterval(tick, 1000);
    return () => window.clearInterval(id);
  }, []);
  return (
    <div className="relative flex h-11 items-center gap-2 overflow-x-auto whitespace-nowrap border-b border-white/[0.06] bg-black/30 px-4 font-mono text-[10.5px] uppercase tracking-[0.18em] text-white/60 backdrop-blur lg:px-12">
      <span className="flex items-center gap-1.5 text-cyan-300"><Globe size={11} /> OPS BRIDGE</span>
      <Sep /><span>GLOBAL</span>
      <Sep /><span className="tabular-nums">{now} UTC</span>
      <Sep /><span>47 SITES</span>
      <Sep /><span>12 ACTIVE PROGRAMMES</span>
      <Sep />
      <span className="flex items-center gap-1.5 text-emerald-300">
        <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-400" />
        ALL TELEMETRY OK
      </span>
    </div>
  );
}

function Sep() { return <span className="text-white/20">·</span>; }

/* ---------- EDITORIAL COLUMN ----------------------------------------- */

function Editorial() {
  return (
    <section className="relative max-w-2xl">
      <div className="orb-rise mb-3 inline-flex items-center gap-2 rounded-full border border-white/[0.10] bg-white/[0.04] px-3 py-1 text-[10.5px] uppercase tracking-[0.20em] text-cyan-300/90">
        <span className="h-1.5 w-1.5 rounded-full bg-cyan-300 animate-pulse" />
        Operations Bridge
      </div>
      <h1 className="orb-rise orb-d1 mt-2 text-[44px] font-semibold leading-[1.04] tracking-[-0.025em] text-white sm:text-[52px]">
        One bridge,<br />every programme.
      </h1>
      <p className="orb-rise orb-d2 mt-5 max-w-xl text-[14.5px] leading-[1.65] text-white/60">
        Bipros is the operations bridge for global capital programmes. Forty-seven
        sites across six continents, twelve active programmes, one calm pane of glass —
        with telemetry streaming back to every desk that needs it.
      </p>

      <div className="orb-rise orb-d3 mt-9 grid max-w-xl grid-cols-3 gap-3">
        {STATS.map((s) => (
          <div key={s.k} className="rounded-lg border border-white/[0.08] bg-white/[0.025] p-3 backdrop-blur-sm">
            <div className="font-mono text-[9.5px] uppercase tracking-[0.20em] text-white/45">{s.k}</div>
            <div className="mt-1 text-[20px] font-semibold tabular-nums tracking-[-0.01em] text-white">{s.v}</div>
          </div>
        ))}
      </div>

      <div className="orb-rise orb-d4 mt-8 hidden max-w-xl items-center gap-3 rounded-lg border border-white/[0.08] bg-black/30 px-3 py-2.5 font-mono text-[10.5px] uppercase tracking-[0.18em] text-white/55 backdrop-blur sm:flex">
        <MapPin size={11} className="text-cyan-300" />
        <span className="text-white/75">RAIL-SCR · Sutton Coldfield</span>
        <span className="text-white/30">→</span>
        <span className="text-amber-300/90">at-risk · 0.4d slack</span>
        <span className="ml-auto text-white/40">live</span>
      </div>
    </section>
  );
}

/* ---------- AUTH CARD ------------------------------------------------ */

function AuthCard() {
  const f = useLoginSubmit();
  return (
    <aside className="orb-rise orb-d2 relative self-center">
      <div
        aria-hidden
        className="pointer-events-none absolute -inset-px rounded-[20px] opacity-70 blur-[1px]"
        style={{ background: "linear-gradient(135deg,rgba(94,191,212,0.55),rgba(94,191,212,0.10) 55%,rgba(245,166,107,0.30))" }}
      />
      <div className="relative rounded-[20px] border border-white/[0.10] bg-[#0E1426]/85 p-6 backdrop-blur-xl sm:p-7">
        <div className="flex items-start justify-between">
          <div>
            <div className="flex items-center gap-1.5 font-mono text-[10px] uppercase tracking-[0.22em] text-cyan-300">
              <Lock size={10} /> Bridge access · Sign in
            </div>
            <h2 className="mt-2 text-[24px] font-semibold leading-[1.15] tracking-[-0.02em] text-white">
              Step onto the bridge.
            </h2>
            <p className="mt-1 text-[13px] text-white/60">Authenticate to your tenant&apos;s global ops view.</p>
          </div>
          <span className="flex items-center gap-1 rounded-md border border-white/15 bg-white/[0.04] px-1.5 py-1 font-mono text-[9px] uppercase tracking-[0.16em] text-white/70">
            <Globe size={10} /> mTLS
          </span>
        </div>

        <div className="mt-4 flex items-center gap-1.5 overflow-x-auto rounded-md border border-white/[0.08] bg-black/30 px-2 py-1.5 font-mono text-[9.5px] uppercase tracking-[0.16em] text-white/55">
          <span className="text-white/35">Region</span>
          <Latency region="EU" ms={24} tone="ok" />
          <Latency region="US" ms={71} tone="ok" />
          <Latency region="APAC" ms={142} tone="warn" />
        </div>

        <form onSubmit={f.handleSubmit} noValidate className="mt-5 space-y-3">
          <SsoButton label="Continue with Microsoft Entra" hint="most teams" />
          <SsoButton label="Continue with Google Workspace" />
          <SsoButton label="Continue with SAML SSO" mono />

          <div className="my-3 flex items-center gap-3 font-mono text-[9.5px] uppercase tracking-[0.20em] text-white/40">
            <div className="h-px flex-1 bg-white/[0.10]" />or with email<div className="h-px flex-1 bg-white/[0.10]" />
          </div>

          <div>
            <label htmlFor="orb-user" className="mb-1.5 block font-mono text-[10px] uppercase tracking-[0.20em] text-white/55">
              Work email
            </label>
            <input
              id="orb-user" type="text" autoComplete="username" autoFocus required
              value={f.username} onChange={(e) => f.setUsername(e.target.value)}
              disabled={f.submitting} placeholder="you@company.com"
              className="block h-11 w-full rounded-lg border border-white/[0.10] bg-white/[0.03] px-3.5 text-[13.5px] text-white placeholder:text-white/30 transition focus:border-cyan-300/60 focus:bg-white/[0.05] focus:outline-none focus:ring-2 focus:ring-cyan-300/25"
            />
          </div>

          <div>
            <div className="mb-1.5 flex items-baseline justify-between">
              <label htmlFor="orb-pwd" className="font-mono text-[10px] uppercase tracking-[0.20em] text-white/55">Password</label>
              <button type="button" onClick={() => {}} className="text-[11px] text-cyan-300 hover:text-white">Forgot?</button>
            </div>
            <div className="relative">
              <input
                id="orb-pwd" type={f.showPassword ? "text" : "password"} autoComplete="current-password" required
                value={f.password} onChange={(e) => f.setPassword(e.target.value)}
                disabled={f.submitting} placeholder="••••••••"
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
              type="checkbox" checked={f.remember} onChange={(e) => f.setRemember(e.target.checked)}
              className="h-3.5 w-3.5 rounded border-white/30 accent-cyan-400"
            />
            Stay signed in for 7 days
          </label>

          {f.fieldError && (
            <div role="alert" className="rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-2.5 text-[12.5px] text-red-200">
              {f.fieldError}
            </div>
          )}

          <button
            type="submit"
            disabled={f.submitting}
            className="group relative inline-flex h-11 w-full items-center justify-center gap-2 overflow-hidden rounded-lg text-[13.5px] font-semibold text-[#0B1020] transition disabled:opacity-60"
            style={{ background: "linear-gradient(120deg,#7DE2F2,#5EBFD4 60%,#9FE7F2)", boxShadow: "0 12px 32px rgba(94,191,212,0.30)" }}
          >
            {f.submitting ? (
              <>
                <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-[#0B1020]/40 border-t-[#0B1020]" />
                Crossing the bridge
              </>
            ) : (
              <>
                Cross the bridge
                <ArrowRight size={14} className="transition group-hover:translate-x-0.5" />
              </>
            )}
          </button>
        </form>

        <div className="mt-6 border-t border-white/[0.08] pt-4">
          <div className="flex items-center justify-between font-mono text-[9.5px] uppercase tracking-[0.18em] text-white/45">
            <span>JWT · 15m · auto-refresh</span>
            <div className="flex flex-wrap gap-1.5">
              {["SOC 2", "ISO 27001", "GDPR", "HIPAA"].map((b) => (
                <span key={b} className="rounded border border-white/15 bg-white/[0.04] px-1.5 py-0.5">{b}</span>
              ))}
            </div>
          </div>
          <p className="mt-3 text-center text-[12px] text-white/55">
            Need access?{" "}
            <Link href="/welcome" className="font-medium text-cyan-300 hover:text-white">Contact tenant administrator →</Link>
          </p>
        </div>
      </div>
    </aside>
  );
}

function SsoButton({ label, hint, mono }: { label: string; hint?: string; mono?: boolean }) {
  return (
    <button
      type="button"
      onClick={() => {}}
      className="group flex h-11 w-full items-center justify-between rounded-lg border border-white/[0.10] bg-white/[0.03] px-3.5 text-[13px] font-medium text-white/85 transition hover:border-cyan-300/40 hover:bg-white/[0.06]"
    >
      <span className={mono ? "font-mono text-[12px] tracking-[0.04em]" : ""}>{label}</span>
      <span className="flex items-center gap-2">
        {hint && (
          <span className="hidden rounded-full border border-white/15 bg-white/[0.04] px-1.5 py-0.5 font-mono text-[9px] uppercase tracking-[0.16em] text-white/55 sm:inline">
            {hint}
          </span>
        )}
        <ArrowRight size={13} className="text-white/40 transition group-hover:translate-x-0.5 group-hover:text-cyan-300" />
      </span>
    </button>
  );
}

function Latency({ region, ms, tone }: { region: string; ms: number; tone: "ok" | "warn" }) {
  const color = tone === "ok" ? "text-emerald-300/90" : "text-amber-300/90";
  return (
    <span className={`flex items-center gap-1 ${color}`}>
      <span className={`h-1.5 w-1.5 rounded-full ${tone === "ok" ? "bg-emerald-400" : "bg-amber-400"}`} />
      {region} {ms}ms
    </span>
  );
}

/* ---------- DATA ----------------------------------------------------- */

const STATS: { k: string; v: string }[] = [
  { k: "Sites worldwide", v: "47" },
  { k: "Capital under control", v: "€18B+" },
  { k: "Telemetry uptime", v: "99.99%" },
];

type Pin = { id: string; x: number; y: number; label: string; warn?: boolean };

// 720x360 equirectangular: x = (lon + 180) * 2, y = (90 - lat) * 2.
const PINS: Pin[] = [
  { id: "lon", x: 360, y: 76, label: "LON" },
  { id: "fra", x: 376, y: 80, label: "FRA" },
  { id: "lyo", x: 370, y: 88, label: "LYO" },
  { id: "ant", x: 369, y: 78, label: "ANT" },
  { id: "ryd", x: 453, y: 130, label: "RYD", warn: true },
  { id: "mum", x: 506, y: 142, label: "MUM" },
  { id: "sgp", x: 568, y: 178, label: "SGP" },
  { id: "tyo", x: 640, y: 110, label: "TYO" },
  { id: "syd", x: 660, y: 250, label: "SYD" },
  { id: "hou", x: 178, y: 130, label: "HOU" },
  { id: "tor", x: 196, y: 92, label: "TOR" },
  { id: "rio", x: 256, y: 226, label: "RIO", warn: true },
];

const ARCS: [string, string][] = [
  ["lon", "tyo"], ["fra", "sgp"], ["hou", "lon"],
  ["tor", "rio"], ["ryd", "mum"], ["sgp", "syd"],
];

/** Quadratic SVG path between two points, bulging "north" (toward smaller y). */
function greatArc(x1: number, y1: number, x2: number, y2: number) {
  const mx = (x1 + x2) / 2;
  const my = (y1 + y2) / 2;
  const dx = x2 - x1;
  const dy = y2 - y1;
  const dist = Math.sqrt(dx * dx + dy * dy) || 1;
  const bulge = Math.min(dist * 0.32, 90);
  const nx = -dy / dist;
  const ny = dx / dist;
  let cy = my + ny * bulge - 8;
  if (cy > my) cy = my - bulge * 0.6;
  return `M ${x1} ${y1} Q ${mx + nx * bulge} ${cy} ${x2} ${y2}`;
}

/**
 * LAND_DOTS — stylised dot map. Each region is a bounding rect approximating
 * a continent on the equirectangular canvas; dots are filled on a 6px grid,
 * thinned at edges for an organic blob.
 */
const LAND_DOTS: [number, number][] = (() => {
  const dots: [number, number][] = [];
  const regions: [number, number, number, number][] = [
    [120, 220, 50, 130], [140, 230, 70, 140], [170, 240, 100, 145], [104, 150, 60, 100], // N America
    [240, 290, 30, 80],                                                                   // Greenland
    [188, 226, 130, 155],                                                                 // C America
    [228, 290, 150, 250], [240, 280, 180, 280],                                           // S America
    [350, 400, 60, 110], [360, 410, 70, 100],                                             // Europe
    [350, 430, 130, 240], [360, 420, 160, 260],                                           // Africa
    [410, 460, 110, 160],                                                                 // Middle East
    [430, 600, 60, 160], [460, 620, 90, 170], [500, 600, 140, 180],                       // Asia
    [540, 620, 170, 200],                                                                 // SE Asia
    [620, 660, 90, 130],                                                                  // Japan
    [600, 680, 220, 270],                                                                 // Australia
    [40, 700, 320, 350],                                                                  // Antarctica strip
  ];
  const step = 6;
  for (const [xs, xe, ys, ye] of regions) {
    for (let y = ys; y <= ye; y += step) {
      for (let x = xs; x <= xe; x += step) {
        const h = ((x * 73856093) ^ (y * 19349663)) >>> 0;
        const r = (h % 100) / 100;
        const cx = (xs + xe) / 2, cy = (ys + ye) / 2;
        const rw = (xe - xs) / 2 || 1, rh = (ye - ys) / 2 || 1;
        const nx = (x - cx) / rw, ny = (y - cy) / rh;
        const inside = nx * nx + ny * ny;
        if (inside > 1.05) continue;
        if (r < 0.78 - inside * 0.35) dots.push([x, y]);
      }
    }
  }
  const seen = new Set<string>();
  return dots.filter(([x, y]) => {
    const k = `${x},${y}`;
    if (seen.has(k)) return false;
    seen.add(k);
    return true;
  });
})();

/* ---------- KEYFRAMES ------------------------------------------------ */

function Keyframes() {
  return (
    <style>{`
      @keyframes orbRise { from { opacity:0; transform: translateY(10px); } to { opacity:1; transform:none; } }
      .orb-rise { animation: orbRise .65s cubic-bezier(.22,1,.36,1) both; }
      .orb-d1 { animation-delay: .07s; } .orb-d2 { animation-delay: .14s; }
      .orb-d3 { animation-delay: .22s; } .orb-d4 { animation-delay: .32s; }
      @keyframes orbPin { 0% { opacity:0; transform: scale(0.6); } 60% { opacity:1; transform: scale(1.15); } 100% { opacity:1; transform: scale(1); } }
      .orb-pin { transform-box: fill-box; transform-origin: center; animation: orbPin .9s cubic-bezier(.22,1,.36,1) both; }
      @keyframes orbArcDraw { from { stroke-dashoffset: 320; opacity: 0; } to { stroke-dashoffset: 0; opacity: 1; } }
      .orb-arc { stroke-dasharray: 2 3; animation: orbArcDraw 1.6s ease-out both; }
      @media (prefers-reduced-motion: reduce) { .orb-rise, .orb-pin, .orb-arc { animation: none !important; } }
    `}</style>
  );
}
