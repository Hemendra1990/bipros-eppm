"use client";

import { useEffect, useMemo, useState } from "react";
import { gisApi, SatelliteImage } from "@/lib/api/gisApi";
import { formatDate } from "date-fns";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Maximize2, Trash2 } from "lucide-react";
import toast from "react-hot-toast";
import { SatelliteImageDetailModal } from "./SatelliteImageDetailModal";
import { useSatelliteRaster } from "./useSatelliteRaster";

/**
 * One thumbnail tile. Loads the raster via {@link useSatelliteRaster} (authenticated
 * blob URL) and renders it with an {@code <img>}. The same raster is shown full-size
 * in {@link SatelliteImageDetailModal} when the card is clicked.
 */
function SatelliteThumbnail({
  projectId,
  imageId,
  mimeType,
}: {
  projectId: string;
  imageId: string;
  mimeType?: string;
}) {
  const { src, error } = useSatelliteRaster(projectId, imageId, mimeType);

  if (error) {
    return (
      <div className="bg-surface-hover h-32 flex items-center justify-center text-text-muted">
        <span className="text-xs">⚠ thumbnail unavailable</span>
      </div>
    );
  }
  if (!src) {
    return (
      <div className="bg-surface-hover h-32 flex items-center justify-center text-text-muted animate-pulse">
        <span className="text-3xl">📡</span>
      </div>
    );
  }
  return (
    // next/image can't open blob: URLs, so use a plain <img>.
    // eslint-disable-next-line @next/next/no-img-element
    <img src={src} alt="Satellite tile" className="h-32 w-full object-cover bg-black" />
  );
}

interface SatelliteImageGalleryProps {
  projectId: string;
  images: SatelliteImage[];
  /** Polygon list for id -> label mapping (label = name || wbsCode). */
  polygons?: Array<{ id: string; name?: string; wbsCode: string }>;
  /** Optional client-side capture-date filter, 'YYYY-MM-DD' (inclusive). */
  dateFrom?: string;
  dateTo?: string;
  /** When set, the gallery follows the map's chosen polygon. */
  selectedPolygonId?: string | null;
}

const ALL = "ALL";
const UNASSIGNED = "UNASSIGNED";

const byCaptureDateDesc = (a: SatelliteImage, b: SatelliteImage) =>
  new Date(b.captureDate).getTime() - new Date(a.captureDate).getTime();

