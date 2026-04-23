"use client";

import { useMemo } from "react";
import type { WbsNodeResponse } from "@/lib/types";

interface WbsNodePickerProps {
  tree: WbsNodeResponse[];
  value: string | null;
  mappedNodeIds: Set<string>;
  onChange: (id: string | null) => void;
  disabled?: boolean;
  excludeNodeId?: string;
}

interface FlatEntry {
  id: string;
  label: string;
  mapped: boolean;
}

function flatten(
  nodes: WbsNodeResponse[],
  depth: number,
  mapped: Set<string>,
  out: FlatEntry[]
): void {
  const sorted = [...nodes].sort((a, b) => a.sortOrder - b.sortOrder);
  for (const n of sorted) {
    const indent = "  ".repeat(depth);
    const prefix = depth === 0 ? "" : "└─ ";
    out.push({
      id: n.id,
      label: `${indent}${prefix}${n.code} — ${n.name}`,
      mapped: mapped.has(n.id),
    });
    if (n.children?.length) flatten(n.children, depth + 1, mapped, out);
  }
}

/**
 * Flat indented node dropdown for attaching a polygon to a WBS node. The tree
 * is shallow (≤ a few dozen nodes for a typical project), so a modal tree
 * view would be over-engineered. Already-mapped nodes are greyed out so the
 * 1:1 node-to-polygon invariant is enforced at pick-time.
 */
export function WbsNodePicker({
  tree,
  value,
  mappedNodeIds,
  onChange,
  disabled,
  excludeNodeId,
}: WbsNodePickerProps) {
  const entries = useMemo(() => {
    const out: FlatEntry[] = [];
    flatten(tree, 0, mappedNodeIds, out);
    return out;
  }, [tree, mappedNodeIds]);

  return (
    <select
      value={value ?? ""}
      onChange={(e) => onChange(e.target.value || null)}
      disabled={disabled}
      className="w-full rounded-md border border-border bg-surface px-2 py-1.5 text-sm text-text-primary"
    >
      <option value="">— choose WBS node —</option>
      {entries.map((e) => (
        <option
          key={e.id}
          value={e.id}
          disabled={e.mapped && e.id !== excludeNodeId}
        >
          {e.label}
          {e.mapped && e.id !== excludeNodeId ? " · ✓ mapped" : ""}
        </option>
      ))}
    </select>
  );
}
