"use client";

import { Suspense } from "react";
import Link from "next/link";
import { ArrowRight, Eye, EyeOff, Lock, ShieldCheck } from "lucide-react";
import { useLoginSubmit } from "../_shared/useLoginSubmit";

/**
 * Concept 6 — Drawing Office · Engineering Blueprint (Cyanotype).
 * Full-bleed inline-SVG blueprint as the canvas: orthographic tower elevation,
 * plan view with column grid, dimension callouts, leader annotations, and a
 * working title block. Auth panel is a stamped approval card.
 */
export default function BlueprintPage() {
  return (
    <div className="relative min-h-screen overflow-hidden bg-[#0E2438] font-sans text-[#E8F1F7] antialiased">
      <Blueprint />
      <Atmosphere />
      <StatusStrip />
      <main className="relative grid min-h-[calc(100vh-44px)] grid-cols-1 gap-10 px-6 py-10 lg:grid-cols-[1.15fr_440px] lg:gap-16 lg:px-14 lg:py-14">
        <Editorial />
        <Suspense
          fallback={
            <div className="grid place-items-center font-mono text-[11px] uppercase tracking-[0.18em] text-[#9DC3D8]/60">
              Loading drawing…
            </div>
          }
        >
          <Form />
        </Suspense>
      </main>
      <Footer />
      <Keyframes />
    </div>
  );
}

/* ---------------------------------------------------------------- */
/*  Blueprint canvas — pure inline SVG                              */
/* ---------------------------------------------------------------- */

