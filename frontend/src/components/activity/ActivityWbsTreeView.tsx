"use client";

import { useState, useMemo } from "react";
import Link from "next/link";
import { ChevronRight, ChevronDown, FolderOpen } from "lucide-react";
import type { WbsNodeResponse } from "@/lib/types";
import type { ActivityResponse } from "@/lib/api/activityApi";
import { StatusBadge } from "@/components/common/StatusBadge";

interface TreeNode {
  id: string;
  code: string;
  name: string;
  type: "wbs" | "activity";
  children?: TreeNode[];
  activity?: ActivityResponse;
}

interface ActivityWbsTreeViewProps {
  wbsNodes: WbsNodeResponse[];
  activities: ActivityResponse[];
  projectId: string;
  progressEdit: Record<string, string>;
  setProgressEdit: React.Dispatch<React.SetStateAction<Record<string, string>>>;
  pendingId: string | null;
  progressMutationIsPending: boolean;
  onSaveProgress: (activity: ActivityResponse) => void;
  onStartActivity: (activity: ActivityResponse) => void;
  onCompleteActivity: (activity: ActivityResponse) => void;
}

function buildTree(wbsNodes: WbsNodeResponse[], activities: ActivityResponse[]): TreeNode[] {
  const activityMap = new Map<string, ActivityResponse[]>();
  for (const a of activities) {
    const list = activityMap.get(a.wbsNodeId) ?? [];
    list.push(a);
    activityMap.set(a.wbsNodeId, list);
  }

  function mapWbsNode(wbs: WbsNodeResponse): TreeNode {
    const childWbsNodes = wbs.children?.map(mapWbsNode) ?? [];
    const childActivities = (activityMap.get(wbs.id) ?? [])
      .slice()
      .sort((a, b) => a.code.localeCompare(b.code));

    const activityNodes: TreeNode[] = childActivities.map((activity) => ({
      id: `activity-${activity.id}`,
      code: activity.code,
      name: activity.name,
      type: "activity",
      activity,
    }));

    return {
      id: `wbs-${wbs.id}`,
      code: wbs.code,
      name: wbs.name,
      type: "wbs",
      children: [...childWbsNodes, ...activityNodes],
    };
  }

  return wbsNodes.map(mapWbsNode);
}

function hasVisibleContent(node: TreeNode): boolean {
  if (node.type === "activity") return true;
  if (!node.children || node.children.length === 0) return false;
  return node.children.some(hasVisibleContent);
}

function filterEmptyNodes(nodes: TreeNode[]): TreeNode[] {
  return nodes
    .filter(hasVisibleContent)
    .map((node) => {
      if (node.type === "activity") return node;
      return {
        ...node,
        children: node.children ? filterEmptyNodes(node.children) : undefined,
      };
    });
}

