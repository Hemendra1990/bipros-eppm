"use client";

import React, { useState, useMemo } from "react";
import { ChevronRight, ChevronDown, List, FolderTree, Layers } from "lucide-react";
import { formatDefaultCurrency } from "@/lib/hooks/useCurrency";

export interface AssignmentRow {
  id: string;
  activityId: string;
  resourceId: string;
  projectId: string;
  resourceName: string;
  activityName: string;
  plannedUnits: number;
  actualUnits: number;
  remainingUnits: number;
  rateType: string;
  plannedCost: number;
  actualCost: number;
}

export interface ResourceTypeInfo {
  id: string;
  resourceType: "LABOR" | "NONLABOR" | "MATERIAL";
}

interface TreeNode {
  id: string;
  code: string;
  name: string;
  children?: TreeNode[];
  assignment?: AssignmentRow;
}

interface Props {
  assignments: AssignmentRow[];
  viewMode: "activity" | "resourceType";
  resources: ResourceTypeInfo[];
  onRowClick?: (row: AssignmentRow) => void;
  selectedId?: string | null;
}

function formatResourceType(type: string): string {
  // Default labels for the seeded 3M base categories. Custom types come pre-formatted from the
  // backend (the def's name) — those bypass this helper because the resource carries
  // resourceTypeName directly.
  switch (type) {
    case "LABOR":
      return "Manpower";
    case "NONLABOR":
      return "Machine";
    case "MATERIAL":
      return "Material";
    default:
      return type;
  }
}

function buildActivityTree(assignments: AssignmentRow[]): TreeNode[] {
  const grouped = new Map<string, AssignmentRow[]>();
  for (const a of assignments) {
    const list = grouped.get(a.activityId) ?? [];
    list.push(a);
    grouped.set(a.activityId, list);
  }

  const sorted = Array.from(grouped.entries()).sort((a, b) =>
    a[1][0].activityName.localeCompare(b[1][0].activityName)
  );

  return sorted.map(([activityId, rows]) => ({
    id: `activity-${activityId}`,
    code: rows[0]?.activityName ?? activityId,
    name: rows[0]?.activityName ?? activityId,
    children: rows
      .slice()
      .sort((a, b) => a.resourceName.localeCompare(b.resourceName))
      .map((row) => ({
        id: row.id,
        code: row.resourceName,
        name: row.resourceName,
        assignment: row,
      })),
  }));
}

function buildResourceTypeTree(
  assignments: AssignmentRow[],
  resources: ResourceTypeInfo[]
): TreeNode[] {
  const resourceMap = new Map(resources.map((r) => [r.id, r.resourceType]));
  const grouped = new Map<string, AssignmentRow[]>();

  for (const a of assignments) {
    const type = resourceMap.get(a.resourceId) ?? "UNKNOWN";
    const list = grouped.get(type) ?? [];
    list.push(a);
    grouped.set(type, list);
  }

  // 3M display order: Manpower (LABOR) → Material → Machine (NONLABOR)
  const typeOrder = ["LABOR", "MATERIAL", "NONLABOR"];
  const sorted = Array.from(grouped.entries()).sort((a, b) => {
    const idxA = typeOrder.indexOf(a[0]);
    const idxB = typeOrder.indexOf(b[0]);
    if (idxA !== -1 && idxB !== -1) return idxA - idxB;
    if (idxA !== -1) return -1;
    if (idxB !== -1) return 1;
    return a[0].localeCompare(b[0]);
  });

  return sorted.map(([type, rows]) => ({
    id: `type-${type}`,
    code: type,
    name: formatResourceType(type),
    children: rows
      .slice()
      .sort((a, b) => a.resourceName.localeCompare(b.resourceName))
      .map((row) => ({
        id: row.id,
        code: row.resourceName,
        name: row.resourceName,
        assignment: row,
      })),
  }));
}

