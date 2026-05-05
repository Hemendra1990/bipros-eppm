"use client";

import { useMemo } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { SimpleTable } from "@/components/common/SimpleTable";
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
  const columns = useMemo<ColumnDef<GisLayer>[]>(
    () => [
      {
        header: "Name",
        accessorKey: "layerName",
        cell: ({ row }) => (
          <div className="text-text-primary">
            {row.original.layerName}
            {row.original.description && (
              <div className="text-xs text-text-muted">
                {row.original.description}
              </div>
            )}
          </div>
        ),
      },
      {
        header: "Type",
        accessorKey: "layerType",
        cell: ({ getValue }) => (
          <span className="text-text-secondary">
            {String(getValue()).replace(/_/g, " ")}
          </span>
        ),
      },
      {
        header: "Visible",
        accessorKey: "isVisible",
        cell: ({ getValue }) =>
          getValue() ? (
            <span className="text-green-400">✓</span>
          ) : (
            <span className="text-text-muted">—</span>
          ),
      },
      {
        header: "Opacity",
        accessorKey: "opacity",
        cell: ({ getValue }) => (
          <span className="text-text-secondary">
            {Math.round(Number(getValue()) * 100)}%
          </span>
        ),
      },
      {
        header: "Order",
        accessorKey: "sortOrder",
        cell: ({ getValue }) => (
          <span className="text-text-muted">{String(getValue())}</span>
        ),
      },
    ],
    []
  );

  return (
    <div className="space-y-3">
      <div className="text-xs text-text-muted">
        Read-only. Use the Map tab for interactive layer controls.
      </div>

      {layers.length === 0 ? (
        <div className="rounded-lg border border-border bg-surface/50 overflow-hidden p-4">
          <p className="text-text-muted text-sm">No layers configured</p>
        </div>
      ) : (
        <SimpleTable data={layers} columns={columns} sortable={false} className="rounded-lg border border-border bg-surface/50 overflow-hidden" />
      )}
    </div>
  );
}
