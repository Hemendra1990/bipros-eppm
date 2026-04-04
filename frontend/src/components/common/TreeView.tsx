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
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});

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
                className="p-0.5 hover:bg-gray-200"
              >
                <ChevronRight
                  size={16}
                  className={`transition-transform ${expanded[node.id] ? "rotate-90" : ""}`}
                />
              </button>
            ) : (
              <div className="w-6" />
            )}
            <button
              onClick={() => onNodeClick?.(node)}
              className="flex-1 rounded px-2 py-1 text-left text-sm hover:bg-blue-50"
            >
              {renderNode ? renderNode(node) : (
                <span>
                  <span className="font-medium text-blue-600">{node.code}</span>
                  <span className="ml-2 text-gray-700">{node.name}</span>
                  {node.children && (
                    <span className="ml-2 text-xs text-gray-500">
                      ({node.children.length})
                    </span>
                  )}
                </span>
              )}
            </button>
          </div>

          {expanded[node.id] && node.children && node.children.length > 0 && (
            <div className="ml-6 border-l border-gray-200 pl-0">
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
