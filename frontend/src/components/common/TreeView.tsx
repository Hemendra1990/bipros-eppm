import React, { useState } from "react";
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
}

export function TreeView<T extends { id: string; code: string; name: string; children?: T[] }>({
  nodes,
  onNodeClick,
  renderNode,
  level = 0,
}: TreeViewProps<T>) {
  const [expanded, setExpanded] = useState<Record<string, boolean>>(() => {
    // Auto-expand nodes that have children
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

  const toggleNode = (id: string) => {
    setExpanded((prev) => ({
      ...prev,
      [id]: !prev[id],
    }));
  };

  return (
    <ul className="space-y-1">
      {nodes.map((node) => (
        <li key={node.id}>
          <div className="flex items-center gap-2">
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
              onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') onNodeClick?.(node); }}
              className="flex-1 rounded-lg px-2 py-1 text-left text-sm hover:bg-slate-800/50 transition-colors cursor-pointer"
            >
              {renderNode ? renderNode(node) : (
                <span>
                  <span className="font-medium text-blue-400">{node.code}</span>
                  <span className="ml-2 text-slate-300">{node.name}</span>
                  {node.children && (
                    <span className="ml-2 text-xs text-slate-500">
                      ({node.children.length})
                    </span>
                  )}
                </span>
              )}
            </div>
          </div>

          {expanded[node.id] && node.children && node.children.length > 0 && (
            <div className="ml-6 border-l border-slate-700 pl-0">
              <TreeView
                nodes={node.children}
                onNodeClick={onNodeClick}
                renderNode={renderNode}
                level={level + 1}
              />
            </div>
          )}
        </li>
      ))}
    </ul>
  );
}
