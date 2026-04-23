"use client";

import { useEffect, useRef, useState } from "react";
import { gisApi } from "@/lib/api/gisApi";

type Cache = Map<string, { url: string; lastUsed: number }>;

/**
 * Fetches a satellite-image raster as a blob URL suitable for
 * {@link https://openlayers.org/ ol/source/ImageStatic}. The URL is cached
 * across scene changes so stepping prev/next doesn't cause a blank flash
 * while the blob is re-fetched. LRU-bounded to {@code cacheSize} (default 3)
 * entries to avoid leaking across the typical prev/current/next window.
 *
 * {@link URL.revokeObjectURL} is called on eviction and on hook unmount, so
 * the Blob → URL pair never outlives the component tree.
 *
 * Authentication + token refresh flow through the same apiClient axios
 * interceptor that powers the rest of the app — so this hook doesn't need to
 * know anything about JWTs.
 */
export function useSceneBlobUrl(
  projectId: string,
  sceneId: string | null,
  cacheSize = 3
): {
  url: string | null;
  loading: boolean;
  error: Error | null;
} {
  const cacheRef = useRef<Cache>(new Map());
  const [url, setUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    // Unmount cleanup: revoke every entry. Snapshot the ref's Map to avoid the
    // "ref may change before cleanup runs" lint warning.
    const cache = cacheRef.current;
    return () => {
      for (const entry of cache.values()) URL.revokeObjectURL(entry.url);
      cache.clear();
    };
  }, []);

  useEffect(() => {
    if (!sceneId) {
      // Clearing state when the scene goes away is a legitimate effect;
      // React 19's "no sync setState in effects" rule is a perf nudge, not a
      // correctness rule, so we opt out locally.
      /* eslint-disable react-hooks/set-state-in-effect */
      setUrl(null);
      setLoading(false);
      setError(null);
      /* eslint-enable react-hooks/set-state-in-effect */
      return;
    }
    const cache = cacheRef.current;
    const existing = cache.get(sceneId);
    if (existing) {
      existing.lastUsed = Date.now();
      setUrl(existing.url);
      setLoading(false);
      setError(null);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);
    setUrl(null);

    gisApi
      .getSatelliteImageThumbnail(
        projectId as `${string}-${string}-${string}-${string}-${string}`,
        sceneId as `${string}-${string}-${string}-${string}-${string}`
      )
      .then((response) => {
        if (cancelled) return;
        const blob = new Blob([response.data], { type: "image/png" });
        const newUrl = URL.createObjectURL(blob);
        cache.set(sceneId, { url: newUrl, lastUsed: Date.now() });
        // Evict least-recently-used entries past the cap.
        while (cache.size > cacheSize) {
          let oldestKey: string | null = null;
          let oldestAt = Infinity;
          for (const [key, entry] of cache) {
            if (entry.lastUsed < oldestAt) {
              oldestAt = entry.lastUsed;
              oldestKey = key;
            }
          }
          if (oldestKey && oldestKey !== sceneId) {
            const victim = cache.get(oldestKey);
            if (victim) URL.revokeObjectURL(victim.url);
            cache.delete(oldestKey);
          } else {
            break;
          }
        }
        setUrl(newUrl);
        setLoading(false);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setError(e instanceof Error ? e : new Error(String(e)));
        setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [projectId, sceneId, cacheSize]);

  return { url, loading, error };
}
