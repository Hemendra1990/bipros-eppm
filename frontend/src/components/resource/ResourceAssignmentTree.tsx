"use client";

import React, { useState, useMemo } from "react";
import { ChevronRight, ChevronDown, List, FolderTree, Layers } from "lucide-react";
import { formatDefaultCurrency } from "@/lib/hooks/useCurrency";

export interface AssignmentRow {
  id: string;
  activityId: string;
  resourceId: string | null;
  projectId: string;
  resourceName: string;
  activityName: string;
  effectiveRoleId: string | null;
  effectiveRoleName: string | null;
  /** Productivity unit of the effective role (e.g. "Day", "Bag"). Null when role is unknown.
   * Used to decide whether activity / type rollups can sum units across mixed assignments. */
  unit: string | null;
  plannedUnits: number;
  actualUnits: number;
  remainingUnits: number;
  rateType: string;
  plannedCost: number;
  actualCost: number;
  remainingCost: number;
}

export interface ResourceTypeInfo {
  id: string;
  /** Type code from the new model (MANPOWER / EQUIPMENT / MATERIAL or custom). */
  resourceTypeCode: string;
}

interface RollupSums {
  childCount: number;
  /** null when units aren't comparable across children (e.g., an activity rolling up multiple roles). */
  plannedUnits: number | null;
  actualUnits: number | null;
  remainingUnits: number | null;
  plannedCost: number;
  actualCost: number;
  remainingCost: number;
}

interface TreeNode {
  id: string;
  code: string;
  name: string;
  children?: TreeNode[];
  assignment?: AssignmentRow;
  rollup?: RollupSums;
}

const UNASSIGNED_ROLE_KEY = "__unassigned__";

function sumRows(rows: AssignmentRow[]): Omit<RollupSums, "childCount"> {
  const acc = { plannedUnits: 0, actualUnits: 0, remainingUnits: 0, plannedCost: 0, actualCost: 0, remainingCost: 0 };
  for (const r of rows) {
    acc.plannedUnits += r.plannedUnits ?? 0;
    acc.actualUnits += r.actualUnits ?? 0;
    acc.remainingUnits += r.remainingUnits ?? 0;
    acc.plannedCost += r.plannedCost ?? 0;
    acc.actualCost += r.actualCost ?? 0;
    acc.remainingCost += r.remainingCost ?? 0;
  }
  return acc;
}

/** True when every row has a non-null unit and they all match. False on mixed or unknown units. */
function shareSameUnit(rows: AssignmentRow[]): boolean {
  if (rows.length === 0) return false;
  const first = rows[0].unit;
  if (first == null) return false;
  return rows.every((r) => r.unit === first);
}

interface Props {
  assignments: AssignmentRow[];
  viewMode: "activity" | "resourceType";
  resources: ResourceTypeInfo[];
  onRowClick?: (row: AssignmentRow) => void;
  selectedId?: string | null;
}

function formatResourceType(type: string): string {
  // Friendly labels for the seeded 3M codes. Custom types fall through.
  switch (type) {
    case "MANPOWER":
    case "LABOR":
      return "Manpower";
    case "EQUIPMENT":
    case "NONLABOR":
      return "Equipment";
    case "MATERIAL":
      return "Material";
    default:
      return type;
  }
}

/**
 * Three-level tree: Activity → Role → Resource.
 *
 * Activity rows roll up only costs (units across mixed roles aren't comparable — bricks vs cement
 * bags vs labour days). Role rows roll up both units and costs (same role, same productivity unit).
 */