function TreeRow({
  node,
  level,
  expanded,
  toggle,
  onRowClick,
  selectedId,
  viewMode,
}: {
  node: TreeNode;
  level: number;
  expanded: Record<string, boolean>;
  toggle: (id: string) => void;
  onRowClick?: (row: AssignmentRow) => void;
  selectedId?: string | null;
  viewMode: "activity" | "resourceType";
}) {
  const isGroup = !!node.children && node.children.length > 0;
  const isSelected = !isGroup && node.assignment && node.assignment.id === selectedId;
  const indent = level * 20;

  return (
    <div className="border-b border-border/50 last:border-b-0">
      <div
        className={`grid grid-cols-[minmax(180px,2fr)_minmax(180px,2fr)_90px_90px_90px_80px_100px_100px] gap-2 items-center py-2.5 px-3 text-sm transition-colors ${
          isSelected
            ? "bg-accent/10"
            : !isGroup
              ? "hover:bg-surface-hover/30 cursor-pointer"
              : "hover:bg-surface-hover/20"
        }`}
        style={{ paddingLeft: `${indent + 12}px` }}
        onClick={() => {
          if (!isGroup && node.assignment) {
            onRowClick?.(node.assignment);
          }
        }}
      >
        {/* Name column */}
        <div className="flex items-center gap-1.5 min-w-0">
          {isGroup ? (
            <button
              onClick={(e) => {
                e.stopPropagation();
                toggle(node.id);
              }}
              className="p-0.5 hover:bg-surface-hover rounded shrink-0"
            >
              {expanded[node.id] ? (
                <ChevronDown size={16} className="text-text-muted" />
              ) : (
                <ChevronRight size={16} className="text-text-muted" />
              )}
            </button>
          ) : (
            <div className="w-[22px] shrink-0" />
          )}
          <span className={`truncate ${isGroup ? "font-semibold text-text-primary" : "text-text-secondary"}`}>
            {node.name}
          </span>
          {isGroup && (
            <span className="shrink-0 ml-1 text-xs text-text-muted bg-surface-hover px-1.5 py-0.5 rounded-full">
              {node.children?.length}
            </span>
          )}
        </div>

        {/* Activity column */}
        <div className="truncate text-text-secondary">
          {node.assignment?.activityName ?? (viewMode === "activity" ? "" : "")}
        </div>

        {/* Planned Units */}
        <div className="text-right text-text-secondary">
          {node.assignment ? Number(node.assignment.plannedUnits).toFixed(2) : ""}
        </div>

        {/* Actual Units */}
        <div className="text-right text-text-secondary">
          {node.assignment ? Number(node.assignment.actualUnits).toFixed(2) : ""}
        </div>

        {/* Remaining Units */}
        <div className="text-right text-text-secondary">
          {node.assignment ? Number(node.assignment.remainingUnits).toFixed(2) : ""}
        </div>

        {/* Rate Type */}
        <div className="text-center text-text-secondary">
          {node.assignment?.rateType ?? ""}
        </div>

        {/* Planned Cost */}
        <div className="text-right text-text-secondary">
          {node.assignment ? formatDefaultCurrency(node.assignment.plannedCost) : ""}
        </div>

        {/* Actual Cost */}
        <div className="text-right text-text-secondary">
          {node.assignment ? formatDefaultCurrency(node.assignment.actualCost) : ""}
        </div>
      </div>

      {isGroup &&
        expanded[node.id] &&
        node.children?.map((child) => (
          <TreeRow
            key={child.id}
            node={child}
            level={level + 1}
            expanded={expanded}
            toggle={toggle}
            onRowClick={onRowClick}
            selectedId={selectedId}
            viewMode={viewMode}
          />
        ))}
    </div>
  );
}

export function ResourceAssignmentTree({
  assignments,
  viewMode,
  resources,
  onRowClick,
  selectedId,
}: Props) {
  const tree = useMemo(() => {
    if (viewMode === "activity") {
      return buildActivityTree(assignments);
    }
    return buildResourceTypeTree(assignments, resources);
  }, [assignments, viewMode, resources]);

  const [expanded, setExpanded] = useState<Record<string, boolean>>(() => {
    const initial: Record<string, boolean> = {};
    const expandAll = (nodes: TreeNode[]) => {
      for (const node of nodes) {
        if (node.children && node.children.length > 0) {
          initial[node.id] = true;
          expandAll(node.children);
        }
      }
    };
    expandAll(tree);
    return initial;
  });

  // Reset expansion when tree changes
  React.useEffect(() => {
    const initial: Record<string, boolean> = {};
    const expandAll = (nodes: TreeNode[]) => {
      for (const node of nodes) {
        if (node.children && node.children.length > 0) {
          initial[node.id] = true;
          expandAll(node.children);
        }
      }
    };
    expandAll(tree);
    setExpanded(initial);
  }, [tree]);

  const toggle = React.useCallback((id: string) => {
    setExpanded((prev) => ({ ...prev, [id]: !prev[id] }));
  }, []);

  if (assignments.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-border py-12 text-center">
        <h3 className="text-lg font-medium text-text-primary">No Assignments</h3>
        <p className="mt-2 text-text-secondary">
          No resource assignments yet. Create one to get started.
        </p>
      </div>
    );
  }

  const firstColLabel = viewMode === "activity" ? "Activity / Resource" : "Resource Type / Resource";

  return (
    <div className="rounded-xl border border-border bg-surface/50 shadow-xl overflow-hidden">
      {/* Header */}
      <div className="grid grid-cols-[minmax(180px,2fr)_minmax(180px,2fr)_90px_90px_90px_80px_100px_100px] gap-2 items-center py-3 px-3 text-xs font-semibold uppercase tracking-wider text-text-secondary bg-surface/80 border-b border-border/50">
        <div className="pl-3">{firstColLabel}</div>
        <div>Activity</div>
        <div className="text-right">Planned</div>
        <div className="text-right">Actual</div>
        <div className="text-right">Remaining</div>
        <div className="text-center">Rate</div>
        <div className="text-right">Planned Cost</div>
        <div className="text-right">Actual Cost</div>
      </div>

      {/* Rows */}
      <div>
        {tree.map((node) => (
          <TreeRow
            key={node.id}
            node={node}
            level={0}
            expanded={expanded}
            toggle={toggle}
            onRowClick={onRowClick}
            selectedId={selectedId}
            viewMode={viewMode}
          />
        ))}
      </div>
    </div>
  );
}

export function ViewModeToggle({
  viewMode,
  onChange,
}: {
  viewMode: "flat" | "activity" | "resourceType";
  onChange: (mode: "flat" | "activity" | "resourceType") => void;
}) {
  const modes: { key: "flat" | "activity" | "resourceType"; label: string; icon: React.ReactNode }[] = [
    { key: "flat", label: "Flat List", icon: <List size={14} /> },
    { key: "activity", label: "By Activity", icon: <FolderTree size={14} /> },
    { key: "resourceType", label: "By Type", icon: <Layers size={14} /> },
  ];

  return (
    <div className="inline-flex rounded-lg border border-border bg-surface/60 p-0.5">
      {modes.map((m) => (
        <button
          key={m.key}
          onClick={() => onChange(m.key)}
          className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
            viewMode === m.key
              ? "bg-accent text-text-primary"
              : "text-text-secondary hover:bg-surface-hover/50 hover:text-text-primary"
          }`}
        >
          {m.icon}
          {m.label}
        </button>
      ))}
    </div>
  );
}
