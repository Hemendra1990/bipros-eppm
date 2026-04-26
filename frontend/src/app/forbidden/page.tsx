"use client";

import Link from "next/link";

/**
 * 403 page — shown when the backend rejects a request with HTTP 403 (axios interceptor
 * routes here) or when the middleware blocks an admin-area visit by a non-admin user.
 */
export default function ForbiddenPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-ivory px-6 py-16">
      <div className="max-w-md text-center">
        <p className="text-xs font-semibold uppercase tracking-[0.2em] text-amber-600">
          403 — Forbidden
        </p>
        <h1 className="mt-3 text-2xl font-semibold text-slate-900">
          You don&apos;t have access to this page
        </h1>
        <p className="mt-3 text-sm leading-relaxed text-slate-600">
          Your account is missing the role or permission required to view this content.
          If you believe this is a mistake, ask your project administrator to check your
          access matrix in <span className="font-medium">Admin → User Access</span>.
        </p>
        <div className="mt-8 flex justify-center gap-3">
          <Link
            href="/"
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-700"
          >
            Back to dashboard
          </Link>
          <Link
            href="/auth/login"
            className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-white"
          >
            Sign in as a different user
          </Link>
        </div>
      </div>
    </div>
  );
}