function TreeRow({
  node,
  depth,
  expanded,
  toggle,
  projectId,
  progressEdit,
  setProgressEdit,
  pendingId,
  progressMutationIsPending,
  onSaveProgress,
  onStartActivity,
  onCompleteActivity,
}: {
  node: TreeNode;
  depth: number;
  expanded: Record<string, boolean>;
  toggle: (id: string) => void;
  projectId: string;
  progressEdit: Record<string, string>;
  setProgressEdit: React.Dispatch<React.SetStateAction<Record<string, string>>>;
  pendingId: string | null;
  progressMutationIsPending: boolean;
  onSaveProgress: (activity: ActivityResponse) => void;
  onStartActivity: (activity: ActivityResponse) => void;
  onCompleteActivity: (activity: ActivityResponse) => void;
}) {
  const isWbs = node.type === "wbs";
  const hasChildren = node.children && node.children.length > 0;
  const isExpanded = expanded[node.id] ?? false;

  if (isWbs) {
    return (
      <>
        <tr className="hover:bg-surface-hover/30">
          <td className="px-4 py-3 text-sm" colSpan={9}>
            <div className="flex items-center gap-2" style={{ paddingLeft: `${depth * 24}px` }}>
              {hasChildren ? (
                <button
                  onClick={() => toggle(node.id)}
                  className="p-0.5 hover:bg-surface-hover rounded shrink-0"
                >
                  {isExpanded ? (
                    <ChevronDown size={16} className="text-text-muted" />
                  ) : (
                    <ChevronRight size={16} className="text-text-muted" />
                  )}
                </button>
              ) : (
                <div className="w-[22px] shrink-0" />
              )}
              <FolderOpen size={16} className="text-accent shrink-0" />
              <span className="font-semibold text-text-primary">{node.code}</span>
              <span className="text-text-secondary">{node.name}</span>
              {hasChildren && (
                <span className="ml-1 text-xs text-text-muted bg-surface-hover px-1.5 py-0.5 rounded-full">
                  {node.children?.length ?? 0}
                </span>
              )}
            </div>
          </td>
        </tr>
        {isExpanded &&
          hasChildren &&
          node.children?.map((child) => (
            <TreeRow
              key={child.id}
              node={child}
              depth={depth + 1}
              expanded={expanded}
              toggle={toggle}
              projectId={projectId}
              progressEdit={progressEdit}
              setProgressEdit={setProgressEdit}
              pendingId={pendingId}
              progressMutationIsPending={progressMutationIsPending}
              onSaveProgress={onSaveProgress}
              onStartActivity={onStartActivity}
              onCompleteActivity={onCompleteActivity}
            />
          ))}
      </>
    );
  }

  // Activity row
  const activity = node.activity!;
  const editing = progressEdit[activity.id] !== undefined;
  const busy = pendingId === activity.id && progressMutationIsPending;

  const canStart = (a: ActivityResponse) =>
    a.status === "NOT_STARTED" || (a.percentComplete ?? 0) === 0;
  const canComplete = (a: ActivityResponse) => (a.percentComplete ?? 0) < 100;

  return (
    <tr className="hover:bg-surface/80">
      <td className="px-4 py-4 text-sm">
        <div className="flex items-center" style={{ paddingLeft: `${depth * 24 + 24}px` }}>
          <span className="font-medium text-text-primary">{activity.code}</span>
        </div>
      </td>
      <td className="px-4 py-4 text-sm text-text-primary">{activity.name}</td>
      <td className="px-4 py-4 text-sm">
        <StatusBadge status={activity.status} />
      </td>
      <td className="px-4 py-4 text-sm text-text-secondary">
        {editing ? (
          <div className="flex items-center gap-1">
            <input
              type="number"
              min={0}
              max={100}
              step={1}
              autoFocus
              className="w-16 rounded-md border border-border bg-surface-hover px-2 py-1 text-sm text-text-primary"
              value={progressEdit[activity.id]}
              onChange={(e) =>
                setProgressEdit({ ...progressEdit, [activity.id]: e.target.value })
              }
              onKeyDown={(e) => {
                if (e.key === "Enter") onSaveProgress(activity);
                if (e.key === "Escape")
                  setProgressEdit((s) => {
                    const n = { ...s };
                    delete n[activity.id];
                    return n;
                  });
              }}
              disabled={busy}
            />
            <button
              type="button"
              onClick={() => onSaveProgress(activity)}
              disabled={busy}
              className="rounded-md bg-success px-2 py-1 text-xs text-text-primary hover:bg-success/80 disabled:opacity-60"
            >
              Save
            </button>
            <button
              type="button"
              onClick={() =>
                setProgressEdit((s) => {
                  const n = { ...s };
                  delete n[activity.id];
                  return n;
                })
              }
              className="text-xs text-text-secondary hover:text-text-primary"
            >
              Cancel
            </button>
          </div>
        ) : (
          <button
            type="button"
            onClick={() =>
              setProgressEdit({
                ...progressEdit,
                [activity.id]: String(activity.percentComplete ?? 0),
              })
            }
            className="rounded-md border border-border px-2 py-0.5 text-xs hover:bg-surface-hover"
            title="Click to edit % complete"
          >
            {activity.percentComplete ?? 0}%
          </button>
        )}
      </td>
      <td className="px-4 py-4 text-sm text-text-secondary">
        {activity.actualStartDate ||
          activity.earlyStartDate ||
          activity.plannedStartDate ||
          "-"}
      </td>
      <td className="px-4 py-4 text-sm text-text-secondary">
        {activity.actualFinishDate ||
          activity.earlyFinishDate ||
          activity.plannedFinishDate ||
          "-"}
      </td>
      <td className="px-4 py-4 text-sm text-text-secondary">
        {activity.remainingDuration || activity.duration || "-"} days
      </td>
      <td className="px-4 py-4 text-sm">
        {activity.totalFloat != null ? (
          <span
            className={`inline-block rounded-full px-3 py-1 text-xs font-semibold ${
              activity.totalFloat === 0
                ? "bg-danger/10 text-danger"
                : activity.totalFloat <= 5
                  ? "bg-warning/10 text-warning"
                  : "bg-success/10 text-success"
            }`}
          >
            {activity.totalFloat.toFixed(1)} days
          </span>
        ) : (
          "-"
        )}
      </td>
      <td className="px-4 py-4 text-right text-sm">
        <div className="flex justify-end gap-2">
          {canStart(activity) && (
            <button
              type="button"
              onClick={() => onStartActivity(activity)}
              disabled={busy}
              className="rounded-md bg-accent px-2 py-1 text-xs font-medium text-text-primary hover:bg-accent-hover disabled:opacity-60"
              title="Record actual start date as today"
            >
              Start
            </button>
          )}
          {canComplete(activity) && (
            <button
              type="button"
              onClick={() => onCompleteActivity(activity)}
              disabled={busy}
              className="rounded-md bg-success px-2 py-1 text-xs font-medium text-text-primary hover:bg-success/80 disabled:opacity-60"
              title="Mark 100% complete and set actual finish to today"
            >
              Complete
            </button>
          )}
          <Link
            href={`/projects/${projectId}/activities/${activity.id}`}
            className="rounded-md border border-border px-2 py-1 text-xs text-text-secondary hover:bg-surface-hover"
          >
            View
          </Link>
        </div>
      </td>
    </tr>
  );
}

