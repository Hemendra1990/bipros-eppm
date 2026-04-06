"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useParams } from "next/navigation";
import { MapViewer } from "@/components/gis/MapViewer";
import { GisLayerList } from "@/components/gis/GisLayerList";
import { SatelliteImageGallery } from "@/components/gis/SatelliteImageGallery";
import { ProgressVarianceTable } from "@/components/gis/ProgressVarianceTable";
import { TabTip } from "@/components/common/TabTip";
import { gisApi } from "@/lib/api/gisApi";

type TabId = "map" | "layers" | "satellite" | "progress";

export default function GisViewerPage() {
  const params = useParams();
  const projectId = params.projectId as `${string}-${string}-${string}-${string}-${string}`;
  const [activeTab, setActiveTab] = useState<TabId>("map");

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
      <div className="flex gap-2 border-b border-slate-800">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`px-4 py-2 font-medium text-sm transition-colors ${
              activeTab === tab.id
                ? "border-b-2 border-blue-600 text-blue-400"
                : "text-slate-400 hover:text-white"
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
                <span className="text-slate-500">Loading map data...</span>
              </div>
            ) : geoJsonResponse?.data ? (
              <MapViewer geoJsonData={geoJsonResponse.data} />
            ) : (
              <div className="flex items-center justify-center h-96">
                <span className="text-slate-500">
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
                <span className="text-slate-500">No layers available</span>
              </div>
            )}
          </div>
        )}

        {activeTab === "satellite" && (
          <div>
            {satelliteImagesResponse?.data && satelliteImagesResponse.data.length > 0 ? (
              <SatelliteImageGallery projectId={projectId} images={satelliteImagesResponse.data} />
            ) : (
              <div className="flex items-center justify-center h-96">
                <span className="text-slate-500">
                  No satellite images available
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
                <span className="text-slate-500">
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