function Blueprint() {
  return (
    <svg aria-hidden className="pointer-events-none absolute inset-0 -z-10 h-full w-full" viewBox="0 0 1600 1000" preserveAspectRatio="xMidYMid slice" fontFamily="ui-monospace, 'JetBrains Mono', monospace">
      <defs>
        <pattern id="bp-grid-fine" width="20" height="20" patternUnits="userSpaceOnUse"><path d="M 20 0 L 0 0 0 20" fill="none" stroke="#9DC3D8" strokeOpacity="0.08" strokeWidth="0.5" /></pattern>
        <pattern id="bp-grid-major" width="100" height="100" patternUnits="userSpaceOnUse"><path d="M 100 0 L 0 0 0 100" fill="none" stroke="#9DC3D8" strokeOpacity="0.18" strokeWidth="0.7" /></pattern>
        <pattern id="bp-concrete" width="8" height="8" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><line x1="0" y1="0" x2="0" y2="8" stroke="#9DC3D8" strokeOpacity="0.55" strokeWidth="0.6" /></pattern>
        <pattern id="bp-ground" width="14" height="14" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><line x1="0" y1="0" x2="0" y2="14" stroke="#9DC3D8" strokeOpacity="0.4" strokeWidth="0.5" /></pattern>
        <radialGradient id="bp-fade" cx="50%" cy="42%" r="75%">
          <stop offset="0%" stopColor="#0E2438" stopOpacity="0" />
          <stop offset="62%" stopColor="#0E2438" stopOpacity="0.15" />
          <stop offset="100%" stopColor="#061322" stopOpacity="0.85" />
        </radialGradient>
        <marker id="bp-arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#9DC3D8" fillOpacity="0.85" /></marker>
        <marker id="bp-tick" viewBox="0 0 4 10" refX="2" refY="5" markerWidth="4" markerHeight="10" orient="auto"><line x1="2" y1="0" x2="2" y2="10" stroke="#9DC3D8" strokeOpacity="0.85" strokeWidth="1" /></marker>
      </defs>

      {/* base canvas + grids */}
      <rect width="1600" height="1000" fill="#0E2438" />
      <rect width="1600" height="1000" fill="url(#bp-grid-fine)" />
      <rect width="1600" height="1000" fill="url(#bp-grid-major)" />

      {/* drawing border + corner brackets */}
      <g stroke="#9DC3D8" strokeOpacity="0.55" fill="none">
        <rect x="32" y="32" width="1536" height="936" strokeWidth="1.1" />
        <rect x="46" y="46" width="1508" height="908" strokeWidth="0.5" strokeOpacity="0.35" />
        {/* corner brackets */}
        <g strokeWidth="1.6" strokeOpacity="0.85">
          <path d="M 32 70 L 32 32 L 70 32" />
          <path d="M 1568 70 L 1568 32 L 1530 32" />
          <path d="M 32 930 L 32 968 L 70 968" />
          <path d="M 1568 930 L 1568 968 L 1530 968" />
        </g>
      </g>

      {/* registration ticks at edges */}
      <g stroke="#9DC3D8" strokeOpacity="0.45" strokeWidth="0.6" fontSize="9" fill="#9DC3D8" fillOpacity="0.55">
        {Array.from({ length: 15 }).map((_, i) => { const x = 100 + i * 100; return (
          <g key={`tx-${i}`}><line x1={x} y1="46" x2={x} y2="58" /><text x={x} y={26} textAnchor="middle">{(i + 1).toString().padStart(2, "0")}</text></g>
        ); })}
        {Array.from({ length: 8 }).map((_, i) => { const y = 120 + i * 100; return (
          <g key={`ty-${i}`}><line x1="46" y1={y} x2="58" y2={y} /><text x={28} y={y + 3} textAnchor="middle">{String.fromCharCode(65 + i)}</text></g>
        ); })}
      </g>

      {/* ============ TOWER ELEVATION (left) ============ */}
      <g transform="translate(120,140)">
        <text x="0" y="-8" fontSize="10" letterSpacing="2" fill="#E8F1F7" fillOpacity="0.85">ELEVATION · NORTH FACE</text>
        <text x="0" y="6" fontSize="8" letterSpacing="2" fill="#9DC3D8" fillOpacity="0.6">DETAIL · 01 / 04 · SCALE 1:200</text>

        {/* ground line + ground hatching */}
        <line x1="-30" y1="640" x2="430" y2="640" stroke="#9DC3D8" strokeOpacity="0.7" strokeWidth="1" />
        <rect x="-30" y="640" width="460" height="40" fill="url(#bp-ground)" />

        {/* foundation footing (concrete hatch) */}
        <rect x="40" y="600" width="320" height="40" fill="url(#bp-concrete)" stroke="#9DC3D8" strokeOpacity="0.85" strokeWidth="1" />
        <rect x="80" y="560" width="240" height="40" fill="url(#bp-concrete)" stroke="#9DC3D8" strokeOpacity="0.85" strokeWidth="1" />

        {/* tower core — 8 storey stack */}
        <g stroke="#E8F1F7" strokeOpacity="0.9" fill="none" strokeWidth="1">
          <rect x="120" y="100" width="160" height="460" />
          {Array.from({ length: 7 }).map((_, i) => (
            <line key={`slab-${i}`} x1="120" y1={160 + i * 56} x2="280" y2={160 + i * 56} strokeOpacity="0.7" />
          ))}
          {Array.from({ length: 5 }).map((_, i) => (
            <line key={`mul-${i}`} x1={150 + i * 26} y1="100" x2={150 + i * 26} y2="560" strokeOpacity="0.35" strokeWidth="0.6" />
          ))}
          <path d="M 120 100 L 200 60 L 280 100" />
          <line x1="200" y1="60" x2="200" y2="40" />
          <circle cx="200" cy="36" r="3" fill="#9DC3D8" fillOpacity="0.8" stroke="none" />
          <line x1="200" y1="100" x2="200" y2="560" strokeOpacity="0.35" strokeDasharray="2 4" />
        </g>

        {/* storey numbers */}
        <g fontSize="8.5" fill="#9DC3D8" fillOpacity="0.85">
          {Array.from({ length: 8 }).map((_, i) => (
            <text key={`L-${i}`} x="294" y={156 - i * 56 + 444} dominantBaseline="middle">L{(i + 1).toString().padStart(2, "0")}</text>
          ))}
        </g>

        {/* vertical dimension chain (left of tower) */}
        <g stroke="#9DC3D8" strokeOpacity="0.85" strokeWidth="0.7" fontSize="9" fill="#E8F1F7" fillOpacity="0.85">
          <line x1="80" y1="100" x2="80" y2="560" markerStart="url(#bp-tick)" markerEnd="url(#bp-tick)" />
          {Array.from({ length: 8 }).map((_, i) => (
            <g key={`vd-${i}`}><line x1="74" y1={104 + i * 56} x2="86" y2={104 + i * 56} /><text x="68" y={132 + i * 56} textAnchor="end">3600</text></g>
          ))}
          <text x="40" y="328" textAnchor="middle" transform="rotate(-90 40 328)" letterSpacing="1.5">TOTAL HEIGHT · 28800</text>
        </g>

        {/* horizontal dimensions */}
        <g stroke="#9DC3D8" strokeOpacity="0.85" strokeWidth="0.7" fontSize="9" fill="#E8F1F7" fillOpacity="0.85">
          <line x1="120" y1="700" x2="280" y2="700" markerStart="url(#bp-arrow)" markerEnd="url(#bp-arrow)" />
          <line x1="120" y1="694" x2="120" y2="706" /><line x1="280" y1="694" x2="280" y2="706" />
          <text x="200" y="715" textAnchor="middle">2400</text>
          <line x1="40" y1="730" x2="360" y2="730" markerStart="url(#bp-arrow)" markerEnd="url(#bp-arrow)" />
          <line x1="40" y1="724" x2="40" y2="736" /><line x1="360" y1="724" x2="360" y2="736" />
          <text x="200" y="745" textAnchor="middle">FOOTING · 4800</text>
        </g>

        {/* leader annotations */}
        <g stroke="#9DC3D8" strokeOpacity="0.7" strokeWidth="0.7" fontSize="9" fill="#E8F1F7" fillOpacity="0.85">
          <path d="M 280 180 L 380 160 L 430 160" fill="none" />
          <text x="436" y="158" letterSpacing="1.2">STEEL FRAME · S355</text>
          <text x="436" y="170" fontSize="7.5" fillOpacity="0.55">FULL HEIGHT · WELDED</text>
          <path d="M 200 216 L 380 234 L 430 234" fill="none" />
          <text x="436" y="232" letterSpacing="1.2">RC SLAB · 250mm</text>
          <text x="436" y="244" fontSize="7.5" fillOpacity="0.55">C40/50 · MESH B785</text>
          <path d="M 120 380 L 60 400 L -12 400" fill="none" />
          <text x="-16" y="398" textAnchor="end" letterSpacing="1.2">CL BEARING</text>
          <path d="M 220 600 L 380 610 L 430 610" fill="none" />
          <text x="436" y="608" letterSpacing="1.2">PILE CAP · C32/40</text>
        </g>

        {/* section cut markers */}
        <g stroke="#E8F1F7" strokeOpacity="0.85" fill="none"><circle cx="-10" cy="328" r="11" /><line x1="-10" y1="320" x2="-10" y2="336" /></g>
        <text x="-10" y="332" textAnchor="middle" fontSize="9" fill="#E8F1F7" fillOpacity="0.9">A</text>
      </g>

      {/* ============ PLAN VIEW (top right) ============ */}
      <g transform="translate(900,140)">
        <text x="0" y="-8" fontSize="10" letterSpacing="2" fill="#E8F1F7" fillOpacity="0.85">PLAN · LEVEL 03 · TYPICAL FLOOR</text>
        <text x="0" y="6" fontSize="8" letterSpacing="2" fill="#9DC3D8" fillOpacity="0.6">DETAIL · 02 / 04 · SCALE 1:150</text>

        {/* outer wall */}
        <rect x="0" y="20" width="540" height="320" fill="none" stroke="#E8F1F7" strokeOpacity="0.9" strokeWidth="1.2" />
        {/* inner wall */}
        <rect x="20" y="40" width="500" height="280" fill="none" stroke="#9DC3D8" strokeOpacity="0.55" strokeWidth="0.7" />

        {/* core */}
        <rect x="220" y="130" width="100" height="100" fill="none" stroke="#E8F1F7" strokeOpacity="0.85" strokeWidth="1" />
        <line x1="220" y1="130" x2="320" y2="230" stroke="#9DC3D8" strokeOpacity="0.5" strokeWidth="0.6" />
        <line x1="320" y1="130" x2="220" y2="230" stroke="#9DC3D8" strokeOpacity="0.5" strokeWidth="0.6" />
        <text x="270" y="184" textAnchor="middle" fontSize="8" fill="#9DC3D8" fillOpacity="0.8" letterSpacing="1">CORE</text>

        {/* column grid (dot intersections) */}
        {Array.from({ length: 6 }).map((_, c) => Array.from({ length: 4 }).map((_, r) => (
          <g key={`col-${c}-${r}`}><circle cx={50 + c * 88} cy={70 + r * 70} r="3" fill="#E8F1F7" fillOpacity="0.85" /><circle cx={50 + c * 88} cy={70 + r * 70} r="6" fill="none" stroke="#9DC3D8" strokeOpacity="0.4" strokeWidth="0.5" /></g>
        )))}

        {/* grid axis labels */}
        <g fontSize="9" fill="#9DC3D8" fillOpacity="0.85" letterSpacing="1">
          {["A", "B", "C", "D", "E", "F"].map((l, i) => (<text key={`gl-${l}`} x={50 + i * 88} y="14" textAnchor="middle">{l}</text>))}
          {[1, 2, 3, 4].map((n, i) => (<text key={`gn-${n}`} x="-12" y={73 + i * 70} textAnchor="middle">{n}</text>))}
        </g>

        {/* horizontal dim chain */}
        <g stroke="#9DC3D8" strokeOpacity="0.85" strokeWidth="0.7" fontSize="8.5" fill="#E8F1F7" fillOpacity="0.85">
          <line x1="50" y1="-30" x2="490" y2="-30" markerStart="url(#bp-tick)" markerEnd="url(#bp-tick)" />
          {Array.from({ length: 5 }).map((_, i) => (
            <g key={`pdh-${i}`}><line x1={50 + i * 88} y1="-34" x2={50 + i * 88} y2="-26" /><text x={94 + i * 88} y="-34" textAnchor="middle">8800</text></g>
          ))}
          <line x1="490" y1="-34" x2="490" y2="-26" />
        </g>

        {/* vertical dim chain */}
        <g stroke="#9DC3D8" strokeOpacity="0.85" strokeWidth="0.7" fontSize="8.5" fill="#E8F1F7" fillOpacity="0.85">
          <line x1="580" y1="70" x2="580" y2="280" markerStart="url(#bp-tick)" markerEnd="url(#bp-tick)" />
          {[0, 1, 2].map((i) => (
            <g key={`pdv-${i}`}><line x1="576" y1={73 + i * 70} x2="584" y2={73 + i * 70} /><text x="588" y={111 + i * 70}>7000</text></g>
          ))}
          <line x1="576" y1="283" x2="584" y2="283" />
        </g>

        {/* section cut line A-A */}
        <line x1="-30" y1="180" x2="570" y2="180" stroke="#E8F1F7" strokeOpacity="0.7" strokeWidth="0.8" strokeDasharray="6 3 1 3" />
        <g fontSize="9" fill="#E8F1F7" fillOpacity="0.9">
          <circle cx="-30" cy="180" r="11" fill="none" stroke="#E8F1F7" strokeOpacity="0.85" /><text x="-30" y="184" textAnchor="middle">A</text>
          <circle cx="570" cy="180" r="11" fill="none" stroke="#E8F1F7" strokeOpacity="0.85" /><text x="570" y="184" textAnchor="middle">A</text>
        </g>

        {/* north arrow */}
        <g transform="translate(540,300)">
          <circle r="20" fill="none" stroke="#9DC3D8" strokeOpacity="0.6" strokeWidth="0.6" />
          <path d="M 0 -16 L 6 12 L 0 6 L -6 12 Z" fill="#E8F1F7" fillOpacity="0.85" />
          <text y="-22" textAnchor="middle" fontSize="9" fill="#E8F1F7" fillOpacity="0.85">N</text>
        </g>
      </g>

      {/* ============ BRIDGE / BEAM SECTION (mid-bottom-left) ============ */}
      <g transform="translate(140,790)">
        <text x="0" y="-8" fontSize="10" letterSpacing="2" fill="#E8F1F7" fillOpacity="0.85">
          SECTION A-A · TYPICAL DECK
        </text>
        <line x1="0" y1="60" x2="420" y2="60" stroke="#E8F1F7" strokeOpacity="0.85" strokeWidth="1.1" />
        <rect x="0" y="60" width="420" height="22" fill="url(#bp-concrete)" stroke="#E8F1F7" strokeOpacity="0.85" strokeWidth="0.9" />
        {/* I-beams under deck */}
        {[60, 160, 260, 360].map((x) => (
          <g key={`beam-${x}`} stroke="#E8F1F7" strokeOpacity="0.85" strokeWidth="0.9" fill="none"><line x1={x} y1="82" x2={x} y2="120" /><line x1={x - 10} y1="82" x2={x + 10} y2="82" /><line x1={x - 10} y1="120" x2={x + 10} y2="120" /></g>
        ))}
        {/* dim */}
        <g stroke="#9DC3D8" strokeOpacity="0.85" strokeWidth="0.7" fontSize="9" fill="#E8F1F7" fillOpacity="0.85">
          {[60, 160, 260].map((x) => (
            <g key={`dk-${x}`}>
              <line x1={x} y1="40" x2={x + 100} y2="40" markerStart="url(#bp-arrow)" markerEnd="url(#bp-arrow)" />
              <text x={x + 50} y="34" textAnchor="middle">1200</text>
            </g>
          ))}
        </g>
      </g>

      {/* fade overlay so centre reads clean for typography */}
      <rect width="1600" height="1000" fill="url(#bp-fade)" />

      {/* ============ TITLE BLOCK (bottom-right) ============ */}
      <g transform="translate(1130,790)">
        <rect x="0" y="0" width="430" height="160" fill="#0E2438" fillOpacity="0.92" stroke="#E8F1F7" strokeOpacity="0.85" strokeWidth="1" />
        {/* internal grid */}
        <g stroke="#9DC3D8" strokeOpacity="0.55" strokeWidth="0.6">
          <line x1="0" y1="32" x2="430" y2="32" />
          <line x1="0" y1="68" x2="430" y2="68" />
          <line x1="0" y1="104" x2="430" y2="104" />
          <line x1="0" y1="132" x2="430" y2="132" />
          <line x1="160" y1="32" x2="160" y2="160" />
          <line x1="290" y1="32" x2="290" y2="160" />
          <line x1="80" y1="104" x2="80" y2="132" />
          <line x1="220" y1="104" x2="220" y2="132" />
        </g>

        {/* header bar */}
        <text x="14" y="20" fontSize="11" letterSpacing="3" fill="#E8F1F7" fillOpacity="0.95">
          BIPROS · DRAWING OFFICE
        </text>
        <text x="416" y="20" fontSize="9" letterSpacing="2" textAnchor="end" fill="#9DC3D8" fillOpacity="0.85">
          BPS-LOGIN-001
        </text>

        {/* labels (small) and values (large) */}
        <g fontSize="7.5" letterSpacing="1.5" fill="#9DC3D8" fillOpacity="0.7">
          <text x="10" y="46">PROJECT</text><text x="170" y="46">CLIENT</text><text x="300" y="46">DISCIPLINE</text>
          <text x="10" y="82">DRAWING NO.</text><text x="170" y="82">REV</text><text x="300" y="82">SCALE</text>
          <text x="10" y="118">DATE</text><text x="84" y="118">DRAWN</text><text x="170" y="118">CHECKED</text><text x="224" y="118">APPROVED</text><text x="300" y="118">SHEET</text>
        </g>
        <g fontSize="11" letterSpacing="1.4" fill="#E8F1F7" fillOpacity="0.95">
          <text x="10" y="62">PORTFOLIO COMMAND CENTRE</text><text x="170" y="62">BIPROS EPPM</text><text x="300" y="62">STRUCTURAL</text>
          <text x="10" y="98">BPS-LOGIN-001</text><text x="170" y="98">A</text><text x="300" y="98">1:200 @ A1</text>
        </g>
        <g fontSize="9.5" letterSpacing="1.4" fill="#E8F1F7" fillOpacity="0.95">
          <text x="10" y="128">2026-05-09</text><text x="84" y="128">J. PEMBERTON</text><text x="170" y="128">M. ASHFORD</text><text x="224" y="128">R. OKAFOR</text><text x="300" y="128">04 OF 04</text>
        </g>

        {/* footer */}
        <text x="10" y="150" fontSize="8" letterSpacing="1.6" fill="#9DC3D8" fillOpacity="0.7">
          STATUS · ISSUED FOR CONSTRUCTION
        </text>
        <text x="420" y="150" fontSize="8" letterSpacing="1.6" textAnchor="end" fill="#9DC3D8" fillOpacity="0.7">
          © BIPROS 2026
        </text>
      </g>
    </svg>
  );
}