export function ActivityWbsTreeView({
  wbsNodes,
  activities,
  projectId,
  progressEdit,
  setProgressEdit,
  pendingId,
  progressMutationIsPending,
  onSaveProgress,
  onStartActivity,
  onCompleteActivity,
}: ActivityWbsTreeViewProps) {
  const tree = useMemo(() => {
    const rawTree = buildTree(wbsNodes, activities);
    return filterEmptyNodes(rawTree);
  }, [wbsNodes, activities]);

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

  const toggle = (id: string) => {
    setExpanded((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  if (tree.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-border py-12 text-center">
        <h3 className="text-lg font-medium text-text-primary">No WBS Structure</h3>
        <p className="mt-2 text-text-secondary">
          No WBS nodes or activities found for this project.
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-border bg-surface/50 shadow-sm">
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead className="border-b border-border bg-surface/80">
            <tr>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary">
                Code
              </th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary">
                Name
              </th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary">
                Status
              </th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary">
                % Complete
              </th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary">
                Start Date
              </th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary">
                Finish Date
              </th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary">
                Duration
              </th>
              <th className="px-4 py-3 text-left text-sm font-semibold text-text-secondary">
                Float
              </th>
              <th className="px-4 py-3 text-right text-sm font-semibold text-text-secondary">
                Actions
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border/50">
            {tree.map((node) => (
              <TreeRow
                key={node.id}
                node={node}
                depth={0}
                expanded={expanded}
                toggle={toggle}
                projectId={projectId}
                progressEdit={progressEdit}
                setProgressEdit={setProgressEdit}
                pendingId={pendingId}
                progressMutationIsPending={progressMutationIsPending}
                onSaveProgress={onSaveProgress}
                onStartActivity={onStartActivity}
                onCompleteActivity={onCompleteActivity}
              />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
