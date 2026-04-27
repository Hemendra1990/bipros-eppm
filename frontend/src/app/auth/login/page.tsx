"use client";

import Link from "next/link";
import Image from "next/image";
import { useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";
import { Eye, EyeOff, ArrowUpRight, ShieldCheck } from "lucide-react";
import { authApi } from "@/lib/api/authApi";
import { useAuthStore } from "@/lib/state/store";
import { isAxiosError } from "axios";

/**
 * Bipros EPPM sign-in. Boutique single-column layout: subtle paper canvas (grid texture +
 * one warm gold blob), brand identity stacked above the card, the form itself in a single
 * elevated card sized to its content. No split panels, no orphan whitespace.
 *
 * <p>The auth flow itself (Suspense + useSearchParams + cookie + localStorage prime + /me
 * fetch + setAuth + hard nav) is the same as the previous version — only the surrounding
 * chrome was rebuilt. Functional parity is what {@code 15-security.spec.ts} verifies.
 */
export default function LoginPage() {
  // useSearchParams must live inside a Suspense boundary in Next.js 16 — otherwise the route
  // gets opted into dynamic-only rendering and the form's hydration runs late, which leaves
  // controlled inputs un-bound to React state until well after the user can click the button.
  return (
    <Suspense fallback={<LoginShell />}>
      <LoginForm />
    </Suspense>
  );
}

function LoginForm() {
  const searchParams = useSearchParams();
  const setAuth = useAuthStore((s) => s.setAuth);

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [fieldError, setFieldError] = useState<string | null>(null);

  // Honour ?next=/foo so the login flow returns the user where they were headed.
  const nextRaw = searchParams.get("next");
  // Defence against open-redirect: only allow same-origin paths starting with "/" and not "//".
  const safeNext =
    nextRaw && nextRaw.startsWith("/") && !nextRaw.startsWith("//") ? nextRaw : "/";

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
      // Persist the access token to a cookie so the middleware sees it on the next request.
      // useAuthStore.setAuth already handles localStorage; the cookie is what middleware reads.
      document.cookie = `access_token=${accessToken}; path=/; max-age=3600; SameSite=Strict`;

      // Prime localStorage so the axios request interceptor attaches the Bearer header
      // on the upcoming /v1/users/me call (the interceptor reads localStorage, not the cookie).
      localStorage.setItem("access_token", accessToken);
      localStorage.setItem("refresh_token", refreshToken);

      // We need the user object for downstream pages (Sidebar, useAccess). Fetch /v1/users/me
      // so the persisted store carries the canonical UserResponse shape.
      const meRes = await authApi.me();
      const user = meRes.data;
      if (!user) {
        throw new Error("Failed to load current user");
      }
      setAuth(user, accessToken, refreshToken);

      // Hard navigation: a soft router.push() doesn't replay middleware (which is what
      // sees the freshly-set access_token cookie); a client-side soft nav would render the
      // dashboard with no auth header on the very first React tree, causing the global
      // axios interceptor to bounce back to /auth/login on the first 401. A full reload
      // forces middleware + a clean React mount with the cookie already present.
      window.location.href = safeNext;
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 401) {
        setFieldError("Invalid username or password.");
      } else if (isAxiosError(err) && err.response && err.response.status >= 500) {
        setFieldError("Sign-in service is unavailable. Please try again in a moment.");
      } else {
        setFieldError("Could not sign you in. Please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="force-light relative min-h-screen overflow-hidden bg-paper text-charcoal">
      {/* ── Ambient background: subtle warm gradient, very faint grid, two muted gold blobs ── */}
      <div
        aria-hidden
        className="absolute inset-0"
        style={{ background: "linear-gradient(180deg,#FFFFFF 0%,#FAF9F6 60%,#F5F2E8 100%)" }}
      />
      <div
        aria-hidden
        className="absolute inset-0 opacity-[0.045]"
        style={{
          backgroundImage:
            "linear-gradient(#1C1C1C 1px,transparent 1px),linear-gradient(90deg,#1C1C1C 1px,transparent 1px)",
          backgroundSize: "48px 48px",
          maskImage:
            "radial-gradient(ellipse 90% 70% at 50% 35%,#000 25%,transparent 75%)",
          WebkitMaskImage:
            "radial-gradient(ellipse 90% 70% at 50% 35%,#000 25%,transparent 75%)",
        }}
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -top-44 -right-32 h-[480px] w-[480px] rounded-full"
        style={{
          background: "radial-gradient(circle, rgba(212,175,55,0.18) 0%, transparent 65%)",
        }}
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -bottom-44 -left-28 h-[380px] w-[380px] rounded-full"
        style={{
          background: "radial-gradient(circle, rgba(212,175,55,0.10) 0%, transparent 70%)",
        }}
      />

      {/* ── Centered content stack ────────────────────────────────────────────── */}
      <div className="relative flex min-h-screen flex-col">
        <main className="flex flex-1 items-center justify-center px-6 py-10 sm:py-14">
          <div className="w-full max-w-[440px]">
            {/* Brand mark — sits just above the card, centered */}
            <div className="mb-7 flex flex-col items-center gap-3">
              <Image
                src="/bipros-logo.png"
                alt="Bipros"
                width={56}
                height={56}
                className="h-14 w-14 rounded-2xl object-contain shadow-[0_2px_8px_rgba(28,28,28,0.06)]"
                priority
              />
              <div className="flex items-baseline gap-2.5">
                <span className="font-display text-2xl font-semibold tracking-tight text-charcoal">
                  Bipros
                </span>
                <span className="text-[10px] font-semibold uppercase tracking-[0.24em] text-gold-deep">
                  EPPM
                </span>
              </div>
            </div>

            {/* The card */}
            <div className="rounded-2xl border border-hairline/80 bg-paper/90 p-7 shadow-[0_12px_40px_rgba(28,28,28,0.08)] backdrop-blur sm:p-8">
              <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.22em] text-gold-deep">
                <span aria-hidden className="inline-block h-px w-6 bg-gold" />
                Sign in
              </div>
              <h1
                className="mt-2 font-display text-[28px] font-semibold leading-[1.1] tracking-[-0.015em] text-charcoal sm:text-[32px]"
                style={{ fontVariationSettings: "'opsz' 144" }}
              >
                Welcome back.
              </h1>
              <p className="mt-2 text-[14px] leading-[1.55] text-slate">
                Use your work account to continue.
              </p>

              <form onSubmit={handleSubmit} className="mt-6 space-y-4" noValidate>
                <div>
                  <label
                    htmlFor="username"
                    className="mb-1.5 block text-[10.5px] font-semibold uppercase tracking-[0.16em] text-slate"
                  >
                    Username
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
                    placeholder="you@company"
                    className="block w-full rounded-xl border border-hairline bg-ivory px-3.5 py-2.5 text-[15px] text-charcoal placeholder-ash transition-colors focus:border-gold-deep focus:bg-paper focus:outline-none focus:ring-4 focus:ring-gold-deep/15 disabled:opacity-60"
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
                      onClick={() =>
                        alert(
                          "Password reset isn't wired in yet — ask your administrator to issue a new password.",
                        )
                      }
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
                      className="block w-full rounded-xl border border-hairline bg-ivory px-3.5 py-2.5 pr-11 text-[15px] text-charcoal placeholder-ash transition-colors focus:border-gold-deep focus:bg-paper focus:outline-none focus:ring-4 focus:ring-gold-deep/15 disabled:opacity-60"
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
                      {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                    </button>
                  </div>
                </div>

                {fieldError && (
                  <div
                    role="alert"
                    className="flex items-start gap-2 rounded-xl border border-burgundy/25 bg-burgundy/[0.04] px-3.5 py-2.5 text-[13px] text-burgundy"
                  >
                    <span
                      aria-hidden
                      className="mt-1.5 inline-block h-1.5 w-1.5 rounded-full bg-burgundy"
                    />
                    <span>{fieldError}</span>
                  </div>
                )}

                <button
                  type="submit"
                  disabled={submitting}
                  className="group relative mt-1 inline-flex h-11 w-full items-center justify-center gap-2 overflow-hidden rounded-xl bg-charcoal px-4 text-[14.5px] font-semibold text-paper shadow-[0_8px_22px_rgba(28,28,28,0.18)] transition-all duration-200 hover:bg-[#0F0F0F] hover:-translate-y-0.5 hover:shadow-[0_12px_28px_rgba(28,28,28,0.26)] disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:translate-y-0"
                >
                  <span
                    aria-hidden
                    className="absolute inset-x-0 -top-px h-px bg-gradient-to-r from-transparent via-gold/60 to-transparent opacity-70"
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
              </form>

              {/* Trust note inside the card, kept short and quiet */}
              <div className="mt-6 flex items-center gap-2 border-t border-hairline pt-4 text-[12px] text-slate">
                <ShieldCheck size={13} className="shrink-0 text-gold-deep" />
                <span>JWT-bound sessions · authenticated end-to-end.</span>
              </div>
            </div>

            {/* Tour link — outside the card, quiet */}
            <p className="mt-5 text-center text-[13px] text-slate">
              New to Bipros EPPM?{" "}
              <Link
                href="/welcome"
                className="font-semibold text-gold-deep transition hover:text-gold-ink hover:underline"
              >
                Take the tour →
              </Link>
            </p>
          </div>
        </main>

        {/* Page-level footer — pinned to the bottom of the viewport, not inside the card */}
        <footer className="relative px-6 pb-6 text-center text-[11px] text-ash sm:flex sm:items-center sm:justify-between sm:px-10">
          <span className="font-mono tracking-[0.08em]">v0.1.0 · production</span>
          <span className="mt-1 block sm:mt-0">© Bipros · all rights reserved</span>
        </footer>
      </div>
    </div>
  );
}

/** Tiny SSR fallback shown while the Suspense boundary resolves. Keeps the login chrome
 *  visible so the page never flashes blank. */
function LoginShell() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-paper px-6 py-16">
      <div className="w-full max-w-md text-center text-slate">Loading sign-in…</div>
    </div>
  );
}