/* ---------------------------------------------------------------- */
/*  Atmosphere — paper grain + vignette                             */
/* ---------------------------------------------------------------- */

function Atmosphere() {
  return (
    <>
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 -z-10"
        style={{
          background:
            "radial-gradient(ellipse 65% 45% at 50% 38%, rgba(157,195,216,0.10), transparent 70%)",
        }}
      />
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 -z-10 opacity-[0.04] mix-blend-overlay"
        style={{
          backgroundImage:
            "url(\"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='200' height='200'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='2' stitchTiles='stitch'/><feColorMatrix values='0 0 0 0 1 0 0 0 0 1 0 0 0 0 1 0 0 0 0.55 0'/></filter><rect width='100%' height='100%' filter='url(%23n)'/></svg>\")",
        }}
      />
    </>
  );
}

/* ---------------------------------------------------------------- */
/*  Top status strip                                                */
/* ---------------------------------------------------------------- */

function StatusStrip() {
  return (
    <div className="relative flex h-11 items-center justify-between border-b border-[#9DC3D8]/20 bg-[#0A1B2C]/70 px-6 font-mono text-[10.5px] uppercase tracking-[0.22em] text-[#9DC3D8]/85 backdrop-blur lg:px-14">
      <div className="flex items-center gap-3 sm:gap-5">
        <span aria-hidden className="inline-block h-1.5 w-1.5 rounded-full bg-[#7AE2C7]" />
        <span>DWG · BPS-LOGIN-001</span>
        <span className="hidden sm:inline opacity-50">·</span>
        <span className="hidden sm:inline">REV A</span>
        <span className="hidden sm:inline opacity-50">·</span>
        <span className="hidden sm:inline">2026-05-09</span>
      </div>
      <div className="flex items-center gap-3">
        <span className="hidden md:inline opacity-65">SCALE 1:200</span>
        <span className="hidden md:inline opacity-50">·</span>
        <span className="text-[#E8F1F7]">STATUS · ISSUED FOR CONSTRUCTION</span>
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------- */
/*  Editorial column                                                */
/* ---------------------------------------------------------------- */

function Editorial() {
  return (
    <section className="relative max-w-2xl">
      <div className="bp-rise mb-6 inline-flex items-center gap-3 font-mono text-[10.5px] uppercase tracking-[0.28em] text-[#9DC3D8]">
        <span aria-hidden className="inline-block h-px w-8 bg-[#9DC3D8]" />
        BIPROS · DRAWING OFFICE
      </div>

      <h1 className="bp-rise bp-d1 font-display text-[52px] font-medium leading-[1.04] tracking-[-0.025em] text-[#F4FAFE] sm:text-[64px]" style={{ fontVariationSettings: "'opsz' 144" }}>
        Engineered to one
        <span className="block italic font-normal text-[#9DC3D8]">calm spine.</span>
      </h1>

      <p className="bp-rise bp-d2 mt-7 max-w-lg text-[15px] leading-[1.7] text-[#E8F1F7]/65">
        Every programme has a drawing — a single sheet that says how the work
        holds together. Bipros is that drawing for capital delivery: schedule,
        cost, contract and risk drawn to one scale, signed by the people accountable.
      </p>

      <div className="bp-rise bp-d3 mt-12 grid max-w-xl grid-cols-3 gap-px overflow-hidden rounded-md border border-[#9DC3D8]/20 bg-[#9DC3D8]/[0.04]">
        {SHEETS.map((s) => (
          <div key={s.label} className="bg-[#0A1B2C]/70 p-5">
            <div className="font-display text-[28px] font-medium leading-none tabular-nums text-[#F4FAFE]" style={{ fontVariationSettings: "'opsz' 144" }}>{s.value}</div>
            <div className="mt-2 font-mono text-[10px] uppercase tracking-[0.18em] text-[#9DC3D8]/75">{s.label}</div>
          </div>
        ))}
      </div>

      <figure className="bp-rise bp-d4 mt-12 max-w-xl border-l-2 border-[#9DC3D8]/40 pl-6">
        <blockquote className="font-display text-[21px] leading-[1.45] tracking-[-0.005em] text-[#F4FAFE]/85" style={{ fontVariationSettings: "'opsz' 24" }}>
          &ldquo;A blueprint imposes discipline because everyone reads it the same way. That&rsquo;s what this platform does to a portfolio.&rdquo;
        </blockquote>
        <figcaption className="mt-4 flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.18em] text-[#9DC3D8]/65">
          <span aria-hidden className="inline-block h-px w-5 bg-[#9DC3D8]" />
          Programme Director · UK Civils · £2.4B framework
        </figcaption>
      </figure>

      <div className="bp-rise bp-d5 mt-10 flex flex-wrap items-center gap-x-9 gap-y-3 font-mono text-[11px] uppercase tracking-[0.2em] text-[#9DC3D8]/55">
        <span className="text-[#9DC3D8]/40">Trusted by</span>
        {["Network Rail", "Mott MacDonald", "Bechtel", "Skanska", "AECOM"].map((n) => (
          <span key={n} className="transition-colors hover:text-[#F4FAFE]">{n}</span>
        ))}
      </div>
    </section>
  );
}

const SHEETS: Array<{ value: string; label: string }> = [
  { value: "1:1", label: "Schedule · Cost" },
  { value: "184K", label: "Activities drawn" },
  { value: "REV A", label: "Audit ledger" },
];

/* ---------------------------------------------------------------- */
/*  Auth panel — stamped approval card                              */
/* ---------------------------------------------------------------- */

function Form() {
  const f = useLoginSubmit();
  return (
    <aside className="bp-rise bp-d1 relative">
      {/* serrated border via SVG behind the card */}
      <svg aria-hidden className="pointer-events-none absolute -inset-2 h-[calc(100%+1rem)] w-[calc(100%+1rem)]" viewBox="0 0 100 100" preserveAspectRatio="none">
        <rect x="1" y="1" width="98" height="98" fill="none" stroke="#9DC3D8" strokeOpacity="0.45" strokeWidth="0.4" strokeDasharray="1.2 0.8" vectorEffect="non-scaling-stroke" />
      </svg>

      <form onSubmit={f.handleSubmit} noValidate className="relative rounded-md border border-[#9DC3D8]/30 bg-[#0A1B2C]/85 p-7 shadow-[0_30px_80px_rgba(0,0,0,0.55)] backdrop-blur">
        {/* corner ticks */}
        <span aria-hidden className="absolute left-2 top-2 h-3 w-3 border-l border-t border-[#9DC3D8]/65" />
        <span aria-hidden className="absolute right-2 top-2 h-3 w-3 border-r border-t border-[#9DC3D8]/65" />
        <span aria-hidden className="absolute bottom-2 left-2 h-3 w-3 border-b border-l border-[#9DC3D8]/65" />
        <span aria-hidden className="absolute bottom-2 right-2 h-3 w-3 border-b border-r border-[#9DC3D8]/65" />

        <div className="flex items-start justify-between">
          <div>
            <div className="flex items-center gap-2 font-mono text-[10.5px] uppercase tracking-[0.26em] text-[#9DC3D8]">
              <span aria-hidden className="inline-block h-px w-5 bg-[#9DC3D8]" /> Sign in
            </div>
            <h2 className="mt-2 font-display text-[26px] font-medium leading-[1.08] tracking-[-0.015em] text-[#F4FAFE]" style={{ fontVariationSettings: "'opsz' 144" }}>Approve &amp; enter.</h2>
            <p className="mt-1 font-mono text-[10.5px] uppercase tracking-[0.18em] text-[#9DC3D8]/70">CHECK-OUT · BPS-LOGIN-001</p>
          </div>
          <span className="flex items-center gap-1 rounded border border-[#9DC3D8]/30 bg-[#9DC3D8]/[0.06] px-1.5 py-1 font-mono text-[9px] uppercase tracking-[0.18em] text-[#9DC3D8]"><Lock size={10} /> TLS</span>
        </div>

        <div className="mt-6 grid grid-cols-3 gap-2">
          <SsoBtn label="Microsoft" />
          <SsoBtn label="Google" />
          <SsoBtn label="SAML" />
        </div>

        <div className="my-5 flex items-center gap-3 font-mono text-[9.5px] uppercase tracking-[0.22em] text-[#9DC3D8]/55">
          <div className="h-px flex-1 bg-[#9DC3D8]/15" />
          or with credentials
          <div className="h-px flex-1 bg-[#9DC3D8]/15" />
        </div>

        <div className="space-y-4">
          <Field id="bp-user" label="Username or email" note="REF · USR-01">
            <input id="bp-user" type="text" autoComplete="username" autoFocus required value={f.username} onChange={(e) => f.setUsername(e.target.value)} disabled={f.submitting} placeholder="you@company.com" className="block h-11 w-full rounded-md border border-[#9DC3D8]/25 bg-[#06121F]/70 px-3.5 font-mono text-[13.5px] text-[#F4FAFE] placeholder:text-[#9DC3D8]/30 transition focus:border-[#9DC3D8]/70 focus:bg-[#06121F] focus:outline-none focus:ring-2 focus:ring-[#9DC3D8]/25" />
          </Field>

          <div>
            <div className="mb-1.5 flex items-baseline justify-between">
              <label htmlFor="bp-pwd" className="font-mono text-[10px] uppercase tracking-[0.2em] text-[#9DC3D8]/80">
                Password <span className="ml-2 text-[#9DC3D8]/40">REF · PWD-01</span>
              </label>
              <button
                type="button"
                onClick={() => {}}
                className="font-mono text-[10px] uppercase tracking-[0.18em] text-[#9DC3D8] hover:text-[#F4FAFE]"
              >
                Reset →
              </button>
            </div>
            <div className="relative">
              <input id="bp-pwd" type={f.showPassword ? "text" : "password"} autoComplete="current-password" required value={f.password} onChange={(e) => f.setPassword(e.target.value)} disabled={f.submitting} placeholder="••••••••" className="block h-11 w-full rounded-md border border-[#9DC3D8]/25 bg-[#06121F]/70 px-3.5 pr-10 font-mono text-[13.5px] text-[#F4FAFE] placeholder:text-[#9DC3D8]/30 transition focus:border-[#9DC3D8]/70 focus:bg-[#06121F] focus:outline-none focus:ring-2 focus:ring-[#9DC3D8]/25" />
              <button type="button" onClick={() => f.setShowPassword((v) => !v)} className="absolute inset-y-0 right-0 flex items-center px-3 text-[#9DC3D8]/65 hover:text-[#F4FAFE]" aria-label={f.showPassword ? "Hide password" : "Show password"} tabIndex={-1}>
                {f.showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>
          </div>
        </div>

        <label className="mt-3.5 flex cursor-pointer items-center gap-2 font-mono text-[11px] uppercase tracking-[0.16em] text-[#9DC3D8]/80">
          <input type="checkbox" checked={f.remember} onChange={(e) => f.setRemember(e.target.checked)} className="h-3.5 w-3.5 rounded border-[#9DC3D8]/40 accent-[#9DC3D8]" />
          Keep this session checked-out for 7 days
        </label>

        {f.fieldError && (
          <div role="alert" className="mt-4 rounded-md border border-[#E97C6E]/40 bg-[#E97C6E]/10 px-3.5 py-2.5 font-mono text-[12px] tracking-[0.04em] text-[#FBC8C2]">
            <span className="mr-2 text-[10px] uppercase tracking-[0.2em] text-[#FBC8C2]/70">RFI</span>
            {f.fieldError}
          </div>
        )}

        <button
          type="submit"
          disabled={f.submitting}
          className="group relative mt-5 inline-flex h-12 w-full items-center justify-center gap-2 overflow-hidden rounded-md font-display text-[15px] font-medium tracking-[0.01em] text-[#0E2438] transition disabled:opacity-60"
          style={{ background: "linear-gradient(180deg,#E8F1F7,#C8DDE9 60%,#9DC3D8)", boxShadow: "0 14px 36px rgba(157,195,216,0.25)" }}
        >
          {f.submitting ? (
            <><span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-[#0E2438]/30 border-t-[#0E2438]" /> Approving…</>
          ) : (
            <>Open the drawing <ArrowRight size={15} className="transition group-hover:translate-x-0.5" /></>
          )}
        </button>

        <div className="mt-6 border-t border-[#9DC3D8]/15 pt-4">
          <div className="flex items-center justify-between">
            <span className="flex items-center gap-1.5 font-mono text-[10.5px] uppercase tracking-[0.18em] text-[#9DC3D8]/75">
              <ShieldCheck size={12} className="text-[#9DC3D8]" /> JWT-bound · end-to-end
            </span>
            <div className="flex flex-wrap justify-end gap-1">
              {["SOC 2", "ISO 27001", "GDPR"].map((b) => (
                <span key={b} className="rounded border border-[#9DC3D8]/25 bg-[#9DC3D8]/[0.05] px-1.5 py-0.5 font-mono text-[8.5px] uppercase tracking-[0.16em] text-[#9DC3D8]/85">{b}</span>
              ))}
            </div>
          </div>
          <p className="mt-4 text-center font-mono text-[10.5px] uppercase tracking-[0.18em] text-[#9DC3D8]/70">
            New to Bipros? <Link href="/welcome" className="text-[#F4FAFE] underline-offset-4 hover:underline">Request access →</Link>
          </p>
        </div>
      </form>
    </aside>
  );
}

function Field({
  id,
  label,
  note,
  children,
}: {
  id: string;
  label: string;
  note: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label
        htmlFor={id}
        className="mb-1.5 flex items-baseline justify-between font-mono text-[10px] uppercase tracking-[0.2em] text-[#9DC3D8]/80"
      >
        <span>{label}</span>
        <span className="text-[#9DC3D8]/40">{note}</span>
      </label>
      {children}
    </div>
  );
}

function SsoBtn({ label }: { label: string }) {
  return (
    <button
      type="button"
      onClick={() => {}}
      className="inline-flex h-10 items-center justify-center gap-1.5 rounded-md border border-[#9DC3D8]/25 bg-[#9DC3D8]/[0.04] font-mono text-[10.5px] uppercase tracking-[0.18em] text-[#E8F1F7]/90 transition hover:-translate-y-px hover:border-[#9DC3D8]/55 hover:bg-[#9DC3D8]/[0.10]"
    >
      {label}
    </button>
  );
}

/* ---------------------------------------------------------------- */
/*  Footer                                                          */
/* ---------------------------------------------------------------- */

function Footer() {
  return (
    <footer className="relative border-t border-[#9DC3D8]/15 px-6 py-5 font-mono text-[10.5px] uppercase tracking-[0.18em] text-[#9DC3D8]/55 lg:px-14">
      <div className="flex flex-col items-start justify-between gap-2 sm:flex-row sm:items-center">
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-1.5">
            <span className="h-1.5 w-1.5 rounded-full bg-[#7AE2C7]" />
            All systems operational
          </span>
          <span className="text-[#9DC3D8]/35">v0.1.0</span>
        </div>
        <div className="flex flex-wrap gap-x-6 gap-y-1">
          <Link href="#" className="hover:text-[#F4FAFE]">Privacy</Link>
          <Link href="#" className="hover:text-[#F4FAFE]">Terms</Link>
          <Link href="#" className="hover:text-[#F4FAFE]">Status</Link>
          <Link href="#" className="hover:text-[#F4FAFE]">Contact</Link>
        </div>
      </div>
    </footer>
  );
}

/* ---------------------------------------------------------------- */
/*  Keyframes                                                       */
/* ---------------------------------------------------------------- */

function Keyframes() {
  return (
    <style>{`
      @keyframes bpRise { from { opacity:0; transform: translateY(10px); } to { opacity:1; transform:none; } }
      .bp-rise { animation: bpRise .65s cubic-bezier(.22,1,.36,1) both; }
      .bp-d1 { animation-delay: .07s; }
      .bp-d2 { animation-delay: .15s; }
      .bp-d3 { animation-delay: .25s; }
      .bp-d4 { animation-delay: .36s; }
      .bp-d5 { animation-delay: .48s; }
      @media (prefers-reduced-motion: reduce) { .bp-rise { animation: none !important; } }
    `}</style>
  );
}
