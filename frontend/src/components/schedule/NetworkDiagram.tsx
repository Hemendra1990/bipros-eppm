"use client";

import React, { useMemo } from "react";
import type { ActivityResponse } from "@/lib/api/activityApi";

interface NetworkDiagramProps {
  activities: ActivityResponse[];
  relationships?: Array<{
    predecessorActivityId: string;
    successorActivityId: string;
    relationshipType: string;
  }>;
}

interface ActivityNode {
  activity: ActivityResponse;
  depth: number;
  x: number;
  y: number;
}

export function NetworkDiagram({ activities, relationships = [] }: NetworkDiagramProps) {
  const { nodes, depthLevels } = useMemo(() => {
    return calculateLayout(activities, relationships);
  }, [activities, relationships]);

  if (activities.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-slate-700 py-12 text-center">
        <p className="text-slate-400">No activities to display</p>
        <p className="mt-2 text-sm text-slate-500">Create activities and add dependencies to see the network diagram.</p>
      </div>
    );
  }

  // Calculate SVG dimensions
  const maxDepth = Math.max(...nodes.map((n) => n.depth), 0);
  const maxActivitiesInDepth = Math.max(
    ...Object.values(depthLevels).map((activities) => activities.length),
    1
  );

  const colWidth = 250;
  const rowHeight = 120;
  const padding = 40;
  const svgWidth = (maxDepth + 1) * colWidth + 2 * padding;
  const svgHeight = maxActivitiesInDepth * rowHeight + 2 * padding;

  // Create node map for easy lookup
  const nodeMap = new Map(nodes.map((n) => [n.activity.id, n]));

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-white">Activity Network Diagram</h2>

      <div className="overflow-auto rounded-lg border border-slate-800 bg-slate-900/50 p-4">
        <svg width={svgWidth} height={svgHeight} className="bg-slate-900/50">
          <defs>
            {/* Arrowhead marker for relationship lines */}
            <marker
              id="arrowhead"
              markerWidth="10"
              markerHeight="10"
              refX="9"
              refY="3"
              orient="auto"
            >
              <polygon points="0 0, 10 3, 0 6" fill="#94a3b8" />
            </marker>
            <marker
              id="arrowhead-critical"
              markerWidth="10"
              markerHeight="10"
              refX="9"
              refY="3"
              orient="auto"
            >
              <polygon points="0 0, 10 3, 0 6" fill="#dc2626" />
            </marker>
          </defs>

          {/* Draw relationship lines */}
          {relationships.map((rel, index) => {
            const predNode = nodeMap.get(rel.predecessorActivityId);
            const succNode = nodeMap.get(rel.successorActivityId);

            if (!predNode || !succNode) return null;

            const x1 = predNode.x + 120; // Right edge of predecessor box
            const y1 = predNode.y + 60; // Center height of predecessor box
            const x2 = succNode.x - 10; // Left edge of successor box
            const y2 = succNode.y + 60;

            const isCritical = predNode.activity.totalFloat === 0 && succNode.activity.totalFloat === 0;

            return (
              <g key={`rel-${index}`}>
                <line
                  x1={x1}
                  y1={y1}
                  x2={x2}
                  y2={y2}
                  stroke={isCritical ? "#dc2626" : "#64748b"}
                  strokeWidth="2"
                  markerEnd={isCritical ? "url(#arrowhead-critical)" : "url(#arrowhead)"}
                />
                {rel.relationshipType && (
                  <text
                    x={(x1 + x2) / 2}
                    y={(y1 + y2) / 2 - 5}
                    fontSize="11"
                    fill="#94a3b8"
                    textAnchor="middle"
                  >
                    {rel.relationshipType}
                  </text>
                )}
              </g>
            );
          })}

          {/* Draw activity boxes */}
          {nodes.map((node) => {
            const isCritical = node.activity.totalFloat === 0;
            const boxWidth = 240;
            const boxHeight = 100;

            return (
              <g key={node.activity.id}>
                {/* Activity box */}
                <rect
                  x={node.x}
                  y={node.y}
                  width={boxWidth}
                  height={boxHeight}
                  fill="#1e293b"
                  stroke={isCritical ? "#dc2626" : "#3b82f6"}
                  strokeWidth={isCritical ? "3" : "2"}
                  rx="4"
                />

                {/* Activity Code (header) */}
                <text
                  x={node.x + 8}
                  y={node.y + 18}
                  fontSize="12"
                  fontWeight="bold"
                  fill="#cbd5e1"
                  className="font-mono"
                >
                  {node.activity.code}
                </text>

                {/* Activity Name */}
                <text
                  x={node.x + 8}
                  y={node.y + 35}
                  fontSize="11"
                  fill="#374151"
                  className="max-w-xs truncate"
                >
                  {node.activity.name.substring(0, 30)}
                  {node.activity.name.length > 30 ? "..." : ""}
                </text>

                {/* Divider line */}
                <line
                  x1={node.x}
                  y1={node.y + 42}
                  x2={node.x + boxWidth}
                  y2={node.y + 42}
                  stroke="#1e293b"
                  strokeWidth="1"
                />

                {/* Schedule info: ES / EF (top row) */}
                <text x={node.x + 8} y={node.y + 58} fontSize="9" fill="#6b7280">
                  ES
                </text>
                <text
                  x={node.x + 8}
                  y={node.y + 70}
                  fontSize="10"
                  fontWeight="bold"
                  fill="#111827"
                  className="font-mono"
                >
                  {formatDate(node.activity.earlyStartDate)}
                </text>

                <text x={node.x + 65} y={node.y + 58} fontSize="9" fill="#6b7280">
                  EF
                </text>
                <text
                  x={node.x + 65}
                  y={node.y + 70}
                  fontSize="10"
                  fontWeight="bold"
                  fill="#111827"
                  className="font-mono"
                >
                  {formatDate(node.activity.earlyFinishDate)}
                </text>

                <text x={node.x + 122} y={node.y + 58} fontSize="9" fill="#6b7280">
                  Float
                </text>
                <text
                  x={node.x + 122}
                  y={node.y + 70}
                  fontSize="10"
                  fontWeight={isCritical ? "bold" : "normal"}
                  fill={isCritical ? "#dc2626" : "#111827"}
                  className="font-mono"
                >
                  {(node.activity.totalFloat ?? 0).toFixed(1)}
                </text>

                {/* Schedule info: LS / LF (bottom row) */}
                <text x={node.x + 8} y={node.y + 82} fontSize="9" fill="#6b7280">
                  LS
                </text>
                <text
                  x={node.x + 8}
                  y={node.y + 94}
                  fontSize="10"
                  fontWeight="bold"
                  fill="#111827"
                  className="font-mono"
                >
                  {formatDate(node.activity.lateStartDate)}
                </text>

                <text x={node.x + 65} y={node.y + 82} fontSize="9" fill="#6b7280">
                  LF
                </text>
                <text
                  x={node.x + 65}
                  y={node.y + 94}
                  fontSize="10"
                  fontWeight="bold"
                  fill="#111827"
                  className="font-mono"
                >
                  {formatDate(node.activity.lateFinishDate)}
                </text>
              </g>
            );
          })}
        </svg>
      </div>

      {/* Legend */}
      <div className="flex flex-wrap gap-6 rounded-lg border border-slate-800 bg-slate-900/80 p-4">
        <div className="flex items-center gap-2">
          <div className="h-4 w-12 border-2 border-blue-500 rounded" />
          <span className="text-sm text-slate-300">Normal Activity</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="h-4 w-12 border-3 border-red-600 rounded" />
          <span className="text-sm text-slate-300">Critical Activity</span>
        </div>
      </div>
    </div>
  );
}

