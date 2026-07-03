"use client";

import { useEffect, useState } from "react";
import { gisApi } from "@/lib/api/gisApi";

type UUID = `${string}-${string}-${string}-${string}-${string}`;

/**
 * Fetches a satellite raster's bytes through the authenticated apiClient and
 * exposes them as an object URL for an {@code <img>}. The same endpoint backs
 * both the gallery thumbnail and the full-size detail view — the stored raster
 * IS the full image, so there is no separate "large" asset. The object URL is
 * revoked on unmount / id change so Blob references don't leak.
 */
export function useSatelliteRaster(
  projectId: string,
  imageId: string,
  mimeType?: string,
) {
  const [src, setSrc] = useState<string | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    let revoked: string | null = null;
    let cancelled = false;
    setSrc(null);
    setError(false);
    gisApi
      .getSatelliteImageThumbnail(projectId as UUID, imageId as UUID)
      .then((response) => {
        if (cancelled) return;
        const blob = new Blob([response.data], { type: mimeType || "image/png" });
        const url = URL.createObjectURL(blob);
        revoked = url;
        setSrc(url);
      })
      .catch(() => {
        if (!cancelled) setError(true);
      });
    return () => {
      cancelled = true;
      if (revoked) URL.revokeObjectURL(revoked);
    };
  }, [projectId, imageId, mimeType]);

  return { src, error };
}
