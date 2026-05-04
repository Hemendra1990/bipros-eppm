"use client";

import { Suspense } from "react";
import { LoginNav } from "./_components/LoginNav";
import { LoginHero } from "./_components/LoginHero";
import { LoginFeatures } from "./_components/LoginFeatures";
import { LoginFooter } from "./_components/LoginFooter";
import { ScheduleCard } from "./_components/ScheduleCard";

/**
 * Bipros EPPM sign-in. Editorial split-screen: navbar, headline + live mini-Gantt
 * preview on the left, refined login card on the right, four-card features strip
 * below, multi-column footer. Force-light because the marketing rhythm needs the
 * paper canvas regardless of the in-app theme toggle.
 *
 * The auth flow itself (Suspense + useSearchParams + cookie + localStorage prime
 * + /v1/users/me + setAuth + hard nav) is unchanged — owned by LoginHero so the
 * `useSearchParams()` hook stays inside the Suspense boundary that Next.js 16
 * requires for client-side routing primitives.
 */
export default function LoginPage() {
  return (
    <div className="relative min-h-screen overflow-hidden bg-paper text-charcoal font-sans">
      <Ambient />
      <LoginNav />
      <Suspense fallback={<HeroFallback />}>
        <LoginHero />
      </Suspense>
      <LoginPulse />
      <LoginFeatures />
      <LoginFooter />
      <Keyframes />
    </div>
  );
}

function Ambient() {
  return (
    <>
      <div
        aria-hidden
        className="absolute inset-0 -z-10"
        style={{
          background:
            "linear-gradient(180deg,var(--paper) 0%,var(--ivory) 55%,var(--parchment) 100%)",
        }}
      />
      <div
        aria-hidden
        className="absolute inset-x-0 top-0 -z-10 h-[1100px] opacity-[0.05] dark:opacity-[0.08]"
        style={{
          backgroundImage:
            "linear-gradient(var(--charcoal) 1px,transparent 1px),linear-gradient(90deg,var(--charcoal) 1px,transparent 1px)",
          backgroundSize: "56px 56px",
          maskImage:
            "radial-gradient(ellipse 100% 60% at 50% 30%,#000 22%,transparent 78%)",
          WebkitMaskImage:
            "radial-gradient(ellipse 100% 60% at 50% 30%,#000 22%,transparent 78%)",
        }}
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -right-44 -top-48 -z-10 h-[620px] w-[620px] rounded-full"
        style={{
          background:
            "radial-gradient(circle, rgba(212,175,55,0.18) 0%, transparent 65%)",
        }}
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -left-44 top-[30%] -z-10 h-[420px] w-[420px] rounded-full"
        style={{
          background:
            "radial-gradient(circle, rgba(212,175,55,0.10) 0%, transparent 70%)",
        }}
      />
    </>
  );
}

function LoginPulse() {
  return (
    <section className="relative px-4 pb-14 pt-2 sm:px-8">
      <div className="mx-auto max-w-7xl">
        <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
          <div>
            <div className="mb-2 flex items-center gap-2 text-[10.5px] font-semibold uppercase tracking-[0.24em] text-gold-deep">
              <span aria-hidden className="inline-block h-px w-7 bg-gold" />
              Live programme telemetry
            </div>
            <h2
              className="font-display text-[24px] font-semibold leading-[1.05] tracking-[-0.015em] text-charcoal sm:text-[28px]"
              style={{ fontVariationSettings: "'opsz' 144" }}
            >
              The book of work, today.
            </h2>
          </div>
          <p className="max-w-md text-[13.5px] leading-[1.55] text-slate">
            Every Bipros customer sees the same shape — programmes, status, float, and
            critical-path drift on one calm canvas. This is what your team would see at sign-in.
          </p>
        </div>
        <ScheduleCard />
      </div>
    </section>
  );
}

function HeroFallback() {
  return (
    <div className="flex min-h-[60vh] items-center justify-center">
      <div className="text-[13px] text-slate">Loading sign-in…</div>
    </div>
  );
}

function Keyframes() {
  return (
    <style>{`
      @keyframes loginRise {
        from { opacity: 0; transform: translateY(14px); }
        to   { opacity: 1; transform: translateY(0); }
      }
      @keyframes loginFade {
        from { opacity: 0; }
        to   { opacity: 1; }
      }
      @keyframes loginDraw {
        from { stroke-dashoffset: 220; }
        to   { stroke-dashoffset: 0; }
      }
      @keyframes barGrow {
        from { transform: scaleX(0); }
        to   { transform: scaleX(1); }
      }
      .anim-rise { animation: loginRise .7s cubic-bezier(.22,1,.36,1) both; }
      .anim-fade { animation: loginFade .9s ease-out both; }
      .anim-draw {
        stroke-dasharray: 220;
        stroke-dashoffset: 220;
        animation: loginDraw 1.4s cubic-bezier(.22,1,.36,1) .65s forwards;
      }
      .delay-1 { animation-delay: .04s; }
      .delay-2 { animation-delay: .12s; }
      .delay-3 { animation-delay: .20s; }
      .delay-4 { animation-delay: .28s; }
      .delay-5 { animation-delay: .40s; }
      .delay-6 { animation-delay: .50s; }
      .delay-7 { animation-delay: .60s; }
      @media (prefers-reduced-motion: reduce) {
        .anim-rise, .anim-fade, .anim-draw { animation: none !important; }
      }
    `}</style>
  );
}
