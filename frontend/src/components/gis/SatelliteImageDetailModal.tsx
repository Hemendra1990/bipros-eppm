"use client";

import type { ReactNode } from "react";
import { formatDate } from "date-fns";
import { Download, ExternalLink, Loader2 } from "lucide-react";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { SatelliteImage } from "@/lib/api/gisApi";
import { useSatelliteRaster } from "./useSatelliteRaster";

function statusBadgeClass(status: SatelliteImage["status"]) {
  if (status === "READY") return "bg-success/10 text-success";
  if (status === "FAILED") return "bg-danger/10 text-danger";
  return "bg-warning/10 text-warning";
}

function extForMime(mime?: string) {
  if (!mime) return "png";
  if (mime.includes("png")) return "png";
  if (mime.includes("jpeg") || mime.includes("jpg")) return "jpg";
  if (mime.includes("tiff")) return "tif";
  return "bin";
}

function fmtCoord(n?: number) {
  return typeof n === "number" ? n.toFixed(5) : "—";
}

/** One label / value row in the metadata grid. */
function Field({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs uppercase tracking-wide text-slate/70">{label}</dt>
      <dd className="mt-0.5 text-sm text-charcoal break-words">{value}</dd>
    </div>
  );
}

export function SatelliteImageDetailModal({
  projectId,
  image,
  onClose,
}: {
  projectId: string;
  image: SatelliteImage;
  onClose: () => void;
}) {
  const { src, error } = useSatelliteRaster(projectId, image.id, image.mimeType);

  const hasBounds =
    image.northBound != null &&
    image.southBound != null &&
    image.eastBound != null &&
    image.westBound != null;

  const download = () => {
    if (!src) return;
    const a = document.createElement("a");
    a.href = src;
    a.download = `${(image.imageName || "satellite-image").replace(/[^\w.-]+/g, "_")}.${extForMime(
      image.mimeType,
    )}`;
    document.body.appendChild(a);
    a.click();
    a.remove();
  };

  return (
    <Dialog open onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="max-w-5xl overflow-hidden p-0">
        <div className="max-h-[90vh] overflow-y-auto">
          {/* Large image */}
          <div className="flex min-h-[280px] items-center justify-center bg-black">
            {error ? (
              <div className="flex flex-col items-center gap-2 py-16 text-white/60">
                <span className="text-4xl">🛰️</span>
                <span className="text-sm">Image unavailable</span>
              </div>
            ) : !src ? (
              <div className="flex flex-col items-center gap-2 py-16 text-white/60">
                <Loader2 className="animate-spin" size={28} />
                <span className="text-sm">Loading full-resolution raster…</span>
              </div>
            ) : (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={src}
                alt={image.imageName}
                className="max-h-[62vh] w-full object-contain"
              />
            )}
          </div>

          {/* Details */}
          <div className="px-6 py-5">
            <div className="mb-4 flex items-start justify-between gap-4 pr-8">
              <div className="min-w-0">
                <h2 className="font-display text-lg font-semibold tracking-tight text-charcoal break-words">
                  {image.imageName}
                </h2>
                <p className="mt-0.5 text-sm text-slate">
                  Captured {formatDate(new Date(image.captureDate), "dd MMM yyyy")}
                </p>
              </div>
              <span
                className={`shrink-0 rounded px-2 py-1 text-xs font-medium ${statusBadgeClass(
                  image.status,
                )}`}
              >
                {image.status}
              </span>
            </div>

            <dl className="grid grid-cols-2 gap-x-6 gap-y-4 sm:grid-cols-3">
              <Field label="Source" value={image.source.replace(/_/g, " ")} />
              <Field label="Capture Date" value={formatDate(new Date(image.captureDate), "dd MMM yyyy")} />
              <Field label="Resolution" value={image.resolution || "—"} />
              <Field
                label="File Size"
                value={`${(image.fileSize / 1024 / 1024).toFixed(2)} MB`}
              />
              <Field label="Format" value={image.mimeType || "—"} />
              <Field
                label="Added"
                value={formatDate(new Date(image.createdAt), "dd MMM yyyy, HH:mm")}
              />
            </dl>

            {hasBounds && (
              <div className="mt-5 rounded-lg border border-hairline bg-ivory p-4">
                <p className="mb-2 text-xs uppercase tracking-wide text-slate/70">
                  Bounding box (lat / lon)
                </p>
                <div className="grid grid-cols-2 gap-x-6 gap-y-2 sm:grid-cols-4 text-sm text-charcoal">
                  <div>
                    <span className="text-slate/70">N</span> {fmtCoord(image.northBound)}
                  </div>
                  <div>
                    <span className="text-slate/70">S</span> {fmtCoord(image.southBound)}
                  </div>
                  <div>
                    <span className="text-slate/70">E</span> {fmtCoord(image.eastBound)}
                  </div>
                  <div>
                    <span className="text-slate/70">W</span> {fmtCoord(image.westBound)}
                  </div>
                </div>
              </div>
            )}

            {image.description && (
              <div className="mt-5">
                <p className="mb-1 text-xs uppercase tracking-wide text-slate/70">
                  Description
                </p>
                <p className="text-sm text-slate">{image.description}</p>
              </div>
            )}

            <div className="mt-5">
              <p className="mb-1 text-xs uppercase tracking-wide text-slate/70">
                Storage path
              </p>
              <p className="text-xs text-slate/80 break-all font-mono">{image.filePath}</p>
            </div>

            <div className="mt-6 flex flex-wrap items-center gap-2">
              <Button variant="primary" onClick={download} disabled={!src}>
                <Download size={16} className="mr-1.5" />
                Download
              </Button>
              {src && (
                // eslint-disable-next-line @next/next/no-html-link-for-pages
                <a
                  href={src}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1.5 rounded-md border border-hairline px-4 py-2 text-sm font-medium text-charcoal transition-colors hover:bg-ivory"
                >
                  <ExternalLink size={16} />
                  Open full image
                </a>
              )}
              <Button variant="ghost" onClick={onClose} className="ml-auto">
                Close
              </Button>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
