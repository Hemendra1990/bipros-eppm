import React, { useState, useRef, useCallback } from "react";
import { ChevronRight } from "lucide-react";

export interface TreeNode {
  id: string;
  code: string;
  name: string;
  children?: TreeNode[];
  [key: string]: unknown;
}

interface TreeViewProps<T = TreeNode> {
  nodes: T[];
  onNodeClick?: (node: T) => void;
  renderNode?: (node: T) => React.ReactNode;
  level?: number;
  draggable?: boolean;
  onMoveNode?: (nodeId: string, newParentId: string | null) => void;
}

interface DragState {
  draggedId: string | null;
  dropTargetId: string | null;
  dropPosition: "on" | "root" | null;
}

const DragContext = React.createContext<{
  dragState: DragState;
  setDragState: React.Dispatch<React.SetStateAction<DragState>>;
  onMoveNode?: (nodeId: string, newParentId: string | null) => void;
  allNodeIds: Set<string>;
} | null>(null);

function collectIds<T extends { id: string; children?: T[] }>(nodes: T[], set: Set<string>) {
  for (const node of nodes) {
    set.add(node.id);
    if (node.children) collectIds(node.children, set);
  }
}

function isDescendant<T extends { id: string; children?: T[] }>(
  nodes: T[],
  parentId: string,
  childId: string
): boolean {
  for (const node of nodes) {
    if (node.id === parentId) {
      return hasDescendant(node, childId);
    }
    if (node.children && isDescendant(node.children, parentId, childId)) {
      return true;
    }
  }
  return false;
}

function hasDescendant<T extends { id: string; children?: T[] }>(node: T, targetId: string): boolean {
  if (!node.children) return false;
  for (const child of node.children) {
    if (child.id === targetId) return true;
    if (hasDescendant(child, targetId)) return true;
  }
  return false;
}

function TreeNodeItem<T extends { id: string; code: string; name: string; children?: T[] }>({
  node,
  nodes,
  onNodeClick,
  renderNode,
  level,
  expanded,
  toggleNode,
  draggable,
}: {
  node: T;
  nodes: T[];
  onNodeClick?: (node: T) => void;
  renderNode?: (node: T) => React.ReactNode;
  level: number;
  expanded: Record<string, boolean>;
  toggleNode: (id: string) => void;
  draggable?: boolean;
}) {
  const ctx = React.useContext(DragContext);
  const rowRef = useRef<HTMLDivElement>(null);

  const handleDragStart = useCallback(
    (e: React.DragEvent) => {
      if (!ctx) return;
      e.dataTransfer.setData("text/plain", node.id);
      e.dataTransfer.effectAllowed = "move";
      ctx.setDragState((prev) => ({ ...prev, draggedId: node.id }));
    },
    [ctx, node.id]
  );

  const handleDragEnd = useCallback(() => {
    if (!ctx) return;
    ctx.setDragState({ draggedId: null, dropTargetId: null, dropPosition: null });
  }, [ctx]);

  const handleDragOver = useCallback(
    (e: React.DragEvent) => {
      if (!ctx || !ctx.dragState.draggedId) return;
      if (ctx.dragState.draggedId === node.id) return;
      // Prevent dropping on a descendant
      if (isDescendant(nodes, ctx.dragState.draggedId, node.id)) return;
      e.preventDefault();
      e.dataTransfer.dropEffect = "move";
      ctx.setDragState((prev) => ({
        ...prev,
        dropTargetId: node.id,
        dropPosition: "on",
      }));
    },
    [ctx, node.id, nodes]
  );

  const handleDragLeave = useCallback(
    (e: React.DragEvent) => {
      if (!ctx) return;
      // Only clear if actually leaving this element
      if (rowRef.current && !rowRef.current.contains(e.relatedTarget as Node)) {
        ctx.setDragState((prev) => ({
          ...prev,
          dropTargetId: prev.dropTargetId === node.id ? null : prev.dropTargetId,
          dropPosition: prev.dropTargetId === node.id ? null : prev.dropPosition,
        }));
      }
    },
    [ctx, node.id]
  );

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      if (!ctx || !ctx.dragState.draggedId) return;
      if (ctx.dragState.draggedId === node.id) return;
      if (isDescendant(nodes, ctx.dragState.draggedId, node.id)) return;
      ctx.onMoveNode?.(ctx.dragState.draggedId, node.id);
      ctx.setDragState({ draggedId: null, dropTargetId: null, dropPosition: null });
    },
    [ctx, node.id, nodes]
  );

  const isDragging = ctx?.dragState.draggedId === node.id;
  const isDropTarget = ctx?.dragState.dropTargetId === node.id && ctx?.dragState.dropPosition === "on";

  return (
    <li>
      <div
        ref={rowRef}
        className={`flex items-center gap-2 ${isDragging ? "opacity-40" : ""}`}
        draggable={draggable}
        onDragStart={draggable ? handleDragStart : undefined}
        onDragEnd={draggable ? handleDragEnd : undefined}
        onDragOver={draggable ? handleDragOver : undefined}
        onDragLeave={draggable ? handleDragLeave : undefined}
        onDrop={draggable ? handleDrop : undefined}
      >
        {node.children && node.children.length > 0 ? (
          <button
            onClick={() => toggleNode(node.id)}
            className="p-0.5 hover:bg-slate-800/50 rounded"
          >
            <ChevronRight
              size={16}
              className={`text-slate-500 transition-transform ${expanded[node.id] ? "rotate-90" : ""}`}
            />
          </button>
        ) : (
          <div className="w-6" />
        )}
        <div
          role="button"
          tabIndex={0}
          onClick={() => onNodeClick?.(node)}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === " ") onNodeClick?.(node);
          }}
          className={`flex-1 rounded-lg px-2 py-1 text-left text-sm transition-colors cursor-pointer ${
            isDropTarget
              ? "bg-blue-500/20 ring-2 ring-blue-500/50 ring-inset"
              : "hover:bg-slate-800/50"
          }`}
        >
          {renderNode ? (
            renderNode(node)
          ) : (
            <span>
              <span className="font-medium text-blue-400">{node.code}</span>
              <span className="ml-2 text-slate-300">{node.name}</span>
              {node.children && (
                <span className="ml-2 text-xs text-slate-500">({node.children.length})</span>
              )}
            </span>
          )}
        </div>
      </div>

      {expanded[node.id] && node.children && node.children.length > 0 && (
        <div className="ml-6 border-l border-slate-700 pl-0">
          <TreeNodeList
            nodes={node.children}
            allNodes={nodes}
            onNodeClick={onNodeClick}
            renderNode={renderNode}
            level={level + 1}
            expanded={expanded}
            toggleNode={toggleNode}
            draggable={draggable}
          />
        </div>
      )}
    </li>
  );
}