export function SatelliteImageGallery({
  projectId,
  images,
  polygons,
  dateFrom,
  dateTo,
  selectedPolygonId,
}: SatelliteImageGalleryProps) {
  const qc = useQueryClient();
  const [selected, setSelected] = useState<SatelliteImage | null>(null);
  const [polygonFilter, setPolygonFilter] = useState<string>(ALL);

  // Follow the map's chosen polygon: when one is selected, scope the gallery to
  // it (the user can still change the dropdown afterwards).
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (selectedPolygonId) setPolygonFilter(selectedPolygonId);
  }, [selectedPolygonId]);
  /* eslint-enable react-hooks/set-state-in-effect */

  const deleteMutation = useMutation({
    mutationFn: (imageId: string) =>
      gisApi.deleteSatelliteImage(
        projectId as `${string}-${string}-${string}-${string}-${string}`,
        imageId as `${string}-${string}-${string}-${string}-${string}`
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["gis", projectId, "satellite-images"] });
      toast.success("Image deleted");
    },
    onError: () => {
      toast.error("Failed to delete image");
    },
  });

  // id -> label (name || wbsCode); also tells us which polygon ids are "known".
  const polygonLabels = useMemo(() => {
    const m = new Map<string, string>();
    (polygons ?? []).forEach((p) => m.set(p.id, p.name || p.wbsCode));
    return m;
  }, [polygons]);

  // Date filter (applied before grouping/polygon filter).
  const dateFiltered = useMemo(() => {
    if (!dateFrom && !dateTo) return images;
    return images.filter((img) => {
      const d = (img.captureDate ?? "").slice(0, 10);
      if (dateFrom && d < dateFrom) return false;
      if (dateTo && d > dateTo) return false;
      return true;
    });
  }, [images, dateFrom, dateTo]);

  // Polygon filter.
  const filtered = useMemo(() => {
    if (polygonFilter === ALL) return dateFiltered;
    if (polygonFilter === UNASSIGNED)
      return dateFiltered.filter((img) => img.wbsPolygonId == null);
    return dateFiltered.filter((img) => img.wbsPolygonId === polygonFilter);
  }, [dateFiltered, polygonFilter]);

  // Group filtered images by polygon; unknown/null ids fall under "Unassigned".
  const groups = useMemo(() => {
    const byKey = new Map<string, SatelliteImage[]>();
    for (const img of filtered) {
      const pid = img.wbsPolygonId;
      const key = pid && polygonLabels.has(pid) ? pid : UNASSIGNED;
      const bucket = byKey.get(key);
      if (bucket) bucket.push(img);
      else byKey.set(key, [img]);
    }
    const ordered: { key: string; label: string; images: SatelliteImage[] }[] = [];
    for (const p of polygons ?? []) {
      const imgs = byKey.get(p.id);
      if (imgs && imgs.length) {
        ordered.push({
          key: p.id,
          label: p.name || p.wbsCode,
          images: [...imgs].sort(byCaptureDateDesc),
        });
      }
    }
    const unassigned = byKey.get(UNASSIGNED);
    if (unassigned && unassigned.length) {
      ordered.push({
        key: UNASSIGNED,
        label: "Unassigned",
        images: [...unassigned].sort(byCaptureDateDesc),
      });
    }
    return ordered;
  }, [filtered, polygons, polygonLabels]);

  const renderCard = (image: SatelliteImage) => (
    <div
      key={image.id}
      role="button"
      tabIndex={0}
      onClick={() => setSelected(image)}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          setSelected(image);
        }
      }}
      title="Click to view the full image and all details"
      className="group bg-surface/50 rounded-lg border border-border overflow-hidden cursor-pointer transition-all hover:shadow-lg hover:border-accent/40 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent"
    >
      <div className="relative">
        <SatelliteThumbnail
          projectId={projectId}
          imageId={image.id}
          mimeType={image.mimeType}
        />
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-black/0 transition-colors group-hover:bg-black/25">
          <Maximize2
            size={22}
            className="text-white opacity-0 drop-shadow transition-opacity group-hover:opacity-90"
          />
        </div>
      </div>

      <div className="p-4">
        <div className="flex items-start justify-between gap-2 mb-2">
          <h4 className="font-medium text-text-primary text-sm line-clamp-2">
            {image.imageName}
          </h4>
          {image.source === "MANUAL_UPLOAD" && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                if (confirm("Delete this uploaded image?")) {
                  deleteMutation.mutate(image.id);
                }
              }}
              disabled={deleteMutation.isPending}
              className="text-text-muted hover:text-danger transition-colors"
              title="Delete"
            >
              <Trash2 size={14} />
            </button>
          )}
        </div>

        <div className="space-y-1 text-xs text-text-secondary">
          <p>
            <span className="font-medium">Date:</span>{" "}
            {formatDate(new Date(image.captureDate), "dd MMM yyyy")}
          </p>
          <p>
            <span className="font-medium">Source:</span>{" "}
            {image.source.replace(/_/g, " ")}
          </p>
          {image.resolution && (
            <p>
              <span className="font-medium">Resolution:</span>{" "}
              {image.resolution}
            </p>
          )}
          <p>
            <span className="font-medium">Size:</span>{" "}
            {(image.fileSize / 1024 / 1024).toFixed(2)} MB
          </p>
          <p>
            <span className="font-medium">Status:</span>
            <span
              className={`ml-1 px-2 py-1 rounded text-xs font-medium ${
                image.status === "READY"
                  ? "bg-success/10 text-success"
                  : image.status === "FAILED"
                    ? "bg-danger/10 text-danger"
                    : "bg-warning/10 text-warning"
              }`}
            >
              {image.status}
            </span>
          </p>
        </div>

        {image.description && (
          <p className="text-xs text-text-muted mt-2 line-clamp-2">
            {image.description}
          </p>
        )}
      </div>
    </div>
  );

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap justify-between items-center gap-2">
        <h3 className="text-lg font-semibold text-text-primary">
          Satellite Images
        </h3>
        <div className="flex flex-wrap items-center gap-2">
          <label
            htmlFor="satellite-polygon-filter"
            className="text-xs text-text-secondary"
          >
            Polygon
          </label>
          <select
            id="satellite-polygon-filter"
            value={polygonFilter}
            onChange={(e) => setPolygonFilter(e.target.value)}
            className="rounded-md border border-border bg-surface px-2 py-1.5 text-sm text-text-primary"
          >
            <option value={ALL}>All polygons</option>
            {(polygons ?? []).map((p) => (
              <option key={p.id} value={p.id}>
                {p.name || p.wbsCode}
              </option>
            ))}
            <option value={UNASSIGNED}>Unassigned</option>
          </select>
          <span className="text-sm text-text-secondary">
            {filtered.length} images
          </span>
        </div>
      </div>

      {filtered.length === 0 ? (
        <div className="bg-surface/50 rounded-lg border border-border p-8 text-center">
          <p className="text-text-secondary">
            {images.length === 0
              ? "No satellite images uploaded"
              : "No satellite images match the current filters"}
          </p>
        </div>
      ) : (
        <div className="space-y-6">
          {groups.map((group) => (
            <div key={group.key} className="space-y-3">
              <div className="flex items-center gap-2 border-b border-border pb-1">
                <h4 className="text-sm font-semibold text-text-primary">
                  {group.label}
                </h4>
                <span className="text-xs text-text-secondary">
                  {group.images.length}{" "}
                  {group.images.length === 1 ? "image" : "images"}
                </span>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {group.images.map((image) => renderCard(image))}
              </div>
            </div>
          ))}
        </div>
      )}

      {selected && (
        <SatelliteImageDetailModal
          projectId={projectId}
          image={selected}
          onClose={() => setSelected(null)}
        />
      )}
    </div>
  );
}