function buildActivityTree(assignments: AssignmentRow[]): TreeNode[] {
  const byActivity = new Map<string, AssignmentRow[]>();
  for (const a of assignments) {
    const list = byActivity.get(a.activityId) ?? [];
    list.push(a);
    byActivity.set(a.activityId, list);
  }

  const sortedActivities = Array.from(byActivity.entries()).sort((a, b) =>
    a[1][0].activityName.localeCompare(b[1][0].activityName)
  );

  return sortedActivities.map(([activityId, activityRows]) => {
    const byRole = new Map<string, AssignmentRow[]>();
    for (const r of activityRows) {
      const key = r.effectiveRoleId ?? UNASSIGNED_ROLE_KEY;
      const list = byRole.get(key) ?? [];
      list.push(r);
      byRole.set(key, list);
    }

    const roleNodes: TreeNode[] = Array.from(byRole.entries())
      .sort(([keyA, rowsA], [keyB, rowsB]) => {
        if (keyA === UNASSIGNED_ROLE_KEY) return 1;
        if (keyB === UNASSIGNED_ROLE_KEY) return -1;
        const aName = rowsA[0].effectiveRoleName ?? "Unassigned role";
        const bName = rowsB[0].effectiveRoleName ?? "Unassigned role";
        return aName.localeCompare(bName);
      })
      .map(([roleKey, roleRows]) => {
        const roleName = roleRows[0].effectiveRoleName ?? "Unassigned role";
        const sums = sumRows(roleRows);
        return {
          id: `activity-${activityId}-role-${roleKey}`,
          code: roleName,
          name: roleName,
          rollup: { childCount: roleRows.length, ...sums },
          children: roleRows
            .slice()
            .sort((a, b) => a.resourceName.localeCompare(b.resourceName))
            .map((row) => ({
              id: row.id,
              code: row.resourceName,
              name: row.resourceName,
              assignment: row,
            })),
        };
      });

    const activitySums = sumRows(activityRows);
    const sameUnit = shareSameUnit(activityRows);
    return {
      id: `activity-${activityId}`,
      code: activityRows[0]?.activityName ?? activityId,
      name: activityRows[0]?.activityName ?? activityId,
      rollup: {
        childCount: activityRows.length,
        plannedUnits: sameUnit ? activitySums.plannedUnits : null,
        actualUnits: sameUnit ? activitySums.actualUnits : null,
        remainingUnits: sameUnit ? activitySums.remainingUnits : null,
        plannedCost: activitySums.plannedCost,
        actualCost: activitySums.actualCost,
        remainingCost: activitySums.remainingCost,
      },
      children: roleNodes,
    };
  });
}

function buildResourceTypeTree(
  assignments: AssignmentRow[],
  resources: ResourceTypeInfo[]
): TreeNode[] {
  const resourceMap = new Map(resources.map((r) => [r.id, r.resourceTypeCode]));
  const grouped = new Map<string, AssignmentRow[]>();

  for (const a of assignments) {
    const type = (a.resourceId ? resourceMap.get(a.resourceId) : null) ?? "UNKNOWN";
    const list = grouped.get(type) ?? [];
    list.push(a);
    grouped.set(type, list);
  }

  // 3M display order: Manpower → Material → Equipment (with legacy aliases tolerated)
  const typeOrder = ["MANPOWER", "LABOR", "MATERIAL", "EQUIPMENT", "NONLABOR"];
  const sorted = Array.from(grouped.entries()).sort((a, b) => {
    const idxA = typeOrder.indexOf(a[0]);
    const idxB = typeOrder.indexOf(b[0]);
    if (idxA !== -1 && idxB !== -1) return idxA - idxB;
    if (idxA !== -1) return -1;
    if (idxB !== -1) return 1;
    return a[0].localeCompare(b[0]);
  });

  return sorted.map(([type, rows]) => {
    const sums = sumRows(rows);
    const sameUnit = shareSameUnit(rows);
    return {
      id: `type-${type}`,
      code: type,
      name: formatResourceType(type),
      rollup: {
        childCount: rows.length,
        plannedUnits: sameUnit ? sums.plannedUnits : null,
        actualUnits: sameUnit ? sums.actualUnits : null,
        remainingUnits: sameUnit ? sums.remainingUnits : null,
        plannedCost: sums.plannedCost,
        actualCost: sums.actualCost,
        remainingCost: sums.remainingCost,
      },
      children: rows
        .slice()
        .sort((a, b) => a.resourceName.localeCompare(b.resourceName))
        .map((row) => ({
          id: row.id,
          code: row.resourceName,
          name: row.resourceName,
          assignment: row,
        })),
    };
  });
}