function TreeNodeList<T extends { id: string; code: string; name: string; children?: T[] }>({
  nodes,
  allNodes,
  onNodeClick,
  renderNode,
  level,
  expanded,
  toggleNode,
  draggable,
}: {
  nodes: T[];
  allNodes: T[];
  onNodeClick?: (node: T) => void;
  renderNode?: (node: T) => React.ReactNode;
  level: number;
  expanded: Record<string, boolean>;
  toggleNode: (id: string) => void;
  draggable?: boolean;
}) {
  return (
    <ul className="space-y-1">
      {nodes.map((node) => (
        <TreeNodeItem
          key={node.id}
          node={node}
          nodes={allNodes}
          onNodeClick={onNodeClick}
          renderNode={renderNode}
          level={level}
          expanded={expanded}
          toggleNode={toggleNode}
          draggable={draggable}
        />
      ))}
    </ul>
  );
}

export function TreeView<T extends { id: string; code: string; name: string; children?: T[] }>({
  nodes,
  onNodeClick,
  renderNode,
  level = 0,
  draggable = false,
  onMoveNode,
}: TreeViewProps<T>) {
  const [expanded, setExpanded] = useState<Record<string, boolean>>(() => {
    const initial: Record<string, boolean> = {};
    const expandAll = (items: T[]) => {
      for (const item of items) {
        if (item.children && item.children.length > 0) {
          initial[item.id] = true;
          expandAll(item.children);
        }
      }
    };
    expandAll(nodes);
    return initial;
  });

  const [dragState, setDragState] = useState<DragState>({
    draggedId: null,
    dropTargetId: null,
    dropPosition: null,
  });

  const toggleNode = useCallback((id: string) => {
    setExpanded((prev) => ({ ...prev, [id]: !prev[id] }));
  }, []);

  const allNodeIds = React.useMemo(() => {
    const set = new Set<string>();
    collectIds(nodes, set);
    return set;
  }, [nodes]);

  const handleRootDragOver = useCallback(
    (e: React.DragEvent) => {
      if (!dragState.draggedId) return;
      e.preventDefault();
      e.dataTransfer.dropEffect = "move";
      setDragState((prev) => ({ ...prev, dropTargetId: "__root__", dropPosition: "root" }));
    },
    [dragState.draggedId]
  );

  const handleRootDragLeave = useCallback(() => {
    setDragState((prev) => ({
      ...prev,
      dropTargetId: prev.dropTargetId === "__root__" ? null : prev.dropTargetId,
      dropPosition: prev.dropTargetId === "__root__" ? null : prev.dropPosition,
    }));
  }, []);

  const handleRootDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      if (!dragState.draggedId) return;
      onMoveNode?.(dragState.draggedId, null);
      setDragState({ draggedId: null, dropTargetId: null, dropPosition: null });
    },
    [dragState.draggedId, onMoveNode]
  );

  const isRootDropTarget = dragState.dropTargetId === "__root__" && dragState.dropPosition === "root";

  return (
    <DragContext.Provider value={{ dragState, setDragState, onMoveNode, allNodeIds }}>
      <div>
        <TreeNodeList
          nodes={nodes}
          allNodes={nodes}
          onNodeClick={onNodeClick}
          renderNode={renderNode}
          level={level}
          expanded={expanded}
          toggleNode={toggleNode}
          draggable={draggable}
        />
        {draggable && dragState.draggedId && (
          <div
            onDragOver={handleRootDragOver}
            onDragLeave={handleRootDragLeave}
            onDrop={handleRootDrop}
            className={`mt-2 rounded-lg border-2 border-dashed px-4 py-3 text-center text-xs transition-colors ${
              isRootDropTarget
                ? "border-blue-500 bg-blue-500/10 text-blue-400"
                : "border-slate-700 text-slate-500"
            }`}
          >
            Drop here to make a root node
          </div>
        )}
      </div>
    </DragContext.Provider>
  );
}
