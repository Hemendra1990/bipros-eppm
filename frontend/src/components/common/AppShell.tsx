"use client";

import { useEffect } from "react";
import { Header } from "./Header";
import { authApi } from "@/lib/api/authApi";
import { useAuthStore } from "@/lib/state/store";

export function AppShell({ children }: { children: React.ReactNode }) {
  // Access-control round (2026-08-11): permissions/profile/data-scope are enforced server-side
  // per request, but the UI's copy comes from /auth/me cached at LOGIN. Re-fetch it once per
  // full page load so an admin's profile edit reaches open sessions on the next refresh
  // instead of the next re-login. Fire-and-forget; failures keep the cached copy.
  const setUser = useAuthStore((s) => s.setUser);
  const isAuthenticated = useAuthStore((s) => s.accessToken !== null);
  useEffect(() => {
    if (!isAuthenticated) return;
    authApi
      .me()
      .then((res) => {
        if (res?.data) setUser(res.data);
      })
      .catch(() => {
        /* token expired or offline — the axios interceptor handles auth errors */
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  // The root layout pins html/body to `h-full` so older sidebar+inner-scroll
  // shells could host an independent scroll container. We use natural browser
  // scroll everywhere now, so release the constraint on mount. Cleanup restores
  // it — defensive in case a future shell ever swaps back at runtime.
  useEffect(() => {
    const html = document.documentElement;
    const body = document.body;
    const hadHtml = html.classList.contains("h-full");
    const hadBody = body.classList.contains("h-full");
    html.classList.remove("h-full");
    body.classList.remove("h-full");
    return () => {
      if (hadHtml) html.classList.add("h-full");
      if (hadBody) body.classList.add("h-full");
    };
  }, []);

  return (
    <div className="min-h-screen bg-ivory">
      <div className="sticky top-0 z-30">
        <Header />
      </div>
      <main className="bg-ivory">
        <div className="px-4 py-6 sm:px-6 lg:px-8">{children}</div>
      </main>
    </div>
  );
}