/**
 * Calculate layout using topological sort
 * Groups activities by depth (column), assigns positions
 */
function calculateLayout(
  activities: ActivityResponse[],
  relationships: Array<{
    predecessorActivityId: string;
    successorActivityId: string;
    relationshipType: string;
  }>
): {
  nodes: ActivityNode[];
  depthLevels: Record<number, ActivityResponse[]>;
} {
  // Build adjacency list
  const predecessors = new Map<string, Set<string>>();
  const successors = new Map<string, Set<string>>();

  activities.forEach((a) => {
    predecessors.set(a.id, new Set());
    successors.set(a.id, new Set());
  });

  relationships.forEach((rel) => {
    predecessors.get(rel.successorActivityId)?.add(rel.predecessorActivityId);
    successors.get(rel.predecessorActivityId)?.add(rel.successorActivityId);
  });

  // Calculate depth for each activity using topological sort
  const depth = new Map<string, number>();
  const visited = new Set<string>();
  const visiting = new Set<string>();

  function calculateDepthDFS(activityId: string): number {
    if (depth.has(activityId)) {
      return depth.get(activityId)!;
    }

    if (visiting.has(activityId)) {
      // Cycle detected, assign depth 0
      return 0;
    }

    visiting.add(activityId);

    const preds = predecessors.get(activityId) || new Set();
    if (preds.size === 0) {
      depth.set(activityId, 0);
    } else {
      let maxPredDepth = 0;
      for (const pred of preds) {
        maxPredDepth = Math.max(maxPredDepth, calculateDepthDFS(pred));
      }
      depth.set(activityId, maxPredDepth + 1);
    }

    visiting.delete(activityId);
    return depth.get(activityId)!;
  }

  activities.forEach((a) => {
    if (!visited.has(a.id)) {
      calculateDepthDFS(a.id);
      visited.add(a.id);
    }
  });

  // Group activities by depth
  const depthLevels: Record<number, ActivityResponse[]> = {};
  activities.forEach((a) => {
    const d = depth.get(a.id) || 0;
    if (!depthLevels[d]) {
      depthLevels[d] = [];
    }
    depthLevels[d].push(a);
  });

  // Sort each depth level by name for consistent ordering
  Object.values(depthLevels).forEach((level) => {
    level.sort((a, b) => a.code.localeCompare(b.code));
  });

  // Create nodes with positions
  const colWidth = 250;
  const rowHeight = 120;
  const padding = 40;

  const nodes: ActivityNode[] = [];
  Object.entries(depthLevels).forEach(([d, activitiesInDepth]) => {
    const depthNum = parseInt(d);
    const x = padding + depthNum * colWidth;

    activitiesInDepth.forEach((activity, index) => {
      const y = padding + index * rowHeight;
      nodes.push({
        activity,
        depth: depthNum,
        x,
        y,
      });
    });
  });

  return { nodes, depthLevels };
}

function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return "N/A";
  const date = new Date(dateStr);
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "2-digit",
  });
}
