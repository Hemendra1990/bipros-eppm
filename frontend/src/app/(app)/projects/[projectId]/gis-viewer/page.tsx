"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useParams } from "next/navigation";
import { MapViewer } from "@/components/gis/MapViewer";
import { GisLayerList } from "@/components/gis/GisLayerList";
import { SatelliteImageGallery } from "@/components/gis/SatelliteImageGallery";
import { ProgressVarianceTable } from "@/components/gis/ProgressVarianceTable";
import { TabTip } from "@/components/common/TabTip";
import { Button } from "@/components/ui/button";
import { gisApi, type IngestionResult } from "@/lib/api/gisApi";

type TabId = "map" | "layers" | "satellite" | "progress";

export default function GisViewerPage() {
  const params = useParams();
  const projectId = params.projectId as `${string}-${string}-${string}-${string}-${string}`;
  const [activeTab, setActiveTab] = useState<TabId>("map");
  const qc = useQueryClient();

  // Default to a 7-day window ending today (matches the nightly scheduler default).
  // Lazy initialisers keep render pure under React 19's hooks/purity rule.
  const [ingestFrom, setIngestFrom] = useState(
    () => new Date(Date.now() - 7 * 24 * 3600 * 1000).toISOString().slice(0, 10)
  );
  const [ingestTo, setIngestTo] = useState(() => new Date().toISOString().slice(0, 10));
  const [lastIngestResult, setLastIngestResult] = useState<IngestionResult | null>(null);

  const ingestMutation = useMutation({
    mutationFn: () => gisApi.ingestSatellite(projectId, ingestFrom, ingestTo),
    onSuccess: (response) => {
      setLastIngestResult(response.data.data);
      qc.invalidateQueries({ queryKey: ["gis", projectId, "satellite-images"] });
      qc.invalidateQueries({ queryKey: ["gis", projectId, "progress-variance"] });
    },
  });

  const { data: geoJsonResponse, isLoading: geoJsonLoading } = useQuery({
    queryKey: ["gis", projectId, "geojson"],
    queryFn: async () => {
      const response = await gisApi.getPolygonsAsGeoJson(projectId);
      return response.data;
    },
  });

  const { data: layersResponse } = useQuery({
    queryKey: ["gis", projectId, "layers"],
    queryFn: async () => {
      const response = await gisApi.getLayers(projectId);
      return response.data;
    },
  });

  const { data: satelliteImagesResponse } = useQuery({
    queryKey: ["gis", projectId, "satellite-images"],
    queryFn: async () => {
      const response = await gisApi.getSatelliteImages(projectId);
      return response.data;
    },
  });

  const { data: varianceResponse } = useQuery({
    queryKey: ["gis", projectId, "progress-variance"],
    queryFn: async () => {
      const response = await gisApi.getProgressVariance(projectId);
      return response.data;
    },
  });

  const tabs = [
    { id: "map" as TabId, label: "Map Viewer" },
    { id: "layers" as TabId, label: "Layers" },
    { id: "satellite" as TabId, label: "Satellite Images" },
    { id: "progress" as TabId, label: "Progress Tracking" },
  ];

  return (
    <div className="flex flex-col h-full gap-4 p-4">
      <TabTip
        title="GIS Map Viewer"
        description="View your project location on a map. Add GIS layers, upload satellite images, and track construction progress geographically."
      />
      <div className="flex gap-2 border-b border-border">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`px-4 py-2 font-medium text-sm transition-colors ${
              activeTab === tab.id
                ? "border-b-2 border-blue-600 text-accent"
                : "text-text-secondary hover:text-text-primary"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="flex-1 overflow-auto">
        {activeTab === "map" && (
          <div>
            {geoJsonLoading ? (
              <div className="flex items-center justify-center h-96">
                <span className="text-text-muted">Loading map data...</span>
              </div>
            ) : geoJsonResponse?.data ? (
              <MapViewer geoJsonData={geoJsonResponse.data} />
            ) : (
              <div className="flex items-center justify-center h-96">
                <span className="text-text-muted">
                  No polygon data available
                </span>
              </div>
            )}
          </div>
        )}

        {activeTab === "layers" && (
          <div>
            {layersResponse?.data && layersResponse.data.length > 0 ? (
              <GisLayerList projectId={projectId} layers={layersResponse.data} />
            ) : (
              <div className="flex items-center justify-center h-96">
                <span className="text-text-muted">No layers available</span>
              </div>
            )}
          </div>
        )}

        {activeTab === "satellite" && (
          <div className="space-y-4">
            {/* Run Ingestion panel: picks a date window and fires the
                satellite ingestion pipeline on-demand. Useful for demos and
                initial backfills. Nightly runs are handled by
                SatelliteIngestionScheduler on the backend. */}
            <div className="rounded-lg border border-border bg-surface/50 p-4">
              <div className="flex flex-wrap items-end gap-3">
                <label className="text-sm">
                  <span className="block text-text-secondary mb-1">From</span>
                  <input
                    type="date"
                    value={ingestFrom}
                    onChange={(e) => setIngestFrom(e.target.value)}
                    max={ingestTo}
                    className="rounded-md border border-border bg-surface px-2 py-1.5 text-sm text-text-primary"
                  />
                </label>
                <label className="text-sm">
                  <span className="block text-text-secondary mb-1">To</span>
                  <input
                    type="date"
                    value={ingestTo}
                    onChange={(e) => setIngestTo(e.target.value)}
                    min={ingestFrom}
                    className="rounded-md border border-border bg-surface px-2 py-1.5 text-sm text-text-primary"
                  />
                </label>
                <Button
                  onClick={() => ingestMutation.mutate()}
                  disabled={ingestMutation.isPending}
                >
                  {ingestMutation.isPending ? "Running ingestion…" : "Run Ingestion"}
                </Button>
                {ingestMutation.isError && (
                  <span className="text-sm text-danger">
                    {(ingestMutation.error as Error)?.message ?? "Ingestion failed"}
                  </span>
                )}
              </div>
              {lastIngestResult && (
                <div className="mt-3 text-sm text-text-secondary">
                  Last run (<span className="text-text-primary">{lastIngestResult.vendorId}</span>):{" "}
                  <span className="text-text-primary">{lastIngestResult.scenesFetched}</span> fetched,{" "}
                  <span className="text-text-primary">{lastIngestResult.scenesSkippedDedupe}</span> dedup-skipped,{" "}
                  <span className="text-text-primary">{lastIngestResult.snapshotsCreated}</span> snapshots queued
                  {lastIngestResult.errors.length > 0 && (
                    <span className="text-danger"> · {lastIngestResult.errors.length} errors</span>
                  )}
                </div>
              )}
            </div>

            {satelliteImagesResponse?.data && satelliteImagesResponse.data.length > 0 ? (
              <SatelliteImageGallery projectId={projectId} images={satelliteImagesResponse.data} />
            ) : (
              <div className="flex items-center justify-center h-40 rounded-lg border border-dashed border-border">
                <span className="text-text-muted">
                  No satellite images yet. Run ingestion or upload manually.
                </span>
              </div>
            )}
          </div>
        )}

        {activeTab === "progress" && (
          <div>
            {varianceResponse?.data && varianceResponse.data.length > 0 ? (
              <ProgressVarianceTable projectId={projectId} variance={varianceResponse.data} />
            ) : (
              <div className="flex items-center justify-center h-96">
                <span className="text-text-muted">
                  No progress data available
                </span>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
