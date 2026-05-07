"use client";

import { useEffect, useRef, useState } from "react";
import { Camera, ImageIcon, Trash2, X } from "lucide-react";
import { dprApi } from "@/lib/api/dprApi";
import type { DprAttachment } from "@/lib/types/dpr";

const MAX_FILE_BYTES = 15 * 1024 * 1024;
const ACCEPTED_MIME = ["image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif"];

export interface PendingPhoto {
  file: File;
  caption: string;
  previewUrl: string;
}

interface Props {
  projectId: string;
  /** When null we're in "new DPR" mode — only pendingPhotos are shown; uploads happen post-save. */
  dprId: string | null;
  pending: PendingPhoto[];
  existing: DprAttachment[];
  onPendingChange: (next: PendingPhoto[]) => void;
  onExistingChange: (next: DprAttachment[]) => void;
}

/**
 * Photos section for the DPR Add/Edit drawer. Two-column behaviour:
 *  - Existing photos (edit mode) load thumbnails through an authenticated fetch + blob URL,
 *    since the binary endpoint is JWT-protected and {@code <img src>} cannot send headers.
 *  - Pending photos (selected before save) live entirely client-side until the parent flushes
 *    them via {@code dprApi.uploadPhotos} after the DPR row is saved.
 */
export function DprPhotosSection({
  projectId,
  dprId,
  pending,
  existing,
  onPendingChange,
  onExistingChange,
}: Props) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyDeletes, setBusyDeletes] = useState<Set<string>>(new Set());

  // Pending preview URLs are owned by this component — revoke when the photo is removed or the
  // component unmounts so the browser can release the underlying ArrayBuffer.
  useEffect(() => {
    return () => {
      pending.forEach((p) => URL.revokeObjectURL(p.previewUrl));
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- intentional: cleanup-on-unmount only
  }, []);

  const handleSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    e.target.value = ""; // allow re-selecting the same file after a remove

    const accepted: PendingPhoto[] = [];
    const rejected: string[] = [];
    for (const file of files) {
      if (!ACCEPTED_MIME.includes(file.type.toLowerCase())) {
        rejected.push(`${file.name}: unsupported type`);
        continue;
      }
      if (file.size > MAX_FILE_BYTES) {
        rejected.push(`${file.name}: exceeds 15 MB`);
        continue;
      }
      accepted.push({
        file,
        caption: "",
        previewUrl: URL.createObjectURL(file),
      });
    }
    if (accepted.length > 0) onPendingChange([...pending, ...accepted]);
    setError(rejected.length > 0 ? rejected.join("; ") : null);
  };

  const removePending = (idx: number) => {
    const next = pending.slice();
    const [removed] = next.splice(idx, 1);
    if (removed) URL.revokeObjectURL(removed.previewUrl);
    onPendingChange(next);
  };

  const setPendingCaption = (idx: number, caption: string) => {
    const next = pending.slice();
    next[idx] = { ...next[idx], caption };
    onPendingChange(next);
  };

  const deleteExisting = async (photoId: string) => {
    if (!dprId) return;
    if (!window.confirm("Delete this photo? This cannot be undone.")) return;
    setBusyDeletes((s) => new Set(s).add(photoId));
    try {
      await dprApi.deletePhoto(projectId, dprId, photoId);
      onExistingChange(existing.filter((p) => p.id !== photoId));
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Failed to delete photo");
    } finally {
      setBusyDeletes((s) => {
        const n = new Set(s);
        n.delete(photoId);
        return n;
      });
    }
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2 text-sm font-semibold text-charcoal">
          <ImageIcon className="h-4 w-4 text-gold" />
          Photos
          {(pending.length > 0 || existing.length > 0) && (
            <span className="text-xs font-normal text-slate">
              ({existing.length} saved{pending.length > 0 ? `, ${pending.length} pending` : ""})
            </span>
          )}
        </div>
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          className="inline-flex cursor-pointer items-center gap-1.5 rounded-md border border-hairline bg-paper px-3 py-1.5 text-sm font-semibold text-charcoal hover:bg-ivory"
        >
          <Camera className="h-4 w-4" />
          Add photos
        </button>
        <input
          ref={inputRef}
          type="file"
          multiple
          accept="image/*"
          hidden
          onChange={handleSelect}
        />
      </div>

      {error && <div className="text-xs text-burgundy">{error}</div>}

      {existing.length === 0 && pending.length === 0 && (
        <div className="rounded-md border border-dashed border-hairline px-3 py-4 text-center text-xs text-slate">
          {dprId
            ? "No photos yet. Use Add photos to attach progress images."
            : "Pick images now — they'll upload after you save the DPR."}
        </div>
      )}

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
        {existing.map((photo) => (
          <ExistingPhotoTile
            key={photo.id}
            projectId={projectId}
            dprId={dprId!}
            photo={photo}
            busy={busyDeletes.has(photo.id)}
            onDelete={() => deleteExisting(photo.id)}
          />
        ))}
        {pending.map((p, idx) => (
          <div
            key={`pending-${idx}-${p.file.name}`}
            className="overflow-hidden rounded-md border border-dashed border-gold/50 bg-paper"
          >
            <div className="relative aspect-[4/3] bg-ivory">
              {/* eslint-disable-next-line @next/next/no-img-element -- blob URL preview, no Next image optimization */}
              <img src={p.previewUrl} alt={p.file.name} className="h-full w-full object-cover" />
              <button
                type="button"
                onClick={() => removePending(idx)}
                className="absolute right-1 top-1 rounded-full bg-paper/95 p-1 text-charcoal shadow hover:bg-burgundy hover:text-white"
                aria-label="Remove photo"
              >
                <X className="h-3.5 w-3.5" />
              </button>
              <span className="absolute left-1 top-1 rounded bg-gold px-1.5 py-0.5 text-[10px] font-bold uppercase text-gold-ink">
                Pending
              </span>
            </div>
            <input
              type="text"
              value={p.caption}
              onChange={(e) => setPendingCaption(idx, e.target.value)}
              placeholder="Caption (optional)"
              maxLength={500}
              className="w-full border-0 border-t border-hairline bg-paper px-2 py-1.5 text-xs text-charcoal placeholder:text-slate focus:outline-none focus:ring-0"
            />
          </div>
        ))}
      </div>
    </div>
  );
}

