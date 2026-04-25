"use client";

import React, { useRef, useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogBody,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { gisApi, type UploadSatelliteImageRequest } from "@/lib/api/gisApi";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Upload } from "lucide-react";

interface UploadSatelliteImageModalProps {
  projectId: string;
  open: boolean;
  onClose: () => void;
}

export function UploadSatelliteImageModal({
  projectId,
  open,
  onClose,
}: UploadSatelliteImageModalProps) {
  const qc = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [file, setFile] = useState<File | null>(null);
  const [imageName, setImageName] = useState("");
  const [captureDate, setCaptureDate] = useState(
    () => new Date().toISOString().slice(0, 10)
  );
  const [description, setDescription] = useState("");
  const [resolution, setResolution] = useState("");
  const [westBound, setWestBound] = useState("");
  const [southBound, setSouthBound] = useState("");
  const [eastBound, setEastBound] = useState("");
  const [northBound, setNorthBound] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});

  const isGeoTiff = file
    ? file.name.toLowerCase().endsWith(".tif") ||
      file.name.toLowerCase().endsWith(".tiff")
    : false;

  const uploadMutation = useMutation({
    mutationFn: (vars: { metadata: UploadSatelliteImageRequest; file: File }) =>
      gisApi.uploadSatelliteImage(
        projectId as `${string}-${string}-${string}-${string}-${string}`,
        vars.metadata,
        vars.file
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["gis", projectId, "satellite-images"] });
      qc.invalidateQueries({ queryKey: ["gis", projectId, "progress-variance"] });
      resetForm();
      onClose();
    },
  });

  const resetForm = () => {
    setFile(null);
    setImageName("");
    setCaptureDate(new Date().toISOString().slice(0, 10));
    setDescription("");
    setResolution("");
    setWestBound("");
    setSouthBound("");
    setEastBound("");
    setNorthBound("");
    setErrors({});
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const validate = (): boolean => {
    const nextErrors: Record<string, string> = {};
    if (!imageName.trim()) nextErrors.imageName = "Image name is required";
    if (!captureDate) nextErrors.captureDate = "Capture date is required";
    if (!file) nextErrors.file = "Please select a file";
    if (file && !isGeoTiff) {
      if (!westBound || !southBound || !eastBound || !northBound) {
        nextErrors.bounds = "Bounding box (W, S, E, N) is required for PNG/JPEG uploads";
      } else {
        const w = parseFloat(westBound);
        const s = parseFloat(southBound);
        const e = parseFloat(eastBound);
        const n = parseFloat(northBound);
        if (isNaN(w) || isNaN(s) || isNaN(e) || isNaN(n)) {
          nextErrors.bounds = "All bounds must be valid numbers";
        } else if (w >= e || s >= n) {
          nextErrors.bounds = "West < East and South < North";
        }
      }
    }
    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate() || !file) return;

    let boundingBoxGeoJson: string | undefined;
    if (!isGeoTiff) {
      const w = parseFloat(westBound);
      const s = parseFloat(southBound);
      const e = parseFloat(eastBound);
      const n = parseFloat(northBound);
      boundingBoxGeoJson = JSON.stringify({
        type: "Polygon",
        coordinates: [[[w, s], [e, s], [e, n], [w, n], [w, s]]],
      });
    }

    const metadata: UploadSatelliteImageRequest = {
      imageName: imageName.trim(),
      captureDate,
      description: description.trim() || undefined,
      resolution: resolution.trim() || undefined,
      boundingBoxGeoJson,
    };

    uploadMutation.mutate({ metadata, file });
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const picked = e.target.files?.[0] ?? null;
    setFile(picked);
    if (picked && !imageName) {
      setImageName(picked.name.replace(/\.[^/.]+$/, ""));
    }
    if (errors.file) setErrors((prev) => ({ ...prev, file: "" }));
  };

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Upload Satellite Image</DialogTitle>
        </DialogHeader>
        <DialogBody>
          <form id="upload-image-form" onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-1">
                Image Name
              </label>
              <input
                type="text"
                value={imageName}
                onChange={(e) => setImageName(e.target.value)}
                className="w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-text-primary"
                placeholder="e.g. Drone survey Jan 2026"
              />
              {errors.imageName && (
                <p className="text-xs text-danger mt-1">{errors.imageName}</p>
              )}
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-medium text-text-secondary mb-1">
                  Capture Date
                </label>
                <input
                  type="date"
                  value={captureDate}
                  onChange={(e) => setCaptureDate(e.target.value)}
                  className="w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-text-primary"
                />
                {errors.captureDate && (
                  <p className="text-xs text-danger mt-1">{errors.captureDate}</p>
                )}
              </div>
              <div>
                <label className="block text-sm font-medium text-text-secondary mb-1">
                  Resolution
                </label>
                <input
                  type="text"
                  value={resolution}
                  onChange={(e) => setResolution(e.target.value)}
                  className="w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-text-primary"
                  placeholder="e.g. 0.5m"
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-text-secondary mb-1">
                Description
              </label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={2}
                className="w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-text-primary"
                placeholder="Optional notes about this image"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-text-secondary mb-1">
                File
              </label>
              <input
                ref={fileInputRef}
                type="file"
                accept=".tif,.tiff,.png,.jpg,.jpeg"
                onChange={handleFileChange}
                className="block w-full text-sm text-text-secondary file:mr-4 file:py-2 file:px-4 file:rounded-md file:border-0 file:text-sm file:font-semibold file:bg-ivory file:text-gold-deep hover:file:bg-gold/10"
              />
              <p className="text-xs text-text-muted mt-1">
                GeoTIFFs auto-extract bounds. PNG/JPEG requires manual bounds.
              </p>
              {errors.file && (
                <p className="text-xs text-danger mt-1">{errors.file}</p>
              )}
            </div>

            {file && !isGeoTiff && (
              <div className="rounded-md border border-border bg-surface/50 p-3 space-y-3">
                <p className="text-sm font-medium text-text-secondary">
                  Bounding Box (WGS84)
                </p>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs text-text-muted mb-1">West</label>
                    <input
                      type="number"
                      step="any"
                      value={westBound}
                      onChange={(e) => setWestBound(e.target.value)}
                      className="w-full rounded-md border border-border bg-surface px-2 py-1.5 text-sm text-text-primary"
                      placeholder="Longitude"
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-text-muted mb-1">East</label>
                    <input
                      type="number"
                      step="any"
                      value={eastBound}
                      onChange={(e) => setEastBound(e.target.value)}
                      className="w-full rounded-md border border-border bg-surface px-2 py-1.5 text-sm text-text-primary"
                      placeholder="Longitude"
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-text-muted mb-1">South</label>
                    <input
                      type="number"
                      step="any"
                      value={southBound}
                      onChange={(e) => setSouthBound(e.target.value)}
                      className="w-full rounded-md border border-border bg-surface px-2 py-1.5 text-sm text-text-primary"
                      placeholder="Latitude"
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-text-muted mb-1">North</label>
                    <input
                      type="number"
                      step="any"
                      value={northBound}
                      onChange={(e) => setNorthBound(e.target.value)}
                      className="w-full rounded-md border border-border bg-surface px-2 py-1.5 text-sm text-text-primary"
                      placeholder="Latitude"
                    />
                  </div>
                </div>
                {errors.bounds && (
                  <p className="text-xs text-danger">{errors.bounds}</p>
                )}
              </div>
            )}

            {uploadMutation.isError && (
              <p className="text-sm text-danger">
                {(uploadMutation.error as Error)?.message ?? "Upload failed"}
              </p>
            )}
          </form>
        </DialogBody>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose} disabled={uploadMutation.isPending}>
            Cancel
          </Button>
          <Button
            onClick={() => document.getElementById("upload-image-form")?.dispatchEvent(new Event("submit", { cancelable: true, bubbles: true }))}
            disabled={uploadMutation.isPending}
          >
            {uploadMutation.isPending ? "Uploading…" : (
              <>
                <Upload size={16} />
                Upload Image
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
