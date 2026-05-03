"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import {
  ArrowUpRight,
  Eye,
  EyeOff,
  Lock,
  ShieldCheck,
} from "lucide-react";
import toast from "react-hot-toast";
import { isAxiosError } from "axios";
import { authApi } from "@/lib/api/authApi";
import { useAuthStore } from "@/lib/state/store";

const TRUSTED = ["Network Rail", "Ørsted", "Bechtel", "AECOM", "Skanska"];

const LIVE_STATS: Array<{
  label: string;
  value: string;
  delta: string;
  trend: "up" | "down" | "flat";
}> = [
  { label: "Active programmes",  value: "12",     delta: "+2 QoQ",     trend: "up" },
  { label: "At-risk programmes", value: "2",      delta: "−1 vs L4W",  trend: "up" },
  { label: "Schedule adherence", value: "94.2%",  delta: "+2.1 pts",   trend: "up" },
  { label: "Activities tracked", value: "184K",   delta: "+9K MoM",    trend: "up" },
];

export function LoginHero() {
  const searchParams = useSearchParams();
  const setAuth = useAuthStore((s) => s.setAuth);

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [remember, setRemember] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [fieldError, setFieldError] = useState<string | null>(null);

  const nextRaw = searchParams.get("next");
  const safeNext =
    nextRaw && nextRaw.startsWith("/") && !nextRaw.startsWith("//")
      ? nextRaw
      : "/";

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setFieldError(null);
    setSubmitting(true);
    try {
      const loginRes = await authApi.login({ username, password });
      const accessToken = loginRes.data?.accessToken;
      const refreshToken = loginRes.data?.refreshToken;
      if (!accessToken || !refreshToken) {
        throw new Error("Login response missing tokens");
      }
      document.cookie = `access_token=${accessToken}; path=/; max-age=3600; SameSite=Strict`;
      localStorage.setItem("access_token", accessToken);
      localStorage.setItem("refresh_token", refreshToken);

      const meRes = await authApi.me();
      const user = meRes.data;
      if (!user) throw new Error("Failed to load current user");
      setAuth(user, accessToken, refreshToken);

      window.location.href = safeNext;
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 401) {
        setFieldError("Invalid username or password.");
      } else if (
        isAxiosError(err) &&
        err.response &&
        err.response.status >= 500
      ) {
        setFieldError(
          "Sign-in service is unavailable. Please try again in a moment.",
        );
      } else {
        setFieldError("Could not sign you in. Please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  const ssoUnavailable = () =>
    toast("SSO sign-in is not yet configured.", { icon: "🔒" });
  const forgotUnavailable = () =>
    toast(
      "Password reset isn't wired in yet — ask your administrator to issue a new password.",
      { icon: "✉️" },
    );

  return (
    <section className="relative px-4 pb-10 pt-6 sm:px-8 sm:pt-8">
      <div className="mx-auto flex max-w-[1440px] flex-col gap-y-10 lg:flex-row lg:items-start lg:justify-between lg:gap-x-10">
        {/* ── LEFT ─ headline + body + CTAs (mobile order: 2) ─────────────── */}
        <div className="order-2 w-full lg:order-1 lg:max-w-[600px] lg:flex-1">
          <div>
            <div className="anim-rise delay-1 mb-4 flex flex-wrap items-center gap-3 text-[10.5px] font-semibold uppercase tracking-[0.24em] text-gold-deep">
              <span className="flex items-center gap-2">
                <span aria-hidden className="inline-block h-px w-7 bg-gold" />
                Enterprise project console
              </span>
              <span className="inline-flex items-center gap-1.5 rounded-full border border-gold/30 bg-gold-tint/40 px-2 py-0.5 font-mono text-[9px] tracking-[0.14em] text-gold-ink">
                <span
                  aria-hidden
                  className="h-1.5 w-1.5 animate-pulse rounded-full bg-emerald"
                />
                Live · v0.1
              </span>
            </div>

            <h1
              className="font-display font-semibold leading-[0.94] tracking-[-0.025em] text-charcoal"
              style={{
                fontVariationSettings: "'opsz' 144",
                fontSize: "clamp(44px, 5.6vw, 68px)",
              }}
            >
              <span className="anim-rise delay-2 block">
                Plan<span className="text-gold">.</span>
              </span>
              <span className="anim-rise delay-3 block">
                Execute<span className="text-gold">.</span>
              </span>
              <span className="anim-rise delay-4 relative block">
                <em className="font-medium italic text-gold-deep">Deliver</em>
                <span className="text-gold">.</span>
                <svg
                  aria-hidden
                  className="absolute -bottom-3 left-0 h-3"
                  style={{ width: "min(78%, 360px)" }}
                  viewBox="0 0 220 12"
                  preserveAspectRatio="none"
                  fill="none"
                >
                  <path
                    d="M2 8 Q 55 2, 110 6 T 218 4"
                    stroke="#D4AF37"
                    strokeWidth="2.4"
                    strokeLinecap="round"
                    fill="none"
                    className="anim-draw"
                  />
                </svg>
              </span>
            </h1>

            <p className="anim-rise delay-5 mt-6 text-[15.5px] leading-[1.6] text-slate">
              Enterprise project control, simplified. Schedule, cost, resources,
              and risk converge on one calm spine — built for programme leaders
              running dozens of projects as a single, disciplined book of work.
            </p>

            <div className="anim-rise delay-6 mt-6 flex flex-wrap items-center gap-3">
              <button
                type="button"
                onClick={() =>
                  toast("Demo requests open Q1 — check back soon.", {
                    icon: "✨",
                  })
                }
                className="group inline-flex h-12 items-center gap-2 rounded-xl bg-gold px-5 text-[14px] font-semibold text-paper shadow-[0_4px_16px_rgba(212,175,55,0.30)] transition-all duration-200 hover:-translate-y-0.5 hover:bg-gold-deep hover:shadow-[0_12px_30px_rgba(212,175,55,0.42)]"
              >
                Request a demo
                <ArrowUpRight
                  size={16}
                  className="transition-transform duration-200 group-hover:translate-x-0.5 group-hover:-translate-y-0.5"
                />
              </button>
              <Link
                href="/welcome"
                className="inline-flex h-12 items-center gap-2 rounded-xl border border-charcoal/15 bg-paper/70 px-5 text-[14px] font-semibold text-charcoal backdrop-blur transition-all duration-200 hover:-translate-y-0.5 hover:border-charcoal/30 hover:bg-paper"
              >
                Watch the product tour
              </Link>
            </div>

            <div className="anim-fade delay-7 mt-7 flex flex-wrap items-center gap-x-6 gap-y-2">
              <span className="font-mono text-[10px] uppercase tracking-[0.20em] text-ash">
                Trusted by
              </span>
              {TRUSTED.map((c) => (
                <span
                  key={c}
                  className="font-display text-[14px] font-semibold tracking-[0.02em] text-slate/80 transition-colors hover:text-charcoal"
                  style={{ fontVariationSettings: "'opsz' 24" }}
                >
                  {c}
                </span>
              ))}
            </div>

            <figure className="anim-fade delay-7 mt-8 border-l-2 border-gold/40 pl-5">
              <blockquote
                className="font-display text-[17px] italic leading-[1.45] text-charcoal/90"
                style={{ fontVariationSettings: "'opsz' 24" }}
              >
                “Schedule, cost, and risk on one calm spine. We stopped reconciling
                because the numbers stopped diverging.”
              </blockquote>
              <figcaption className="mt-3 flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.18em] text-slate">
                <span aria-hidden className="inline-block h-px w-5 bg-gold-deep" />
                Programme Director · Tier-1 European EPC
              </figcaption>
            </figure>
          </div>
        </div>

        {/* ── MIDDLE ─ Live programme pulse sidebar (desktop only) ────────── */}
        <aside className="anim-rise delay-3 order-3 hidden lg:order-2 lg:block lg:w-[220px] lg:flex-shrink-0">
          <div className="relative overflow-hidden rounded-2xl border border-hairline bg-paper/55 p-4 shadow-[0_12px_40px_rgba(28,28,28,0.06)] backdrop-blur-sm">
            <div
              aria-hidden
              className="absolute inset-x-5 top-0 h-px"
              style={{
                background:
                  "linear-gradient(90deg,transparent,#D4AF37,transparent)",
              }}
            />

            <div className="flex items-center justify-between border-b border-hairline pb-3">
              <div className="flex items-center gap-1.5 font-mono text-[9.5px] font-semibold uppercase tracking-[0.16em] text-emerald">
                <span
                  aria-hidden
                  className="inline-block h-1.5 w-1.5 animate-pulse rounded-full bg-emerald"
                />
                Live now
              </div>
              <span className="font-mono text-[8.5px] uppercase tracking-[0.14em] text-ash">
                14s ago
              </span>
            </div>

            <ul className="divide-y divide-hairline/70">
              {LIVE_STATS.map((s) => (
                <li key={s.label} className="py-3.5 first:pt-3.5 last:pb-1">
                  <div className="font-mono text-[9px] font-semibold uppercase tracking-[0.14em] text-ash">
                    {s.label}
                  </div>
                  <div
                    className="mt-1 font-display text-[26px] font-semibold leading-none tracking-[-0.015em] text-charcoal"
                    style={{ fontVariationSettings: "'opsz' 144" }}
                  >
                    {s.value}
                  </div>
                  <div
                    className={`mt-1.5 flex items-center gap-1 text-[10.5px] font-medium ${
                      s.trend === "up"
                        ? "text-emerald"
                        : s.trend === "down"
                          ? "text-burgundy"
                          : "text-slate"
                    }`}
                  >
                    {s.trend === "up" && <span aria-hidden>↑</span>}
                    {s.trend === "down" && <span aria-hidden>↓</span>}
                    {s.delta}
                  </div>
                </li>
              ))}
            </ul>

            <div className="mt-3 border-t border-hairline pt-3 font-mono text-[9.5px] uppercase tracking-[0.14em] text-slate">
              Across <span className="text-charcoal">400+</span> delivery teams
            </div>
          </div>

          <div className="mt-4 rounded-xl border border-hairline bg-ivory/40 px-3.5 py-3">
            <div className="flex items-center gap-1.5 font-mono text-[9px] font-semibold uppercase tracking-[0.16em] text-gold-deep">
              <ShieldCheck size={11} />
              Protected
            </div>
            <p className="mt-1.5 text-[11.5px] leading-[1.4] text-slate">
              SSO, MFA, and role-based access enforced on every session — your
              programme data stays in your tenant.
            </p>
          </div>
        </aside>

        {/* ── RIGHT ─ Login card (mobile order: 1) ─────────────────────────── */}
        <div className="order-1 w-full lg:order-3 lg:w-[420px] lg:flex-shrink-0">
          <form
            onSubmit={handleSubmit}
            noValidate
            className="anim-rise delay-2 relative overflow-hidden rounded-2xl border border-hairline bg-paper/95 p-5 shadow-[0_22px_60px_rgba(28,28,28,0.10)] backdrop-blur sm:p-6"
          >
            <div
              aria-hidden
              className="absolute inset-x-7 top-0 h-px"
              style={{
                background:
                  "linear-gradient(90deg,transparent,#D4AF37,transparent)",
              }}
            />

            <div className="flex items-start justify-between">
              <div>
                <div className="flex items-center gap-2 text-[10.5px] font-semibold uppercase tracking-[0.22em] text-gold-deep">
                  <span aria-hidden className="inline-block h-px w-5 bg-gold" />
                  Sign in
                </div>
                <h2
                  className="mt-2 font-display text-[28px] font-semibold leading-[1.05] tracking-[-0.015em] text-charcoal"
                  style={{ fontVariationSettings: "'opsz' 144" }}
                >
                  Welcome back.
                </h2>
                <p className="mt-1 text-[13.5px] text-slate">
                  Use your work account to continue.
                </p>
              </div>
              <span className="flex shrink-0 items-center gap-1 rounded border border-hairline bg-ivory px-1.5 py-1 font-mono text-[9px] font-semibold uppercase tracking-[0.18em] text-slate">
                <Lock size={10} />
                TLS
              </span>
            </div>

            <div className="mt-5 grid grid-cols-3 gap-2">
              <SsoButton
                onClick={ssoUnavailable}
                label="Google"
                logo={<GoogleMark />}
              />
              <SsoButton
                onClick={ssoUnavailable}
                label="Microsoft"
                logo={<MicrosoftMark />}
              />
              <SsoButton
                onClick={ssoUnavailable}
                label="SAML"
                logo={<KeyMark />}
              />
            </div>

            <div className="my-4 flex items-center gap-3 text-[10px] font-semibold uppercase tracking-[0.16em] text-ash">
              <div className="h-px flex-1 bg-hairline" />
              or with email
              <div className="h-px flex-1 bg-hairline" />
            </div>

            <div className="space-y-3.5">
              <div>
                <label
                  htmlFor="username"
                  className="mb-1.5 block text-[10.5px] font-semibold uppercase tracking-[0.16em] text-slate"
                >
                  Username or email
                </label>
                <input
                  id="username"
                  name="username"
                  type="text"
                  autoComplete="username"
                  autoFocus
                  required
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  disabled={submitting}
                  placeholder="you@company.com"
                  className="block h-11 w-full rounded-xl border border-divider bg-ivory px-3.5 text-[14.5px] text-charcoal placeholder-ash transition-all duration-150 hover:border-gold-deep/50 focus:border-gold-deep focus:bg-paper focus:outline-none focus:ring-4 focus:ring-gold-deep/15 disabled:opacity-60"
                />
              </div>

              <div>
                <div className="mb-1.5 flex items-baseline justify-between">
                  <label
                    htmlFor="password"
                    className="text-[10.5px] font-semibold uppercase tracking-[0.16em] text-slate"
                  >
                    Password
                  </label>
                  <button
                    type="button"
                    onClick={forgotUnavailable}
                    className="text-[11px] font-semibold text-gold-deep transition hover:text-gold-ink hover:underline"
                  >
                    Forgot password?
                  </button>
                </div>
                <div className="relative">
                  <input
                    id="password"
                    name="password"
                    type={showPassword ? "text" : "password"}
                    autoComplete="current-password"
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    disabled={submitting}
                    placeholder="••••••••"
                    className="block h-11 w-full rounded-xl border border-divider bg-ivory px-3.5 pr-11 text-[14.5px] text-charcoal placeholder-ash transition-all duration-150 hover:border-gold-deep/50 focus:border-gold-deep focus:bg-paper focus:outline-none focus:ring-4 focus:ring-gold-deep/15 disabled:opacity-60"
                  />
                  <button
                    type="button"
                    aria-label={showPassword ? "Hide password" : "Show password"}
                    aria-pressed={showPassword}
                    onClick={() => setShowPassword((v) => !v)}
                    disabled={submitting}
                    className="absolute inset-y-0 right-0 flex items-center px-3 text-slate transition hover:text-gold-deep disabled:opacity-60"
                    tabIndex={-1}
                  >
                    {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
                  </button>
                </div>
              </div>
            </div>

            <label className="mt-3.5 flex cursor-pointer items-center gap-2 text-[12.5px] text-slate">
              <input
                type="checkbox"
                checked={remember}
                onChange={(e) => setRemember(e.target.checked)}
                className="h-3.5 w-3.5 rounded border-hairline accent-[#B8962E]"
              />
              Keep me signed in for 7 days
            </label>

            {fieldError && (
              <div
                role="alert"
                className="mt-4 flex items-start gap-2 rounded-xl border border-burgundy/25 bg-burgundy/[0.04] px-3.5 py-2.5 text-[13px] text-burgundy"
              >
                <span
                  aria-hidden
                  className="mt-1.5 inline-block h-1.5 w-1.5 shrink-0 rounded-full bg-burgundy"
                />
                <span>{fieldError}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={submitting}
              className="group relative mt-5 inline-flex h-12 w-full items-center justify-center gap-2 overflow-hidden rounded-xl bg-charcoal px-4 text-[14.5px] font-semibold text-paper shadow-[0_10px_28px_rgba(28,28,28,0.22)] transition-all duration-200 hover:-translate-y-0.5 hover:opacity-90 hover:shadow-[0_16px_36px_rgba(28,28,28,0.30)] disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:translate-y-0 dark:shadow-[0_10px_28px_rgba(0,0,0,0.55)] dark:hover:shadow-[0_16px_36px_rgba(0,0,0,0.65)]"
            >
              <span
                aria-hidden
                className="absolute inset-x-0 -top-px h-px"
                style={{
                  background:
                    "linear-gradient(90deg,transparent,rgba(212,175,55,0.6),transparent)",
                }}
              />
              {submitting ? (
                <>
                  <span className="h-4 w-4 animate-spin rounded-full border-2 border-paper/30 border-t-paper" />
                  Signing in…
                </>
              ) : (
                <>
                  Sign in
                  <ArrowUpRight
                    size={15}
                    className="transition-transform duration-200 group-hover:translate-x-0.5 group-hover:-translate-y-0.5"
                  />
                </>
              )}
            </button>

            <div className="mt-5 flex items-center justify-between gap-2 border-t border-hairline pt-4">
              <span className="flex items-center gap-1.5 text-[11.5px] text-slate">
                <ShieldCheck size={12} className="text-gold-deep" />
                JWT-bound · end-to-end
              </span>
              <div className="flex flex-wrap justify-end gap-1">
                {["SOC 2", "ISO 27001", "GDPR"].map((t) => (
                  <span
                    key={t}
                    className="rounded border border-hairline bg-ivory px-1.5 py-0.5 font-mono text-[8.5px] uppercase tracking-[0.14em] text-slate"
                  >
                    {t}
                  </span>
                ))}
              </div>
            </div>

            <p className="mt-4 text-center text-[12.5px] text-slate">
              New to Bipros EPPM?{" "}
              <Link
                href="/welcome"
                className="font-semibold text-gold-deep transition hover:text-gold-ink hover:underline"
              >
                Take the tour →
              </Link>
            </p>
          </form>
        </div>
      </div>
    </section>
  );
}

function SsoButton({
  label,
  logo,
  onClick,
}: {
  label: string;
  logo: React.ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="group relative inline-flex h-10 items-center justify-center gap-1.5 overflow-hidden rounded-[10px] border border-hairline bg-paper text-[11.5px] font-medium text-charcoal transition-all hover:-translate-y-px hover:border-gold-deep/50 hover:bg-ivory"
    >
      <span className="relative inline-flex items-center justify-center">
        {logo}
      </span>
      {label}
    </button>
  );
}

function GoogleMark() {
  return (
    <svg width="14" height="14" viewBox="0 0 18 18" aria-hidden>
      <path
        fill="#4285F4"
        d="M17.64 9.2c0-.64-.06-1.25-.17-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.71v2.26h2.92c1.7-1.57 2.68-3.88 2.68-6.61z"
      />
      <path
        fill="#34A853"
        d="M9 18c2.43 0 4.47-.81 5.96-2.18l-2.92-2.27c-.81.54-1.84.86-3.04.86-2.34 0-4.32-1.58-5.03-3.7H.96v2.32A9 9 0 0 0 9 18z"
      />
      <path
        fill="#FBBC05"
        d="M3.97 10.71a5.42 5.42 0 0 1 0-3.43V4.96H.96a9 9 0 0 0 0 8.08l3.01-2.33z"
      />
      <path
        fill="#EA4335"
        d="M9 3.58c1.32 0 2.5.45 3.44 1.34l2.58-2.58A9 9 0 0 0 .96 4.96l3.01 2.32C4.68 5.16 6.66 3.58 9 3.58z"
      />
    </svg>
  );
}

function MicrosoftMark() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" aria-hidden>
      <path fill="#F25022" d="M0 0h11.4v11.4H0z" />
      <path fill="#7FBA00" d="M12.6 0H24v11.4H12.6z" />
      <path fill="#00A4EF" d="M0 12.6h11.4V24H0z" />
      <path fill="#FFB900" d="M12.6 12.6H24V24H12.6z" />
    </svg>
  );
}

function KeyMark() {
  return (
    <svg
      width="13"
      height="13"
      viewBox="0 0 24 24"
      fill="none"
      stroke="#B8962E"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      <circle cx="7.5" cy="15.5" r="3.5" />
      <path d="M21 2l-9.6 9.6" />
      <path d="M15.5 7.5l3 3" />
    </svg>
  );
}
