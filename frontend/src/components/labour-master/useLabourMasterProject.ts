"use client";

import { useEffect } from "react";
import { useRouter, useSearchParams, usePathname } from "next/navigation";
import { useAppStore } from "@/lib/state/store";

/**
 * Single source of truth for the labour-master active projectId.
 *
 * Behaviour:
 *  - If the URL has `?projectId=`, that wins. We also push it into the
 *    Zustand store so other tabs can pick it up after navigation.
 *  - If the URL is blank but the store has a previously-selected project,
 *    we silently rewrite the URL to include `?projectId=…` (replace, no new
 *    history entry). This makes the URL canonical and shareable while still
 *    "remembering" the user's choice across tabs and refreshes.
 *  - {@link setProjectId} writes both places at once.
 */
export function useLabourMasterProject(): {
  projectId: string | undefined;
  setProjectId: (id: string | null) => void;
} {
  const search = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const urlId = search?.get("projectId") || undefined;
  const storedId = useAppStore((s) => s.currentProjectId) || undefined;
  const setStored = useAppStore((s) => s.setCurrentProjectId);

  // URL → store: keep store fresh when URL changes (deep links, history, refresh).
  useEffect(() => {
    if (urlId && urlId !== storedId) setStored(urlId);
  }, [urlId, storedId, setStored]);

  // Store → URL: if URL is blank but store remembers the last project,
  // backfill the URL so tab links carry it.
  useEffect(() => {
    if (!urlId && storedId && pathname) {
      router.replace(`${pathname}?projectId=${storedId}`);
    }
    // We only want to react to URL/pathname changes; the store value is read-once here.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [urlId, pathname]);

  const setProjectId = (id: string | null) => {
    setStored(id);
    if (pathname) {
      const target = id ? `${pathname}?projectId=${id}` : pathname;
      router.replace(target);
    }
  };

  return {
    projectId: urlId ?? storedId,
    setProjectId,
  };
}
