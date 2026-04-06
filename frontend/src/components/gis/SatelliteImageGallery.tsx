"use client";

import { SatelliteImage } from "@/lib/api/gisApi";
import { formatDate } from "date-fns";

interface SatelliteImageGalleryProps {
  projectId: string;
  images: SatelliteImage[];
}

export function SatelliteImageGallery({
  projectId,
  images,
}: SatelliteImageGalleryProps) {
  const sortedImages = [...images].sort(
    (a, b) =>
      new Date(b.captureDate).getTime() - new Date(a.captureDate).getTime()
  );

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h3 className="text-lg font-semibold text-white">
          Satellite Images
        </h3>
        <span className="text-sm text-slate-400">{images.length} images</span>
      </div>

      {images.length === 0 ? (
        <div className="bg-slate-900/50 rounded-lg border border-slate-800 p-8 text-center">
          <p className="text-slate-400">No satellite images uploaded</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {sortedImages.map((image) => (
            <div
              key={image.id}
              className="bg-slate-900/50 rounded-lg border border-slate-800 overflow-hidden hover:shadow-lg transition-shadow"
            >
              <div className="bg-slate-800 h-32 flex items-center justify-center text-slate-500">
                <span className="text-3xl">📡</span>
              </div>

              <div className="p-4">
                <h4 className="font-medium text-white text-sm mb-2 line-clamp-2">
                  {image.imageName}
                </h4>

                <div className="space-y-1 text-xs text-slate-400">
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
                          ? "bg-green-950 text-green-300"
                          : image.status === "FAILED"
                            ? "bg-red-950 text-red-300"
                            : "bg-yellow-950 text-yellow-300"
                      }`}
                    >
                      {image.status}
                    </span>
                  </p>
                </div>

                {image.description && (
                  <p className="text-xs text-slate-500 mt-2 line-clamp-2">
                    {image.description}
                  </p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