function num(n: number | null | undefined): string {
  if (n == null) return "";
  return Number(n).toFixed(2);
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

  // What to show in each numeric column. For a leaf, use the assignment row directly. For a
  // group, use its rollup — units may be null at the activity / type level (mixed-unit children).
  const plannedUnits = node.assignment?.plannedUnits ?? node.rollup?.plannedUnits ?? null;
  const actualUnits = node.assignment?.actualUnits ?? node.rollup?.actualUnits ?? null;
  const remainingUnits = node.assignment?.remainingUnits ?? node.rollup?.remainingUnits ?? null;
  const plannedCost = node.assignment?.plannedCost ?? node.rollup?.plannedCost ?? null;
  const actualCost = node.assignment?.actualCost ?? node.rollup?.actualCost ?? null;
  const remainingCost = node.assignment?.remainingCost ?? node.rollup?.remainingCost ?? null;

  return (
    <div className="border-b border-border/50 last:border-b-0">
      <div
        className={`grid grid-cols-[minmax(220px,2fr)_minmax(180px,2fr)_90px_90px_90px_80px_110px_110px_110px] gap-2 items-center py-2.5 px-3 text-sm transition-colors ${
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
              {node.rollup?.childCount ?? node.children?.length}
            </span>
          )}
        </div>

        {/* Activity column — for activity-mode groups it's redundant (the group IS the activity);
            for resource-type leaves it tells you which activity the row belongs to. */}
        <div className="truncate text-text-secondary">
          {node.assignment?.activityName ?? ""}
        </div>

        {/* Planned Units */}
        <div className={`text-right ${isGroup ? "font-medium text-text-primary" : "text-text-secondary"}`}>
          {num(plannedUnits)}
        </div>

        {/* Actual Units */}
        <div className={`text-right ${isGroup ? "font-medium text-text-primary" : "text-text-secondary"}`}>
          {num(actualUnits)}
        </div>

        {/* Remaining Units */}
        <div className={`text-right ${isGroup ? "font-medium text-text-primary" : "text-text-secondary"}`}>
          {num(remainingUnits)}
        </div>

        {/* Rate Type */}
        <div className="text-center text-text-secondary">
          {node.assignment?.rateType ?? ""}
        </div>

        {/* Planned Cost */}
        <div className={`text-right ${isGroup ? "font-medium text-text-primary" : "text-text-secondary"}`}>
          {plannedCost != null ? formatDefaultCurrency(plannedCost) : ""}
        </div>

        {/* Actual Cost */}
        <div className={`text-right ${isGroup ? "font-medium text-text-primary" : "text-text-secondary"}`}>
          {actualCost != null ? formatDefaultCurrency(actualCost) : ""}
        </div>

        {/* Remaining Cost */}
        <div className={`text-right ${isGroup ? "font-medium text-text-primary" : "text-text-secondary"}`}>
          {remainingCost != null ? formatDefaultCurrency(remainingCost) : ""}
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

  // Default: only the top-level groups (activities or types) are open. In activity mode, the
  // role sub-groups underneath stay collapsed so the user sees one summary row per role and
  // can drill into individuals on demand.
  const [expanded, setExpanded] = useState<Record<string, boolean>>(() => {
    const initial: Record<string, boolean> = {};
    for (const node of tree) {
      if (node.children && node.children.length > 0) initial[node.id] = true;
    }
    return initial;
  });

  React.useEffect(() => {
    const initial: Record<string, boolean> = {};
    for (const node of tree) {
      if (node.children && node.children.length > 0) initial[node.id] = true;
    }
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

  const firstColLabel =
    viewMode === "activity" ? "Activity / Role / Resource" : "Resource Type / Resource";

  return (
    <div className="rounded-xl border border-border bg-surface/50 shadow-xl overflow-hidden">
      {/* Header */}
      <div className="grid grid-cols-[minmax(220px,2fr)_minmax(180px,2fr)_90px_90px_90px_80px_110px_110px_110px] gap-2 items-center py-3 px-3 text-xs font-semibold uppercase tracking-wider text-text-secondary bg-surface/80 border-b border-border/50">
        <div className="pl-3">{firstColLabel}</div>
        <div>Activity</div>
        <div className="text-right">Planned</div>
        <div className="text-right">Actual</div>
        <div className="text-right">Remaining</div>
        <div className="text-center">Rate</div>
        <div className="text-right">Planned Cost</div>
        <div className="text-right">Actual Cost</div>
        <div className="text-right">Remaining Cost</div>
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
