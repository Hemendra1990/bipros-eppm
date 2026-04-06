"use client";

import { GisLayer } from "@/lib/api/gisApi";
import { useState } from "react";

interface GisLayerListProps {
  projectId: string;
  layers: GisLayer[];
}

export function GisLayerList({ projectId, layers }: GisLayerListProps) {
  const [visibleLayers, setVisibleLayers] = useState<Set<string>>(
    new Set(layers.filter((l) => l.isVisible).map((l) => l.id.toString()))
  );

  const toggleLayer = (layerId: string) => {
    const newVisible = new Set(visibleLayers);
    if (newVisible.has(layerId)) {
      newVisible.delete(layerId);
    } else {
      newVisible.add(layerId);
    }
    setVisibleLayers(newVisible);
  };

  return (
    <div className="space-y-4">
      <div className="bg-slate-900/50 rounded-lg border border-slate-800 p-4">
        <h3 className="text-lg font-semibold text-white mb-4">
          GIS Layers
        </h3>

        {layers.length === 0 ? (
          <p className="text-slate-500 text-sm">No layers configured</p>
        ) : (
          <div className="space-y-3">
            {layers.map((layer) => (
              <div
                key={layer.id}
                className="flex items-center justify-between p-3 bg-slate-900/80 rounded border border-slate-800"
              >
                <div className="flex-1">
                  <input
                    type="checkbox"
                    checked={visibleLayers.has(layer.id.toString())}
                    onChange={() => toggleLayer(layer.id.toString())}
                    className="mr-3 cursor-pointer"
                  />
                  <div className="inline-block">
                    <p className="text-sm font-medium text-white">
                      {layer.layerName}
                    </p>
                    <p className="text-xs text-slate-500">
                      Type: {layer.layerType.replace(/_/g, " ")}
                    </p>
                    {layer.description && (
                      <p className="text-xs text-slate-400 mt-1">
                        {layer.description}
                      </p>
                    )}
                  </div>
                </div>
                <div className="text-right">
                  <div className="text-xs text-slate-500 mb-1">
                    Opacity: {Math.round(layer.opacity * 100)}%
                  </div>
                  <input
                    type="range"
                    min="0"
                    max="100"
                    defaultValue={Math.round(layer.opacity * 100)}
                    className="w-20 h-2 bg-slate-700/50 rounded cursor-pointer"
                    disabled
                  />
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
