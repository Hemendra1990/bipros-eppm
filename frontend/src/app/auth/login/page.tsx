"use client";

import Link from "next/link";
import Image from "next/image";
import { useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";
import { authApi } from "@/lib/api/authApi";
import { useAuthStore } from "@/lib/state/store";
import { isAxiosError } from "axios";

/**
 * Real login form. Posts username/password to {@code /v1/auth/login}, persists tokens via
 * {@link useAuthStore.setAuth}, and bounces the user to {@code ?next=} (or the dashboard).
 *
 * <p>The previous landing page that lived here is now under {@code /welcome}.
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
    <div className="flex min-h-screen items-center justify-center bg-paper px-6 py-16">
      <div className="w-full max-w-md">
        <div className="mb-8 flex items-center justify-center gap-3">
          <Image
            src="/bipros-logo.png"
            alt="Bipros"
            width={48}
            height={48}
            className="h-12 w-12 rounded-xl object-contain"
            priority
          />
          <div className="flex flex-col leading-none">
            <span className="font-display text-2xl font-semibold tracking-tight text-charcoal">
              Bipros
            </span>
            <span className="mt-0.5 text-[10px] font-semibold uppercase tracking-[0.2em] text-gold-deep">
              EPPM
            </span>
          </div>
        </div>

        <div className="rounded-2xl border border-hairline bg-paper p-8 shadow-sm">
          <h1 className="text-xl font-semibold text-charcoal">Sign in</h1>
          <p className="mt-1 text-sm text-slate">
            Enter your username and password to continue.
          </p>

          <form onSubmit={handleSubmit} className="mt-6 space-y-4" noValidate>
            <div>
              <label htmlFor="username" className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate">
                Username
              </label>
              <input
                id="username"
                name="username"
                type="text"
                autoComplete="username"
                required
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                disabled={submitting}
                className="w-full rounded-lg border border-hairline bg-ivory px-3 py-2 text-sm text-charcoal placeholder-ash focus:border-gold-deep focus:outline-none focus:ring-2 focus:ring-gold-deep/30 disabled:opacity-60"
              />
            </div>

            <div>
              <label htmlFor="password" className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate">
                Password
              </label>
              <input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={submitting}
                className="w-full rounded-lg border border-hairline bg-ivory px-3 py-2 text-sm text-charcoal placeholder-ash focus:border-gold-deep focus:outline-none focus:ring-2 focus:ring-gold-deep/30 disabled:opacity-60"
              />
            </div>

            {fieldError && (
              <div
                role="alert"
                className="rounded-lg border border-burgundy/30 bg-burgundy/5 px-3 py-2 text-sm text-burgundy"
              >
                {fieldError}
              </div>
            )}

            <button
              type="submit"
              disabled={submitting}
              className="w-full rounded-lg bg-charcoal px-4 py-2.5 text-sm font-semibold text-paper transition hover:bg-slate disabled:opacity-60"
            >
              {submitting ? "Signing in…" : "Sign in"}
            </button>
          </form>

          <p className="mt-6 text-center text-xs text-slate">
            New to Bipros EPPM?{" "}
            <Link href="/welcome" className="font-semibold text-gold-deep hover:underline">
              Learn more
            </Link>
          </p>
        </div>
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