interface TileProps {
  projectId: string;
  dprId: string;
  photo: DprAttachment;
  busy: boolean;
  onDelete: () => void;
}

function ExistingPhotoTile({ projectId, dprId, photo, busy, onDelete }: TileProps) {
  const [src, setSrc] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    let createdUrl: string | null = null;
    dprApi
      .fetchPhotoBlobUrl(projectId, dprId, photo.id)
      .then((url) => {
        if (cancelled) {
          URL.revokeObjectURL(url);
        } else {
          createdUrl = url;
          setSrc(url);
        }
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });
    return () => {
      cancelled = true;
      if (createdUrl) URL.revokeObjectURL(createdUrl);
    };
  }, [projectId, dprId, photo.id]);

  return (
    <div className="overflow-hidden rounded-md border border-hairline bg-paper">
      <div className="relative aspect-[4/3] bg-ivory">
        {src && !failed ? (
          // eslint-disable-next-line @next/next/no-img-element -- auth-fetched blob URL, not eligible for next/image
          <img src={src} alt={photo.fileName} className="h-full w-full object-cover" />
        ) : (
          <div className="flex h-full w-full items-center justify-center text-xs text-slate">
            {failed ? "Failed to load" : "Loading…"}
          </div>
        )}
        <button
          type="button"
          onClick={onDelete}
          disabled={busy}
          className="absolute right-1 top-1 rounded-full bg-paper/95 p-1 text-charcoal shadow hover:bg-burgundy hover:text-white disabled:opacity-50"
          aria-label="Delete photo"
        >
          <Trash2 className="h-3.5 w-3.5" />
        </button>
      </div>
      <div className="border-t border-hairline px-2 py-1.5 text-xs text-charcoal">
        {photo.caption ? <span className="line-clamp-2">{photo.caption}</span> : <span className="text-slate">No caption</span>}
      </div>
    </div>
  );
}
