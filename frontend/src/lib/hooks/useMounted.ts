"use client";

import { useEffect, useState } from "react";

/**
 * True only after the component has mounted on the client.
 *
 * Use to gate UI derived from client-only state (e.g. `useAuthStore` permissions,
 * which are empty during SSR but populated from localStorage on the client) so the
 * first client render matches the server-rendered HTML — otherwise React reports a
 * hydration mismatch (QC round, 2026-08-19).
 */
export function useMounted(): boolean {
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  return mounted;
}
