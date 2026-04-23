"use client";

import { GisLayer } from "@/lib/api/gisApi";

interface GisLayerListProps {
  projectId: string;
  layers: GisLayer[];
}

/**
 * Read-only view of the raw GIS layer rows in the backend. Interactive
 * visibility/opacity controls live on the Map tab via LayerControlPanel; this
 * view is kept for admins who want to inspect the layer registry directly.
 */
export function GisLayerList({ layers }: GisLayerListProps) {
  return (
    <div className="space-y-3">
      <div className="text-xs text-text-muted">
        Read-only. Use the Map tab for interactive layer controls.
      </div>

      <div className="rounded-lg border border-border bg-surface/50 overflow-hidden">
        {layers.length === 0 ? (
          <p className="p-4 text-text-muted text-sm">No layers configured</p>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-surface/80 text-text-secondary text-xs uppercase">
              <tr>
                <th className="text-left px-3 py-2">Name</th>
                <th className="text-left px-3 py-2">Type</th>
                <th className="text-left px-3 py-2">Visible</th>
                <th className="text-left px-3 py-2">Opacity</th>
                <th className="text-left px-3 py-2">Order</th>
              </tr>
            </thead>
            <tbody>
              {layers.map((l) => (
                <tr
                  key={l.id as string}
                  className="border-t border-border"
                >
                  <td className="px-3 py-2 text-text-primary">
                    {l.layerName}
                    {l.description && (
                      <div className="text-xs text-text-muted">
                        {l.description}
                      </div>
                    )}
                  </td>
                  <td className="px-3 py-2 text-text-secondary">
                    {l.layerType.replace(/_/g, " ")}
                  </td>
                  <td className="px-3 py-2">
                    {l.isVisible ? (
                      <span className="text-green-400">✓</span>
                    ) : (
                      <span className="text-text-muted">—</span>
                    )}
                  </td>
                  <td className="px-3 py-2 text-text-secondary">
                    {Math.round(l.opacity * 100)}%
                  </td>
                  <td className="px-3 py-2 text-text-muted">{l.sortOrder}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
